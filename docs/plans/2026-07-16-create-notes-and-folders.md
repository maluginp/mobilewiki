# Создание md-файлов и папок — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дать пользователю создавать новые .md-заметки и папки прямо с экрана списка, в текущем открытом каталоге.

**Architecture:** Чистая логика имён — в `:vault:api` (без IO, переиспользуется VM и UI). Создание папки — новый метод `VaultRepository.createFolder`; заметка — существующий `writeFile(path, "")`. VM добавляет `createNote`/`createFolder` (IO + refresh + analytics), навигацию к новой заметке делает `AppNavHost` через колбэк после записи. UI — FAB → меню → диалог с валидацией.

**Tech Stack:** Kotlin Multiplatform, Compose Material3, okio (vault FS), kotlinx.coroutines, JUnit/kotlin.test, Robolectric + compose-uiTest, compose-resources (i18n).

## Global Constraints

- Все пользовательские строки — через compose-resources (`stringResource(Res.string.*)`), заводятся в `core/translations/.../values/strings.xml` и `values-ru/strings.xml`. Нелокализованные технические строки — не в UI.
- Новые чистые хелперы — в `:vault:api` (пакет `app.obsidianmd.vault`), рядом с `WikiLinks.kt` — доступны и из `:vault:impl`, и из `:composeApp`.
- TDD строго: тест → падение → минимальный код → зелёный → коммит.
- Оба фейка `FakeVaultRepository` (composeApp/commonTest и features/ai/impl/commonTest) реализуют интерфейс `VaultRepository` — при добавлении метода в интерфейс обновляются оба.
- Аналитика через `app.obsidianmd.analytics.Analytics.event(name, params)` (как `note_save`, `note_open`).

---

### Task 1: Чистая логика имён (EntryNaming)

**Files:**
- Create: `features/vault/api/src/commonMain/kotlin/app/obsidianmd/vault/EntryNaming.kt`
- Test: `features/vault/api/src/commonTest/kotlin/app/obsidianmd/vault/EntryNamingTest.kt`

