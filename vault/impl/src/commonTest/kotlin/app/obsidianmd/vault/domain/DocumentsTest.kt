package app.obsidianmd.vault.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentsTest {
    @Test fun first_heading_returns_text() {
        assertEquals("Title", firstHeading("# Title\nbody"))
    }

    @Test fun first_heading_any_level_first_match() {
        assertEquals("Sub", firstHeading("## Sub\n# Main"))
    }

    @Test fun first_heading_skips_frontmatter() {
        assertEquals("Real", firstHeading("---\nkey: v\n---\n# Real\ntext"))
    }

    @Test fun first_heading_none_returns_null() {
        assertNull(firstHeading("just text\nmore"))
    }
}
