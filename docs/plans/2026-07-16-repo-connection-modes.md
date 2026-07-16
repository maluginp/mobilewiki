# Три режима подключения репозитория — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дать пользователю три способа подключить хранилище заметок — GitHub (OAuth), любой git-репозиторий (URL + токен) и локальный режим без синхронизации — чтобы закрыть требование сторов об альтернативном входе.

**Architecture:** Слой git (`GitSync`/`SyncConfig`) уже провайдер-независим (URL + token по HTTPS), поэтому основная работа — точки входа в UI онбординга/настроек, сохранение введённого токена в существующий `TokenStore`, явный флаг завершения онбординга (для локального режима) и host-agnostic проверка доступа через `git ls-remote`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin (DI), JGit (Android), ktor (OAuth), Navigation3, compose-resources (строки).

## Global Constraints

- UI-строки — только через compose-resources (`Res.string.*`), значения добавляются в оба файла: `core/translations/src/commonMain/composeResources/values/strings.xml` (база/EN) и `.../values-ru/strings.xml` (RU). Имя приложения — «Brainer».
- Один активный репозиторий и один слот токена (`TokenStore`) — мультирепозиторий вне scope.
- SSH и менеджмент ключей — вне scope.
- Реальную очистку/переклон при смене репозитория (`JGitSync.sync` уже сносит старый origin) НЕ меняем — только предупреждаем пользователя.
- JGit доступен только в `composeApp/androidMain` — реализации git-операций живут там, интерфейсы-порты в `:features:sync:api`.
- Тесты бизнес-логики — в `commonTest` (kotlin.test + kotlinx-coroutines-test), UI/сеть — ручная приёмка.

---

### Task 1: Флаг завершения онбординга + стартовый бэкстек

Локальный режим не имеет ни токена, ни URL, поэтому «онбординг завершён» становится явным флагом.

**Files:**
- Modify: `features/settings/api/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt`
- Modify: `features/settings/impl/src/androidMain/kotlin/app/obsidianmd/settings/data/SharedPrefsRepoSettingsStore.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/StartStack.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt:56-59`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/nav/StartStackTest.kt`

**Interfaces:**
- Produces: `RepoSettingsStore.getOnboardingDone(): Boolean` / `setOnboardingDone(done: Boolean)`; `startStack(onboardingDone: Boolean, hasToken: Boolean): List<Route>`.

- [ ] **Step 1: Переписать тест `StartStackTest` под новую сигнатуру**

```kotlin
package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart
import kotlin.test.Test
import kotlin.test.assertEquals

class StartStackTest {
    // Онбординг завершён (любой режим, включая локальный) → сразу список заметок.
    @Test fun done_starts_at_vault_list() {
        assertEquals(listOf(Route.VaultList()), startStack(onboardingDone = true, hasToken = false))
        assertEquals(listOf(Route.VaultList()), startStack(onboardingDone = true, hasToken = true))
    }

    // Не завершён, но есть токен GitHub → возобновляем на выборе репозитория.
    @Test fun not_done_with_token_resumes_repo_picker() {
        assertEquals(
            listOf(Route.Onboarding(OnboardingStart.RepoPicker)),
            startStack(onboardingDone = false, hasToken = true),
        )
    }

