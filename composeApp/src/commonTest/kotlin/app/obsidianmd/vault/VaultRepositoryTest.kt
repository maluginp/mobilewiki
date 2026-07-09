package app.obsidianmd.vault

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VaultRepositoryTest {
    private val root = "/vault".toPath()

    private fun repoWith(vararg files: String): VaultRepository {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        files.forEach { fs.write(root / it) { writeUtf8("x") } }
        return VaultRepository(fs, root)
    }

    @Test
    fun lists_only_md_files_sorted_by_name() {
        val repo = repoWith("b.md", "a.md", "note.txt", "image.png")
        val names = repo.listMarkdownFiles().map { it.name }
        assertEquals(listOf("a.md", "b.md"), names)
    }

    @Test
    fun list_entries_folders_first_then_md_files_sorted() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.createDirectories(root / "Zeta")
        fs.createDirectories(root / "alpha")
        fs.write(root / "b.md") { writeUtf8("x") }
        fs.write(root / "a.md") { writeUtf8("x") }
        fs.write(root / "note.txt") { writeUtf8("x") }
        fs.createDirectories(root / ".git")
        val repo = VaultRepository(fs, root)

        val entries = repo.listEntries(root.toString())
        assertEquals(listOf("alpha", "Zeta", "a.md", "b.md"), entries.map { it.name })
        assertEquals(listOf(true, true, false, false), entries.map { it.isFolder })
    }

    @Test
    fun list_entries_of_subfolder_and_parent_navigation() {
        val fs = FakeFileSystem()
        fs.createDirectories(root / "Daily")
        fs.write(root / "Daily" / "mon.md") { writeUtf8("x") }
        val repo = VaultRepository(fs, root)

        val sub = (root / "Daily").toString()
        assertEquals(listOf("mon.md"), repo.listEntries(sub).map { it.name })
        assertEquals(root.toString(), repo.parentOf(sub))
        assertTrue(repo.isRoot(repo.parentOf(sub)))
        assertEquals(root.toString(), repo.parentOf(root.toString())) // clamped at root
    }

    @Test
    fun empty_vault_returns_empty_list() {
        val repo = repoWith()
        assertEquals(emptyList(), repo.listMarkdownFiles())
    }

    @Test
    fun reads_file_content() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "a.md") { writeUtf8("# Hello") }
        val repo = VaultRepository(fs, root)
        assertEquals("# Hello", repo.readFile((root / "a.md").toString()))
    }

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

    @Test
    fun path_for_resolves_under_root() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        val repo = VaultRepository(fs, root)
        repo.writeFile(repo.pathFor("x.md"), "hi")
        assertEquals("hi", repo.readFile(repo.pathFor("x.md")))
        assertEquals(listOf("x.md"), repo.listMarkdownFiles().map { it.name })
    }

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
}
