# Read-only Repo Gating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** На экране проверки доступа определять право записи (push) в репозиторий и, если доступ только на чтение, блокировать создание/редактирование заметок и папок, оставляя просмотр и AI.

**Architecture:** После `lsRemote` (чтение) `JGitRepoAccessCheck` пробует открыть push-соединение к `git-receive-pack` — read-only даёт auth-ошибку → `AccessResult.Ok(canWrite=false)`. Флаг персистится в `RepoSettingsStore` (default true) на экране проверки; `AppNavHost` читает его и прокидывает `readOnly` в список (скрыть FAB) и заметку (только чтение). AI не трогаем.

**Tech Stack:** Kotlin Multiplatform, Compose, JGit (androidMain), Koin, kotlin.test, Robolectric + Compose UI test.

## Global Constraints

- Пользовательские строки — через Compose Resources (`stringResource(Res.string.*)`), EN + RU (`composeResources/values/strings.xml` и `values-ru/`).
- Дефолт `writable = true` — обратная совместимость: старые установки и локальный режим не блокируются.
- AI-функционал не блокируется ни при каких условиях.
- Каждая задача заканчивается зелёным прогоном затронутого модуля `:test`.
- Аналитика: событие подключения репозитория `repo_connected` уже шлётся; добавить в него параметр `writable` (true/false) на git-пути.

---

### Task 1: `AccessResult.Ok` несёт `canWrite`

**Files:**
- Modify: `features/sync/api/src/commonMain/kotlin/app/obsidianmd/sync/RepoAccessCheck.kt`
- Modify (compile-fix consumers): `features/onboarding/impl/.../RepoValidationViewModel.kt`, `features/onboarding/impl/src/commonTest/.../RepoValidationViewModelTest.kt`

**Interfaces:**
- Produces: `AccessResult.Ok(val canWrite: Boolean)` — раньше `data object Ok`.

- [ ] **Step 1: Изменить модель (компилятор — «тест»)**

`RepoAccessCheck.kt`:
```kotlin
sealed interface AccessResult {
    /** Доступ на чтение есть (ls-remote прошёл). canWrite — прошла ли проба записи. */
    data class Ok(val canWrite: Boolean) : AccessResult
    data class Denied(val reason: String) : AccessResult
    data class Unknown(val reason: String) : AccessResult
}
```

- [ ] **Step 2: Собрать модуль — увидеть, что консюмеры не компилируются**

Run: `./gradlew :features:sync:api:compileKotlinMetadata`
Expected: FAIL — `RepoValidationViewModel` и тест ссылаются на `AccessResult.Ok` как на object.

- [ ] **Step 3: Починить маппинг в VM (минимально)**

`RepoValidationViewModel.kt` — ветка Ok (полностью реализуется в Task 2, здесь только компиляция):
```kotlin
is AccessResult.Ok -> {
    Analytics.event("repo_connected", mapOf("mode" to "git", "writable" to r.canWrite.toString()))
    ValidationState.Ok(r.canWrite)
}
```
(`ValidationState.Ok` станет data class в Task 2 — временно оставь как есть, если ещё object; синхронизируется в Task 2. Если удобнее — делай Task 1 и Task 2 одним коммитом.)

- [ ] **Step 4: Собрать**

Run: `./gradlew :features:sync:api:compileKotlinMetadata`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add features/sync/api/src/commonMain/kotlin/app/obsidianmd/sync/RepoAccessCheck.kt
git commit -m "feat(sync): AccessResult.Ok несёт признак записи canWrite"
```

---

### Task 2: `RepoValidationViewModel` пробрасывает `canWrite`

**Files:**
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/RepoValidationViewModel.kt`
- Test: `features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/RepoValidationViewModelTest.kt`

**Interfaces:**
- Consumes: `AccessResult.Ok(canWrite)` (Task 1).
- Produces: `ValidationState.Ok(val canWrite: Boolean)`.

- [ ] **Step 1: Написать падающий тест**