    // Не завершён, токена нет → приветствие с выбором режима (шаг Login).
    @Test fun not_done_no_token_starts_at_login() {
        assertEquals(
            listOf(Route.Onboarding(OnboardingStart.Login)),
            startStack(onboardingDone = false, hasToken = false),
        )
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.StartStackTest"`
Expected: FAIL (компиляция — `startStack` принимает `hasRepo`, не `onboardingDone`).

- [ ] **Step 3: Обновить `RepoSettingsStore` + `SharedPrefsRepoSettingsStore` + `startStack`**

`RepoSettingsStore.kt`:
```kotlin
package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
    fun getOnboardingDone(): Boolean
    fun setOnboardingDone(done: Boolean)
}
```

`SharedPrefsRepoSettingsStore.kt` — добавить:
```kotlin
    override fun getOnboardingDone(): Boolean = prefs.getBoolean("onboarding_done", false)
    override fun setOnboardingDone(done: Boolean) { prefs.edit().putBoolean("onboarding_done", done).apply() }
```

`StartStack.kt`:
```kotlin
package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart

/** Стартовый бэкстек: завершённый онбординг ведёт в приложение, иначе — в нужный шаг онбординга. */
fun startStack(onboardingDone: Boolean, hasToken: Boolean): List<Route> = when {
    onboardingDone -> listOf(Route.VaultList())
    hasToken -> listOf(Route.Onboarding(OnboardingStart.RepoPicker))
    else -> listOf(Route.Onboarding(OnboardingStart.Login))
}

/** Смена репо из настроек: онбординг с шага выбора репо поверх списка (есть куда вернуться). */
fun stackForChangeRepo(): List<Route> = listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.RepoPicker))
```

- [ ] **Step 4: Обновить вызов в `MainActivity`**

Заменить строки 56–59 на:
```kotlin
                        val hasToken = koin.get<TokenStore>().get() != null
                        val hasRemote = !koin.get<RepoSettingsStore>().getRemoteUrl().isNullOrBlank()
                        val done = koin.get<RepoSettingsStore>().getOnboardingDone()
                        if (hasRemote) AutoSyncScheduler(applicationContext).schedule()
                        startStack(onboardingDone = done, hasToken = hasToken)
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.StartStackTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add features/settings composeApp/src/commonMain/kotlin/app/obsidianmd/nav/StartStack.kt composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt composeApp/src/commonTest/kotlin/app/obsidianmd/nav/StartStackTest.kt
git commit -m "feat(onboarding): явный флаг завершения онбординга для локального режима"
```

---

### Task 2: `OnboardingStart.ManualUrl` — прямой вход в ручное подключение

Чтобы настройки могли открыть экран ручного ввода URL напрямую, а не только через RepoPicker.

**Files:**
- Modify: `features/onboarding/api/src/commonMain/kotlin/app/obsidianmd/onboarding/OnboardingPresentationProvider.kt`
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingFlow.kt`
- Test: `features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/StartStepTest.kt` (create)

**Interfaces:**
- Consumes: `OnboardingStart`, `Step` (внутренние).
- Produces: `OnboardingStart.ManualUrl`; `startStep(OnboardingStart.ManualUrl) == Step.ManualUrl`.

- [ ] **Step 1: Написать падающий тест `StartStepTest`**

```kotlin
package app.obsidianmd.onboarding

import app.obsidianmd.onboarding.presentation.Step
import app.obsidianmd.onboarding.presentation.startStep
import kotlin.test.Test
import kotlin.test.assertEquals

class StartStepTest {
    @Test fun login_maps_to_login() = assertEquals(Step.Login, startStep(OnboardingStart.Login))
    @Test fun repo_picker_maps_to_repo_picker() = assertEquals(Step.RepoPicker, startStep(OnboardingStart.RepoPicker))
    @Test fun manual_url_maps_to_manual_url() = assertEquals(Step.ManualUrl, startStep(OnboardingStart.ManualUrl))
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.StartStepTest"`
Expected: FAIL (`OnboardingStart.ManualUrl` не существует; ветка в `startStep` не исчерпывающая).

- [ ] **Step 3: Добавить значение enum и маппинг**

`OnboardingPresentationProvider.kt` — расширить enum:
```kotlin
/** С какого шага начинать флоу. RepoPicker/ManualUrl — для сценария «сменить репозиторий из настроек». */
enum class OnboardingStart { Login, RepoPicker, ManualUrl }
```

`OnboardingFlow.kt` — дополнить `startStep`:
```kotlin
internal fun startStep(start: OnboardingStart): Step = when (start) {
    OnboardingStart.Login -> Step.Login
    OnboardingStart.RepoPicker -> Step.RepoPicker
    OnboardingStart.ManualUrl -> Step.ManualUrl
}
```

- [ ] **Step 4: Запустить — убедиться, что проходит**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.StartStepTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add features/onboarding/api features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingFlow.kt features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/StartStepTest.kt
git commit -m "feat(onboarding): OnboardingStart.ManualUrl для прямого входа в ручное подключение"
```

---

### Task 3: Порт `RepoAccessCheck` (host-agnostic) + AccessResult в `:features:sync:api`

Единый порт проверки доступа для любого git-хоста; реализация на JGit — в Task 7.

**Files:**
- Create: `features/sync/api/src/commonMain/kotlin/app/obsidianmd/sync/RepoAccessCheck.kt`

**Interfaces:**
- Produces: `RepoAccessCheck.check(url: String, token: String?): AccessResult`; `AccessResult = Ok | Denied(reason) | Unknown(reason)`.

- [ ] **Step 1: Создать порт (интерфейс + результат)**

```kotlin
package app.obsidianmd.sync

sealed interface AccessResult {
    /** Доступ есть — ls-remote отработал. */
    data object Ok : AccessResult
    /** Доступ запрещён или репозиторий не найден (ошибка транспорта/авторизации). */
    data class Denied(val reason: String) : AccessResult
    /** Не удалось проверить (сеть/прочее) — не блокируем жёстко, но и не пускаем как Ok. */
    data class Unknown(val reason: String) : AccessResult
}

/** Проверка доступа к git-репозиторию по URL и (опционально) токену/паролю. */
interface RepoAccessCheck {
    suspend fun check(url: String, token: String?): AccessResult
}
```

- [ ] **Step 2: Проверить компиляцию модуля**

Run: `./gradlew :features:sync:api:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL (это только определение типов, тест будет у потребителя в Task 4).

- [ ] **Step 3: Commit**

```bash
git add features/sync/api/src/commonMain/kotlin/app/obsidianmd/sync/RepoAccessCheck.kt
git commit -m "feat(sync): порт RepoAccessCheck для host-agnostic проверки доступа"
```

---

### Task 4: `RepoValidationViewModel` на `RepoAccessCheck`, удалить GitHub-only проверку

Валидация перестаёт быть GitHub-специфичной: `Denied` несёт строковую причину, а не HTTP-код.

**Files:**
- Modify: `features/onboarding/impl/build.gradle.kts` (добавить зависимость на `:features:sync:api`)
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/RepoValidationViewModel.kt`
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/RepoValidationScreen.kt`
- Modify: `features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/RepoValidationViewModelTest.kt`
- Delete: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/RepoAccess.kt` (старый GitHub-only `RepoAccess`/`AccessResult`/`GitHubRepoAccess`/`parseGitHubSlug`)
- Delete: `features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/RepoAccessTest.kt`
- Modify: `core/translations/.../values/strings.xml` и `.../values-ru/strings.xml` (строка `repo_check_denied_body`)

**Interfaces:**
- Consumes: `RepoAccessCheck`, `AccessResult` (из `:features:sync:api`).
- Produces: `ValidationState.Denied(reason: String)` (было `status: Int`).

- [ ] **Step 1: Добавить зависимость модуля**

В `features/onboarding/impl/build.gradle.kts`, в `commonMain.dependencies`, рядом с `:features:settings:api`:
```kotlin
            implementation(project(":features:sync:api"))
```

- [ ] **Step 2: Переписать тест `RepoValidationViewModelTest`**

```kotlin
package app.obsidianmd.onboarding

import app.obsidianmd.sync.AccessResult
import app.obsidianmd.sync.RepoAccessCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

private class FakeAccessCheck(val result: AccessResult) : RepoAccessCheck {
    override suspend fun check(url: String, token: String?): AccessResult = result
}

class RepoValidationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun validate_ok() = runTest(dispatcher) {
        val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Ok), { "t" })
        vm.validate("https://gitlab.com/me/notes.git"); advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Ok)
    }

    @Test fun validate_denied() = runTest(dispatcher) {
        val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Denied("auth failed")), { "t" })
        vm.validate("https://gitlab.com/me/x.git"); advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Denied)
    }

    @Test fun validate_unknown() = runTest(dispatcher) {
        val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Unknown("no net")), { "t" })
        vm.validate("https://example.com/me/x.git"); advanceUntilIdle()
        assertTrue(vm.state.value is ValidationState.Unknown)
    }
}
```

- [ ] **Step 3: Запустить — убедиться, что падает**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.RepoValidationViewModelTest"`
Expected: FAIL (компиляция — старый `RepoAccess`/`AccessResult.Denied(Int)`).

