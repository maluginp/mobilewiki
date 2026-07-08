# Markdown Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Править содержимое открытой заметки и сохранять на диск (в vault); правки подхватывает git-синк.

**Architecture:** `VaultRepository.writeFile` пишет через okio; `VaultViewModel.saveFile` персистит и обновляет `content`; `MarkdownScreen` получает режим-переключатель просмотр↔редактор. Не-UI логика тестируется на `FakeFileSystem`.

**Tech Stack:** Compose Multiplatform, okio, kotlinx-coroutines.

## Global Constraints

- Пакеты: `app.obsidianmd.vault`, `app.obsidianmd.ui`.
- Правка — только открытого существующего файла; создание/переименование/удаление вне слайса.
- Сохранение = запись файла; синк отдельно (кнопка/автосинк).
- Не-UI логика тестируется в commonTest на `FakeFileSystem`. Аналитика не вводится (нет стека).

---

### Task 1: VaultRepository.writeFile

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/vault/VaultRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/vault/VaultRepositoryTest.kt` (добавить)

**Interfaces:**
- Produces: `fun VaultRepository.writeFile(path: String, content: String)`.

- [ ] **Step 1: Падающий тест**

Добавить в `VaultRepositoryTest`:
```kotlin
    @Test
    fun writes_new_and_overwrites_existing() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        val repo = VaultRepository(fs, root)
        val path = (root / "note.md").toString()

        repo.writeFile(path, "# Hi")
        assertEquals("# Hi", repo.readFile(path))

        repo.writeFile(path, "# Changed")
        assertEquals("# Changed", repo.readFile(path))
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: FAIL — `writeFile` не определён.

- [ ] **Step 3: Реализация (добавить в VaultRepository)**

```kotlin
    fun writeFile(path: String, content: String) {
        fs.write(path.toPath()) { writeUtf8(content) }
    }
```
(`import okio.Path.Companion.toPath` уже есть в файле.)

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/vault/VaultRepository.kt composeApp/src/commonTest/kotlin/app/obsidianmd/vault/VaultRepositoryTest.kt
git commit -m "feat: VaultRepository.writeFile + test"
```

---

### Task 2: VaultViewModel.saveFile

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt` (добавить)

**Interfaces:**
- Consumes: `VaultRepository.writeFile` (Task 1). Produces: `fun VaultViewModel.saveFile(path: String, content: String)`.

- [ ] **Step 1: Падающий тест**

Добавить в `VaultViewModelTest`:
```kotlin
    @Test
    fun save_file_persists_and_updates_content() = runTest {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "a.md") { writeUtf8("old") }
        val repo = VaultRepository(fs, root)
        val model = VaultViewModel(repo, this, StandardTestDispatcher(testScheduler))
        val path = (root / "a.md").toString()

        model.saveFile(path, "новый текст")
        advanceUntilIdle()

        assertEquals("новый текст", repo.readFile(path))
        assertEquals("новый текст", model.state.value.content)
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: FAIL — `saveFile` не определён.

- [ ] **Step 3: Реализация (добавить в VaultViewModel)**

```kotlin
    fun saveFile(path: String, content: String) {
        scope.launch {
            withContext(io) { repo.writeFile(path, content) }
            _state.value = _state.value.copy(content = content)
        }
    }
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt
git commit -m "feat: VaultViewModel.saveFile (persist + update content)"
```

---

### Task 3: MarkdownScreen режим правки + проводка в App

UI — ручная приёмка (юнит-тестов Compose нет).

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/MarkdownScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`

**Interfaces:**
- Consumes: `VaultViewModel.saveFile`, `VaultState.selected`.
- Produces: `MarkdownScreen(content, onBack, onSave)`.

- [ ] **Step 1: MarkdownScreen — режим просмотр/редактор**

Заменить содержимое `MarkdownScreen.kt`:
```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MarkdownScreen(content: String, onBack: () -> Unit, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(content) { mutableStateOf(content) }
    Column(Modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = onBack) { Text("← Назад") }
            if (!editing) {
                TextButton(onClick = { draft = content; editing = true }) { Text("Редактировать") }
            }
        }
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp)) {
                Button(onClick = { onSave(draft); editing = false }) { Text("Сохранить") }
                TextButton(onClick = { editing = false }) { Text("Отмена") }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Markdown(content)
            }
        }
    }
}
```

- [ ] **Step 2: App — передать onSave**

В `App.kt` заменить вызов `MarkdownScreen`:
```kotlin
                else -> MarkdownScreen(
                    content = state.content,
                    onBack = vm::back,
                    onSave = { text -> state.selected?.let { vm.saveFile(it.path, text) } },
                )
```

- [ ] **Step 3: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Ручная приёмка**

Прогнать кейсы из спеки: правка+сохранение; отмена; правка уходит в синк.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain
git commit -m "feat: edit mode in MarkdownScreen (view <-> editor) + wiring"
```

---

## Self-review

**Покрытие спеки:**
- `VaultRepository.writeFile` → Task 1.
- `VaultViewModel.saveFile` → Task 2.
- `MarkdownScreen` режим правки + проводка → Task 3.
- Приёмочные кейсы → в спеке; Task 3 Step 4.
- Аналитика → неприменима.

**Placeholder-скан:** реальный код/команды; заглушек нет.

**Согласованность типов:** `VaultRepository.writeFile(path, content)`;
`VaultViewModel.saveFile(path, content)`; `MarkdownScreen(content, onBack, onSave)` c
`onSave: (String) -> Unit`; в App `onSave` использует `state.selected.path`. `readFile`/`content`
— существующие, имена совпадают.
