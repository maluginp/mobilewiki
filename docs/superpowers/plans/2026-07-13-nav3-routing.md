# Nav3 Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить двухслойную навигацию на флагах (`Gate` в `MainActivity` + булева каша в `App.kt`) единой маршрутной навигацией на Compose Multiplatform Navigation 3 с адаптивным list-detail.

**Architecture:** Одна `@Serializable`-иерархия `Route : NavKey` в `commonMain`. Один `NavDisplay` + `rememberNavBackStack` (хост `AppNavHost`) владеет всей историей: онбординг, папки, заметки, настройки, AI. Выбор стартового стека и переходы logout/смены-репо — чистая функция в `commonTest`-покрытии. Экранные composable не меняются — хост вызывает их как есть. На широких экранах `VaultList`/`Note` показываются как list-detail.

**Tech Stack:** Kotlin Multiplatform (Android-only на практике), Compose Multiplatform, Navigation 3 (`org.jetbrains.androidx.navigation3`), `adaptive-navigation3`, Koin, kotlinx.serialization.

## Global Constraints

- Compose Multiplatform **≥ 1.10** (сейчас 1.7.0). Nav3 KMP не работает ниже.
- Kotlin **≥ 2.1** (тянется апгрейдом Compose MP; compose-compiler бандлится с Kotlin).
- `navigation3-ui` = **1.1.1** (stable). Runtime транзитивно.
- `adaptive-navigation3` = **1.3.0-beta02** (beta — допустимо).
- Хост навигации и маршруты живут в **`commonMain`** (не в `androidMain`).
- Экранные composable в `commonMain/ui` и `vault:impl` **не меняем** — только вызываем.
- Режим правки заметки и защита от потери правок (`showUnsaved`) — **локальный state внутри экрана Note**, не маршрут.
- Нижняя навигация Brain↔AI видна только при `settings.aiEnabled` и скрыта при открытой клавиатуре (`WindowInsets.isImeVisible`) — поведение сохранить.
- Комментарии и UI-строки — на русском, как в существующем коде.
- kotlinx.serialization plugin уже подключён (`libs.plugins.kotlinSerialization`).

---

## Файловая структура

**Создаём:**
- `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/Route.kt` — `sealed interface Route : NavKey`, все маршруты, `navSavedStateConfiguration`.
- `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/StartStack.kt` — чистые функции: стартовый стек из `(hasToken, hasRepo)`, редьюсеры logout / смены репо.
- `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt` — `NavDisplay` + `entryProvider`, TopAppBar-по-маршруту, нижняя навигация, list-detail scene strategy, диалоги (conflict, unsaved).
- `composeApp/src/commonTest/kotlin/app/obsidianmd/nav/StartStackTest.kt` — тесты стартового стека/редьюсеров.
- `composeApp/src/commonTest/kotlin/app/obsidianmd/nav/RouteSerializationTest.kt` — round-trip сериализации маршрута.

**Меняем:**
- `gradle/libs.versions.toml` — версии тулчейна и nav3-зависимости.
- `composeApp/build.gradle.kts` — подключить nav3 + adaptive-navigation3; поднять compileSdk при необходимости.
- `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt` — `Gate` сворачивается: вычисляет стартовый стек и зовёт `AppNavHost`. Удаляется `enum RepoStep`.
- `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt` — убрать навигационный state: `history`, `back()`, `atHistoryRoot()`, `clearSelection()`; `open`/`openPath` сводятся к загрузке (историю держит бэкстек).
- `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt` — обновить под изменённый VM.

**Удаляем (в Task 5, после переноса логики):**
- `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`.
- `composeApp/src/commonMain/kotlin/app/obsidianmd/PlatformBackHandler.kt` и `.../androidMain/.../PlatformBackHandler.android.kt` (back держит NavDisplay).

---

## Task 1: Апгрейд тулчейна + подключение Nav3 (риск-гейт)