- [ ] **Step 4: Переписать ViewModel, удалить старый RepoAccess, поправить экран/строку**

`RepoValidationViewModel.kt`:
```kotlin
package app.obsidianmd.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.obsidianmd.analytics.Analytics
import app.obsidianmd.sync.AccessResult
import app.obsidianmd.sync.RepoAccessCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ValidationState {
    data object Checking : ValidationState
    data object Ok : ValidationState
    data class Denied(val reason: String) : ValidationState
    data class Unknown(val reason: String) : ValidationState
}

class RepoValidationViewModel(
    private val access: RepoAccessCheck,
    private val token: () -> String?,
) : ViewModel() {
    private val _state = MutableStateFlow<ValidationState>(ValidationState.Checking)
    val state: StateFlow<ValidationState> = _state.asStateFlow()

    fun validate(url: String) {
        viewModelScope.launch {
            _state.value = ValidationState.Checking
            _state.value = when (val r = access.check(url, token())) {
                is AccessResult.Ok -> { Analytics.event("repo_connected"); ValidationState.Ok }
                is AccessResult.Denied -> ValidationState.Denied(r.reason)
                is AccessResult.Unknown -> ValidationState.Unknown(r.reason)
            }
        }
    }
}
```

Удалить файлы `RepoAccess.kt` и `RepoAccessTest.kt`.

