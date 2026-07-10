# Онбординг + выбор репозитория из GitHub — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Понятный первый запуск (объяснение → вход через GitHub с копированием кода → выбор репозитория из списка GitHub или вручную) и смена репозитория тем же способом из настроек.

**Architecture:** Новый клиент `GitHubRepos` (Ktor, инжектируемый `HttpClient`) отдаёт список репо; чистая `filterRepos` фильтрует по поиску; `RepoPickerViewModel` держит состояние Loading/Loaded/Error и сохраняет выбранный `clone_url` через колбэк. UI (`WelcomeScreen`, переработанный `LoginScreen`, `RepoPickerScreen`) — тонкий, проверяется вручную. `MainActivity` получает трёхступенчатый гейтинг: нет токена → онбординг-вход; токен без repoUrl → выбор репо; и то и другое → волт.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Ktor client (okhttp), kotlinx.serialization, kotlinx.coroutines-test, Ktor MockEngine.

## Global Constraints

- Вся тестируемая логика — в `commonMain`, пакет `app.obsidianmd.auth`; тесты — `commonTest`.
- `HttpClient` инжектируется извне; в тестах — Ktor `MockEngine` (см. `GitHubDeviceAuthTest`).
- Никаких новых зависимостей: копирование в буфер — через Compose `LocalClipboardManager`.
- Команда юнит-тестов: `./gradlew :composeApp:testDebugUnitTest`.
- Тексты для пользователя — через ресурсы (`Res.string.*`), русские строки.
- Одна страница репозиториев (100, `sort=updated`); пагинация — вне scope.

---

### Task 1: Клиент GitHubRepos + модель GitHubRepo

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/auth/GitHubRepos.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/auth/GitHubReposTest.kt`

**Interfaces:**
- Produces: `data class GitHubRepo(fullName: String, cloneUrl: String, private: Boolean)`;
  `interface RepoList { suspend fun list(token: String): List<GitHubRepo> }`;
  `class GitHubRepos(http: HttpClient) : RepoList`.

- [ ] **Step 1: Написать падающий тест (парсинг + пустой + ошибка)**

```kotlin
package app.obsidianmd.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun client(block: MockEngine.Config.() -> Unit): HttpClient {
    val engine = MockEngine.create { block() } // не используется; см. ниже
    error("placeholder")
}