В `RepoValidationViewModelTest.kt` заменить `validate_ok` и добавить read-only кейс:
```kotlin
@Test fun validate_ok_readwrite() = runTest(dispatcher) {
    val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Ok(canWrite = true)), { "t" })
    vm.validate("https://gitlab.com/me/notes.git"); advanceUntilIdle()
    val s = vm.state.value
    assertTrue(s is ValidationState.Ok && s.canWrite)
}

@Test fun validate_ok_readonly() = runTest(dispatcher) {
    val vm = RepoValidationViewModel(FakeAccessCheck(AccessResult.Ok(canWrite = false)), { "t" })
    vm.validate("https://gitlab.com/me/notes.git"); advanceUntilIdle()
    val s = vm.state.value
    assertTrue(s is ValidationState.Ok && !s.canWrite)
}
```

- [ ] **Step 2: Запустить — упадёт**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "*RepoValidationViewModelTest*"`
Expected: FAIL — `ValidationState.Ok` без `canWrite`.

- [ ] **Step 3: Минимальная реализация**

`RepoValidationViewModel.kt`:
```kotlin
sealed interface ValidationState {
    data object Checking : ValidationState
    data class Ok(val canWrite: Boolean) : ValidationState
    data class Denied(val reason: String) : ValidationState
    data class Unknown(val reason: String) : ValidationState
}
```
и в `validate`:
```kotlin
is AccessResult.Ok -> {
    Analytics.event("repo_connected", mapOf("mode" to "git", "writable" to r.canWrite.toString()))
    ValidationState.Ok(r.canWrite)
}
```

- [ ] **Step 4: Запустить — пройдёт**

Run: `./gradlew :features:onboarding:impl:testDebugUnitTest --tests "*RepoValidationViewModelTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/RepoValidationViewModel.kt \
        features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/RepoValidationViewModelTest.kt
git commit -m "feat(onboarding): ValidationState.Ok несёт canWrite + аналитика writable"
```

---

### Task 3: `RepoSettingsStore` — флаг `writable` (default true)

**Files:**
- Modify: `features/settings/api/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt`
- Modify: `features/settings/impl/src/androidMain/kotlin/app/obsidianmd/settings/data/SharedPrefsRepoSettingsStore.kt`
- Modify: `features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/FakeRepoSettingsStore.kt`
- Test: `features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/RepoSettingsStoreContractTest.kt`

**Interfaces:**
- Produces: `RepoSettingsStore.getWritable(): Boolean` (default true), `setWritable(writable: Boolean)`.

- [ ] **Step 1: Написать падающий тест (контракт)**

В `RepoSettingsStoreContractTest.kt` добавить:
```kotlin
@Test fun writable_defaults_true_and_persists() {
    val store = newStore()               // фабрика, как в существующих тестах контракта
    assertTrue(store.getWritable())      // дефолт
    store.setWritable(false)
    assertFalse(store.getWritable())
    store.setWritable(true)
    assertTrue(store.getWritable())
}
```
(Если контракт-тест параметризован фабрикой стора — использовать её; иначе повторить для fake и SharedPrefs по образцу файла.)

- [ ] **Step 2: Запустить — упадёт**

Run: `./gradlew :features:settings:impl:testDebugUnitTest --tests "*RepoSettingsStoreContractTest*"`
Expected: FAIL — нет `getWritable`/`setWritable`.

- [ ] **Step 3: Минимальная реализация**

`RepoSettingsStore.kt` — добавить в интерфейс:
```kotlin
fun getWritable(): Boolean
fun setWritable(writable: Boolean)
```
`FakeRepoSettingsStore.kt`:
```kotlin
private var writable = true
override fun getWritable() = writable
override fun setWritable(writable: Boolean) { this.writable = writable }
```
`SharedPrefsRepoSettingsStore.kt` (ключ `writable`, дефолт true):
```kotlin
override fun getWritable(): Boolean = prefs.getBoolean(KEY_WRITABLE, true)
override fun setWritable(writable: Boolean) { prefs.edit().putBoolean(KEY_WRITABLE, writable).apply() }
// companion: private const val KEY_WRITABLE = "writable"
```

- [ ] **Step 4: Запустить — пройдёт**

Run: `./gradlew :features:settings:impl:testDebugUnitTest --tests "*RepoSettingsStoreContractTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add features/settings/api/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt \
        features/settings/impl/src/androidMain/kotlin/app/obsidianmd/settings/data/SharedPrefsRepoSettingsStore.kt \
        features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/FakeRepoSettingsStore.kt \
        features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/RepoSettingsStoreContractTest.kt
