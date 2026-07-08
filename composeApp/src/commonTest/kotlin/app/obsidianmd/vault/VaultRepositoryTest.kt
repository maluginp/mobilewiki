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
}