private fun clientOk(body: String): HttpClient {
    val engine = MockEngine {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
}

private fun clientStatus(status: HttpStatusCode): HttpClient {
    val engine = MockEngine { respondError(status) }
    return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
}

class GitHubReposTest {
    @Test
    fun list_parses_repos() = runTest {
        val http = clientOk(
            """[
              {"full_name":"me/notes","clone_url":"https://github.com/me/notes.git","private":true},
              {"full_name":"org/wiki","clone_url":"https://github.com/org/wiki.git","private":false}
            ]"""
        )
        val repos = GitHubRepos(http).list("gho_token")
        assertEquals(2, repos.size)
        assertEquals("me/notes", repos[0].fullName)
        assertEquals("https://github.com/me/notes.git", repos[0].cloneUrl)
        assertTrue(repos[0].private)
        assertEquals("org/wiki", repos[1].fullName)
    }

    @Test
    fun list_empty() = runTest {
        assertTrue(GitHubRepos(clientOk("[]")).list("t").isEmpty())
    }

    @Test
    fun list_http_error_throws() = runTest {
        assertFailsWith<Exception> { GitHubRepos(clientStatus(HttpStatusCode.Unauthorized)).list("t") }
    }
}
```

> Примечание для разработчика: неиспользуемые заглушки `client(...)` не пиши — оставлены только `clientOk`/`clientStatus`. Удали `client(...)` перед запуском.

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.GitHubReposTest"`
Expected: FAIL — `GitHubRepos`/`GitHubRepo` не существуют (ошибка компиляции).

- [ ] **Step 3: Минимальная реализация**

```kotlin
package app.obsidianmd.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepo(
    @SerialName("full_name") val fullName: String,
    @SerialName("clone_url") val cloneUrl: String,
    val private: Boolean = false,
)

interface RepoList {
    suspend fun list(token: String): List<GitHubRepo>
}

class GitHubRepos(private val http: HttpClient) : RepoList {
    override suspend fun list(token: String): List<GitHubRepo> =
        http.get("https://api.github.com/user/repos") {
            expectSuccess = true // не-2xx → исключение
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append(HttpHeaders.Accept, "application/vnd.github+json")
            }
            url.parameters.append("per_page", "100")
            url.parameters.append("sort", "updated")
            url.parameters.append("affiliation", "owner,collaborator,organization_member")
        }.body()
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.GitHubReposTest"`
Expected: PASS (3 теста).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/auth/GitHubRepos.kt composeApp/src/commonTest/kotlin/app/obsidianmd/auth/GitHubReposTest.kt
git commit -m "feat: клиент списка GitHub-репозиториев"
```

---

### Task 2: Чистая фильтрация filterRepos

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/auth/GitHubRepos.kt` (добавить top-level функцию)
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/auth/FilterReposTest.kt`

**Interfaces:**
- Produces: `fun filterRepos(repos: List<GitHubRepo>, query: String): List<GitHubRepo>`.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package app.obsidianmd.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class FilterReposTest {
    private val repos = listOf(
        GitHubRepo("me/Notes", "https://github.com/me/Notes.git", true),
        GitHubRepo("me/wiki", "https://github.com/me/wiki.git", false),
        GitHubRepo("org/docs", "https://github.com/org/docs.git", false),
    )

    @Test
    fun empty_query_returns_all() {
        assertEquals(3, filterRepos(repos, "").size)
    }

    @Test
    fun matches_substring_case_insensitive() {
        val r = filterRepos(repos, "NOT")
        assertEquals(1, r.size)
        assertEquals("me/Notes", r[0].fullName)
    }

    @Test
    fun no_match_returns_empty() {
        assertEquals(0, filterRepos(repos, "zzz").size)
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.FilterReposTest"`
Expected: FAIL — `filterRepos` не существует.

- [ ] **Step 3: Минимальная реализация (в GitHubRepos.kt)**

```kotlin
fun filterRepos(repos: List<GitHubRepo>, query: String): List<GitHubRepo> {
    val q = query.trim()
    if (q.isEmpty()) return repos
    return repos.filter { it.fullName.contains(q, ignoreCase = true) }
}
```

- [ ] **Step 4: Запустить — PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.FilterReposTest"`
Expected: PASS (3 теста).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/auth/GitHubRepos.kt composeApp/src/commonTest/kotlin/app/obsidianmd/auth/FilterReposTest.kt
git commit -m "feat: фильтрация репозиториев по имени"
```

---

### Task 3: RepoPickerViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/auth/RepoPickerViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/auth/RepoPickerViewModelTest.kt`

**Interfaces:**
- Consumes: `RepoList`, `GitHubRepo` (Task 1).
- Produces: `sealed interface RepoPickerState { Loading; Loaded(repos); Error(reason) }`;
  `class RepoPickerViewModel(repos: RepoList, token: () -> String?, onPick: (String) -> Unit, scope: CoroutineScope)`
  с `val state: StateFlow<RepoPickerState>`, `fun load()`, `fun pick(cloneUrl: String)`.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package app.obsidianmd.auth

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeRepoList(val result: Result<List<GitHubRepo>>) : RepoList {
    override suspend fun list(token: String): List<GitHubRepo> = result.getOrThrow()
}

class RepoPickerViewModelTest {
    private val two = listOf(
        GitHubRepo("me/a", "https://github.com/me/a.git", false),
        GitHubRepo("me/b", "https://github.com/me/b.git", true),
    )

    @Test
    fun load_success_moves_to_loaded() = runTest {
        val vm = RepoPickerViewModel(FakeRepoList(Result.success(two)), { "t" }, {}, this)
        vm.load()
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is RepoPickerState.Loaded && s.repos.size == 2)
    }

    @Test
    fun load_failure_moves_to_error() = runTest {
        val vm = RepoPickerViewModel(FakeRepoList(Result.failure(RuntimeException("no net"))), { "t" }, {}, this)
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value is RepoPickerState.Error)
    }

    @Test
    fun pick_invokes_callback_with_clone_url() = runTest {
        var picked: String? = null
        val vm = RepoPickerViewModel(FakeRepoList(Result.success(two)), { "t" }, { picked = it }, this)
        vm.pick("https://github.com/me/b.git")
        assertEquals("https://github.com/me/b.git", picked)
    }
}
```

- [ ] **Step 2: Запустить — FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.RepoPickerViewModelTest"`
Expected: FAIL — `RepoPickerViewModel`/`RepoPickerState` не существуют.

