package app.obsidianmd.editor

/** Мини-модель поля ввода (текст + выделение) без зависимости от Compose — тестируется в commonTest. */
data class EditState(val text: String, val selStart: Int, val selEnd: Int)

object MdEdit {
    /** Обернуть выделение маркером (bold="**", italic="*"); нет выделения → маркеры с кареткой между. */
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

    /** Добавить префикс в начало строки, где стоит каретка ("# ", "- ", "- [ ] "). */
    fun linePrefix(s: EditState, prefix: String): EditState {
        val lineStart = s.text.lastIndexOf('\n', (s.selStart - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val text = s.text.substring(0, lineStart) + prefix + s.text.substring(lineStart)
        return EditState(text, s.selStart + prefix.length, s.selEnd + prefix.length)
    }

    /** Ссылка: есть выделение → "[выд]()" каретка в "()"; нет → "[]()" каретка в "[]". */
    fun link(s: EditState): EditState {
        val pre = s.text.substring(0, s.selStart)
        val sel = s.text.substring(s.selStart, s.selEnd)
        val post = s.text.substring(s.selEnd)
        return if (sel.isEmpty()) {
            EditState("$pre[]()$post", s.selStart + 1, s.selStart + 1)
        } else {
            val caret = s.selStart + 1 + sel.length + 2 // после "]("
            EditState("$pre[$sel]()$post", caret, caret)
        }
    }
}