`RepoValidationScreen.kt` — заменить формат `repo_check_denied_body` (был `%1$d` для статуса) на текст без кода: убрать аргумент `state.status`:
```kotlin
                Text(stringResource(Res.string.repo_check_denied_body), Modifier.padding(top = 12.dp))
```
(и убрать ссылку на `state.status`; `state` типа `ValidationState.Denied` теперь несёт `reason`, показывать его не обязательно — хватает подсказок hint1..3.)

Строки — заменить значение `repo_check_denied_body` в обоих файлах:
- `values/strings.xml`: `<string name="repo_check_denied_body">No access to the repository. What to check:</string>`
- `values-ru/strings.xml`: `<string name="repo_check_denied_body">Нет доступа к репозиторию. Что стоит проверить:</string>`

- [ ] **Step 5: Запустить — убедиться, что проходит**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.RepoValidationViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add features/onboarding core/translations
git commit -m "feat(onboarding): host-agnostic проверка доступа через RepoAccessCheck"
```

---

### Task 5: `ManualConnectViewModel` — сохранение токена перед валидацией

**Files:**
- Create: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/ManualConnectViewModel.kt`
- Test: `features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/ManualConnectViewModelTest.kt`
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/di/OnboardingModule.kt`

**Interfaces:**
- Consumes: `TokenStore` (из `:core:auth`).
- Produces: `ManualConnectViewModel.connect(url: String, token: String): String` — сохраняет непустой токен, возвращает `url` для перехода на `Validate`.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package app.obsidianmd.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManualConnectViewModelTest {
    @Test fun saves_non_blank_token_and_returns_url() {
        val store = FakeTokenStore()
        val vm = ManualConnectViewModel(store)
        val url = vm.connect("https://gitlab.com/x/y.git", "glpat-abc")
        assertEquals("https://gitlab.com/x/y.git", url)
        assertEquals("glpat-abc", store.get())
    }

    @Test fun blank_token_does_not_overwrite_store() {
        val store = FakeTokenStore().apply { save("existing") }
        val vm = ManualConnectViewModel(store)
        val url = vm.connect("https://github.com/x/y.git", "")
        assertEquals("https://github.com/x/y.git", url)
        // Пустой токен (публичный репо) не затирает существующий.
        assertEquals("existing", store.get())
    }

    @Test fun blank_token_leaves_empty_store_empty() {
        val store = FakeTokenStore()
        ManualConnectViewModel(store).connect("https://github.com/x/y.git", "  ")
        assertNull(store.get())
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.ManualConnectViewModelTest"`
Expected: FAIL (`ManualConnectViewModel` не существует).

- [ ] **Step 3: Реализовать ViewModel**

```kotlin
package app.obsidianmd.onboarding

import androidx.lifecycle.ViewModel
import app.obsidianmd.auth.TokenStore

class ManualConnectViewModel(private val store: TokenStore) : ViewModel() {
    /** Сохраняет непустой токен и возвращает URL для перехода на шаг проверки доступа. */
    fun connect(url: String, token: String): String {
        if (token.isNotBlank()) store.save(token.trim())
        return url.trim()
    }
}
```

- [ ] **Step 4: Запустить — убедиться, что проходит**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.ManualConnectViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Зарегистрировать в DI**

`OnboardingModule.kt` — добавить в `module { … }`:
```kotlin
    viewModel { ManualConnectViewModel(get<TokenStore>()) }
```

- [ ] **Step 6: Commit**

```bash
git add features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/ManualConnectViewModel.kt features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/ManualConnectViewModelTest.kt features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/di/OnboardingModule.kt
git commit -m "feat(onboarding): ManualConnectViewModel — сохранение токена для ручного подключения"
```

---

### Task 6: `ManualUrlScreen` — поле токена + предупреждение read-only, проводка шага