- [ ] **Step 3: Минимальная реализация**

```kotlin
package app.obsidianmd.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RepoPickerState {
    data object Loading : RepoPickerState
    data class Loaded(val repos: List<GitHubRepo>) : RepoPickerState
    data class Error(val reason: String) : RepoPickerState
}

class RepoPickerViewModel(
    private val repos: RepoList,
    private val token: () -> String?,
    private val onPick: (String) -> Unit,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RepoPickerState>(RepoPickerState.Loading)
    val state: StateFlow<RepoPickerState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.value = RepoPickerState.Loading
            _state.value = try {
                RepoPickerState.Loaded(repos.list(token().orEmpty()))
            } catch (e: Exception) {
                RepoPickerState.Error(e.message ?: e.toString())
            }
        }
    }

    fun pick(cloneUrl: String) = onPick(cloneUrl)
}
```

- [ ] **Step 4: Запустить — PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.auth.RepoPickerViewModelTest"`
Expected: PASS (3 теста).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/auth/RepoPickerViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/auth/RepoPickerViewModelTest.kt
git commit -m "feat: RepoPickerViewModel — загрузка и выбор репозитория"
```

---

### Task 4: Строки ресурсов для онбординга

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Produces строки: `onboarding_title`, `onboarding_body`, `onboarding_sign_in`,
  `login_copy_code`, `login_code_copied`, `repo_pick_title`, `repo_pick_search`,
  `repo_pick_manual_label`, `repo_pick_manual_hint`, `repo_pick_from_github`,
  `repo_pick_error`, `action_retry` (если ещё нет — переиспользовать существующую).

- [ ] **Step 1: Добавить строки в strings.xml**

Открой `composeApp/src/commonMain/composeResources/values/strings.xml` и добавь (сохрани стиль файла; `action_retry` уже есть — не дублируй):

```xml
<string name="onboarding_title">Obsidian на телефоне</string>
<string name="onboarding_body">Читайте и редактируйте свой Obsidian-волт из GitHub-репозитория. Заметки синхронизируются по git.</string>
<string name="onboarding_sign_in">Войти через GitHub</string>
<string name="login_copy_code">Скопировать код</string>
<string name="login_code_copied">Скопировано</string>
<string name="repo_pick_title">Выберите репозиторий</string>
<string name="repo_pick_search">Поиск по репозиториям</string>
<string name="repo_pick_manual_label">Или введите URL вручную</string>
<string name="repo_pick_manual_hint">https://github.com/имя/репозиторий.git</string>
<string name="repo_pick_from_github">Выбрать из GitHub</string>
<string name="repo_pick_error">Не удалось загрузить список репозиториев</string>
```

- [ ] **Step 2: Проверить сборку ресурсов**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL — сгенерированы `Res.string.onboarding_title` и т.д.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat: строки онбординга и выбора репозитория"
```

---

### Task 5: WelcomeScreen + переработка LoginScreen (копирование кода)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/WelcomeScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/LoginScreen.kt`

**Interfaces:**
- Consumes: `AuthState` (auth), новые строки (Task 4).
- Produces: `@Composable fun WelcomeScreen(onSignIn: () -> Unit)`; обновлённый
  `LoginScreen(state, onLogin, onOpenUrl)` с кнопкой копирования кода.

Компоненты Compose проверяются вручную (см. приёмочные кейсы спеки), юнит-тестов нет.

