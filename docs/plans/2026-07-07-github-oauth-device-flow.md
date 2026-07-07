# GitHub OAuth Device Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Вход через GitHub по device flow: получить токен без ручного PAT, сохранить безопасно, скормить движку синка.

**Architecture:** `GitHubDeviceAuth` (Ktor, реализует интерфейс `DeviceAuth`) делает HTTP к GitHub; `poll` — стейт-машина опроса. Токен хранится через `TokenStore` (Android: EncryptedSharedPreferences). `AuthViewModel` оркестрирует и отдаёт состояние в `LoginScreen`. Логика тестируется на Ktor MockEngine и фейках (`runTest`).

**Tech Stack:** Ktor client 2.3.13 (core/okhttp/content-negotiation) + kotlinx-serialization-json 1.7.3, androidx.security-crypto 1.1.0-alpha06, Compose, корутины.

## Global Constraints

- Пакеты: `app.obsidianmd.auth`, UI в `app.obsidianmd.ui`.
- `client_id` — `BuildConfig.GITHUB_CLIENT_ID` из `local.properties` (ключ `github.clientId`), не коммитится.
- HTTP через инъектируемый Ktor `HttpClient` (в тестах — `MockEngine`); сеть в юнит-тестах не трогаем.
- `delay` в `poll` — под `runTest` виртуальное время (тесты мгновенны).
- `SyncConfig.token` берётся из `TokenStore`; `BuildConfig.SYNC_TOKEN` удаляется (URL остаётся).
- Аналитика: аналитического стека нет — не вводится (как в предыдущих слайсах).
- Ktor версии 2.3.13 / serialization 1.7.3 — при несовпадении с Kotlin 2.0.21 developing подбирает ближайшую рабочую (калибровка, не смена дизайна).

---

### Task 1: Зависимости + DTO + requestDeviceCode (Ktor + MockEngine)

**Files:**
- Modify: `gradle/libs.versions.toml` (ktor/serialization/security версии и библиотеки + плагин)
- Modify: `composeApp/build.gradle.kts` (плагин serialization; ktor в commonMain; okhttp в androidMain; mock в commonTest)
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/auth/GitHubDeviceAuth.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/auth/GitHubDeviceAuthTest.kt`

**Interfaces:**
- Produces: `DeviceAuthorization`, `AuthResult`, `interface DeviceAuth`, `class GitHubDeviceAuth(http, clientId) : DeviceAuth` c `requestDeviceCode()`.

- [ ] **Step 1: Зависимости**

`gradle/libs.versions.toml` — в `[versions]`:
```toml
ktor = "2.3.13"
serialization = "1.7.3"
securityCrypto = "1.1.0-alpha06"
```
в `[libraries]`:
```toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "securityCrypto" }
```
в `[plugins]`:
```toml
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`composeApp/build.gradle.kts` — добавить плагин:
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}
```
и зависимости в `sourceSets`:
```kotlin
        commonMain.dependencies {
            // ...существующее...
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            // ...существующее...
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
        }
        commonTest.dependencies {
            // ...существующее...
            implementation(libs.ktor.client.mock)
        }
