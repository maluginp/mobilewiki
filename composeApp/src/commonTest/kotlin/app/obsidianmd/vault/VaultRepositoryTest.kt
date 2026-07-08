package app.obsidianmd.vault

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

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