UI-задача (без юнит-теста; проверяется в приёмке).

**Files:**
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/ManualUrlScreen.kt`
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingPresentationProviderImpl.kt`
- Modify: `core/translations/.../values/strings.xml` и `.../values-ru/strings.xml`

**Interfaces:**
- Consumes: `ManualConnectViewModel.connect` (Task 5).
- Produces: `ManualUrlScreen(onSubmit: (url: String, token: String) -> Unit, onBack: () -> Unit)`.

- [ ] **Step 1: Добавить строки** (оба файла)

`values/strings.xml`:
```xml
<string name="repo_pick_manual_token_label">Access token / password (optional)</string>
<string name="repo_pick_manual_readonly_warning">Without an access token, changes won\'t be pushed to the repository — read-only mode.</string>
```
`values-ru/strings.xml`:
```xml
<string name="repo_pick_manual_token_label">Токен доступа / пароль (необязательно)</string>
<string name="repo_pick_manual_readonly_warning">Без токена доступа изменения не будут сохраняться в репозиторий — режим только для чтения.</string>
```

- [ ] **Step 2: Обновить `ManualUrlScreen`**

Сигнатура и тело (ключевые изменения — второе поле-секрет и предупреждение):
```kotlin
@Composable
internal fun ManualUrlScreen(
    onSubmit: (url: String, token: String) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(Res.string.repo_pick_manual_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(Res.string.repo_pick_manual_label)) },
            placeholder = { Text(stringResource(Res.string.repo_pick_manual_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(Res.string.repo_pick_manual_token_label)) },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { tokenVisible = !tokenVisible }) {
                    Text(stringResource(if (tokenVisible) Res.string.action_hide else Res.string.action_show))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (token.isBlank()) {
            Text(
                stringResource(Res.string.repo_pick_manual_readonly_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onSubmit(url.trim(), token) },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(Res.string.action_continue)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(Res.string.action_back))
        }
    }
}
```
Добавить импорты: `androidx.compose.ui.text.input.PasswordVisualTransformation`, `androidx.compose.ui.text.input.VisualTransformation`, `app.obsidianmd.resources.action_hide`, `app.obsidianmd.resources.action_show`, `app.obsidianmd.resources.repo_pick_manual_token_label`, `app.obsidianmd.resources.repo_pick_manual_readonly_warning`.

- [ ] **Step 3: Проводка шага `ManualUrl` в `OnboardingPresentationProviderImpl`**

Заменить `entry<Step.ManualUrl> { … }` на использование `ManualConnectViewModel`:
```kotlin
                    entry<Step.ManualUrl> {
                        val vm: ManualConnectViewModel = koinViewModel()
                        ManualUrlScreen(
                            onSubmit = { url, token -> backStack.add(Step.Validate(vm.connect(url, token))) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
```
Добавить импорт `app.obsidianmd.onboarding.ManualConnectViewModel`.

- [ ] **Step 4: Проверить сборку**

Run: `./gradlew :features:onboarding:impl:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add features/onboarding core/translations
git commit -m "feat(onboarding): поле токена и предупреждение read-only на ручном подключении"
```

---

### Task 7: `WelcomeScreen` — объяснение + три режима, установка флага завершения

UI + проводка. Локальный режим завершает онбординг без remote; аналитика режима подключения.

**Files:**
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/WelcomeScreen.kt`
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingPresentationProviderImpl.kt`
- Modify: `core/translations/.../values/strings.xml` и `.../values-ru/strings.xml`

**Interfaces:**
- Consumes: `RepoSettingsStore.setOnboardingDone` (Task 1), `OnboardingStart`, `Analytics`.
- Produces: `WelcomeScreen(onSignInGitHub, onConnectByUrl, onUseLocal)`.

- [ ] **Step 1: Добавить строки** (оба файла)

`values/strings.xml`:
```xml
<string name="onboarding_mode_git">Connect any git repository</string>
<string name="onboarding_mode_local">Use locally (no sync)</string>
```
`values-ru/strings.xml`:
```xml
<string name="onboarding_mode_git">Подключить любой git-репозиторий</string>
<string name="onboarding_mode_local">Использовать локально (без синхронизации)</string>
```
(`onboarding_title`, `onboarding_body`, `onboarding_sign_in` уже есть; текст `onboarding_body` уже объясняет, что делает приложение.)

- [ ] **Step 2: Три кнопки в `WelcomeScreen`**

