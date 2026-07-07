# Git Sync Engine (JGit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Движок синхронизации ваулта с git-remote (shallow clone → commit/fetch/merge/push, «сервер выигрывает»), auth-агностичный, тестируемый локально.

**Architecture:** Интерфейс `GitSync` в `commonMain`; реализация `JGitSync` на JGit в `androidMain`, блокирующие вызовы на `Dispatchers.IO`. Тесты — JVM (`androidUnitTest`) против локального bare-репо (`file://`), без сети и без GitHub.

**Tech Stack:** JGit `org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r` (6.x — Java 11), kotlinx-coroutines, kotlin-test (JUnit4 на Android).

## Global Constraints

- Пакет: `app.obsidianmd.sync`.
- Стратегия хранения: shallow (`setDepth(1)`) — из решения docs/specs/2026-07-06-git-storage-strategy.md.
- Конфликт: «сервер выигрывает» (JGit recursive + `ContentMergeStrategy.THEIRS`).
- JGit — только в `androidMain` (JVM-либа); `commonMain` знает лишь интерфейс.
- Сообщение коммита — константа `"obsidian-md sync"` (без timestamp; тесты не зависят от текста).
- Аналитика: не применима — внутренний движок без user-visible поведения (UI будет в след. слайсах).
- Токен `null` → без credentials provider (локальные `file://` тесты); иначе `UsernamePasswordCredentialsProvider(token, "")`.

---

### Task 1: JGit-зависимость + интерфейс + shallow clone

**Files:**
- Modify: `gradle/libs.versions.toml` (jgit версия + библиотека)
- Modify: `composeApp/build.gradle.kts` (jgit в androidMain, тест-деп в androidUnitTest)
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/sync/GitSync.kt`
- Create: `composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitSync.kt`
- Create (test helper): `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/BareRepo.kt`
- Test: `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitSyncTest.kt`

**Interfaces:**
- Produces: `GitSync`, `SyncConfig`, `SyncResult` (commonMain); `JGitSync : GitSync` (androidMain).

- [ ] **Step 1: Зависимость JGit**

В `gradle/libs.versions.toml` в `[versions]` добавить:
```toml
jgit = "6.10.0.202406032230-r"
```
В `[libraries]`:
```toml
jgit = { module = "org.eclipse.jgit:org.eclipse.jgit", version.ref = "jgit" }
```
В `composeApp/build.gradle.kts` внутри `kotlin { sourceSets { ... } }` добавить:
```kotlin
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.jgit)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
```
(строку `implementation(libs.jgit)` — добавить к существующему `androidMain.dependencies`; блок `androidUnitTest.dependencies` — новый.)

- [ ] **Step 2: Интерфейс и типы (commonMain)**

`composeApp/src/commonMain/kotlin/app/obsidianmd/sync/GitSync.kt`:
```kotlin
package app.obsidianmd.sync

data class SyncConfig(
    val remoteUrl: String,
    val localPath: String,
    val branch: String = "main",
    val token: String? = null,
    val authorName: String = "obsidian-md",
    val authorEmail: String = "obsidian-md@localhost",
)

sealed interface SyncResult {
    data object Cloned : SyncResult
    data object UpToDate : SyncResult
    data class Synced(val pushed: Boolean, val conflictsResolved: Int) : SyncResult
    data class Failed(val reason: String) : SyncResult
}

