# UX редактирования заметок — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Панель форматирования, безопасное сохранение и чистый полноэкранный редактор для правки заметок.

**Architecture:** Чистая логика форматирования markdown (`MdEdit` над `EditState` — текст+выделение) выносится в commonMain и покрывается TDD. Редактор в `MarkdownScreen` переходит на `TextFieldValue` (нужно выделение), получает панель `EditorToolbar` и чистый `BasicTextField`. Безопасное сохранение (dirty-индикатор + диалог при выходе) добавляется в `App.kt`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (material3, material-icons-extended), kotlin.test.

## Global Constraints

- Чистая логика — в `commonMain`, пакет `app.obsidianmd.editor`; тесты — `commonTest`.
- Тексты UI — английские, через `Res.string.*` (стиль проекта).
- Команда юнит-тестов: `./gradlew :composeApp:testDebugUnitTest`.
- material-icons-extended уже подключён — иконки можно использовать напрямую.

---

### Task 1: MdEdit.wrapInline

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/editor/MdEdit.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt`

**Interfaces:**
- Produces: `data class EditState(text: String, selStart: Int, selEnd: Int)`;
  `object MdEdit { fun wrapInline(s: EditState, marker: String): EditState }`.

- [ ] **Step 1: Failing test**

```kotlin
package app.obsidianmd.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class MdEditTest {
    @Test fun wrap_with_selection() {
        assertEquals(EditState("**abc**", 2, 5), MdEdit.wrapInline(EditState("abc", 0, 3), "**"))
    }
    @Test fun wrap_empty_puts_caret_between() {
        assertEquals(EditState("**", 1, 1), MdEdit.wrapInline(EditState("", 0, 0), "*"))
    }
    @Test fun wrap_caret_in_text() {
        assertEquals(EditState("a**b", 2, 2), MdEdit.wrapInline(EditState("ab", 1, 1), "*"))
    }
}
```

- [ ] **Step 2: Run — FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.editor.MdEditTest"`
Expected: FAIL — `EditState`/`MdEdit` не существуют.

- [ ] **Step 3: Minimal implementation**

```kotlin
package app.obsidianmd.editor

data class EditState(val text: String, val selStart: Int, val selEnd: Int)

object MdEdit {
    fun wrapInline(s: EditState, marker: String): EditState {
        val pre = s.text.substring(0, s.selStart)
        val sel = s.text.substring(s.selStart, s.selEnd)
        val post = s.text.substring(s.selEnd)
        val text = "$pre$marker$sel$marker$post"
        return if (s.selStart == s.selEnd) {
            val caret = s.selStart + marker.length
            EditState(text, caret, caret)
        } else {
            EditState(text, s.selStart + marker.length, s.selEnd + marker.length)
        }
    }
}
```

- [ ] **Step 4: Run — PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.editor.MdEditTest"`
Expected: PASS (3).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/editor/MdEdit.kt composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt
git commit -m "feat: MdEdit.wrapInline — обёртка выделения markdown-маркером"
```

---

### Task 2: MdEdit.linePrefix

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/editor/MdEdit.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt`

**Interfaces:**
- Produces: `fun MdEdit.linePrefix(s: EditState, prefix: String): EditState`.

- [ ] **Step 1: Failing test (добавить в MdEditTest)**

```kotlin
    @Test fun line_prefix_first_line() {
        assertEquals(EditState("# hello", 2, 2), MdEdit.linePrefix(EditState("hello", 0, 0), "# "))
    }
    @Test fun line_prefix_second_line() {
        assertEquals(EditState("a\n- bc", 4, 4), MdEdit.linePrefix(EditState("a\nbc", 2, 2), "- "))
    }
    @Test fun line_prefix_checkbox() {
        assertEquals(EditState("- [ ] x", 6, 6), MdEdit.linePrefix(EditState("x", 0, 0), "- [ ] "))
    }
```

- [ ] **Step 2: Run — FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.editor.MdEditTest"`
Expected: FAIL — `linePrefix` не существует.

- [ ] **Step 3: Minimal implementation (в object MdEdit)**

```kotlin
    fun linePrefix(s: EditState, prefix: String): EditState {
        val lineStart = s.text.lastIndexOf('\n', (s.selStart - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val text = s.text.substring(0, lineStart) + prefix + s.text.substring(lineStart)
        return EditState(text, s.selStart + prefix.length, s.selEnd + prefix.length)
    }
```