git commit -m "feat(settings): персист флага writable (default true)"
```

> Примечание: если `RepoSettingsStore` реализуется/мокается ещё где-то (напр. `FakeVaultRepository`-подобные фейки, DI-модули) — добавить методы там же, чтобы модуль компилировался. Проверить: `rg -n "RepoSettingsStore" --type kotlin`.

---

### Task 4: Проба записи в `JGitRepoAccessCheck`

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitRepoAccessCheck.kt`
- Test: `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitRepoAccessCheckTest.kt` (создать; рядом с `BareRepo.kt`)

**Interfaces:**
- Consumes: `AccessResult.Ok(canWrite)` (Task 1), `BareRepo` тест-хелпер.
- Produces: `check(url, token)` возвращает `Ok(canWrite=true)` для доступного на запись bare-репо.

- [ ] **Step 1: Написать падающий тест**

`JGitRepoAccessCheckTest.kt`:
```kotlin
package app.obsidianmd.sync

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JGitRepoAccessCheckTest {
    @Test fun writable_bare_repo_reports_canWrite_true() = runTest {
        val bare = BareRepo.create()                 // как в существующих JGit-тестах
        val res = JGitRepoAccessCheck().check(bare.url, token = null)
        assertTrue(res is AccessResult.Ok)
        assertEquals(true, (res as AccessResult.Ok).canWrite)
    }

    @Test fun unreachable_repo_is_denied_or_unknown() = runTest {
        val res = JGitRepoAccessCheck().check("file:///nonexistent/repo.git", token = null)
        assertTrue(res !is AccessResult.Ok)          // lsRemote не прошёл → не Ok
    }
}
```
(Свериться с `BareRepo.kt`: как получить пишущий file-URL bare-репо — использовать его фактический API. Если `BareRepo` даёт `Path`/`File`, url = `"file://" + path`.)

- [ ] **Step 2: Запустить — упадёт**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*JGitRepoAccessCheckTest*"`
Expected: FAIL — сейчас `Ok` без `canWrite` не собирается / проба не реализована.

- [ ] **Step 3: Реализовать пробу**

`JGitRepoAccessCheck.kt`:
```kotlin
override suspend fun check(url: String, token: String?): AccessResult = withContext(io) {
    try {
        val cmd = Git.lsRemoteRepository().setRemote(url).setHeads(true)
        token?.takeIf { it.isNotBlank() }?.let {
            cmd.setCredentialsProvider(UsernamePasswordCredentialsProvider(it, ""))
        }
        cmd.call()                                   // чтение подтверждено
        AccessResult.Ok(canWrite = probeWrite(url, token))
    } catch (e: org.eclipse.jgit.api.errors.TransportException) {
        AccessResult.Denied(e.message ?: "transport error")
    } catch (e: Exception) {
        AccessResult.Unknown(e.message ?: e.toString())
    }
}

private fun probeWrite(url: String, token: String?): Boolean = try {
    val transport = org.eclipse.jgit.transport.Transport.open(
        org.eclipse.jgit.transport.URIish(url)
    )
    token?.takeIf { it.isNotBlank() }?.let {
        transport.credentialsProvider = UsernamePasswordCredentialsProvider(it, "")
    }
    try {
        transport.openPush().close()                 // git-receive-pack advertisement; ничего не пушим
        true
    } finally {
        transport.close()
    }
} catch (e: Exception) {
    false                                            // нет права записи / ошибка → консервативно read-only
}
```

