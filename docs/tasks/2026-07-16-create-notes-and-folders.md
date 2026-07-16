# Создание md-файлов и папок в vault

Jira: —  (tracker: none, free-text)
Branch: create-notes-and-folders

<intent>
В приложении нельзя создать новую заметку (.md) или папку — можно только просматривать,
редактировать и искать уже существующие. Нужно добавить создание файлов и папок прямо из
экрана списка, в текущем открытом каталоге.
</intent>

<context>
Экран списка — `VaultListScreen` (`:vault:impl`), состояние — `VaultViewModel.VaultState`
(`currentDir`, `entries`), навигация — `AppNavHost` (бэкстек: список ↔ заметка).
Доступ к vault — `VaultRepository` (`:vault:api`), реализация `OkioVaultRepository` поверх okio.
Уже есть: `writeFile(path, content)` (создаёт/перезаписывает файл), `listEntries(dir)`,
`parentOf`, `isRoot`, `rootPath`, `pathFor(name)` (только корень). Создания папки нет.
Редактор заметки — `NoteScreen` (открывается по `Route.Note(path)`), сохранение — `vm.saveFile`.
i18n — строки в `core/translations/.../values/strings.xml` (+ `values-ru/`), секция «Notes list» пустая.
Тесты: `VaultRepositoryTest` (okio `FakeFileSystem`), `VaultListScreenTest` (Robolectric compose-uiTest),
`VaultViewModelTest`; фейки `FakeVaultRepository` в двух местах (composeApp/commonTest, features/ai/impl/commonTest).

Исходный текст задачи: «Вижу что не хватает возможности добавления файлов (md-файлов) и папок.»
</context>

<requirements>
- Со экрана списка можно создать новую .md-заметку и новую папку в текущем каталоге.
- Новая заметка сразу открывается в редакторе.
- Новая папка появляется в списке (папки — первыми), можно в неё зайти.
- Имя валидируется: пустое/с «/»/уже существующее — создать нельзя (понятная ошибка в диалоге).
- После создания список обновляется; изменения подхватывает обычная git-синхронизация (push).
</requirements>
