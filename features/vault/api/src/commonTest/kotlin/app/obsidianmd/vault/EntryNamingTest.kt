package app.obsidianmd.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntryNamingTest {
    @Test fun note_file_name_adds_md_and_trims() {
        assertEquals("todo.md", noteFileName("  todo "))
        assertEquals("a.md", noteFileName("a.md"))
        assertEquals("a.MD", noteFileName("a.MD")) // расширение уже есть (без регистра), не удваиваем
    }

    @Test fun name_error_reports_blank_slash_exists() {
        assertEquals(NameError.Blank, entryNameError("", listOf()))
        assertEquals(NameError.Blank, entryNameError("   ", listOf()))
        assertEquals(NameError.Slash, entryNameError("a/b", listOf()))
        assertEquals(NameError.Exists, entryNameError("Note.md", listOf("note.md"))) // без регистра
        assertNull(entryNameError("fresh.md", listOf("note.md")))
    }
}
