# Note Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Поиск заметок по имени и содержимому с открытием найденной.

**Architecture:** `VaultRepository.search` наивно сканирует имена и содержимое; `VaultViewModel.search` кладёт запрос/результаты в состояние; `VaultListScreen` показывает поле поиска и результаты. Не-UI логика тестируется на `FakeFileSystem`.

**Tech Stack:** Compose Multiplatform, okio, kotlinx-coroutines.

## Global Constraints

- Пакеты: `app.obsidianmd.vault`, `app.obsidianmd.ui`.
- Совпадение = подстрока (lowercase) в имени ИЛИ содержимом; пустой запрос → пусто.
- Наивный скан без индекса (ponytail-комментарий). Аналитика не вводится (нет стека).

---

### Task 1: VaultRepository.search

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/vault/VaultRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/vault/VaultRepositoryTest.kt` (добавить)

**Interfaces:**
- Produces: `fun VaultRepository.search(query: String): List<MdFile>`.

- [ ] **Step 1: Падающий тест**

Добавить в `VaultRepositoryTest`:
```kotlin
    @Test
    fun search_matches_name_and_content_case_insensitive() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "todo.md") { writeUtf8("список дел") }
        fs.write(root / "notes.md") { writeUtf8("важный проект здесь") }
        fs.write(root / "misc.md") { writeUtf8("ничего") }
        val repo = VaultRepository(fs, root)

        assertEquals(listOf("notes.md"), repo.search("проект").map { it.name })
        assertEquals(listOf("todo.md"), repo.search("todo").map { it.name })
        assertEquals(listOf("todo.md"), repo.search("TODO").map { it.name })
        assertEquals(emptyList(), repo.search(""))
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: FAIL — `search` не определён.

- [ ] **Step 3: Реализация (добавить в VaultRepository)**

```kotlin
    // ponytail: наивный полный скан содержимого, без индекса — ок для личного vault;
    // индекс, если станет медленно на больших хранилищах.
    fun search(query: String): List<MdFile> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase()
        return listMarkdownFiles().filter { f ->
            f.name.lowercase().contains(q) || readFile(f.path).lowercase().contains(q)
        }
    }
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/vault/VaultRepository.kt composeApp/src/commonTest/kotlin/app/obsidianmd/vault/VaultRepositoryTest.kt
git commit -m "feat: VaultRepository.search (name + content, case-insensitive)"
```

---

### Task 2: VaultViewModel.search + состояние

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt` (добавить)

**Interfaces:**
- Consumes: `VaultRepository.search` (Task 1). Produces: `VaultState.query`/`results`; `fun VaultViewModel.search(query)`.

- [ ] **Step 1: Падающий тест**

Добавить в `VaultViewModelTest`:
```kotlin
    @Test
    fun search_updates_query_and_results() = runTest {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "a.md") { writeUtf8("важный проект") }
        fs.write(root / "b.md") { writeUtf8("ничего") }
        val model = VaultViewModel(VaultRepository(fs, root), this, StandardTestDispatcher(testScheduler))

        model.search("проект")
        advanceUntilIdle()

        assertEquals("проект", model.state.value.query)
        assertEquals(listOf("a.md"), model.state.value.results.map { it.name })
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: FAIL — нет `search`/`query`/`results`.

- [ ] **Step 3: Реализация**

В `VaultState` добавить поля:
```kotlin
    val query: String = "",
    val results: List<MdFile> = emptyList(),
```
В `VaultViewModel` добавить метод:
```kotlin
    fun search(query: String) {
        scope.launch {
            val results = withContext(io) { repo.search(query) }
            _state.value = _state.value.copy(query = query, results = results)
        }
    }
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt
git commit -m "feat: VaultViewModel.search + query/results state"
```

---

### Task 3: VaultListScreen поле поиска + проводка в App

UI — ручная приёмка (юнит-тестов Compose нет).

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultListScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`

**Interfaces:**
- Consumes: `VaultViewModel.search`, `VaultState.query/results`. Produces: `VaultListScreen(..., query, results, onSearch)`.

- [ ] **Step 1: VaultListScreen — поле поиска + результаты**

Добавить в сигнатуру `VaultListScreen` параметры `query: String`, `results: List<MdFile>`,
`onSearch: (String) -> Unit`; добавить импорт `androidx.compose.material3.OutlinedTextField`.
Внутри `Column` (после кнопок «Синхронизировать»/«Настройки», перед списком):
```kotlin
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
```
И заменить источник списка: вместо `state.files` использовать
```kotlin
        val shown = if (query.isBlank()) state.files else results
```
— пустой блок `if (shown.isEmpty()) { Box(... "Нет файлов") }` и `LazyColumn { items(shown) { ... } }`
(текст «Нет файлов» оставить для пустого списка). Тап так же зовёт `onOpen`.

- [ ] **Step 2: App — прокинуть поиск**

В `App.kt` в ветке списка:
```kotlin
                state.selected == null -> VaultListScreen(
                    state, syncStatus,
                    onSync = vm::sync,
                    onOpen = vm::open,
                    onOpenSettings = { showSettings = true },
                    query = state.query,
                    results = state.results,
                    onSearch = vm::search,
                )
```

- [ ] **Step 3: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Ручная приёмка**

Прогнать кейсы из спеки (поиск по имени; по содержимому; очистка → весь список; нет совпадений).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain
git commit -m "feat: search field + results in VaultListScreen"
```

---

## Self-review

**Покрытие спеки:**
- `VaultRepository.search` → Task 1.
- `VaultViewModel.search` + `query`/`results` → Task 2.
- Поле поиска + показ результатов + проводка → Task 3.
- Приёмочные кейсы → в спеке; Task 3 Step 4.
- Аналитика → неприменима.

**Placeholder-скан:** реальный код/команды; заглушек нет.

**Согласованность типов:** `VaultRepository.search(query): List<MdFile>`;
`VaultState.{query: String, results: List<MdFile>}`; `VaultViewModel.search(query)`;
`VaultListScreen(state, syncStatus, onSync, onOpen, onOpenSettings, query, results, onSearch)`.
`MdFile.name`/`path`, `onOpen` — существующие. Имена согласованы Task 1→3.