interface GitSync {
    suspend fun sync(config: SyncConfig): SyncResult
}
```

- [ ] **Step 3: Тест-хелпер bare-репо**

`composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/BareRepo.kt`:
```kotlin
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
```

- [ ] **Step 4: Падающий тест на clone**

`composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitSyncTest.kt`:
```kotlin
package app.obsidianmd.sync

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JGitSyncTest {

    private fun config(bare: File, local: File) = SyncConfig(
        remoteUrl = bare.toURI().toString(),
        localPath = local.absolutePath,
        branch = "main",
        token = null,
    )

    @Test
    fun first_sync_shallow_clones() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val result = JGitSync().sync(config(bare, local))
        assertEquals(SyncResult.Cloned, result)
        assertTrue(File(local, "welcome.md").exists())
        assertTrue(File(local, ".git/shallow").exists(), "должен быть shallow-клон")
    }
}
```

- [ ] **Step 5: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: FAIL — `JGitSync` не существует (ошибка компиляции).

- [ ] **Step 6: Минимальная реализация clone**

`composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitSync.kt`:
```kotlin
package app.obsidianmd.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class JGitSync(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : GitSync {

    override suspend fun sync(config: SyncConfig): SyncResult = withContext(io) {
        try {
            val dir = File(config.localPath)
            val creds = config.token?.let { UsernamePasswordCredentialsProvider(it, "") }
            if (!File(dir, ".git").exists()) {
                Git.cloneRepository()
                    .setURI(config.remoteUrl)
                    .setDirectory(dir)
                    .setBranch(config.branch)
                    .setBranchesToClone(listOf("refs/heads/${config.branch}"))
                    .setCloneAllBranches(false)
                    .setDepth(1)
                    .setCredentialsProvider(creds)
                    .call()
                    .close()
                return@withContext SyncResult.Cloned
            }
            SyncResult.UpToDate
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e.toString())
        }
    }
}
```

- [ ] **Step 7: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/commonMain/kotlin/app/obsidianmd/sync composeApp/src/androidMain/kotlin/app/obsidianmd/sync composeApp/src/androidUnitTest
git commit -m "feat: GitSync interface + JGit shallow clone + tests"
```

---

### Task 2: Push локальной правки

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitSync.kt`
- Test: `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitSyncTest.kt` (добавить)

**Interfaces:**
- Consumes: `JGitSync.sync` (Task 1). Produces: ветка add/commit/push внутри `sync`.

- [ ] **Step 1: Падающий тест (спайк-гейт shallow push)**

Добавить в `JGitSyncTest`:
```kotlin
    @Test
    fun local_edit_is_committed_and_pushed() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone
        File(local, "welcome.md").writeText("# Welcome\n\nlocal edit\n")

        val result = sync.sync(cfg)

        assertTrue(result is SyncResult.Synced && result.pushed, "должен быть push")
        assertTrue(readFromServer(bare, "welcome.md").contains("local edit"),
            "правка должна оказаться на сервере (push из shallow-клона)")
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: FAIL — второй `sync()` возвращает `UpToDate`, файла на сервере нет.

> Спайк-гейт: если push из shallow-клона на JGit невозможен, этот тест не пройдёт никаким минимальным кодом → STOP, эскалация (см. спеку/решение о хранении), не форсить.

- [ ] **Step 3: Реализация add/commit/push (заменить ветку «есть репо»)**

В `JGitSync.sync`, вместо `SyncResult.UpToDate` в ветке существующего репо:
```kotlin
            Git.open(dir).use { git ->
                git.add().addFilepattern(".").call()
                git.add().addFilepattern(".").setUpdate(true).call()
                val committedLocal = if (!git.status().call().isClean) {
                    git.commit()
                        .setMessage("obsidian-md sync")
                        .setAuthor(config.authorName, config.authorEmail)
                        .setCommitter(config.authorName, config.authorEmail)
                        .call()
                    true
                } else {
                    false
                }
                if (committedLocal) {
                    git.push().setRemote("origin").setCredentialsProvider(creds).call()
                    SyncResult.Synced(pushed = true, conflictsResolved = 0)
                } else {
                    SyncResult.UpToDate
                }
            }
```
(весь блок `Git.open(dir).use { ... }` — это возвращаемое значение `withContext`; убедиться, что он на месте `SyncResult.UpToDate` из Task 1.)

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: PASS (все тесты).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src
git commit -m "feat: sync commits and pushes local edits (shallow push verified)"
```

---

### Task 3: Fetch + merge без конфликта

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitSync.kt`
- Test: `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitSyncTest.kt` (добавить хелпер + тест)

**Interfaces:**
- Consumes: `sync` (Task 2). Produces: fetch+merge внутри `sync`; тест-хелпер `pushRemoteChange`.

- [ ] **Step 1: Хелпер «изменить файл на сервере» + падающий тест**