- [ ] **Step 1: WelcomeScreen**

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.onboarding_body
import app.obsidianmd.resources.onboarding_sign_in
import app.obsidianmd.resources.onboarding_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun WelcomeScreen(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.onboarding_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(onClick = onSignIn, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(Res.string.onboarding_sign_in))
        }
    }
}
```

- [ ] **Step 2: Переработать LoginScreen — код в карточке + копирование**

Замени тело `LoginScreen` (файл `ui/LoginScreen.kt`). Ключевое: состояние `AwaitingUser` показывает код в `Card` моноширинным шрифтом, кнопку «Скопировать код» через `LocalClipboardManager`, затем «Открыть GitHub» и прогресс.

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.obsidianmd.auth.AuthState
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_retry
import app.obsidianmd.resources.login_code_copied
import app.obsidianmd.resources.login_copy_code
import app.obsidianmd.resources.login_error
import app.obsidianmd.resources.login_open_github
import app.obsidianmd.resources.login_sign_in
import app.obsidianmd.resources.login_signed_in
import app.obsidianmd.resources.login_waiting
import org.jetbrains.compose.resources.stringResource

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
            AuthState.Idle -> Button(onClick = onLogin) { Text(stringResource(Res.string.login_sign_in)) }
            is AuthState.AwaitingUser -> {
                val clipboard = LocalClipboardManager.current
                var copied by remember(state.userCode) { mutableStateOf(false) }
                Card {
                    Text(
                        state.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(state.userCode)); copied = true },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(stringResource(if (copied) Res.string.login_code_copied else Res.string.login_copy_code))
                }
                Button(
                    onClick = { onOpenUrl(state.verificationUri) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(Res.string.login_open_github)) }
                CircularProgressIndicator(Modifier.padding(top = 24.dp))
                Text(stringResource(Res.string.login_waiting), Modifier.padding(top = 8.dp))
            }
            AuthState.Success -> Text(stringResource(Res.string.login_signed_in))
            is AuthState.Failed -> {
                Text(stringResource(Res.string.login_error, state.reason))
                Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(Res.string.action_retry))
                }
            }
        }
    }
}
```

- [ ] **Step 3: Проверить сборку**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/WelcomeScreen.kt composeApp/src/commonMain/kotlin/app/obsidianmd/ui/LoginScreen.kt
git commit -m "feat: экран приветствия и копирование кода в device-авторизации"
```

---

### Task 6: RepoPickerScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/RepoPickerScreen.kt`

**Interfaces:**
- Consumes: `RepoPickerState`, `GitHubRepo`, `filterRepos` (Tasks 1–3), строки (Task 4).
- Produces: `@Composable fun RepoPickerScreen(state, onPick, onRetry, onManualSave)`.

Проверяется вручную (приёмочные кейсы), юнит-тестов нет.

- [ ] **Step 1: RepoPickerScreen**

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.auth.GitHubRepo
import app.obsidianmd.auth.RepoPickerState
import app.obsidianmd.auth.filterRepos
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_retry
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.repo_pick_error
import app.obsidianmd.resources.repo_pick_manual_hint
import app.obsidianmd.resources.repo_pick_manual_label
import app.obsidianmd.resources.repo_pick_search
import org.jetbrains.compose.resources.stringResource