> Примечание: при `selStart == 0` `lastIndexOf('\n', -1)` вернёт -1 → lineStart=0 (первая строка).

- [ ] **Step 4: Run — PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.editor.MdEditTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/editor/MdEdit.kt composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt
git commit -m "feat: MdEdit.linePrefix — префикс строки (заголовок/список/чекбокс)"
```

---

### Task 3: MdEdit.link

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/editor/MdEdit.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt`

**Interfaces:**
- Produces: `fun MdEdit.link(s: EditState): EditState`.

- [ ] **Step 1: Failing test**

```kotlin
    @Test fun link_with_selection_caret_in_url() {
        // "[term]()" — каретка между "()" на позиции 7
        assertEquals(EditState("[term]()", 7, 7), MdEdit.link(EditState("term", 0, 4)))
    }
    @Test fun link_empty_caret_in_label() {
        // "[]()" — каретка между "[]" на позиции 1
        assertEquals(EditState("[]()", 1, 1), MdEdit.link(EditState("", 0, 0)))
    }
```

- [ ] **Step 2: Run — FAIL**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.editor.MdEditTest"`
Expected: FAIL — `link` не существует.

- [ ] **Step 3: Minimal implementation**

```kotlin
    fun link(s: EditState): EditState {
        val pre = s.text.substring(0, s.selStart)
        val sel = s.text.substring(s.selStart, s.selEnd)
        val post = s.text.substring(s.selEnd)
        return if (sel.isEmpty()) {
            EditState("$pre[]()$post", s.selStart + 1, s.selStart + 1) // каретка в "[]"
        } else {
            val text = "$pre[$sel]()$post"
            val caret = s.selStart + 1 + sel.length + 2 // после "]("
            EditState(text, caret, caret)
        }
    }
```

- [ ] **Step 4: Run — PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.editor.MdEditTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/editor/MdEdit.kt composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt
git commit -m "feat: MdEdit.link — вставка markdown-ссылки"
```

---

### Task 4: EditorToolbar + чистый редактор в MarkdownScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/EditorToolbar.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/MarkdownScreen.kt`

**Interfaces:**
- Consumes: `EditState`, `MdEdit` (Tasks 1–3).
- Produces: `EditorToolbar(onTransform: (EditState) -> EditState)`; режим правки на
  `TextFieldValue` + `BasicTextField`.

Проверяется вручную (приёмочные кейсы), юнит-тестов нет.

- [ ] **Step 1: EditorToolbar**

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.obsidianmd.editor.EditState
import app.obsidianmd.editor.MdEdit

@Composable
fun EditorToolbar(onTransform: ((EditState) -> EditState) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        IconButton(onClick = { onTransform { MdEdit.wrapInline(it, "**") } }) {
            Icon(Icons.Filled.FormatBold, contentDescription = "Bold")
        }
        IconButton(onClick = { onTransform { MdEdit.wrapInline(it, "*") } }) {
            Icon(Icons.Filled.FormatItalic, contentDescription = "Italic")
        }
        IconButton(onClick = { onTransform { MdEdit.linePrefix(it, "# ") } }) {
            Icon(Icons.Filled.Title, contentDescription = "Heading")
        }
        IconButton(onClick = { onTransform { MdEdit.linePrefix(it, "- ") } }) {
            Icon(Icons.Filled.FormatListBulleted, contentDescription = "List")
        }
        IconButton(onClick = { onTransform { MdEdit.linePrefix(it, "- [ ] ") } }) {
            Icon(Icons.Filled.CheckBox, contentDescription = "Checkbox")
        }
        IconButton(onClick = { onTransform { MdEdit.link(it) } }) {
            Icon(Icons.Filled.Link, contentDescription = "Link")
        }
    }
}
```

- [ ] **Step 2: Правка MarkdownScreen — TextFieldValue + BasicTextField + тулбар**

Заменить `if (editing) { OutlinedTextField(...) }` на редактор с локальным `TextFieldValue`,
конвертацией в/из `EditState` и панелью снизу. Ключевой фрагмент:

```kotlin
// импорты: androidx.compose.foundation.text.BasicTextField,
// androidx.compose.ui.text.input.TextFieldValue, androidx.compose.ui.text.TextRange,
// androidx.compose.foundation.layout.Column/imePadding, MaterialTheme, app.obsidianmd.editor.EditState
if (editing) {
    var tfv by remember { mutableStateOf(TextFieldValue(draft)) }
    Column(Modifier.fillMaxSize()) {
        BasicTextField(
            value = tfv,
            onValueChange = { tfv = it; onDraftChange(it.text) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp),
        )
        EditorToolbar(
            onTransform = { transform ->
                val s = EditState(tfv.text, tfv.selection.start, tfv.selection.end)
                val r = transform(s)
                tfv = TextFieldValue(r.text, TextRange(r.selStart, r.selEnd))
                onDraftChange(r.text)
            },
            modifier = Modifier.imePadding(),
        )
    }
}
```

> `remember { TextFieldValue(draft) }` НЕ ключевать на `draft` — иначе поле сбрасывается на
> каждый ввод. Инициализируется один раз при входе в режим правки.

- [ ] **Step 3: Собрать**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Если `imePadding` недоступен — убрать (клавиатура и так сдвинет).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/EditorToolbar.kt composeApp/src/commonMain/kotlin/app/obsidianmd/ui/MarkdownScreen.kt
git commit -m "feat: панель форматирования и чистый BasicTextField-редактор"
```