Цель: проект собирается и запускается на Compose MP 1.10 + новый Kotlin, зависимости Nav3 доступны. Навигация пока НЕ трогается — вся флаговая логика на месте. Это изолирует главный риск.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`

**Interfaces:**
- Produces: доступные в `commonMain` артефакты `androidx.navigation3.*` и `androidx.compose.material3.adaptive.*`; тулчейн Compose MP ≥1.10 / Kotlin ≥2.1.

- [ ] **Step 1: Поднять версии тулчейна в каталоге**

В `gradle/libs.versions.toml` в `[versions]` заменить:
```toml
kotlin = "2.1.21"
compose-mp = "1.10.2"
```
Стартовые значения. Если Gradle при сборке ругается на несовместимость Kotlin ↔ Compose Multiplatform, взять пару из release notes Compose MP 1.10.x и обновить обе. (ponytail: known ceiling — точная пара пиннится здесь по факту сборки, не угадываем заранее.)

- [ ] **Step 2: Добавить Nav3-зависимости в каталог**

В `[versions]`:
```toml
nav3 = "1.1.1"
adaptive-nav3 = "1.3.0-beta02"
```
В `[libraries]`:
```toml
navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "nav3" }
adaptive-navigation3 = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation3", version.ref = "adaptive-nav3" }
```
(ponytail: `lifecycle-viewmodel-navigation3` НЕ добавляем — VM скоупятся через Koin как сейчас; добавить только если появится нужда в VM, привязанной к записи стека.)

- [ ] **Step 3: Подключить зависимости в модуле**

В `composeApp/build.gradle.kts`, блок `commonMain.dependencies { ... }`, после `implementation(compose.material3)`:
```kotlin
            implementation(libs.navigation3.ui)
            implementation(libs.adaptive.navigation3)
```

- [ ] **Step 4: Собрать проект**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL. При провале — чинить fallout апгрейда (API material3/resources/foundation), пока не соберётся. Это ожидаемая часть задачи.

- [ ] **Step 5: Прогнать существующие тесты**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — существующие тесты зелёные на новом тулчейне.

- [ ] **Step 6: Запустить приложение и убедиться, что оно работает как прежде**

Собрать и запустить на эмуляторе/устройстве; пройти онбординг→список→заметка→настройки. Поведение идентично старому (навигация ещё флаговая). Это ручная проверка риск-гейта.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "build: апгрейд Compose MP 1.10 + Kotlin, подключение Nav3"
```

---

