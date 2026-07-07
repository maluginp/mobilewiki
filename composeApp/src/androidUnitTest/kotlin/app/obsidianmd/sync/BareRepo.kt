package app.obsidianmd.sync

import org.eclipse.jgit.api.Git
import java.io.File
import java.nio.file.Files

/** Создаёт bare-репо с одной веткой `main` и стартовым коммитом (файл welcome.md). */
fun createSeededBareRepo(): File {
    val work = Files.createTempDirectory("seed").toFile()
    Git.init().setDirectory(work).setInitialBranch("main").call().use { git ->
        File(work, "welcome.md").writeText("# Welcome\n")
        git.add().addFilepattern(".").call()
        git.commit().setMessage("init").setAuthor("seed", "seed@localhost")
            .setCommitter("seed", "seed@localhost").call()
    }
    val bare = Files.createTempDirectory("bare").toFile()
    Git.cloneRepository().setURI(work.toURI().toString()).setDirectory(bare)
        .setBare(true).call().close()
    return bare
}

fun newLocalDir(): File = Files.createTempDirectory("local").toFile()

/** Читает содержимое файла из свежего полного clone bare-репо (для проверок «что на сервере»). */
fun readFromServer(bare: File, path: String): String {
    val tmp = Files.createTempDirectory("check").toFile()
    Git.cloneRepository().setURI(bare.toURI().toString()).setDirectory(tmp).call().close()
    return File(tmp, path).readText()
}

/** Делает независимый клон bare, правит файл и пушит — эмулирует изменение с другого устройства. */
fun pushRemoteChange(bare: File, path: String, content: String) {
    val tmp = Files.createTempDirectory("remoteedit").toFile()
    Git.cloneRepository().setURI(bare.toURI().toString()).setDirectory(tmp).call().use { git ->
        File(tmp, path).writeText(content)
        git.add().addFilepattern(".").call()
        git.commit().setMessage("remote").setAuthor("dev2", "dev2@localhost")
            .setCommitter("dev2", "dev2@localhost").call()
        git.push().call()
    }
}