```kotlin
@Composable
internal fun WelcomeScreen(
    onSignInGitHub: () -> Unit,
    onConnectByUrl: () -> Unit,
    onUseLocal: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(Res.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.onboarding_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onSignInGitHub, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(Res.string.onboarding_sign_in), style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(onClick = onConnectByUrl, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp)) {
            Text(stringResource(Res.string.onboarding_mode_git))
        }
        TextButton(onClick = onUseLocal, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(Res.string.onboarding_mode_local))
        }
    }
}
```
Добавить импорты `androidx.compose.material3.OutlinedButton`, `androidx.compose.material3.TextButton`, `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.layout.fillMaxWidth`, ресурсы `onboarding_mode_git`, `onboarding_mode_local`.

- [ ] **Step 3: Проводка в `OnboardingPresentationProviderImpl` (шаг Login) + флаг завершения**

В `entry<Step.Login>` заменить `WelcomeScreen(onSignIn = vm::login)` на:
```kotlin
                        if (state is AuthState.Idle) {
                            WelcomeScreen(
                                onSignInGitHub = vm::login,
                                onConnectByUrl = { backStack.add(Step.ManualUrl) },
                                onUseLocal = {
                                    Analytics.event("repo_connected", mapOf("mode" to "local"))
                                    settings.setOnboardingDone(true)
                                    onFinished()
                                },
                            )
                        } else { … }  // без изменений
```
И проставлять флаг при завершении любого сетевого режима. В `entry<Step.Validate>` заменить `onContinue`:
```kotlin
                            onContinue = {
                                settings.setRemoteUrl(key.url)
                                settings.setOnboardingDone(true)
                                onFinished()
                            },
```
Импорт `app.obsidianmd.analytics.Analytics`. (`settings` уже `koinInject<RepoSettingsStore>()` в этом composable.)

> Аналитика: событие `repo_connected` с параметром `mode` (`local` здесь; для git/github — в Task 8/существующем `repo_connected`). Для git-режима добавить параметр в Task 8, где известен факт `Ok`.

- [ ] **Step 4: Проверить сборку**

Run: `./gradlew :features:onboarding:impl:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add features/onboarding core/translations
git commit -m "feat(onboarding): три режима подключения на приветственном экране"
```

---

### Task 8: JGit-реализация `RepoAccessCheck` (ls-remote) + DI

Реальная проверка доступа для любого хоста. Сеть — приёмка, не юнит.

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitRepoAccessCheck.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/di/AppModule.kt`
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/di/OnboardingModule.kt`

**Interfaces:**
- Consumes: `RepoAccessCheck`, `AccessResult` (Task 3).
- Produces: `RepoAccessCheck` в графе Koin, инъекция в `RepoValidationViewModel`.

- [ ] **Step 1: Реализация на JGit**

```kotlin
package app.obsidianmd.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class JGitRepoAccessCheck(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RepoAccessCheck {
    override suspend fun check(url: String, token: String?): AccessResult = withContext(io) {
        try {
            val cmd = Git.lsRemoteRepository().setRemote(url).setHeads(true)
            token?.takeIf { it.isNotBlank() }?.let {
                cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(it, ""))
            }
            cmd.call()
            AccessResult.Ok
        } catch (e: org.eclipse.jgit.api.errors.TransportException) {
            AccessResult.Denied(e.message ?: "transport error")
        } catch (e: Exception) {
            AccessResult.Unknown(e.message ?: e.toString())
        }
    }
}
```

- [ ] **Step 2: Зарегистрировать в `AppModule.kt`**

Рядом с `single<GitSync> { JGitSync() }`:
```kotlin
    single<RepoAccessCheck> { JGitRepoAccessCheck() }
```
Импорт `app.obsidianmd.sync.RepoAccessCheck`, `app.obsidianmd.sync.JGitRepoAccessCheck`.

- [ ] **Step 3: Инъекция в `RepoValidationViewModel` через DI**

`OnboardingModule.kt` — заменить строку регистрации:
```kotlin
    viewModel { RepoValidationViewModel(access = get(), token = get<TokenStore>()::get) }
```
(`get()` резолвит `RepoAccessCheck` из графа приложения; удалить импорт `GitHubRepoAccess`.)

- [ ] **Step 4: Добавить параметр режима в аналитику успеха git-подключения**