- [ ] **Step 4: Запустить — пройдёт**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*JGitRepoAccessCheckTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitRepoAccessCheck.kt \
        composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitRepoAccessCheckTest.kt
git commit -m "feat(sync): проба записи (git-receive-pack) в JGitRepoAccessCheck"
```

---

### Task 5: Экран проверки — текст + запись флага

**Files:**
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/RepoValidationScreen.kt`
- Modify: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingPresentationProviderImpl.kt`
- Modify strings: `features/onboarding/impl/src/commonMain/composeResources/values/strings.xml` (+ `values-ru/strings.xml`)

**Interfaces:**
- Consumes: `ValidationState.Ok(canWrite)` (Task 2), `RepoSettingsStore.setWritable` (Task 3).

- [ ] **Step 1: Добавить строки (EN + RU)**

`values/strings.xml`:
```xml
<string name="repo_check_readonly_body">You have read-only access. You can browse notes and use AI, but creating and editing will be disabled.</string>
```
`values-ru/strings.xml`:
```xml
<string name="repo_check_readonly_body">Доступ только на чтение. Просмотр заметок и AI доступны, создание и редактирование — нет.</string>
```

- [ ] **Step 2: Ветка Ok показывает read-only текст**

`RepoValidationScreen.kt` — сделать сигнатуру `state: ValidationState`, а в ветке
`is ValidationState.Ok`: под `repo_check_ok_body` при `!state.canWrite` добавить строку
`repo_check_readonly_body`:
```kotlin
is ValidationState.Ok -> {
    Text(stringResource(Res.string.repo_check_ok_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    Text(stringResource(Res.string.repo_check_ok_body), Modifier.padding(top = 12.dp))
    if (!state.canWrite) Text(stringResource(Res.string.repo_check_readonly_body), Modifier.padding(top = 8.dp))
    Spacer(Modifier.weight(1f))
    PrimaryButton(stringResource(Res.string.action_continue), onContinue)
}
```
(`ValidationState.Ok` теперь data class — сменить `ValidationState.Ok ->` на `is ValidationState.Ok ->`.)

- [ ] **Step 3: Записать флаг в onContinue**

`OnboardingPresentationProviderImpl.kt`, шаг `Validate`:
```kotlin
onContinue = {
    settings.setRemoteUrl(key.url)
    settings.setWritable((state as? ValidationState.Ok)?.canWrite ?: true)
    settings.setOnboardingDone(true)
    onFinished()
}
```

- [ ] **Step 4: Собрать модуль**

Run: `./gradlew :features:onboarding:impl:compileDebugKotlinAndroid`
Expected: PASS (строки сгенерированы в `Res`, экран компилируется).

- [ ] **Step 5: Commit**

```bash
git add features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/RepoValidationScreen.kt \
        features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingPresentationProviderImpl.kt \
        features/onboarding/impl/src/commonMain/composeResources/values/strings.xml \
        features/onboarding/impl/src/commonMain/composeResources/values-ru/strings.xml
git commit -m "feat(onboarding): экран проверки показывает read-only и пишет флаг writable"
```

---

### Task 6: Список — скрыть FAB создания при read-only

**Files:**
- Modify: `features/vault/api/src/commonMain/kotlin/app/obsidianmd/vault/VaultPresentationProvider.kt`
- Modify: `features/vault/impl/src/commonMain/kotlin/app/obsidianmd/vault/presentation/VaultPresentationProviderImpl.kt`
- Modify: `features/vault/impl/src/commonMain/kotlin/app/obsidianmd/vault/presentation/VaultListScreen.kt`
- Test: `features/vault/impl/src/androidUnitTest/kotlin/app/obsidianmd/vault/presentation/VaultListScreenTest.kt`

**Interfaces:**
- Produces: `ListScreen(..., readOnly: Boolean = false, ...)` — при `readOnly` FAB создания скрыт.

- [ ] **Step 1: Написать падающий тест**

В `VaultListScreenTest.kt` (найти тег/иконку FAB — по существующему тесту создания; если тега нет, добавить `contentDescription`/`testTag` в FAB и искать по нему):
```kotlin
@Test fun readOnly_hides_create_fab() {
    composeTestRule.setContent {
        VaultListScreen(/* тот же набор аргументов, что в соседних тестах */ readOnly = true,
            onCreateNote = {}, onCreateFolder = {}, /* ... */)
    }
    composeTestRule.onNodeWithContentDescription("Create").assertDoesNotExist()
}

@Test fun readWrite_shows_create_fab() {
    composeTestRule.setContent {
        VaultListScreen(/* ... */ readOnly = false, onCreateNote = {}, onCreateFolder = {}, /* ... */)
    }
    composeTestRule.onNodeWithContentDescription("Create").assertExists()
}
```
(Использовать реальный `contentDescription` FAB из `VaultListScreen.kt`; если он локализован — искать `onNodeWithContentDescription(getString(Res.string.…))` по образцу существующих тестов.)

- [ ] **Step 2: Запустить — упадёт**

Run: `./gradlew :features:vault:impl:testDebugUnitTest --tests "*VaultListScreenTest*"`
Expected: FAIL — нет параметра `readOnly`.

- [ ] **Step 3: Минимальная реализация**

- `VaultPresentationProvider.ListScreen(...)` и `VaultPresentationProviderImpl` — добавить `readOnly: Boolean = false`, пробросить в `VaultListScreen`.
- `VaultListScreen.kt` — обернуть FAB создания:
```kotlin
floatingActionButton = {
    if (!readOnly) { /* существующий FAB создания заметки/папки */ }
},
```
(Сохранить существующий `contentDescription`; не менять логику меню создания.)

- [ ] **Step 4: Запустить — пройдёт**

Run: `./gradlew :features:vault:impl:testDebugUnitTest --tests "*VaultListScreenTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add features/vault/api/src/commonMain/kotlin/app/obsidianmd/vault/VaultPresentationProvider.kt \
        features/vault/impl/src/commonMain/kotlin/app/obsidianmd/vault/presentation/VaultPresentationProviderImpl.kt \
        features/vault/impl/src/commonMain/kotlin/app/obsidianmd/vault/presentation/VaultListScreen.kt \
        features/vault/impl/src/androidUnitTest/kotlin/app/obsidianmd/vault/presentation/VaultListScreenTest.kt
git commit -m "feat(vault): скрыть FAB создания при read-only доступе"
```

---

### Task 7: Заметка — режим только чтения при read-only

**Files:**
- Modify: `features/note/api/src/commonMain/kotlin/app/obsidianmd/note/NotePresentationProvider.kt`
- Modify: `features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NotePresentationProviderImpl.kt`
- Modify: `features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NoteScreen.kt`
- Test: `features/note/impl/src/androidUnitTest/kotlin/app/obsidianmd/note/presentation/NoteScreenTest.kt` (создать, если нет; иначе дополнить)

**Interfaces:**
- Produces: `NoteScreen(..., readOnly: Boolean = false, ...)` — при `readOnly` иконка редактирования/сохранения скрыта, вход в редактор недоступен.

- [ ] **Step 1: Написать падающий тест**

```kotlin
@Test fun readOnly_hides_edit_action() {
    composeTestRule.setContent {
        NoteScreenContent(/* аргументы как в существующем использовании */ readOnly = true,
            content = "# hello", onSave = {}, /* ... */)
    }
    composeTestRule.onNodeWithContentDescription("Edit").assertDoesNotExist()
}
```
(Взять реальный `contentDescription` иконки редактирования из `NoteScreen.kt`. Если у иконки его нет — добавить и искать по нему.)

- [ ] **Step 2: Запустить — упадёт**

Run: `./gradlew :features:note:impl:testDebugUnitTest --tests "*NoteScreenTest*"`
Expected: FAIL — нет `readOnly`.

- [ ] **Step 3: Минимальная реализация**

- `NotePresentationProvider.NoteScreen(...)` + impl — добавить `readOnly: Boolean = false`, пробросить в `NoteScreenContent`.
- `NoteScreen.kt` — иконку редактирования (и вход в `editing`) показывать только при `!readOnly`; в `readOnly` рендерить контент как просмотр. Диалог «Unsaved changes» в read-only недостижим (правок нет).

- [ ] **Step 4: Запустить — пройдёт**

Run: `./gradlew :features:note:impl:testDebugUnitTest --tests "*NoteScreenTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add features/note/api/src/commonMain/kotlin/app/obsidianmd/note/NotePresentationProvider.kt \
        features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NotePresentationProviderImpl.kt \
        features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NoteScreen.kt \
        features/note/impl/src/androidUnitTest/kotlin/app/obsidianmd/note/presentation/NoteScreenTest.kt
git commit -m "feat(note): режим только чтения заметки при read-only доступе"
```

---

### Task 8: `AppNavHost` — читать флаг и прокинуть `readOnly`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt`

**Interfaces:**
- Consumes: `RepoSettingsStore.getWritable()` (Task 3), `ListScreen(readOnly=)` (Task 6), `NoteScreen(readOnly=)` (Task 7).

- [ ] **Step 1: Прочитать флаг**

В `AppNavHost` рядом с остальными `koinInject`:
```kotlin
val settingsStore = koinInject<RepoSettingsStore>()
val readOnly = !settingsStore.getWritable()
```

- [ ] **Step 2: Прокинуть в экраны**

- `entry<Route.VaultList>` → `vaultPresentation.ListScreen(..., readOnly = readOnly, ...)`.
- `entry<Route.Note>` → `notePresentation.NoteScreen(..., readOnly = readOnly, ...)`.

- [ ] **Step 3: Собрать приложение**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: PASS

- [ ] **Step 4: Полный прогон затронутых тестов**

Run: `./gradlew :composeApp:testDebugUnitTest :features:onboarding:impl:testDebugUnitTest :features:settings:impl:testDebugUnitTest :features:vault:impl:testDebugUnitTest :features:note:impl:testDebugUnitTest`
Expected: PASS (все зелёные).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt
git commit -m "feat(nav): прокинуть read-only в список и заметку по флагу writable"
```

---

## Самопроверка плана

1. **Покрытие спеки:**
   - Проба записи (решение) → Task 4. Модель `Ok(canWrite)` → Task 1. Проброс VM → Task 2.
   - Текст экрана read-only → Task 5. Персист флага → Task 3 + запись в Task 5.
   - Гейтинг списка (FAB) → Task 6. Гейтинг заметки → Task 7. Чтение флага/проброс → Task 8.
   - Аналитика `writable` → Task 1/Task 2 (параметр в `repo_connected`).
   - Обратная совместимость (default true) → Task 3. Локальный режим → дефолт (не пишем false).
   - Все 5 TDD-единиц спеки покрыты (VM-маппинг, контракт стора, JGit happy-path, FAB, заметка).
2. **Плейсхолдеры:** нет «add error handling / TODO / similar to Task N» — код показан. Места, где
   нужно свериться с фактическим API (`BareRepo`, `contentDescription` FAB/Edit), помечены явно как
   «взять реальное значение из файла», а не как заглушки логики.
3. **Согласованность типов:** `AccessResult.Ok(canWrite)` (Task 1) ↔ `ValidationState.Ok(canWrite)`
   (Task 2) ↔ `setWritable/getWritable` (Task 3) ↔ `readOnly` в `ListScreen`/`NoteScreen`
   (Task 6/7) ↔ проброс в `AppNavHost` (Task 8) — имена сквозные. `probeWrite` приватный, вызывается
   только внутри `check`.