Добавить в `BareRepo.kt`:
```kotlin
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
```
Добавить в `JGitSyncTest`:
```kotlin
    @Test
    fun merges_remote_and_local_without_conflict() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone

        pushRemoteChange(bare, "remote.md", "# Remote\n")     // сервер: новый файл
        File(local, "welcome.md").writeText("# Welcome\n\nlocal\n") // локально: другой файл

        val result = sync.sync(cfg)

        assertTrue(result is SyncResult.Synced)
        assertTrue(File(local, "remote.md").exists(), "серверный файл подтянут")
        assertTrue(File(local, "welcome.md").readText().contains("local"), "локальная правка цела")
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: FAIL — без fetch/merge `remote.md` локально не появляется.

- [ ] **Step 3: Реализация fetch+merge**

В `JGitSync.sync`, внутри `Git.open(dir).use { git -> ... }`, заменить блок после вычисления `committedLocal` на:
```kotlin
                git.fetch().setRemote("origin").setDepth(1)
                    .setCredentialsProvider(creds).call()
                val remoteRef = git.repository.findRef("refs/remotes/origin/${config.branch}")
                    ?: return@use SyncResult.Failed("no remote branch ${config.branch}")

                val merge = git.merge()
                    .include(remoteRef)
                    .setContentMergeStrategy(org.eclipse.jgit.merge.ContentMergeStrategy.THEIRS)
                    .setCommit(true)
                    .call()

                val shouldPush = committedLocal ||
                    merge.mergeStatus == org.eclipse.jgit.api.MergeResult.MergeStatus.MERGED
                val pushed = if (shouldPush) {
                    git.push().setRemote("origin").setCredentialsProvider(creds).call()
                    true
                } else {
                    false
                }

                val upToDate = !committedLocal && !pushed &&
                    merge.mergeStatus == org.eclipse.jgit.api.MergeResult.MergeStatus.ALREADY_UP_TO_DATE
                if (upToDate) SyncResult.UpToDate
                else SyncResult.Synced(pushed = pushed, conflictsResolved = 0)
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src
git commit -m "feat: fetch + merge remote changes (server-wins strategy)"
```

---

### Task 4: Конфликт → сервер выигрывает + счётчик

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/sync/JGitSync.kt`
- Test: `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitSyncTest.kt` (добавить)

**Interfaces:**
- Consumes: `sync` (Task 3). Produces: `conflictsResolved` считается через приватный `countBothSidesModified`.

- [ ] **Step 1: Падающий тест на конфликт**

Добавить в `JGitSyncTest`:
```kotlin
    @Test
    fun conflict_server_wins_and_counts() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone

        pushRemoteChange(bare, "welcome.md", "# Welcome\n\nSERVER\n") // сервер правит welcome.md
        File(local, "welcome.md").writeText("# Welcome\n\nLOCAL\n")    // локально тот же файл иначе

        val result = sync.sync(cfg)

        assertTrue(result is SyncResult.Synced)
        assertEquals(1, (result as SyncResult.Synced).conflictsResolved)
        assertTrue(File(local, "welcome.md").readText().contains("SERVER"), "серверная версия победила")
        assertTrue(!File(local, "welcome.md").readText().contains("LOCAL"), "локальная версия перезаписана")
    }
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: FAIL — `conflictsResolved` == 0 (пока не считаем).

- [ ] **Step 3: Реализация подсчёта конфликтов**

Добавить приватный метод в `JGitSync` (считает файлы, изменённые и локально, и на сервере относительно merge-base — это и есть множество конфликтов, разрешаемых в пользу сервера):
```kotlin
    private fun countBothSidesModified(
        repo: org.eclipse.jgit.lib.Repository,
        ours: org.eclipse.jgit.lib.ObjectId,
        theirs: org.eclipse.jgit.lib.ObjectId,
    ): Int {
        org.eclipse.jgit.revwalk.RevWalk(repo).use { walk ->
            val oursCommit = walk.parseCommit(ours)
            val theirsCommit = walk.parseCommit(theirs)
            walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE)
            walk.markStart(oursCommit)
            walk.markStart(theirsCommit)
            val base = walk.next() ?: return 0
            val baseTree = walk.parseCommit(base).tree
            val changed = { from: org.eclipse.jgit.revwalk.RevTree, to: org.eclipse.jgit.revwalk.RevTree ->
                org.eclipse.jgit.treewalk.TreeWalk(repo).use { tw ->
                    tw.addTree(from); tw.addTree(to); tw.isRecursive = true
                    tw.filter = org.eclipse.jgit.treewalk.filter.TreeFilter.ANY_DIFF
                    val set = HashSet<String>()
                    while (tw.next()) set.add(tw.pathString)
                    set
                }
            }
            val oursChanged = changed(baseTree, oursCommit.tree)
            val theirsChanged = changed(baseTree, theirsCommit.tree)
            return oursChanged.intersect(theirsChanged).size
        }
    }