В `RepoValidationViewModel` (Task 4) уточнить событие: при `Ok` отправлять
```kotlin
                is AccessResult.Ok -> { Analytics.event("repo_connected", mapOf("mode" to "git")); ValidationState.Ok }
```
(GitHub OAuth путь тоже проходит через Validate → «git»; отдельно локальный режим шлёт `mode=local` из Task 7. Достаточно для замера долей режимов.)

- [ ] **Step 5: Проверить сборку**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/di/OnboardingModule.kt
git commit -m "feat(sync): JGit ls-remote проверка доступа + аналитика режима подключения"
```

---

### Task 9: Настройки — текущий режим, смена репозитория с предупреждением

Секция репозитория показывает текущий режим и предлагает смену через выбор режима с предупреждением о потере несинхронизированных заметок.

**Files:**
- Modify: `features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsScreen.kt`
- Modify: `features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsViewModel.kt`
- Modify: `features/settings/impl/.../presentation/SettingsPresentationProviderImpl.kt` и `features/settings/api/.../SettingsPresentationProvider.kt` (колбэки `onConnectManually`, `onUseLocal`)
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/StartStack.kt` (+ `stackForChangeRepoManual()`), `.../nav/StartStackTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt` (проводка колбэков настроек)
- Modify: `core/translations/.../values/strings.xml` и `.../values-ru/strings.xml`

**Interfaces:**
- Consumes: `OnboardingStart.ManualUrl` (Task 2), `RepoSettingsStore`.
- Produces: `stackForChangeRepoManual(): List<Route>`; SettingsScreen с показом режима + диалогами.

- [ ] **Step 1: Тест на nav-хелпер смены репо (ручной режим)**

Добавить в `StartStackTest.kt`:
```kotlin
    @Test fun change_repo_manual_opens_manual_over_vault() {
        assertEquals(
            listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.ManualUrl)),
            stackForChangeRepoManual(),
        )
    }
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.StartStackTest"`
Expected: FAIL (`stackForChangeRepoManual` не существует).

- [ ] **Step 3: Добавить хелпер**

`StartStack.kt`:
```kotlin
/** Смена репо из настроек через ручной ввод URL — экран ManualUrl поверх списка. */
fun stackForChangeRepoManual(): List<Route> = listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.ManualUrl))
```

- [ ] **Step 4: Запустить — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.StartStackTest"`
Expected: PASS.

- [ ] **Step 5: Строки** (оба файла)

`values/strings.xml`:
```xml
<string name="settings_repo_current">Current repository</string>
<string name="settings_repo_local">Local storage (no sync)</string>
<string name="settings_repo_change">Change repository</string>
<string name="settings_repo_change_warning">You\'ll see notes from the new repository. Unsynced notes may be lost.</string>
<string name="settings_repo_mode_github">GitHub</string>
<string name="settings_repo_mode_manual">Enter manually</string>
<string name="settings_repo_mode_local">Local (no sync)</string>
<string name="action_cancel">Cancel</string>
```
`values-ru/strings.xml`:
```xml
<string name="settings_repo_current">Текущий репозиторий</string>
<string name="settings_repo_local">Локальное хранилище (без синхронизации)</string>
<string name="settings_repo_change">Изменить репозиторий</string>
<string name="settings_repo_change_warning">Будут показаны заметки нового репозитория. Несинхронизированные заметки могут быть потеряны.</string>
<string name="settings_repo_mode_github">GitHub</string>
<string name="settings_repo_mode_manual">Ввести вручную</string>
<string name="settings_repo_mode_local">Локально (без синхронизации)</string>
<string name="action_cancel">Отмена</string>
```

- [ ] **Step 6: Переработать секцию репозитория в `SettingsScreen`**