@Composable
fun RepoPickerScreen(
    state: RepoPickerState,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
    onManualSave: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (state) {
            RepoPickerState.Loading -> Text(stringResource(Res.string.repo_pick_search))
            is RepoPickerState.Error -> {
                Text(stringResource(Res.string.repo_pick_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(Res.string.action_retry))
                }
            }
            is RepoPickerState.Loaded -> {
                var query by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.repo_pick_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(filterRepos(state.repos, query)) { repo ->
                        RepoRow(repo, onPick)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        ManualEntry(onManualSave)
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onPick: (String) -> Unit) {
    TextButton(onClick = { onPick(repo.cloneUrl) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (repo.private) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            }
            Text(repo.fullName)
        }
    }
}

@Composable
private fun ManualEntry(onManualSave: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(Res.string.repo_pick_manual_label)) },
        placeholder = { Text(stringResource(Res.string.repo_pick_manual_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onManualSave(url) },
        enabled = url.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(stringResource(Res.string.action_save)) }
}
```

- [ ] **Step 2: Проверить сборку**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Если `Icons.Filled.Lock` недоступен — заменить на `Icons.Filled.Star` или убрать иконку (не критично для функциональности).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/RepoPickerScreen.kt
git commit -m "feat: экран выбора репозитория (список + поиск + ручной ввод)"
```

---

### Task 7: Проводка в MainActivity + гейтинг по репозиторию

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt`

**Interfaces:**
- Consumes: `WelcomeScreen`, `LoginScreen`, `RepoPickerScreen`, `RepoPickerViewModel`,
  `GitHubRepos`, `SettingsViewModel.save`, существующие `store`/`settingsStore`/`http`.

- [ ] **Step 1: Трёхступенчатый гейтинг**

В `setContent` замени текущую двухступенчатую логику. Слушаем `settingsVm.url` как `StateFlow`. Собираем `RepoPickerViewModel` c `GitHubRepos(http)`, `token = store::get`, `onPick = settingsVm::save`.

```kotlin
setContent {
    var loggedIn by remember { mutableStateOf(store.get() != null) }
    val authState by authVm.state.collectAsState()
    if (authState is AuthState.Success && !loggedIn) loggedIn = true
    val repoUrl by settingsVm.url.collectAsState()
    val hasRepo = repoUrl.isNotBlank()

    if (loggedIn && hasRepo) {
        LaunchedEffect(Unit) {
            app.obsidianmd.sync.AutoSyncScheduler(applicationContext).schedule()
        }
    }

    MaterialTheme {
        Surface {
            when {
                !loggedIn -> androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                    when (authState) {
                        AuthState.Idle -> WelcomeScreen(onSignIn = authVm::login)
                        else -> LoginScreen(
                            state = authState,
                            onLogin = authVm::login,
                            onOpenUrl = { url ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                        )
                    }
                }
                !hasRepo -> {
                    val pickerVm = remember {
                        RepoPickerViewModel(
                            repos = GitHubRepos(http),
                            token = store::get,
                            onPick = settingsVm::save,
                            scope = lifecycleScope,
                        )
                    }
                    LaunchedEffect(Unit) { pickerVm.load() }
                    val pickerState by pickerVm.state.collectAsState()
                    androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                        RepoPickerScreen(
                            state = pickerState,
                            onPick = pickerVm::pick,
                            onRetry = pickerVm::load,
                            onManualSave = settingsVm::save,
                        )
                    }
                }
                else -> {
                    val vm = VaultViewModel(/* как сейчас */)
                    val aiVm = /* как сейчас */
                    App(vm, settingsVm, aiVm)
                }
            }
        }
    }
}
```

> Разработчику: блоки `VaultViewModel(...)` и `aiVm` перенеси из текущего `else`-ветки без изменений. Добавь импорты: `WelcomeScreen`, `RepoPickerScreen`, `app.obsidianmd.auth.RepoPickerViewModel`, `app.obsidianmd.auth.GitHubRepos`.

- [ ] **Step 2: Собрать и установить проверочно**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt
git commit -m "feat: онбординг-гейтинг — вход, затем выбор репозитория, затем волт"
```

---