```

- [ ] **Step 2: Падающий тест requestDeviceCode**

`composeApp/src/commonTest/kotlin/app/obsidianmd/auth/GitHubDeviceAuthTest.kt`:
```kotlin
package app.obsidianmd.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private fun clientReturning(vararg bodies: String): HttpClient {
    var i = 0
    val engine = MockEngine {
        respond(
            content = bodies[minOf(i++, bodies.lastIndex)],
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    return HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
}

class GitHubDeviceAuthTest {
    @Test
    fun requestDeviceCode_parses_response() = runTest {
        val http = clientReturning(
            """{"device_code":"dc","user_code":"WXYZ-1234","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
        )
        val auth = GitHubDeviceAuth(http, "client123")
        val result = auth.requestDeviceCode()
        assertEquals("dc", result.deviceCode)
        assertEquals("WXYZ-1234", result.userCode)
        assertEquals("https://github.com/login/device", result.verificationUri)
        assertEquals(5, result.interval)
        assertEquals(900, result.expiresIn)
    }
}
```

- [ ] **Step 3: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.GitHubDeviceAuthTest"`
Expected: FAIL — классов нет (ошибка компиляции).

- [ ] **Step 4: Реализация DTO + интерфейс + requestDeviceCode**

`composeApp/src/commonMain/kotlin/app/obsidianmd/auth/GitHubDeviceAuth.kt`:
```kotlin
package app.obsidianmd.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceAuthorization(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    val interval: Int,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    val error: String? = null,
)

sealed interface AuthResult {
    data class Success(val token: String) : AuthResult
    data class Failed(val reason: String) : AuthResult
}

interface DeviceAuth {
    suspend fun requestDeviceCode(): DeviceAuthorization
    suspend fun poll(auth: DeviceAuthorization): AuthResult
}

class GitHubDeviceAuth(
    private val http: HttpClient,
    private val clientId: String,
) : DeviceAuth {

    override suspend fun requestDeviceCode(): DeviceAuthorization =
        http.post("https://github.com/login/device/code") {
            headers { append(HttpHeaders.Accept, "application/json") }
            parameter("client_id", clientId)
            parameter("scope", "repo")
        }.body()

    override suspend fun poll(auth: DeviceAuthorization): AuthResult {
        // реализуется в Task 2
        return AuthResult.Failed("not implemented")
    }
}
```

- [ ] **Step 5: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.GitHubDeviceAuthTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/commonMain/kotlin/app/obsidianmd/auth composeApp/src/commonTest/kotlin/app/obsidianmd/auth
git commit -m "feat: GitHubDeviceAuth.requestDeviceCode via Ktor + tests"
```

---

### Task 2: poll — стейт-машина опроса

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/auth/GitHubDeviceAuth.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/auth/GitHubDeviceAuthTest.kt` (добавить)

**Interfaces:**
- Consumes: `requestDeviceCode`/DTO (Task 1). Produces: рабочий `poll`.

- [ ] **Step 1: Падающие тесты poll**

Добавить в `GitHubDeviceAuthTest`:
```kotlin
    private val da = DeviceAuthorization("dc", "UC", "https://github.com/login/device", interval = 1, expiresIn = 100)

    @Test
    fun poll_pending_then_success() = runTest {
        val http = clientReturning(
            """{"error":"authorization_pending"}""",
            """{"error":"authorization_pending"}""",
            """{"access_token":"gho_abc"}""",
        )
        val result = GitHubDeviceAuth(http, "c").poll(da)
        assertEquals(AuthResult.Success("gho_abc"), result)
    }

    @Test
    fun poll_slow_down_then_success() = runTest {
        val http = clientReturning(
            """{"error":"slow_down"}""",
            """{"access_token":"gho_x"}""",
        )
        assertEquals(AuthResult.Success("gho_x"), GitHubDeviceAuth(http, "c").poll(da))
    }

    @Test
    fun poll_expired_token_fails() = runTest {
        val http = clientReturning("""{"error":"expired_token"}""")
        val result = GitHubDeviceAuth(http, "c").poll(da)
        assertEquals(AuthResult.Failed("expired_token"), result)
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.GitHubDeviceAuthTest"`
Expected: FAIL — `poll` возвращает `Failed("not implemented")`.

- [ ] **Step 3: Реализация poll (заменить заглушку)**

```kotlin
    override suspend fun poll(auth: DeviceAuthorization): AuthResult {
        var interval = auth.interval
        var waited = 0
        while (waited <= auth.expiresIn) {
            delay(interval * 1000L)
            waited += interval
            val resp: TokenResponse = http.post("https://github.com/login/oauth/access_token") {
                headers { append(HttpHeaders.Accept, "application/json") }
                parameter("client_id", clientId)
                parameter("device_code", auth.deviceCode)
                parameter("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }.body()
            when {
                resp.accessToken != null -> return AuthResult.Success(resp.accessToken)
                resp.error == "authorization_pending" -> Unit
                resp.error == "slow_down" -> interval += 5
                else -> return AuthResult.Failed(resp.error ?: "unknown")
            }
        }
        return AuthResult.Failed("expired")
    }
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.GitHubDeviceAuthTest"`
Expected: PASS (все 4 теста).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src
git commit -m "feat: GitHubDeviceAuth.poll state machine (pending/slow_down/expired)"
```

---

### Task 3: TokenStore + FakeTokenStore

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/auth/TokenStore.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/auth/FakeTokenStore.kt` (+ тест контракта)

**Interfaces:**
- Produces: `interface TokenStore { save/get/clear }`; тестовый `FakeTokenStore`.

- [ ] **Step 1: Падающий тест контракта (с FakeTokenStore)**

`composeApp/src/commonTest/kotlin/app/obsidianmd/auth/FakeTokenStore.kt`:
```kotlin
package app.obsidianmd.auth

class FakeTokenStore : TokenStore {
    private var token: String? = null
    override fun save(token: String) { this.token = token }
    override fun get(): String? = token
    override fun clear() { token = null }
}
```
Добавить тест в тот же файл (или отдельный `TokenStoreContractTest.kt`):
```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreContractTest {
    @Test
    fun save_get_clear() {
        val store = FakeTokenStore()
        assertNull(store.get())
        store.save("gho_1")
        assertEquals("gho_1", store.get())
        store.clear()
        assertNull(store.get())
    }
}
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.TokenStoreContractTest"`
Expected: FAIL — интерфейса `TokenStore` нет (ошибка компиляции).

- [ ] **Step 3: Реализация интерфейса**

`composeApp/src/commonMain/kotlin/app/obsidianmd/auth/TokenStore.kt`:
```kotlin
package app.obsidianmd.auth

interface TokenStore {
    fun save(token: String)
    fun get(): String?
    fun clear()
}
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.TokenStoreContractTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/auth/TokenStore.kt composeApp/src/commonTest/kotlin/app/obsidianmd/auth/FakeTokenStore.kt composeApp/src/commonTest/kotlin/app/obsidianmd/auth/TokenStoreContractTest.kt
git commit -m "feat: TokenStore interface + FakeTokenStore + contract test"
```

---

### Task 4: AuthViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/auth/AuthViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/auth/AuthViewModelTest.kt`

**Interfaces:**
- Consumes: `DeviceAuth`, `TokenStore`, `DeviceAuthorization`, `AuthResult`.
- Produces: `sealed interface AuthState`; `class AuthViewModel(auth, store, scope)` c `state: StateFlow<AuthState>`, `login()`.

- [ ] **Step 1: Падающий тест (фейковый DeviceAuth)**

`composeApp/src/commonTest/kotlin/app/obsidianmd/auth/AuthViewModelTest.kt`:
```kotlin
package app.obsidianmd.auth

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDeviceAuth(val result: AuthResult) : DeviceAuth {
    override suspend fun requestDeviceCode() =
        DeviceAuthorization("dc", "UC-1", "https://github.com/login/device", 1, 100)
    override suspend fun poll(auth: DeviceAuthorization) = result
}

class AuthViewModelTest {
    @Test
    fun login_success_saves_token_and_reports_success() = runTest {
        val store = FakeTokenStore()
        val vm = AuthViewModel(FakeDeviceAuth(AuthResult.Success("gho_ok")), store, this)
        vm.login()
        advanceUntilIdle()
        assertEquals("gho_ok", store.get())
        assertTrue(vm.state.value is AuthState.Success)
    }

    @Test
    fun login_exposes_user_code_while_awaiting() = runTest {
        // poll «висит» → проверяем промежуточное состояние AwaitingUser
        val store = FakeTokenStore()
        val slow = object : DeviceAuth {
            override suspend fun requestDeviceCode() =
                DeviceAuthorization("dc", "UC-2", "https://github.com/login/device", 1, 100)
            override suspend fun poll(auth: DeviceAuthorization): AuthResult {
                kotlinx.coroutines.delay(1000); return AuthResult.Success("t")
            }
        }
        val vm = AuthViewModel(slow, store, this)
        vm.login()
        // до advanceUntilIdle: requestDeviceCode отработал, poll в delay
        kotlinx.coroutines.test.runCurrent()
        val s = vm.state.value
        assertTrue(s is AuthState.AwaitingUser && s.userCode == "UC-2")
    }

    @Test
    fun login_failure_reports_failed() = runTest {
        val vm = AuthViewModel(FakeDeviceAuth(AuthResult.Failed("expired")), FakeTokenStore(), this)
        vm.login()
        advanceUntilIdle()
        assertTrue(vm.state.value is AuthState.Failed)
    }
}
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.AuthViewModelTest"`
Expected: FAIL — `AuthViewModel`/`AuthState` нет.

- [ ] **Step 3: Реализация**

`composeApp/src/commonMain/kotlin/app/obsidianmd/auth/AuthViewModel.kt`:
```kotlin
package app.obsidianmd.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Idle : AuthState
    data class AwaitingUser(val userCode: String, val verificationUri: String) : AuthState
    data object Success : AuthState
    data class Failed(val reason: String) : AuthState
}

class AuthViewModel(
    private val auth: DeviceAuth,
    private val store: TokenStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Idle)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun login() {
        scope.launch {
            try {
                val da = auth.requestDeviceCode()
                _state.value = AuthState.AwaitingUser(da.userCode, da.verificationUri)
                _state.value = when (val r = auth.poll(da)) {
                    is AuthResult.Success -> {
                        store.save(r.token)
                        AuthState.Success
                    }
                    is AuthResult.Failed -> AuthState.Failed(r.reason)
                }
            } catch (e: Exception) {
                _state.value = AuthState.Failed(e.message ?: e.toString())
            }
        }
    }
}
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.AuthViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/auth/AuthViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/auth/AuthViewModelTest.kt
git commit -m "feat: AuthViewModel orchestrates device flow + token save"
```

---

### Task 5: LoginScreen (Compose)

UI — ручная приёмка (юнит-тестов Compose нет).

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/LoginScreen.kt`

**Interfaces:**
- Consumes: `AuthState`.
- Produces: `LoginScreen(state, onLogin, onOpenUrl)`.

- [ ] **Step 1: LoginScreen.kt**

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.auth.AuthState

@Composable
fun LoginScreen(
    state: AuthState,
    onLogin: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            AuthState.Idle -> Button(onClick = onLogin) { Text("Войти через GitHub") }
            is AuthState.AwaitingUser -> {
                Text("Код: ${state.userCode}")
                Button(
                    onClick = { onOpenUrl(state.verificationUri) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Открыть GitHub") }
                Text("Ожидание подтверждения…", Modifier.padding(top = 16.dp))
            }
            AuthState.Success -> Text("Вход выполнен")
            is AuthState.Failed -> {
                Text("Ошибка входа: ${state.reason}")
                Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) { Text("Повторить") }
            }
        }
    }
}
```

- [ ] **Step 2: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/LoginScreen.kt
git commit -m "feat: LoginScreen (device flow UI)"
```

---

### Task 6: Android-проводка — EncryptedTokenStore, BuildConfig, гейтинг входа

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/obsidianmd/auth/EncryptedTokenStore.kt`
- Modify: `composeApp/build.gradle.kts` (GITHUB_CLIENT_ID; убрать SYNC_TOKEN)
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt`

**Interfaces:**
- Consumes: `TokenStore`, `GitHubDeviceAuth`, `AuthViewModel`, `LoginScreen`, `VaultViewModel`, `SyncConfig`.

- [ ] **Step 1: build.gradle.kts — GITHUB_CLIENT_ID, убрать SYNC_TOKEN**

В `android { defaultConfig { ... } }`: заменить строку `SYNC_TOKEN` на client id:
```kotlin
        buildConfigField("String", "SYNC_REMOTE_URL", "\"${localProp("sync.remoteUrl")}\"")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${localProp("github.clientId")}\"")
```
(строку `buildConfigField(... "SYNC_TOKEN" ...)` удалить.)

- [ ] **Step 2: EncryptedTokenStore.kt (androidMain)**

```kotlin
package app.obsidianmd.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedTokenStore(context: Context) : TokenStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "obsidian_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun save(token: String) { prefs.edit().putString("github_token", token).apply() }
    override fun get(): String? = prefs.getString("github_token", null)
    override fun clear() { prefs.edit().remove("github_token").apply() }
}
```

- [ ] **Step 3: MainActivity — гейтинг входа + токен из хранилища**

```kotlin
package app.obsidianmd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.obsidianmd.auth.AuthState
import app.obsidianmd.auth.AuthViewModel
import app.obsidianmd.auth.EncryptedTokenStore
import app.obsidianmd.auth.GitHubDeviceAuth
import app.obsidianmd.sync.JGitSync
import app.obsidianmd.sync.SyncConfig
import app.obsidianmd.sync.UiConflictResolver
import app.obsidianmd.ui.LoginScreen
import app.obsidianmd.ui.VaultViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = EncryptedTokenStore(applicationContext)
        val repo = createRepository(applicationContext)
        val root = vaultRoot(applicationContext)

        val http = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val deviceAuth = GitHubDeviceAuth(http, BuildConfig.GITHUB_CLIENT_ID)
        val authVm = AuthViewModel(deviceAuth, store, lifecycleScope)

        setContent {
            var loggedIn by remember { mutableStateOf(store.get() != null) }
            val authState by authVm.state.collectAsState()
            if (authState is AuthState.Success && !loggedIn) loggedIn = true

            MaterialTheme {
                Surface {
                    if (!loggedIn) {
                        LoginScreen(
                            state = authState,
                            onLogin = authVm::login,
                            onOpenUrl = { url ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                        )
                    } else {
                        val syncConfig = BuildConfig.SYNC_REMOTE_URL.takeIf { it.isNotBlank() }?.let {
                            SyncConfig(remoteUrl = it, localPath = root.toString(), token = store.get())
                        }
                        val vm = VaultViewModel(
                            repo, lifecycleScope, Dispatchers.IO,
                            gitSync = JGitSync(), syncConfig = syncConfig,
                            resolver = UiConflictResolver(),
                        )
                        App(vm)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Ручная приёмка**

Прогнать приёмочные кейсы из спеки (вход через GitHub; токен сохраняется между запусками;
синк с токеном; отказ/таймаут).

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/androidMain
git commit -m "feat: wire GitHub login gating + EncryptedTokenStore + token into sync"
```

---

## Self-review

**Покрытие спеки:**
- DTO + `requestDeviceCode` (Ktor) → Task 1.
- `poll` стейт-машина (pending/slow_down/expired) → Task 2.
- `TokenStore` + фейк → Task 3; Android `EncryptedTokenStore` → Task 6.
- `AuthViewModel` (состояния, сохранение токена) → Task 4.
- `LoginScreen` → Task 5.
- Проводка: BuildConfig `GITHUB_CLIENT_ID`, удаление `SYNC_TOKEN`, токен из хранилища в `SyncConfig`, гейтинг входа, open-url intent → Task 6.
- Приёмочные кейсы → в спеке; Task 6 Step 5.
- Аналитика → неприменима (нет стека), отмечено в Global Constraints.

**Placeholder-скан:** реальный код/команды в каждом шаге; заглушка `poll` из Task 1 явно
заменяется в Task 2 (RED опирается на неё). Ошибки обрабатываются (`Failed`/`catch`).

**Согласованность типов:** `DeviceAuthorization(deviceCode,userCode,verificationUri,interval,expiresIn)`;
`AuthResult.{Success(token),Failed(reason)}`; `interface DeviceAuth{requestDeviceCode(),poll(auth)}`;
`GitHubDeviceAuth(http,clientId):DeviceAuth`; `TokenStore{save,get,clear}`;
`AuthState.{Idle,AwaitingUser(userCode,verificationUri),Success,Failed(reason)}`;
`AuthViewModel(auth,store,scope){state,login()}`; `LoginScreen(state,onLogin,onOpenUrl)` — имена
согласованы Task 1→6. `BuildConfig.GITHUB_CLIENT_ID` из `github.clientId`; `SYNC_TOKEN` удаляется.
