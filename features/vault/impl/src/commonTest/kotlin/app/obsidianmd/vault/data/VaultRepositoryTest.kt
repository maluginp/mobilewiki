package app.obsidianmd.vault.data

import app.obsidianmd.vault.VaultRepository

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
        return OkioVaultRepository(fs, root)
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
        val repo = OkioVaultRepository(fs, root)

        val entries = repo.listEntries(root.toString())
        assertEquals(listOf("alpha", "Zeta", "a.md", "b.md"), entries.map { it.name })
        assertEquals(listOf(true, true, false, false), entries.map { it.isFolder })
    }

    @Test
    fun list_entries_of_subfolder_and_parent_navigation() {
        val fs = FakeFileSystem()
        fs.createDirectories(root / "Daily")
        fs.write(root / "Daily" / "mon.md") { writeUtf8("x") }
        val repo = OkioVaultRepository(fs, root)

        val sub = (root / "Daily").toString()
        assertEquals(listOf("mon.md"), repo.listEntries(sub).map { it.name })
        assertEquals(root.toString(), repo.parentOf(sub))
        assertTrue(repo.isRoot(repo.parentOf(sub)))
        assertEquals(root.toString(), repo.parentOf(root.toString())) // clamped at root
    }

    @Test
    fun all_files_walks_recursively_skipping_dot_dirs() {
        val fs = FakeFileSystem()
        fs.createDirectories(root / "sub")
        fs.createDirectories(root / ".git")
        fs.write(root / "a.md") { writeUtf8("x") }
        fs.write(root / "sub" / "b.md") { writeUtf8("x") }
        fs.write(root / "sub" / "pic.png") { writeUtf8("x") }
        fs.write(root / ".git" / "cfg") { writeUtf8("x") }
        val repo = OkioVaultRepository(fs, root)

        val rels = repo.allFiles().map { it.relPath }
        assertEquals(listOf("a.md", "sub/b.md", "sub/pic.png"), rels)
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
        val repo = OkioVaultRepository(fs, root)
        assertEquals("# Hello", repo.readFile((root / "a.md").toString()))
    }

    @Test
    fun writes_new_and_overwrites_existing() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        val repo = OkioVaultRepository(fs, root)
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
        val repo = OkioVaultRepository(fs, root)
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
        val repo = OkioVaultRepository(fs, root)

        assertEquals(listOf("notes.md"), repo.search("проект").map { it.name })
        assertEquals(listOf("todo.md"), repo.search("todo").map { it.name })
        assertEquals(listOf("todo.md"), repo.search("TODO").map { it.name })
        assertEquals(emptyList(), repo.search(""))
    }

    @Test
    fun search_walks_subfolders() {
        val fs = FakeFileSystem()
        fs.createDirectories(root / "sub" / "deep")
        fs.createDirectories(root / ".git")
        fs.write(root / "a.md") { writeUtf8("nothing") }
        fs.write(root / "sub" / "match-name.md") { writeUtf8("x") }
        fs.write(root / "sub" / "deep" / "bybody.md") { writeUtf8("нужное слово") }
        fs.write(root / ".git" / "config.md") { writeUtf8("нужное слово") } // dot-каталог пропускается
        val repo = OkioVaultRepository(fs, root)

        assertEquals(listOf("match-name.md"), repo.search("match-name").map { it.name })
        assertEquals(listOf("bybody.md"), repo.search("нужное").map { it.name })
    }

    @Test
    fun documents_lists_md_with_title_and_wikilink_target() {
        val fs = FakeFileSystem()
        fs.createDirectories(root / "sub")
        fs.write(root / "a.md") { writeUtf8("# Alpha\nbody") }
        fs.write(root / "sub" / "b.md") { writeUtf8("no heading here") }
        fs.write(root / "pic.png") { writeUtf8("x") } // не .md — пропускается
        val repo = OkioVaultRepository(fs, root)

        val docs = repo.documents()
        assertEquals(listOf("a.md", "sub/b.md"), docs.map { it.relPath })
        assertEquals(listOf("Alpha", "b"), docs.map { it.title }) // без заголовка → имя без .md
        assertEquals(listOf("a", "b"), docs.map { it.target })    // цель wikilink — базовое имя без .md
    }

    @Test
    fun lists_and_reads_skills_from_claude_and_codex() {
        val fs = FakeFileSystem()
        fs.createDirectories(root / ".claude" / "skills" / "summarize")
        fs.write(root / ".claude" / "skills" / "summarize" / "SKILL.md") {
            writeUtf8("---\nname: summarize\ndescription: Summarize notes\n---\nBody: do the thing.")
        }
        // frontmatter без name → имя берётся из папки; из .codex тоже подхватывается
        fs.createDirectories(root / ".codex" / "skills" / "review")
        fs.write(root / ".codex" / "skills" / "review" / "SKILL.md") {
            writeUtf8("---\ndescription: Review code\n---\nReview body.")
        }
        val repo = OkioVaultRepository(fs, root)

        val skills = repo.listSkills()
        assertEquals(listOf("review", "summarize"), skills.map { it.name })
        assertEquals(listOf("Review code", "Summarize notes"), skills.map { it.description })
        assertEquals("---\nname: summarize\ndescription: Summarize notes\n---\nBody: do the thing.", repo.readSkill("summarize"))
        assertEquals(null, repo.readSkill("missing"))
    }

    @Test
    fun create_folder_makes_dir_visible_in_entries_and_is_idempotent() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        val repo = OkioVaultRepository(fs, root)

        repo.createFolder((root / "Ideas").toString())
        repo.createFolder((root / "Ideas").toString()) // повторно — не падает

        val entries = repo.listEntries(root.toString())
        assertEquals(listOf("Ideas"), entries.map { it.name })
        assertTrue(entries.single().isFolder)
    }

    @Test
    fun create_folder_creates_parent_dirs() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        val repo = OkioVaultRepository(fs, root)

        repo.createFolder((root / "a" / "b").toString())
        assertEquals(listOf("b"), repo.listEntries((root / "a").toString()).map { it.name })
    }
}
