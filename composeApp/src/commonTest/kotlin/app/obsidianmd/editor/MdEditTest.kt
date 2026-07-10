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

    @Test fun line_prefix_first_line() {
        assertEquals(EditState("# hello", 2, 2), MdEdit.linePrefix(EditState("hello", 0, 0), "# "))
    }

    @Test fun line_prefix_second_line() {
        assertEquals(EditState("a\n- bc", 4, 4), MdEdit.linePrefix(EditState("a\nbc", 2, 2), "- "))
    }

    @Test fun line_prefix_checkbox() {
        assertEquals(EditState("- [ ] x", 6, 6), MdEdit.linePrefix(EditState("x", 0, 0), "- [ ] "))
    }

    @Test fun link_with_selection_caret_in_url() {
        assertEquals(EditState("[term]()", 7, 7), MdEdit.link(EditState("term", 0, 4)))
    }

    @Test fun link_empty_caret_in_label() {
        assertEquals(EditState("[]()", 1, 1), MdEdit.link(EditState("", 0, 0)))
    }

    @Test fun wikilink_insert_at_caret() {
        assertEquals(EditState("[[note]]", 8, 8), MdEdit.wikiLink(EditState("", 0, 0), "note"))
    }

    @Test fun wikilink_replaces_selection() {
        assertEquals(EditState("[[x]]", 5, 5), MdEdit.wikiLink(EditState("abc", 0, 3), "x"))
    }
}
