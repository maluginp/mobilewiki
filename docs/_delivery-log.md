# Delivery log

- 2026-07-06 — Скаффолд KMP/Compose + просмотр md (`scaffold-md-viewer`): код готов и зелёный на ветке, MR пропущен (git remote не настроен, flow локальный).
- 2026-07-06 — Стратегия локального git-хранения (`git-storage-strategy`): принято решение — shallow clone (--depth=1), история только на сервере. Только дизайн-решение, кода нет.
- 2026-07-07 — Движок git-синхронизации (JGit, слайс 1) (`git-sync-engine`): код готов и зелёный на ветке (12 тестов); спайк выявил несовместимость shallow с merge → решение о хранении пересмотрено на полный клон; конфликты .md спрашивают резолвер, не-.md — сервер. MR пропущен (remote не настроен).
- 2026-07-07 — UI git-синка (кнопка/статус/диалог) (`sync-ui`): код готов и зелёный на ветке (16 тестов); мост UiConflictResolver↔Compose, .md-конфликт спрашивает выбор, конфиг репо через BuildConfig, авто-сид убран. MR пропущен (remote не настроен).
- 2026-07-07 — GitHub OAuth Device Flow (`github-oauth-device-flow`): код готов и зелёный (24 теста); Ktor device flow, EncryptedSharedPreferences, экран входа, токен из хранилища в синк, BuildConfig.SYNC_TOKEN убран. MR пропущен (GitHub, не GitLab).
- 2026-07-07 — Экран настроек репозитория (`repo-settings`): код готов и зелёный (28 тестов); RepoSettingsStore + SettingsScreen, VaultViewModel через syncConfigProvider, BuildConfig.SYNC_REMOTE_URL убран. MR пропущен (GitHub, не GitLab).
- 2026-07-08 — Автосинк на WorkManager (`autosync`): код готов и зелёный (31 тест); BackgroundSyncRunner (USE_LOCAL в фоне), SyncWorker, периодический AutoSyncScheduler (~30 мин), планирование при входе. MR пропущен (GitHub, не GitLab).
- 2026-07-08 — Редактирование md (`markdown-editing`): код готов и зелёный (33 теста); VaultRepository.writeFile, VaultViewModel.saveFile, режим правки (просмотр↔редактор) в MarkdownScreen. MR пропущен (GitHub, не GitLab).
- 2026-07-08 — Поиск по заметкам (`note-search`): код готов и зелёный (35 тестов); VaultRepository.search (имя+содержимое, без регистра), VaultViewModel.search, поле поиска в списке. MR пропущен (GitHub, не GitLab).
