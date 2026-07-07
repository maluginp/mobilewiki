# Спека: движок git-синхронизации (JGit) — слайс 1

Дата: 2026-07-07 · Tracker: none (key-less)
Основано на решении: docs/specs/2026-07-06-git-storage-strategy.md (shallow clone).

## Цель
Ядро синхронизации ваулта с git-remote: shallow clone при первом запуске, затем
commit локальных правок + fetch + merge («сервер выигрывает») + push. Auth-агностично —
токен принимается как параметр. Полностью тестируется локально против bare-репо.

## Границы (scope)
**В слайсе:** движок `GitSync` (интерфейс в commonMain) + реализация `JGitSync` на JGit
(androidMain), стратегия конфликта «сервер выигрывает», shallow clone/fetch, push.
**НЕ в слайсе:** UI, получение токена через OAuth device flow (слайс 2), автосинк/
WorkManager (слайс 3), выбор ветки/репо в UI. Решения по ним зафиксированы:
auth = OAuth device flow, триггер = автосинк, конфликт = сервер выигрывает.

## Архитектура
- `GitSync` — интерфейс в `commonMain` (пакет `app.obsidianmd.sync`), чтобы UI/общий код
  зависел от абстракции, а не от JGit.
- `JGitSync` — реализация в `androidMain` через JGit (чистая Java-либа, работает на JVM и
  Android). Блокирующие операции выполняются на `Dispatchers.IO`.
- Библиотека: `org.eclipse.jgit:org.eclipse.jgit` (кандидат по умолчанию из решения).

## Интерфейсы (commonMain)
```kotlin
package app.obsidianmd.sync

data class SyncConfig(
    val remoteUrl: String,
    val localPath: String,
    val branch: String = "main",
    val token: String? = null,        // null → без креденшлов (file:// в тестах)
    val authorName: String = "obsidian-md",
    val authorEmail: String = "obsidian-md@localhost",
)

sealed interface SyncResult {
    data object Cloned : SyncResult
    data object UpToDate : SyncResult
    data class Synced(val pushed: Boolean, val conflictsResolved: Int) : SyncResult
    data class Failed(val reason: String) : SyncResult
}

data class MdConflict(val path: String, val local: String, val server: String)
enum class Resolution { USE_LOCAL, USE_SERVER }
fun interface ConflictResolver {
    suspend fun resolve(conflict: MdConflict): Resolution
}

interface GitSync {
    suspend fun sync(config: SyncConfig, resolver: ConflictResolver): SyncResult
}
```

## Разрешение конфликтов (обновлено)
- Конфликт в **`.md`-файле** → движок вызывает `resolver.resolve(MdConflict(path, local, server))`
  и применяет выбор: `USE_LOCAL` (оставить локальную) или `USE_SERVER` (взять серверную).
  Движок headless — сам диалог рисует UI-слой (отдельный слайс); здесь резолвер приходит
  параметром и в тестах подставляется фейковым.
- Конфликт в **не-`.md`** → всегда серверная версия (theirs), резолвер не вызывается.
- `local`/`server` для `MdConflict` читаются из деревьев коммитов (ours = HEAD, theirs =
  origin/branch) по пути файла.

## Поток `sync()`
1. **Нет локального репо** (папки `.git` нет) → shallow clone: `CloneCommand` с
   `setDepth(1)`, `setBranchesToClone`/single-branch, `setBranch(branch)`. Результат `Cloned`.
2. **Есть репо:**
   a. `git add -A` (стейдж всех изменений рабочего дерева).
   b. Если есть что коммитить — commit с автором из конфига, сообщение
      `"sync: <timestamp>"` (timestamp прокидывается снаружи, т.к. в тестах время
      детерминировано).
   c. `fetch` с `setDepth(1)`.
   d. `merge` в recursive-режиме с `ContentMergeStrategy.THEIRS` (на конфликтующих файлах
      берётся серверная версия; неконфликтующие локальные правки сохраняются). Считаем
      число файлов, где конфликт был разрешён в пользу сервера → `conflictsResolved`.
   e. Если после merge локальная ветка впереди remote — `push`. `Synced(pushed, n)`.
   f. Если ничего не менялось ни локально, ни на сервере → `UpToDate`.
