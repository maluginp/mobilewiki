# Лоадер при загрузке уже выбранного репозитория

Jira: —  (tracker: none, free-text)
Branch: fix-vault-loading-loader

<intent>
При открытии приложения с уже выбранным репозиторием список показывает «No files»,
пока файлы ещё загружаются. Вместо пустого состояния нужно показывать лоадер.
</intent>

<context>
Экран списка — `VaultListScreen`, состояние — `VaultViewModel.VaultState`.
На старте `App.kt` вызывает `vm.refresh()` → `loadDir()`, который читает список с IO,
но не выставляет флаг `loading`. Пока чтение идёт, `entries` пуст и экран рисует
`notes_empty` («No files»). Прецедент лоадера уже есть в `ModelPickerScreen`
(`if (loading && models.isEmpty()) CircularProgressIndicator`).

Исходный текст задачи: «При загрузке уже выбранного репозитория показывает No files,
хотя он загружается — нужно показывать loader».
</context>

<requirements>
- Пока идёт первичная загрузка списка и он пуст — показать лоадер, а не «No files».
- «No files» показывать только когда загрузка завершилась и файлов действительно нет.
</requirements>