### Task 8: Кнопка «Выбрать из GitHub» в настройках

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SettingsScreen.kt`

**Interfaces:**
- Consumes: `RepoPickerScreen`/`RepoPickerState` косвенно через новый параметр-колбэк.
- Produces: `SettingsScreen(..., onPickFromGitHub: () -> Unit)` — открывает выбор репо;
  выбранный URL сохраняется через существующий `onSave`.

Подход (ponytail): не встраивать список внутрь настроек, а переиспользовать overlay выбора репо на уровне `App`/экрана настроек. Простейший вариант — локальное состояние в `SettingsScreen`, показывающее `RepoPickerScreen` поверх, когда нажата кнопка. Но `RepoPickerScreen` требует загруженный `RepoPickerState`, который живёт в VM на уровне Activity. Поэтому прокинем колбэк `onPickFromGitHub` наружу, а сам overlay поднимем в `App`.

- [ ] **Step 1: Добавить кнопку и параметр**

В `SettingsScreen` рядом с полем URL добавь кнопку:

```kotlin
// в сигнатуру:
//   onPickFromGitHub: () -> Unit,
// под SettingField(repo url ...):
TextButton(onClick = onPickFromGitHub, modifier = Modifier.padding(top = 4.dp)) {
    Text(stringResource(Res.string.repo_pick_from_github))
}
```

- [ ] **Step 2: Поднять overlay выбора репо в App.kt**

В `App.kt` добавь состояние `var pickingRepo by remember { mutableStateOf(false) }` и, когда `showSettings && pickingRepo`, показывай `RepoPickerScreen` вместо `SettingsScreen`. Для этого `App` должен принять `repoPicker: @Composable (onDone: () -> Unit) -> Unit` от Activity (там живёт `RepoPickerViewModel`).

Простейшая реализация (ponytail): `App` принимает новый необязательный параметр
`onOpenRepoPicker: (() -> Unit)?` и сам `RepoPickerScreen` рендерится Activity через тот же
overlay-механизм. Чтобы не разводить сложную проводку, допускается такой минимальный контракт:

```kotlin
// App(...) новый параметр:
//   repoPickerContent: (@Composable (onClose: () -> Unit) -> Unit)? = null,
// в SettingsScreen передаём onPickFromGitHub = { pickingRepo = true }
// и рядом:
if (showSettings && pickingRepo && repoPickerContent != null) {
    repoPickerContent { pickingRepo = false }
}
```

В `MainActivity` передаём:

```kotlin
App(
    vm, settingsVm, aiVm,
    repoPickerContent = { onClose ->
        val pickerVm = remember {
            RepoPickerViewModel(GitHubRepos(http), store::get,
                onPick = { url -> settingsVm.save(url); onClose() }, lifecycleScope)
        }
        LaunchedEffect(Unit) { pickerVm.load() }
        val s by pickerVm.state.collectAsState()
        RepoPickerScreen(s, pickerVm::pick, pickerVm::load, onManualSave = { url -> settingsVm.save(url); onClose() })
    },
)
```

> Разработчику: если проводка overlay в `App` окажется громоздкой, допустимо более простое
> UX-решение — кнопка «Выбрать из GitHub» в настройках просто очищает `remoteUrl`
> (`settingsVm.save("")`), из-за чего гейтинг Task 7 сам покажет полноэкранный `RepoPickerScreen`.
> Это одна строка и переиспользует уже готовый экран. Выбери этот вариант, если overlay
> раздувает `App`. (ponytail: одна строка вместо overlay-проводки; потолок — выход из настроек
> на полноэкранный выбор, что для смены репо приемлемо.)

- [ ] **Step 3: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SettingsScreen.kt composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt
git commit -m "feat: смена репозитория из настроек — выбор из GitHub"
```

---

## Аналитика

Приложение локальное, без встроенной аналитической системы (событий/метрик в кодовой базе нет —
ни в auth, ни в sync, ни в settings). Пользовательская телеметрия вне scope этого слайса и всего
проекта на данный момент, поэтому шаг аналитики не добавляется. Успех проверяется приёмочными
тест-кейсами спеки (ручной прогон).

## Самопроверка (выполнено)

1. **Покрытие спеки:** клиент репо → Task 1; фильтрация → Task 2; VM → Task 3; строки → Task 4;
   приветствие+копирование кода → Task 5; экран выбора → Task 6; гейтинг → Task 7; смена из
   настроек → Task 8. Все разделы спеки покрыты.
2. **Плейсхолдеры:** реальный код и команды во всех шагах; заглушка `client(...)` в тесте Task 1
   помечена к удалению.
3. **Согласованность типов:** `GitHubRepo(fullName, cloneUrl, private)`, `RepoList.list(token)`,
   `RepoPickerState`, `RepoPickerViewModel(repos, token, onPick, scope)` — имена совпадают между
   Task 1/3/6/7.