3. Любая ошибка (сеть, auth, git) ловится → `Failed(reason)` (не бросаем исключение выше).

**Креденшлы:** при `token != null` — `UsernamePasswordCredentialsProvider(token, "")`
(GitHub принимает токен как username по HTTPS). При `token == null` — без провайдера
(локальный `file://`).

## Тестирование (TDD, JVM-тесты в `androidUnitTest`)
JGit — чистая Java, тесты гоняются на JVM без Android-эмулятора. Remote = локальный
**bare-репо** (`git init --bare` во временной папке), доступ по пути/`file://`. Хелпер
создаёт bare + первый коммит с парой файлов.

Единицы и первый падающий тест для каждой:

1. **Clone.** *Первый падающий тест:* `sync()` на пустой локальной папке → `Cloned`, файл
   `welcome.md` из bare появился локально, репозиторий shallow (`.git/shallow` существует /
   depth==1). *Минимальный код:* реализовать ветку clone в `JGitSync`. Падает изначально —
   `JGitSync` не существует.
2. **Push локальной правки.** Тест: после clone дописать файл, `sync()` → `Synced(pushed=true)`;
   во втором свежем clone из bare правка видна. Минимально — ветка add/commit/push.
3. **Слияние без конфликта.** Тест: удалённая правка файла `a.md` (через второй clone+push) +
   локальная правка `b.md`, `sync()` → обе правки на месте локально, `Synced`. Минимально —
   fetch+merge.
4. **Конфликт → сервер выигрывает.** Тест: и remote, и локально правят `a.md` по-разному,
   `sync()` → локальный `a.md` == серверная версия, `conflictsResolved == 1`, изменения
   запушены. Минимально — merge с `ContentMergeStrategy.THEIRS` + подсчёт.
5. **Up-to-date.** Тест: сразу после clone без изменений `sync()` → `UpToDate`.

**Спайк-гейт (из решения о хранении):** тесты 2 и 4 пушат из shallow-клона — если JGit не
умеет push из shallow против bare-репо, тест 2 падает и мы это видим до любого UI. Если так —
стоп, эскалация: пересмотреть библиотеку/стратегию (полный single-branch clone + обрезка,
либо другая либа). Это гейт, а не «поправим потом».

Изоляция: `GitSync` — интерфейс, `JGitSync` тестируется через реальный git (JGit + bare),
без моков (тестируем настоящее поведение, а не заглушки).

## Приёмочные тест-кейсы (ручные, после разработки)
Движок без UI, поэтому основная приёмка — автоматические интеграционные тесты выше. Один
ручной e2e-кейс против реального GitHub (опционально; нужен тестовый приватный репо и classic
PAT — токен передаётся в `SyncConfig.token` строкой, UI-авторизация не требуется):

- **Название:** Синхронизация с реальным GitHub, «сервер выигрывает».
- **Изначальное состояние:** пустой тестовый GitHub-репо с 1 markdown-файлом; на руках classic
  PAT с доступом к репо; две локальные папки (копия A и копия B).
- **Шаги:**
  1. Запустить `sync()` для копии A (URL репо + токен) → произойдёт shallow clone.
  2. Изменить общий файл в копии A, `sync()` → правки уходят на GitHub.
  3. Изменить тот же файл в копии B иначе и `sync()` копии B.
- **Ожидаемый результат:** после шага 3 в копии B этот файл содержит серверную (из копии A)
  версию — сервер выиграл; никакие файлы не потеряны; на GitHub история содержит оба
  синка. Локальные `.git` в обеих копиях остаются малы (shallow, ≈ размер содержимого).