```
И использовать его в `sync`: **до** вызова `merge`, вычислить `HEAD` и число конфликтов:
```kotlin
                val headBefore = git.repository.resolve("HEAD")
                val conflicts = if (headBefore != null)
                    countBothSidesModified(git.repository, headBefore, remoteRef.objectId)
                else 0
```
(вставить сразу после получения `remoteRef` и до `git.merge()`), затем в финале вернуть
`SyncResult.Synced(pushed = pushed, conflictsResolved = conflicts)` вместо `conflictsResolved = 0`.

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src
git commit -m "feat: count conflicting files resolved server-wins"
```

---

### Task 5: Up-to-date

**Files:**
- Test: `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/sync/JGitSyncTest.kt` (добавить)

**Interfaces:**
- Consumes: `sync` (Task 3 уже возвращает `UpToDate` при отсутствии изменений). Это тест-закрепление поведения.

- [ ] **Step 1: Тест на UpToDate**

Добавить в `JGitSyncTest`:
```kotlin
    @Test
    fun no_changes_returns_up_to_date() = runTest {
        val bare = createSeededBareRepo()
        val local = newLocalDir()
        val sync = JGitSync()
        val cfg = config(bare, local)
        sync.sync(cfg) // clone

        val result = sync.sync(cfg) // без изменений

        assertEquals(SyncResult.UpToDate, result)
    }
```

- [ ] **Step 2: Прогнать**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.sync.JGitSyncTest"`
Expected: PASS сразу (поведение реализовано в Task 3). Если PASS без падения — это закрепляющий тест; убедиться, что он реально исполняется (проверить в отчёте, что тест виден).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src
git commit -m "test: assert up-to-date sync returns UpToDate"
```

---

## Self-review

**Покрытие спеки:**
- Интерфейс `GitSync`/`SyncConfig`/`SyncResult` (commonMain) → Task 1 Step 2.
- Shallow clone → Task 1.
- add/commit/push + спайк-гейт shallow-push → Task 2.
- fetch + merge без конфликта, без потерь → Task 3.
- «сервер выигрывает» + `conflictsResolved` → Task 4.
- `UpToDate` → Task 5 (поведение из Task 3).
- Тесты против bare-репо через `file://`, без сети → все тест-шаги (`token=null`).
- Приёмочный ручной кейс с реальным GitHub → в спеке; кода не требует.
- Аналитика → неприменима (внутренний движок), отмечено в Global Constraints.

**Placeholder-скан:** реальный код и команды в каждом шаге; «обработать ошибки» реализовано как `catch → Failed`.

**Согласованность типов:** `SyncConfig(remoteUrl, localPath, branch, token, authorName, authorEmail)`, `SyncResult.{Cloned,UpToDate,Synced(pushed,conflictsResolved),Failed(reason)}`, `JGitSync().sync(config)` — имена и сигнатуры едины между Task 1→5. `countBothSidesModified(repo, ours, theirs)` определён в Task 4 и там же используется.

**Риск (перенесён из спеки):** JGit 6.10.0.202406032230-r должен поддерживать `ContentMergeStrategy` в `MergeCommand` и shallow push; если конкретная версия/API не совпадёт — developing подберёт ближайшую рабочую 6.x и/или уточнит вызовы (это ожидаемая калибровка, не смена дизайна).