**Interfaces:**
- Produces:
  - `fun noteFileName(raw: String): String` — trim + добавить `.md`, если нет (без регистра).
  - `enum class NameError { Blank, Slash, Exists }`
  - `fun entryNameError(finalName: String, existing: List<String>): NameError?`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.obsidianmd.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntryNamingTest {
    @Test fun note_file_name_adds_md_and_trims() {
        assertEquals("todo.md", noteFileName("  todo "))
        assertEquals("a.md", noteFileName("a.md"))
        assertEquals("a.md", noteFileName("a.MD")) // расширение уже есть, не удваиваем
    }

    @Test fun name_error_reports_blank_slash_exists() {
        assertEquals(NameError.Blank, entryNameError("", listOf()))
        assertEquals(NameError.Blank, entryNameError("   ", listOf()))
        assertEquals(NameError.Slash, entryNameError("a/b", listOf()))
        assertEquals(NameError.Exists, entryNameError("Note.md", listOf("note.md"))) // без регистра
        assertNull(entryNameError("fresh.md", listOf("note.md")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :features:vault:api:testDebugUnitTest --tests "app.obsidianmd.vault.EntryNamingTest"`
Expected: FAIL (unresolved reference: `noteFileName` / `entryNameError` / `NameError`).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package app.obsidianmd.vault

/** Чистая логика имён для создания заметок и папок. Без IO — тестируется в изоляции. */

/** Имя файла заметки: обрезает пробелы и добавляет `.md`, если расширения ещё нет. */
fun noteFileName(raw: String): String {
    val name = raw.trim()
    return if (name.endsWith(".md", ignoreCase = true)) name else "$name.md"
}

enum class NameError { Blank, Slash, Exists }

/**
 * Ошибка имени новой записи или null, если имя годное.
 * [finalName] — уже приведённое имя (заметка → [noteFileName], папка → trim).
 * [existing] — имена записей текущего каталога.
 */
fun entryNameError(finalName: String, existing: List<String>): NameError? {
    val trimmed = finalName.trim()
    return when {
        trimmed.isEmpty() || trimmed == ".md" -> NameError.Blank
        trimmed.contains('/') -> NameError.Slash
        existing.any { it.equals(trimmed, ignoreCase = true) } -> NameError.Exists
        else -> null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :features:vault:api:testDebugUnitTest --tests "app.obsidianmd.vault.EntryNamingTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add features/vault/api/src/commonMain/kotlin/app/obsidianmd/vault/EntryNaming.kt \
        features/vault/api/src/commonTest/kotlin/app/obsidianmd/vault/EntryNamingTest.kt
git commit -m "feat(vault): pure entry-name logic (noteFileName, entryNameError)"
```

---

### Task 2: Создание папки в репозитории (createFolder)

**Files:**
- Modify: `features/vault/api/src/commonMain/kotlin/app/obsidianmd/vault/VaultRepository.kt`
- Modify: `features/vault/impl/src/commonMain/kotlin/app/obsidianmd/vault/data/OkioVaultRepository.kt`
- Modify: `composeApp/src/commonTest/kotlin/app/obsidianmd/vault/FakeVaultRepository.kt`
- Modify: `features/ai/impl/src/commonTest/kotlin/app/obsidianmd/vault/FakeVaultRepository.kt`
- Test: `features/vault/impl/src/commonTest/kotlin/app/obsidianmd/vault/data/VaultRepositoryTest.kt`

**Interfaces:**
- Produces: `fun createFolder(path: String)` в интерфейсе `VaultRepository`.
- Consumes: okio `FileSystem.createDirectories`, `String.toPath()` (уже используются).

- [ ] **Step 1: Write the failing test** (добавить в `VaultRepositoryTest`)

```kotlin
    @Test
    fun create_folder_makes_dir_visible_in_entries_and_is_idempotent() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        val repo = OkioVaultRepository(fs, root)

        repo.createFolder((root / "Ideas").toString())
        repo.createFolder((root / "Ideas").toString()) // повторно — не падает

        val entries = repo.listEntries(root.toString())
        assertEquals(listOf("Ideas"), entries.map { it.name })
        assertTrue(entries.single().isFolder)
    }

    @Test
    fun create_folder_creates_parent_dirs() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        val repo = OkioVaultRepository(fs, root)

        repo.createFolder((root / "a" / "b").toString())
        assertEquals(listOf("b"), repo.listEntries((root / "a").toString()).map { it.name })
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :features:vault:impl:testDebugUnitTest --tests "app.obsidianmd.vault.data.VaultRepositoryTest"`
Expected: FAIL (нет метода `createFolder` в `VaultRepository`; не компилируется).

- [ ] **Step 3: Write minimal implementation**

В `VaultRepository.kt` добавить в интерфейс:

```kotlin
    /** Создать папку по пути (вместе с родителями); идемпотентно. */
    fun createFolder(path: String)
```

В `OkioVaultRepository.kt` реализовать (рядом с `writeFile`):

```kotlin
    override fun createFolder(path: String) {
        fs.createDirectories(path.toPath())
    }
```

В обоих `FakeVaultRepository.kt` — папку моделируем маркерным ключом внутри неё, чтобы `listEntries` увидел каталог (он выводит папки из путей):

```kotlin
    override fun createFolder(path: String) {
        files["$path/.keep"] = ""
    }
```

> Внимание: фейки фильтруют записи, начинающиеся с `.`, из содержимого папки (`rest.startsWith(".")`), поэтому `.keep` не засоряет список внутри новой папки, но родитель уже видит саму папку по префиксу пути. Проверь, что в `listEntries` папка появляется (в тестах VM ниже).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :features:vault:impl:testDebugUnitTest --tests "app.obsidianmd.vault.data.VaultRepositoryTest"`
Expected: PASS. Затем сборка зависимых модулей (фейки компилируются): `./gradlew :composeApp:compileDebugUnitTestKotlinAndroid :features:ai:impl:compileDebugUnitTestKotlinAndroid` — без ошибок.

- [ ] **Step 5: Commit**

```bash
git add features/vault/api/.../VaultRepository.kt \
        features/vault/impl/.../data/OkioVaultRepository.kt \
        features/vault/impl/.../data/VaultRepositoryTest.kt \
        composeApp/src/commonTest/kotlin/app/obsidianmd/vault/FakeVaultRepository.kt \
        features/ai/impl/src/commonTest/kotlin/app/obsidianmd/vault/FakeVaultRepository.kt
git commit -m "feat(vault): createFolder on repository + fakes"
```

---

### Task 3: ViewModel — createNote / createFolder + аналитика

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt`

**Interfaces:**
- Consumes: `noteFileName` (Task 1), `repo.createFolder` (Task 2), `repo.writeFile`, `VaultState.currentDir`, `Analytics.event`.
- Produces:
  - `fun createFolder(rawName: String)`
  - `fun createNote(rawName: String, onCreated: (String) -> Unit)`

- [ ] **Step 1: Write the failing test** (добавить в `VaultViewModelTest`)

```kotlin
    @Test
    fun create_folder_creates_and_refreshes() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val repo = FakeVaultRepository(root, mapOf("$root/a.md" to "x"))
        val model = VaultViewModel(repo, io)
        model.refresh(); advanceUntilIdle()

        model.createFolder("Ideas"); advanceUntilIdle()

        assertTrue(model.state.value.entries.any { it.name == "Ideas" && it.isFolder })
    }

    @Test
    fun create_note_writes_empty_file_and_reports_path() = runTest(dispatcher) {
        val io = StandardTestDispatcher(testScheduler)
        val repo = FakeVaultRepository(root, mapOf("$root/a.md" to "x"))
        val model = VaultViewModel(repo, io)
        model.refresh(); advanceUntilIdle()

        var created: String? = null
        model.createNote("todo") { created = it }
        advanceUntilIdle()

        assertEquals("$root/todo.md", created)
        assertEquals("", repo.readFile("$root/todo.md"))
        assertTrue(model.state.value.entries.any { it.name == "todo.md" })
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: FAIL (unresolved reference: `createFolder` / `createNote`).

- [ ] **Step 3: Write minimal implementation** (в `VaultViewModel`, рядом с `saveFile`)

```kotlin
    /** Создать папку с именем [rawName] в текущем каталоге и обновить список. */
    fun createFolder(rawName: String) {
        val dir = _state.value.currentDir.ifBlank { repo.rootPath }
        val path = "$dir/${rawName.trim()}"
        Analytics.event("folder_create")
        viewModelScope.launch {
            withContext(io) { repo.createFolder(path) }
            loadDir(dir)
        }
    }

    /** Создать пустую .md-заметку в текущем каталоге; [onCreated] получает путь ПОСЛЕ записи. */
    fun createNote(rawName: String, onCreated: (String) -> Unit) {
        val dir = _state.value.currentDir.ifBlank { repo.rootPath }
        val path = "$dir/" + app.obsidianmd.vault.noteFileName(rawName)
        Analytics.event("note_create")
        viewModelScope.launch {
            withContext(io) { repo.writeFile(path, "") }
            loadDir(dir)
            onCreated(path)
        }
    }
```

> `loadDir` — приватный `suspend`, уже есть; `onCreated` вызывается на диспетчере viewModelScope (Main) после записи — навигация без гонки read-before-write.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt \
        composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt
git commit -m "feat(vault): VM createNote/createFolder with analytics"
```

---

### Task 4: UI — FAB, меню и диалог создания + строки

**Files:**
- Modify: `features/vault/impl/src/commonMain/kotlin/app/obsidianmd/vault/presentation/VaultListScreen.kt`
- Modify: `features/vault/api/src/commonMain/kotlin/app/obsidianmd/vault/VaultPresentationProvider.kt`
- Modify: `features/vault/impl/src/commonMain/kotlin/app/obsidianmd/vault/presentation/VaultPresentationProviderImpl.kt`
- Modify: `core/translations/src/commonMain/composeResources/values/strings.xml`
- Modify: `core/translations/src/commonMain/composeResources/values-ru/strings.xml`
- Test: `features/vault/impl/src/androidUnitTest/kotlin/app/obsidianmd/vault/presentation/VaultListScreenTest.kt`

**Interfaces:**
- Consumes: `entries` (для валидации), `noteFileName`/`entryNameError`/`NameError` (Task 1).
- Produces: у `ListScreen` — новые параметры `onCreateNote: (String) -> Unit`, `onCreateFolder: (String) -> Unit`.

- [ ] **Step 1: Write the failing test** (добавить в `VaultListScreenTest`; строки — англ. значения ресурсов)

```kotlin
    @Test
    fun fab_menu_shows_create_options() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = emptyList(), loading = false, refreshing = false,
                query = "", results = emptyList(), title = "Notes",
                onQueryChange = {}, onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {},
            )
        }
        onNodeWithContentDescription("Create").performClick()
        onNodeWithText("New note").assertIsDisplayed()
        onNodeWithText("New folder").assertIsDisplayed()
    }

    @Test
    fun create_button_disabled_for_blank_and_duplicate() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = listOf(VaultEntry("note.md", "/vault/note.md", isFolder = false)),
                loading = false, refreshing = false,
                query = "", results = emptyList(), title = "Notes",
                onQueryChange = {}, onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {},
            )
        }
        onNodeWithContentDescription("Create").performClick()
        onNodeWithText("New note").performClick()
        // пусто → «Create» неактивна
        onNodeWithText("Create").assertIsNotEnabled()
        // дубликат → «Create» неактивна
        onNodeWithText("Name").performTextInput("note")
        onNodeWithText("Create").assertIsNotEnabled()
    }
```

> Импорты для теста: `androidx.compose.ui.test.performClick`, `performTextInput`, `assertIsNotEnabled`, `onNodeWithContentDescription`, `app.obsidianmd.vault.VaultEntry`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :features:vault:impl:testDebugUnitTest --tests "app.obsidianmd.vault.presentation.VaultListScreenTest"`
Expected: FAIL (нет параметров `onCreateNote`/`onCreateFolder`; нет FAB/меню/диалога).

- [ ] **Step 3: Write minimal implementation**

3a. Строки в `values/strings.xml` (секция «Notes list»):

```xml
    <string name="action_new_note">New note</string>
    <string name="action_new_folder">New folder</string>
    <string name="action_create">Create</string>
    <string name="cd_create">Create</string>
    <string name="create_note_title">New note</string>
    <string name="create_folder_title">New folder</string>
    <string name="create_name_hint">Name</string>
    <string name="create_error_blank">Enter a name</string>
    <string name="create_error_slash">Name can't contain “/”</string>
    <string name="create_error_exists">Already exists</string>
```

3b. Те же ключи в `values-ru/strings.xml`:

```xml
    <string name="action_new_note">Новая заметка</string>
    <string name="action_new_folder">Новая папка</string>
    <string name="action_create">Создать</string>
    <string name="cd_create">Создать</string>
    <string name="create_note_title">Новая заметка</string>
    <string name="create_folder_title">Новая папка</string>
    <string name="create_name_hint">Имя</string>
    <string name="create_error_blank">Введите имя</string>
    <string name="create_error_slash">Имя не может содержать «/»</string>
    <string name="create_error_exists">Уже существует</string>
```

3c. `VaultPresentationProvider.ListScreen` и `VaultPresentationProviderImpl` — добавить два параметра `onCreateNote: (String) -> Unit`, `onCreateFolder: (String) -> Unit` и пробросить их в `VaultListScreen`.

3d. `VaultListScreen`: добавить `onCreateNote`/`onCreateFolder` в сигнатуру; в `Scaffold` — `floatingActionButton` (скрыт при `searching`); FAB открывает `DropdownMenu`; пункт открывает диалог. Диалог — приватный компонент:

```kotlin
// В Scaffold(...) добавить:
floatingActionButton = {
    if (!searching) {
        var menu by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<Boolean?>(null) } // true=заметка, false=папка, null=закрыт
        Box {
            FloatingActionButton(onClick = { menu = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.cd_create))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_new_note)) },
                    onClick = { menu = false; dialog = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_new_folder)) },
                    onClick = { menu = false; dialog = false },
                )
            }
        }
        dialog?.let { isNote ->
            CreateEntryDialog(
                isNote = isNote,
                existingNames = entries.map { it.name },
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    dialog = null
                    if (isNote) onCreateNote(name) else onCreateFolder(name)
                },
            )
        }
    }
},
```

Диалог (в этом же файле, приватная @Composable):

```kotlin
@Composable
private fun CreateEntryDialog(
    isNote: Boolean,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val finalName = if (isNote) noteFileName(text) else text.trim()
    val error = entryNameError(finalName, existingNames)
    val errorText = when (error) {
        NameError.Blank -> stringResource(Res.string.create_error_blank)
        NameError.Slash -> stringResource(Res.string.create_error_slash)
        NameError.Exists -> stringResource(Res.string.create_error_exists)
        null -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNote) Res.string.create_note_title else Res.string.create_folder_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.create_name_hint)) },
                isError = text.isNotEmpty() && error != null,
                supportingText = { if (text.isNotEmpty() && errorText != null) Text(errorText) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = error == null) {
                Text(stringResource(Res.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
```

> Новые импорты в `VaultListScreen.kt`: `Icons.Filled.Add`, `FloatingActionButton`, `DropdownMenu`, `DropdownMenuItem`, `AlertDialog`, `OutlinedTextField`, `TextButton`, `Text`, и `app.obsidianmd.vault.noteFileName`, `entryNameError`, `NameError`, ресурсы `Res.string.*` (добавить в блок генерируемых импортов `app.obsidianmd.resources.*`). `action_cancel` уже есть в строках.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :features:vault:impl:testDebugUnitTest --tests "app.obsidianmd.vault.presentation.VaultListScreenTest"`
Expected: PASS (включая прежние 2 теста лоадера).

- [ ] **Step 5: Commit**

```bash
git add features/vault/api/.../VaultPresentationProvider.kt \
        features/vault/impl/.../presentation/VaultListScreen.kt \
        features/vault/impl/.../presentation/VaultPresentationProviderImpl.kt \
        features/vault/impl/.../presentation/VaultListScreenTest.kt \
        core/translations/src/commonMain/composeResources/values/strings.xml \
        core/translations/src/commonMain/composeResources/values-ru/strings.xml
git commit -m "feat(vault): create-entry FAB, menu and dialog with validation"
```

---

### Task 5: Проброс в навигацию (AppNavHost)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt:117-130`

**Interfaces:**
- Consumes: `vm.createNote` / `vm.createFolder` (Task 3), новые параметры `ListScreen` (Task 4), `backStack`, `Route.Note`.

> Это склеивающий слой (навигация + бэкстек) — юнит-тестами не покрываем, проверяется приёмочными кейсами. Компиляция + существующие тесты — регрессионная страховка.

- [ ] **Step 1: Modify — прокинуть колбэки в `vaultPresentation.ListScreen(...)`**

```kotlin
                    vaultPresentation.ListScreen(
                        title = title,
                        entries = state.entries,
                        loading = state.loading,
                        refreshing = state.syncStatus is SyncStatus.Running,
                        query = state.query,
                        results = state.results,
                        onQueryChange = vm::search,
                        onOpenFile = { backStack.add(Route.Note(it.path)) },
                        onOpenFolder = { backStack.add(Route.VaultList(it.path)) },
                        onRefresh = vm::sync,
                        onOpenSettings = { backStack.add(Route.Settings) },
                        onBack = if (backStack.size > 1) ({ backStack.removeLastOrNull(); Unit }) else null,
                        onCreateNote = { name -> vm.createNote(name) { path -> backStack.add(Route.Note(path)) } },
                        onCreateFolder = vm::createFolder,
                    )
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: SUCCESS (все параметры `ListScreen` переданы).

- [ ] **Step 3: Full test suite (регрессия)**

Run: `./gradlew :composeApp:testDebugUnitTest :features:vault:api:testDebugUnitTest :features:vault:impl:testDebugUnitTest`
Expected: PASS (все зелёные).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt
git commit -m "feat(vault): wire create-note/create-folder in nav host"
```

---

## Self-review

**Spec coverage:**
- Создание заметки в текущем каталоге + открытие редактора → Task 3 (`createNote`) + Task 5 (навигация к `Route.Note`).
- Создание папки, папки первыми → Task 2 (`createFolder`) + Task 3 + существующая сортировка `listEntries`.
- Валидация имени (пусто/«/»/дубликат) → Task 1 (`entryNameError`) + Task 4 (диалог).
- Обновление списка после создания → Task 3 (`loadDir`).
- Синхронизация подхватывает изменения → существующий git-sync (кода не требует; приёмочный кейс).
- FAB скрыт при поиске, меню «Новая заметка»/«Новая папка» → Task 4.
- i18n (en+ru) → Task 4. Аналитика (`note_create`, `folder_create`) → Task 3.

**Placeholder scan:** нет «TBD»/«add error handling» — весь код приведён.

**Type consistency:** `noteFileName`/`entryNameError`/`NameError` (Task 1) используются в Task 3 и Task 4 с теми же сигнатурами; `createFolder(path)` (Task 2) вызывается в Task 3; параметры `onCreateNote`/`onCreateFolder` (Task 4) прокидываются в Task 5 идентично.
