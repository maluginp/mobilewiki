package app.obsidianmd.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiLinksTest {
    private val files = listOf(
        VaultFile("README.md", "/v/README.md", "README.md"),
        VaultFile("concepts/ai.md", "/v/concepts/ai.md", "ai.md"),
        VaultFile("articles/ai.md", "/v/articles/ai.md", "ai.md"),
        VaultFile("root.md", "/v/root.md", "root.md"),
        VaultFile("docs/specs/x.md", "/v/docs/specs/x.md", "x.md"),
        VaultFile("other/x.md", "/v/other/x.md", "x.md"),
        VaultFile("raw/pic.png", "/v/raw/pic.png", "pic.png"),
    )

    @Test
    fun bare_name_appends_md_and_matches_by_name() {
        // ai → одна из ai.md; при равной глубине тай-брейк по relPath → articles/ai.md
        assertEquals("/v/articles/ai.md", resolveWikiLink("ai", files)?.absPath)
    }

    @Test
    fun root_file_wins_over_nested_for_bare_name() {
        val f = files + VaultFile("ai.md", "/v/ai.md", "ai.md")
        assertEquals("/v/ai.md", resolveWikiLink("ai", f)?.absPath)
    }

    @Test
    fun path_form_matches_exact_relpath() {
        assertEquals("/v/docs/specs/x.md", resolveWikiLink("docs/specs/x", files)?.absPath)
    }

    @Test
    fun explicit_extension_matches() {
        assertEquals("/v/README.md", resolveWikiLink("README.md", files)?.absPath)
    }

    @Test
    fun not_found_returns_null() {
        assertNull(resolveWikiLink("nope", files))
        assertNull(resolveWikiLink("no/where", files))
    }

    @Test
    fun link_rewritten_to_markdown_link_with_target() {
        val note = renderNote("see [[README.md]] here", files)
        assertEquals(1, note.blocks.size)
        val text = (note.blocks[0] as MdBlock.Text).markdown
        assertEquals("see [README.md](wikilink:0) here", text)
        assertEquals(listOf("/v/README.md"), note.linkTargets)
    }

    @Test
    fun unresolved_link_stays_literal() {
        val note = renderNote("look [[nope]] ok", files)
        assertEquals("look [[nope]] ok", (note.blocks[0] as MdBlock.Text).markdown)
        assertTrue(note.linkTargets.isEmpty())
    }

    @Test
    fun image_embed_on_own_line_becomes_image_block() {
        val note = renderNote("intro\n![[pic.png]]\noutro", files)
        assertEquals(3, note.blocks.size)
        assertEquals("intro", (note.blocks[0] as MdBlock.Text).markdown)
        assertEquals("/v/raw/pic.png", (note.blocks[1] as MdBlock.Image).absPath)
        assertEquals("outro", (note.blocks[2] as MdBlock.Text).markdown)
    }

    @Test
    fun unresolved_image_embed_stays_literal_text() {
        val note = renderNote("![[missing.png]]", files)
        assertEquals(1, note.blocks.size)
        assertEquals("![[missing.png]]", (note.blocks[0] as MdBlock.Text).markdown)
    }
}