## Task 2: Маршруты и конфиг сериализации

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/Route.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/nav/RouteSerializationTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface Route : NavKey` c объектами/классами: `Welcome`, `Login`, `RepoPicker`, `RepoManualUrl`, `RepoValidate(url: String)`, `VaultList(dir: String = "")`, `Note(path: String)`, `Settings`, `ModelPicker`, `AiChat`.
  - `val navSavedStateConfiguration: SavedStateConfiguration`.

- [ ] **Step 1: Написать падающий тест сериализации**

Create `composeApp/src/commonTest/kotlin/app/obsidianmd/nav/RouteSerializationTest.kt`:
```kotlin
package app.obsidianmd.nav

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RouteSerializationTest {
    // Полиморфный round-trip через конфиг: маршрут с параметром переживает сериализацию.
    @Test
    fun note_round_trips_polymorphically() {
        val json = Json { serializersModule = navSavedStateConfiguration.serializersModule }
        val original: NavKey = Route.Note("vault/a.md")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<NavKey>(encoded)
        assertEquals(original, decoded)
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что не компилируется/падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.RouteSerializationTest"`
Expected: FAIL — `Route` и `navSavedStateConfiguration` ещё не существуют.

- [ ] **Step 3: Создать маршруты и конфиг**

Create `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/Route.kt`:
```kotlin
package app.obsidianmd.nav

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Все пункты назначения приложения. Параметры живут в маршруте, а не во флагах. */
@Serializable
sealed interface Route : NavKey {
    // Онбординг
    @Serializable data object Welcome : Route
    @Serializable data object Login : Route
    @Serializable data object RepoPicker : Route
    @Serializable data object RepoManualUrl : Route
    @Serializable data class RepoValidate(val url: String) : Route

    // Основное приложение
    @Serializable data class VaultList(val dir: String = "") : Route
    @Serializable data class Note(val path: String) : Route
    @Serializable data object Settings : Route
    @Serializable data object ModelPicker : Route
    @Serializable data object AiChat : Route
}

/**
 * Nav3 на не-Android таргетах требует явной полиморфной сериализации NavKey.
 * Регистрируем каждый маршрут по отдельности — надёжно и явно.
 */
val navSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Welcome::class, Route.Welcome.serializer())
            subclass(Route.Login::class, Route.Login.serializer())
            subclass(Route.RepoPicker::class, Route.RepoPicker.serializer())
            subclass(Route.RepoManualUrl::class, Route.RepoManualUrl.serializer())
            subclass(Route.RepoValidate::class, Route.RepoValidate.serializer())
            subclass(Route.VaultList::class, Route.VaultList.serializer())
            subclass(Route.Note::class, Route.Note.serializer())
            subclass(Route.Settings::class, Route.Settings.serializer())
            subclass(Route.ModelPicker::class, Route.ModelPicker.serializer())
            subclass(Route.AiChat::class, Route.AiChat.serializer())
        }
    }
}
```
Примечание: если импорт `SavedStateConfiguration` в резолвнутой версии лежит под другим пакетом (`androidx.navigation3.runtime.*` vs `androidx.savedstate.serialization.*`) — поправить импорт по факту (тип точно есть, это точка версионной вариативности).

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.RouteSerializationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/nav/Route.kt composeApp/src/commonTest/kotlin/app/obsidianmd/nav/RouteSerializationTest.kt
git commit -m "feat(nav): типобезопасные маршруты Route : NavKey + конфиг сериализации"
```

---

## Task 3: Логика стартового стека и переходов (чистая, TDD)

Заменяет флаги `loggedIn`/`hasRepo`/`changingRepo`. Онбординг = какой стек собрать.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/StartStack.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/nav/StartStackTest.kt`

**Interfaces:**
- Consumes: `Route` (Task 2).
- Produces:
  - `fun startStack(hasToken: Boolean, hasRepo: Boolean): List<Route>`
  - `fun stackAfterRepoChosen(): List<Route>` — стек после успешной валидации репо.
  - `fun stackForChangeRepo(): List<Route>` — открыть выбор репо поверх списка (есть куда вернуться).

- [ ] **Step 1: Написать падающие тесты**

Create `composeApp/src/commonTest/kotlin/app/obsidianmd/nav/StartStackTest.kt`:
```kotlin
package app.obsidianmd.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class StartStackTest {
    // Нет токена → приветствие (первый экран онбординга).
    @Test fun no_token_starts_at_welcome() {
        assertEquals(listOf(Route.Welcome), startStack(hasToken = false, hasRepo = false))
    }

    // Токен есть, репо нет → выбор репо; выхода назад нет (репо обязателен) → один экран в стеке.
    @Test fun token_without_repo_starts_at_repo_picker() {
        assertEquals(listOf(Route.RepoPicker), startStack(hasToken = true, hasRepo = false))
    }

    // Токен + репо → сразу список заметок (корень).
    @Test fun token_and_repo_starts_at_vault_list() {
        assertEquals(listOf(Route.VaultList()), startStack(hasToken = true, hasRepo = true))
    }

    // После выбора/валидации репо — чистый стек со списком (онбординг не вернуть кнопкой назад).
    @Test fun after_repo_chosen_resets_to_vault_list() {
        assertEquals(listOf(Route.VaultList()), stackAfterRepoChosen())
    }

    // Смена репо из настроек — поверх списка, чтобы «назад» возвращал в приложение.
    @Test fun change_repo_keeps_vault_list_underneath() {
        assertEquals(listOf(Route.VaultList(), Route.RepoPicker), stackForChangeRepo())
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.StartStackTest"`
Expected: FAIL — функции не определены.

- [ ] **Step 3: Реализовать**

Create `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/StartStack.kt`:
```kotlin
package app.obsidianmd.nav

/** Стартовый бэкстек по состоянию авторизации/репозитория. */
fun startStack(hasToken: Boolean, hasRepo: Boolean): List<Route> = when {
    !hasToken -> listOf(Route.Welcome)
    !hasRepo -> listOf(Route.RepoPicker)
    else -> listOf(Route.VaultList())
}

/** После успешного выбора/валидации репо — онбординг недоступен назад. */
fun stackAfterRepoChosen(): List<Route> = listOf(Route.VaultList())

/** Смена репо из настроек: выбор репо поверх списка (есть куда вернуться). */
fun stackForChangeRepo(): List<Route> = listOf(Route.VaultList(), Route.RepoPicker)
```

- [ ] **Step 4: Запустить — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.nav.StartStackTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/nav/StartStack.kt composeApp/src/commonTest/kotlin/app/obsidianmd/nav/StartStackTest.kt
git commit -m "feat(nav): чистая логика стартового стека и переходов онбординга"
```

---

## Task 4: Упростить VaultViewModel — историю держит бэкстек

Nav3-стек заменяет внутреннюю `history`-деку и навигационные хелперы VM.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt`

**Interfaces:**
- Produces (публичный API VM после правок):
  - `fun refresh()`, `fun sync()`, `fun resolveConflict(...)`, `fun search(q)`, `fun saveFile(path, content)`, `fun bytesOf(path)`, `fun loadDocuments()` — без изменений.
  - `fun openDir(dir: String)` — загрузить содержимое папки (переименование/замена `loadDir`+`openFolder`+`upFolder`; папку выбирает бэкстек).
  - `fun openNote(path: String)` — загрузить содержимое заметки в `state.content` (заменяет `open`/`openPath`/`loadSelected`; без истории).
  - `fun clearNote()` — сбросить `content` (для пустого detail-пейна).
- Удаляется: `history`, `back()`, `atHistoryRoot()`, `clearSelection()`, `openFolder()`, `upFolder()`, `open()`, `openPath()`, поле `selected` и `atRoot` в `VaultState` (навигацию знает стек, не state).

- [ ] **Step 1: Обновить тест VM под новый API**

В `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt` заменить обращения к удаляемому API. Ключевой новый тест — открытие заметки грузит контент без побочной истории:
```kotlin
    @Test
    fun openNote_loads_content() = runTest {
        val repo = FakeVaultRepository(files = mapOf("a.md" to "hello"))
        val vm = VaultViewModel(repo, io = StandardTestDispatcher(testScheduler))
        vm.openNote("a.md")
        advanceUntilIdle()
        assertEquals("hello", vm.state.value.content)
    }

    @Test
    fun openDir_lists_entries() = runTest {
        val repo = FakeVaultRepository(dirs = mapOf("sub" to listOf(/* entries */)))
        val vm = VaultViewModel(repo, io = StandardTestDispatcher(testScheduler))
        vm.openDir("sub")
        advanceUntilIdle()
        assertEquals("sub", vm.state.value.currentDir)
    }
```
Удалить/переписать существующие тесты на `open`/`openPath`/`back`/`atHistoryRoot`/`clearSelection`/`openFolder`/`upFolder` под `openNote`/`openDir`/`clearNote`. (Сверить с фактическим содержимым `FakeVaultRepository` — конструктор фейка и его методы уже есть в `commonTest/.../vault/FakeVaultRepository.kt`; использовать их сигнатуры.)

- [ ] **Step 2: Запустить — убедиться, что падает/не компилируется**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: FAIL — новых методов ещё нет.

- [ ] **Step 3: Переписать VM**

В `VaultViewModel.kt`:
1. Из `VaultState` убрать `selected: MdFile?` и `atRoot: Boolean` (они выражаются маршрутом). Оставить `currentDir`, `content`, `entries`, `allFiles`, `loading`, `query`, `results`, `syncStatus`, `pendingConflict`, `documents`.
2. Удалить: `private val history`, `back()`, `atHistoryRoot()`, `clearSelection()`, `open()`, `openPath()`, `openFolder()`, `upFolder()`, приватный `loadSelected()`.
3. Переименовать приватный `loadDir` в публичный `openDir(dir: String)`:
```kotlin
    fun openDir(dir: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val entries = withContext(io) { repo.listEntries(dir) }
            val all = withContext(io) { repo.allFiles() }
            _state.value = _state.value.copy(
                entries = entries,
                allFiles = all,
                currentDir = dir,
                query = "",
                results = emptyList(),
                loading = false,
            )
        }
    }
```
4. Добавить загрузку заметки без истории и сброс:
```kotlin
    fun openNote(path: String) {
        val name = path.substringAfterLast('/')
        Analytics.event("note_open", mapOf("source" to "nav"))
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val text = withContext(io) { repo.readFile(path) }
            _state.value = _state.value.copy(content = text, loading = false)
        }
    }

    fun clearNote() { _state.value = _state.value.copy(content = "") }
```
5. В `refresh()` и `sync()` заменить `loadDir(...)` на `openDir(...)`.

- [ ] **Step 4: Запустить — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: PASS.
Примечание: `App.kt` пока ссылается на удалённые поля (`state.selected` и т.п.) → модуль целиком не соберётся до Task 5. Это ожидаемо: Task 4 проверяется только через `--tests` VaultViewModelTest, полную сборку даёт Task 5. (Если нужен зелёный build между тасками — объединить Task 4 и Task 5 при исполнении.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt
git commit -m "refactor(vault): историю навигации держит бэкстек, VM упрощён"
```

---

## Task 5: AppNavHost + перевод MainActivity, удаление флаговой навигации

Ядро задачи: один `NavDisplay` со всеми маршрутами; `Gate` и `App.kt` заменяются; `PlatformBackHandler` удаляется.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt`
- Delete: `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`
- Delete: `composeApp/src/commonMain/kotlin/app/obsidianmd/PlatformBackHandler.kt`, `composeApp/src/androidMain/kotlin/app/obsidianmd/PlatformBackHandler.android.kt`

**Interfaces:**
- Consumes: `Route`, `navSavedStateConfiguration` (Task 2); `startStack/stackAfterRepoChosen/stackForChangeRepo` (Task 3); `VaultViewModel.openDir/openNote/clearNote` (Task 4); существующие экраны и их сигнатуры (`VaultPresentationProvider.ListScreen`, `MarkdownScreen`, `SettingsScreen`, `ModelPickerScreen`, `AiChatScreen`, `WelcomeScreen`, `LoginScreen`, `RepoPickerScreen`, `ManualUrlScreen`, `RepoValidationScreen`); ViewModel'и из Koin.
- Produces:
  - `@Composable fun AppNavHost(initialStack: List<Route>)` — ViewModel'и берутся внутри через Koin (`koinViewModel()`), как в текущем `Gate`. Никаких callback'ов: смену репо хост делает сам через бэкстек (`stackForChangeRepo()`).

- [ ] **Step 1: Написать хост**

Create `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt`. Полный каркас (импорты material3/nav3 добавить по факту, тела экранов — вызовы существующих composable с их текущими параметрами из `App.kt`/`MainActivity.kt`):

```kotlin
package app.obsidianmd.nav

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
// ... остальные импорты по факту

/**
 * Единый хост навигации. Бэкстек — источник правды для истории; онбординг, папки,
 * заметки, настройки и AI живут в одном стеке. TopAppBar и нижняя навигация зависят
 * от верхнего маршрута.
 */
@Composable
fun AppNavHost(initialStack: List<Route>) {
    val backStack = rememberNavBackStack(navSavedStateConfiguration, *initialStack.toTypedArray())

    // ViewModel'и — как в MainActivity (Koin). Пример:
    // val vm: VaultViewModel = koinViewModel(); val settingsVm: SettingsViewModel = koinViewModel(); ...

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // sceneStrategy — добавляется в Task 6 (list-detail)
        entryProvider = entryProvider {
            entry<Route.Welcome> {
                // WelcomeScreen(onSignIn = authVm::login)
                // После успешного логина (authState == Success): backStack.replaceAll(startStack(true, hasRepo))
            }
            entry<Route.Login> { /* LoginScreen(state, onLogin, onOpenUrl) */ }
            entry<Route.RepoPicker> {
                // RepoPickerScreen(state, onChoose=pickerVm::pick, onRetry, onEnterManually={ backStack.add(Route.RepoManualUrl) },
                //   onBack = if (backStack.size > 1) ({ backStack.removeLastOrNull() }) else null)
                // при picked → backStack.add(Route.RepoValidate(url))
            }
            entry<Route.RepoManualUrl> {
                // ManualUrlScreen(onSubmit={ url -> backStack.add(Route.RepoValidate(url)) }, onBack={ backStack.removeLastOrNull() })
            }
            entry<Route.RepoValidate> { key ->
                // RepoValidationScreen(..., onContinue={ settingsVm.save(key.url); vm.sync(); backStack.replaceWith(stackAfterRepoChosen()) },
                //   onBack={ backStack.removeLastOrNull() })
            }
            entry<Route.VaultList> { key ->
                // LaunchedEffect(key.dir) { vm.openDir(key.dir.ifBlank { repoRoot }) }
                // vaultPresentation.ListScreen(..., onOpenFile={ backStack.add(Route.Note(it.path)) },
                //   onOpenFolder={ backStack.add(Route.VaultList(it.path)) }, ...)
            }
            entry<Route.Note> { key ->
                // LaunchedEffect(key.path) { vm.openNote(key.path) }
                // MarkdownScreen(...) — режим правки/showUnsaved остаётся локальным state ЗДЕСЬ.
                // onOpenPath (wikilink) = { backStack.add(Route.Note(it)) }
            }
            entry<Route.Settings> {
                // SettingsScreen(..., onEditModel={ backStack.add(Route.ModelPicker) },
                //   onPickFromGitHub={ backStack.replaceWith(stackForChangeRepo()) })
            }
            entry<Route.ModelPicker> {
                // ModelPickerScreen(..., onSelect={ settingsVm.setAiModel(it); backStack.removeLastOrNull() })
            }
            entry<Route.AiChat> {
                // AiChatScreen(...); onOpenFile={ path -> backStack.add(Route.Note(path)) } — «назад» вернёт в чат сам.
            }
        }
    )
    // TopAppBar и нижнюю навигацию (Brain↔AI) обернуть вокруг NavDisplay в Scaffold:
    //  - заголовок/иконки = when (backStack.lastOrNull()) { ... }
    //  - нижняя навигация видна при settings.aiEnabled && верхний маршрут ∈ {VaultList, AiChat} && !isImeVisible
    //  - Brain↔AI = замена верхнего корня: backStack.replaceTopRoot(Route.VaultList()) / Route.AiChat
    //  - ConflictDialog(conflict) и диалог showUnsaved — как в App.kt
}
```

Реализовать полностью, перенеся тела экранов из `App.kt` (строки 156–357) и `MainActivity.Gate` (строки 91–192) один-в-один, заменив флаги на `backStack`-операции по таблице спеки. Хелперы вроде `replaceWith(list)`/`replaceTopRoot(route)` — тривиальные extension'ы над `SnapshotStateList` (`clear(); addAll(list)`), объявить рядом.

- [ ] **Step 2: Перевести MainActivity на хост**

В `MainActivity.kt`:
1. Оставить off-main-thread прогрев Koin-хранилищ и вычисление `initialLoggedIn` (как сейчас, строки ~64–84).
2. Заменить вызов `Gate(loggedIn)` на вычисление стартового стека и `AppNavHost`:
```kotlin
val settings by settingsVm.state.collectAsState()   // для hasRepo
val start = startStack(hasToken = loggedIn, hasRepo = settings.url.isNotBlank())
AppNavHost(initialStack = start)
```
3. Удалить `@Composable private fun Gate(...)` и `private enum class RepoStep`.
4. Прогрев VM/ключей AI (строки 166–183 старого `Gate`) перенести в `entry<Route.AiChat>`/хост, где он нужен.

- [ ] **Step 3: Удалить старое**

```bash
git rm composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt \
       composeApp/src/commonMain/kotlin/app/obsidianmd/PlatformBackHandler.kt \
       composeApp/src/androidMain/kotlin/app/obsidianmd/PlatformBackHandler.android.kt
```
Убедиться, что `PlatformBackHandler` больше нигде не используется:
Run: `grep -rn "PlatformBackHandler" composeApp/src` → пусто.

- [ ] **Step 4: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Прогнать все юнит- и Robolectric-тесты**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — экранные Robolectric-тесты зелёные (экраны не менялись).

- [ ] **Step 6: Ручная проверка навигации**

Запустить приложение. Проверить сценарии, ранее жившие на флагах:
- Онбординг: Welcome → Login → RepoPicker → (Manual/)Validate → список.
- Список → папка → вложенная папка → системный «назад» поднимает по папкам.
- Список → заметка → wikilink → заметка → «назад» возвращает к предыдущей заметке, затем к списку.
- AI-чат → открыть заметку из чата → «назад» возвращает в чат (бывший `noteFromAi`).
- Правка заметки → «назад» с несохранёнными правками → диалог `showUnsaved`.
- Настройки → пикер моделей → выбор → возврат. Смена репо из настроек → «назад» возвращает в приложение.
- Нижняя навигация Brain↔AI при `aiEnabled`; скрыта при открытой клавиатуре.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(nav): единый NavDisplay-хост, удаление флаговой навигации и PlatformBackHandler"
```

---

## Task 6: Адаптивный list-detail для VaultList/Note

На широких экранах список и заметка — рядом.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt`

**Interfaces:**
- Consumes: `NavDisplay`, `entry` metadata API из `adaptive-navigation3`.

- [ ] **Step 1: Подключить scene strategy**

В `AppNavHost` создать стратегию и передать в `NavDisplay`:
```kotlin
val listDetail = rememberListDetailSceneStrategy<Route>()
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    sceneStrategy = listDetail,
    entryProvider = entryProvider { ... }
)
```
Примечание версионной вариативности: в резолвнутом `adaptive-navigation3` фабрика может называться `rememberListDetailSceneStrategy<Route>()` и передаваться как `sceneStrategy = listDetail` **или** `sceneStrategies = listOf(listDetail)`. Метаданные пейнов — `ListDetailSceneStrategy.listPane()`/`detailPane()` **или** `ListDetailScene.listPane()`/`detailPane()`. Взять фактические имена из версии 1.3.0-beta02 (одна из двух пар); обе — известная граница, не placeholder.

- [ ] **Step 2: Разметить пейны у маршрутов**

У `entry<Route.VaultList>` и `entry<Route.Note>` добавить metadata:
```kotlin
entry<Route.VaultList>(metadata = ListDetailSceneStrategy.listPane()) { key -> /* ... */ }
entry<Route.Note>(metadata = ListDetailSceneStrategy.detailPane()) { key -> /* ... */ }
```
Остальные маршруты (онбординг, Settings, ModelPicker, AiChat) — без metadata: показываются на всю ширину.

- [ ] **Step 3: Пустой detail-пейн**

Добавить в хост заглушку и показывать её как detail, когда `Note` не в стеке, но окно широкое. Простейший вариант — при разложенном list-detail без `Note` на верхушке стратегия сама покажет placeholder-composable; задать его текстом «Выберите заметку» (использовать `Res.string` — добавить строку `detail_empty` в `core/translations`, если строки берутся оттуда; иначе временный литерал согласовать с существующим стилем).

- [ ] **Step 4: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Ручная проверка на широком экране**

Запустить на планшетном эмуляторе / развернуть окно (resizable emulator) в ширину >600dp:
- Список слева, при выборе заметки — заметка справа, список остаётся.
- Без выбранной заметки справа — «Выберите заметку».
- Узкий экран (телефон) — поведение как в Task 6 не изменилось: список → пуш заметки → «назад».
- Онбординг/настройки/AI — на всю ширину.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt core/translations
git commit -m "feat(nav): адаптивный list-detail для списка и заметки"
```

---

## Финальная проверка

- [ ] `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest` — зелёно.
- [ ] `grep -rn "showSettings\|showModelPicker\|showAi\|noteFromAi\|RepoStep\|PlatformBackHandler" composeApp/src` — пусто (флаговая навигация удалена).
- [ ] Сверить сценарии Task 5 Step 6 ещё раз после list-detail.