Заменить блок «поле URL + Pick from GitHub + Save» на показ текущего режима и кнопку «Изменить» с двумя диалогами (предупреждение → выбор режима):
```kotlin
        Text(stringResource(Res.string.settings_repo_current), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp))
        Text(
            url.ifBlank { stringResource(Res.string.settings_repo_local) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        var showWarning by remember { mutableStateOf(false) }
        var showModes by remember { mutableStateOf(false) }
        Button(onClick = { showWarning = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.settings_repo_change))
        }

        if (showWarning) {
            AlertDialog(
                onDismissRequest = { showWarning = false },
                title = { Text(stringResource(Res.string.settings_repo_change)) },
                text = { Text(stringResource(Res.string.settings_repo_change_warning)) },
                confirmButton = {
                    TextButton(onClick = { showWarning = false; showModes = true }) {
                        Text(stringResource(Res.string.action_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWarning = false }) { Text(stringResource(Res.string.action_cancel)) }
                },
            )
        }
        if (showModes) {
            AlertDialog(
                onDismissRequest = { showModes = false },
                title = { Text(stringResource(Res.string.settings_repo_change)) },
                text = {
                    Column {
                        TextButton(onClick = { showModes = false; onPickFromGitHub() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.settings_repo_mode_github))
                        }
                        TextButton(onClick = { showModes = false; onConnectManually() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.settings_repo_mode_manual))
                        }
                        TextButton(onClick = { showModes = false; onUseLocal() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.settings_repo_mode_local))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModes = false }) { Text(stringResource(Res.string.action_cancel)) }
                },
            )
        }
```
Сигнатура `SettingsScreen` получает новые колбэки: `onConnectManually: () -> Unit = {}`, `onUseLocal: () -> Unit = {}` (в дополнение к существующему `onPickFromGitHub`). Убрать локальный `draft`/`SettingField(url)`/кнопку «Сохранить» и `onSave`. Добавить импорты `androidx.compose.material3.AlertDialog`, ресурсы новых строк, `action_cancel`. Блок «Синхронизировать сейчас» и `aiSection()` — без изменений.

- [ ] **Step 7: `SettingsViewModel` — локальный режим и упрощение**

`SettingsViewModel`: `save(url)` больше не вызывается из экрана; добавить `useLocal()` для сброса на локальный режим из настроек:
```kotlin
    fun useLocal() {
        store.setRemoteUrl("")
        store.setOnboardingDone(true)
        _state.update { it.copy(url = "") }
    }
```
(Оставить `save` если используется где-то ещё; иначе удалить. Проверить usages перед удалением.)

- [ ] **Step 8: Проводка колбэков в `AppNavHost` / `SettingsPresentationProvider`**

Провайдер настроек и его использование в `AppNavHost` (`entry<Route.Settings>`) прокидывают:
- `onPickFromGitHub` → `backStack.resetTo(stackForChangeRepo())` (существующее поведение смены репо; GitHub-роутинг токен→RepoPicker остаётся, при отсутствии токена RepoPicker покажет ошибку загрузки и предложит вход — приемлемо; при желании заменить на `startStack(false, hasToken)`-логику).
- `onConnectManually` → `backStack.resetTo(stackForChangeRepoManual())`.
- `onUseLocal` → вызвать `SettingsViewModel.useLocal()` (сброс на локальный режим; остаёмся в приложении).

Точный способ проводки — по образцу уже существующего `onPickFromGitHub` в `SettingsPresentationProviderImpl`/`AppNavHost` (следовать текущему паттерну модуля, не менять архитектуру).

- [ ] **Step 9: Проверить сборку и все тесты**

Run: `./gradlew :composeApp:testDebugUnitTest :features:onboarding:impl:testDebugUnitTest :features:settings:impl:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 10: Commit**

```bash
git add features/settings composeApp core/translations
git commit -m "feat(settings): показ текущего режима и смена репозитория с предупреждением"
```

---

## Self-review

**Spec coverage:**
- Три режима (GitHub / любой git / локально) → Task 7 (Welcome), Task 1 (флаг завершения для локального).
- Поле токена + предупреждение read-only → Task 6.
- Сохранение токена → Task 5.
- Host-agnostic проверка доступа (ls-remote), блок прохода без Ok → Task 3, 4, 8.
- Прямой вход в ручное подключение (для настроек) → Task 2.
- Настройки: показ режима + смена с предупреждением + выбор режима → Task 9.
- Аналитика (`repo_connected` с `mode`) → Task 7 (local), Task 8 (git).

**Placeholder scan:** каждый шаг содержит реальный код/команды; UI-шаги — конкретные composable-фрагменты; проводка колбэков в Task 9 Step 8 намеренно следует существующему паттерну модуля (не выдумывает архитектуру), точные строки — по образцу текущего `onPickFromGitHub`.

**Type consistency:** `AccessResult`/`RepoAccessCheck` определены в `:features:sync:api` (Task 3) и потребляются одинаково в Task 4/8; `ValidationState.Denied(reason: String)` согласован между VM, экраном и тестом (Task 4); `OnboardingStart.ManualUrl` и `Step.ManualUrl` согласованы (Task 2); `startStack(onboardingDone, hasToken)` — единая сигнатура в Task 1 и вызове MainActivity.