---

### Task 5: Безопасное сохранение (диалог + dirty-индикатор)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: существующие `editing`, `draft`, `state.content`, `vm.saveFile`.

- [ ] **Step 1: Строки**

Добавить в strings.xml:
```xml
<string name="unsaved_title">Unsaved changes</string>
<string name="unsaved_message">You have unsaved changes. Save before leaving?</string>
<string name="action_discard">Don\'t save</string>
```
(«Сохранить»/«Отмена» — переиспользовать `action_save`/`action_cancel`.)

- [ ] **Step 2: Логика в App.kt**

```kotlin
// dirty-флаг:
val dirty = editing && draft != state.content
var showUnsaved by remember { mutableStateOf(false) }

// «Назад» во время правки:
editing -> ({ if (dirty) showUnsaved = true else editing = false })

// иконка ✓ активна только при dirty:
IconButton(onClick = { state.selected?.let { vm.saveFile(it.path, draft) }; editing = false }, enabled = dirty) { ... }

// диалог (рядом с ConflictDialog):
if (showUnsaved) {
    AlertDialog(
        onDismissRequest = { showUnsaved = false },
        title = { Text(stringResource(Res.string.unsaved_title)) },
        text = { Text(stringResource(Res.string.unsaved_message)) },
        confirmButton = {
            TextButton(onClick = {
                state.selected?.let { vm.saveFile(it.path, draft) }
                showUnsaved = false; editing = false
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = { showUnsaved = false; editing = false }) {
                Text(stringResource(Res.string.action_discard))
            }
            // «Отмена» — через onDismissRequest (тап вне диалога); либо третья кнопка при желании.
        },
    )
}
```

> `AlertDialog` в material3 поддерживает только confirm/dismiss кнопки. «Отмена» = закрытие
> диалога (onDismissRequest / системный back), возвращает в редактор без потерь. Если нужны
> явные три кнопки — сверстать кастомный контент, но для слайса достаточно двух + dismiss.

- [ ] **Step 3: Собрать + прогнать тесты**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, тесты зелёные.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat: защита от потери правок (диалог) + индикатор несохранённого"
```

---

## Аналитика

Проект локальный, аналитической системы нет (см. предыдущие планы). Шаг аналитики не
добавляется; успех проверяется приёмочными тест-кейсами спеки.

## Самопроверка (выполнено)

1. **Покрытие спеки:** wrapInline → Task 1; linePrefix → Task 2; link → Task 3; панель +
   чистый редактор → Task 4; безопасное сохранение (диалог + dirty) → Task 5.
2. **Плейсхолдеры:** реальный код и команды во всех шагах.
3. **Согласованность типов:** `EditState(text, selStart, selEnd)`, `MdEdit.wrapInline/linePrefix/link`,
   `EditorToolbar(onTransform)` — имена совпадают между Task 1–5.
