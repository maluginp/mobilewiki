# Дизайн: вынос `settings` и `note` в feature-модули

Дата: 2026-07-14

## Цель

Вынести из `composeApp` в отдельные feature-модули (по эталону `vault`/`ai`) две
области:

- **`settings`** — экран настроек, `SettingsViewModel`, `RepoSettingsStore` и его
  реализация.
- **`note`** — UI просмотра/редактирования заметки (`MarkdownScreen`, редактор
  markdown, тулбар форматирования).

Модули кладём под `features/` (как `ai`/`auth`/`vault`/`sync`). Пакеты сохраняем
(`app.obsidianmd.settings`, `app.obsidianmd.note` — новый), чтобы импорты моделей по
проекту не ломались.

## Границы (что переносим, что нет)

Оба модуля следуют варианту **A**: фича отдаёт **stateless-экран** через
`{Feature}PresentationProvider` (интерфейс в `:api`), а владельцы состояния/навигации
остаются в `composeApp`. Это согласуется с записанным в `MODULE_CONVENTIONS.md` решением
«shell-VM (`VaultViewModel`) и навигация живут в основном модуле».

**Остаётся в `composeApp` (сознательно):**

- `VaultViewModel` (god-VM: просмотр vault + заметка + оркестрация синка) и `SyncStatus`
  (объявлен внутри него).
- `AppNavHost`, `Route`, вся навигация.
- `ConflictDialog` (диалог конфликта синка, всплывает поверх любого экрана — дело shell,
  не экрана заметки).
- `decodeImage` (expect/actual) — байты картинок отдаёт shell, экран заметки получает
  готовый `loadImage: (String) -> ImageBitmap?` параметром.
- `SyncWorker`, `BackgroundSyncRunner` и прочий синк.

## `:features:note`

Пакет `app.obsidianmd.note`.

### `note:api`

Compose-плагины (`org.jetbrains.compose` + `kotlin.plugin.compose`), зависит от
`compose.runtime/ui/material3` и `:features:vault:api` (ради `DocRef`/`VaultFile`).

```kotlin
interface NotePresentationProvider {
    @Composable fun NoteScreen(
        title: String,
        content: String,
        files: List<VaultFile>,
        documents: List<DocRef>,
        loadImage: (String) -> ImageBitmap?,
        onOpenPath: (String) -> Unit,
        onNavigateBack: () -> Unit,
        onSave: (String) -> Unit,
        bottomBar: @Composable () -> Unit,
    )
}
```

Сигнатура — 1:1 текущий `MarkdownScreen`, только через интерфейс.

### `note:impl`

Зависит от `:features:note:api`, `:features:vault:api`, `:core:translations`,
markdown-либы (`com.mikepenz.markdown`).

- `presentation/NoteScreen.kt` — нынешний `MarkdownScreen` (internal) + приватные
  `DocumentPickerDialog`, `ZoomableImage`.
- `presentation/EditorToolbar.kt` — тулбар форматирования (internal).
- `domain/MdEdit.kt` — `MdEdit` + `EditState` (internal), чистые трансформации текста.
- `presentation/NotePresentationProviderImpl.kt` — internal, рендерит `NoteScreen`.
- `di/NoteModule.kt` — `val noteModule` (commonMain), биндит
  `NotePresentationProvider`. **Platform-модуль не нужен** (нет platform-зависимостей).

Тесты: `commonTest` — `MdEditTest` (переезжает как есть).

## `:features:settings`

Пакет `app.obsidianmd.settings`.

### `settings:api`

Compose-плагины, зависит от `compose.runtime/ui/material3`.

```kotlin
interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
}

interface SettingsPresentationProvider {
    @Composable fun Screen(
        syncing: Boolean,
        syncStatusText: String,
        onSync: () -> Unit,
        onNavigateBack: () -> Unit,
        onPickFromGitHub: () -> Unit,
        aiSection: @Composable () -> Unit,
    )
}
```

`syncing`/`syncStatusText` — нейтральные примитивы: фича настроек не знает про
`SyncStatus`/`SyncResult` (они у shell). `composeApp` считает текст сам и передаёт строку.

### `settings:impl`

Зависит от `:features:settings:api`, `:core:translations`.

- `presentation/SettingsScreen.kt` — **stateless** internal-composable: принимает
  `url`, `onSave`, `syncing`, `syncStatusText`, `onSync`, `onNavigateBack`,
  `onPickFromGitHub`, `aiSection`. Тестируется в изоляции без Koin.
- `presentation/SettingsViewModel.kt` — `SettingsViewModel` + `SettingsState`
  (internal, переезжают как есть).
- `presentation/SettingsPresentationProviderImpl.kt` — internal, инжектит
  `SettingsViewModel` через `koinViewModel()`, кормит stateless `SettingsScreen`.
- `data/SharedPrefsRepoSettingsStore.kt` (androidMain) — **public** (не `internal`):
  фоновый `SyncWorker` создаёт его напрямую, вне Koin-графа. Это тот же прецедент, что
  и public `EncryptedTokenStore` в `auth:impl`.
- `di/SettingsModule.kt` — `val settingsModule` (commonMain): `viewModel { SettingsViewModel(get()) }`
  + бинд `SettingsPresentationProvider` + `includes(settingsPlatformModule)`.
- `di/SettingsModule.<platform>.kt` — `expect/actual val settingsPlatformModule`; на
  Android биндит `RepoSettingsStore` → `SharedPrefsRepoSettingsStore(androidContext())`.

Тесты: `commonTest` — `RepoSettingsStoreContractTest`, `SettingsViewModelTest`,
`FakeRepoSettingsStore` (переезжают как есть); `androidUnitTest` — `SettingsScreenTest`
(переписан под новую stateless-сигнатуру: `url: String` вместо `SettingsState`,
`syncing`/`syncStatusText` вместо `SyncStatus`).

## Изменения в `composeApp`

- `di/AppModule.kt`: убрать `single<RepoSettingsStore> { SharedPrefsRepoSettingsStore(...) }`
  и `viewModel { SettingsViewModel(...) }` (уходят в `settingsModule`/`settingsPlatformModule`);
  убрать импорты `SettingsViewModel`, `SharedPrefsRepoSettingsStore`. `SyncConfigProvider`
  продолжает брать `get<RepoSettingsStore>()` (теперь из `settingsModule`).
- `nav/AppNavHost.kt`:
  - убрать `val settingsVm: SettingsViewModel = koinViewModel()` и `settings`-стейт;
  - инжектить `notePresentation = koinInject<NotePresentationProvider>()` и
    `settingsPresentation = koinInject<SettingsPresentationProvider>()`;
  - `Route.Note` → `notePresentation.NoteScreen(...)`;
  - `Route.Settings` → `settingsPresentation.Screen(syncing = state.syncStatus is SyncStatus.Running,
    syncStatusText = syncStatusText(state.syncStatus), onSync = vm::sync, …)`;
  - онбординг (`RepoValidate.onContinue`, выбор стартового стека при входе) читает/пишет
    URL напрямую через `RepoSettingsStore` (`koinInject`), а не через VM.
- `ui/SyncStatusText.kt` (новый) — хелпер `syncStatusText(SyncStatus): String`
  переезжает из `SettingsScreen.kt` (использует `SyncStatus` + `SyncResult`, оба в
  `composeApp`).
- `BrainerApp.onKoinStartup`: добавить `noteModule, settingsModule`.
- `settings.gradle.kts`: `include(":features:note:api", ":features:note:impl",
  ":features:settings:api", ":features:settings:impl")`.
- `composeApp/build.gradle.kts`: `api(project(":features:note:api"))` +
  `implementation(project(":features:note:impl"))`; аналогично для `settings`;
  перенести markdown-зависимость в `note:impl`, если она больше нигде в `composeApp`
  не нужна.
- Удалить перенесённые файлы: `settings/*`, `ui/SettingsScreen.kt`,
  `ui/MarkdownScreen.kt`, `ui/EditorToolbar.kt`, `editor/MdEdit.kt` и их тесты.
- `MainActivity.kt` — импорт `RepoSettingsStore` остаётся (тот же пакет), правок нет.
- `SyncWorker.kt` — импорт `SharedPrefsRepoSettingsStore` остаётся (тот же пакет,
  класс public), правок нет.

## Обновление документации

`docs/MODULE_CONVENTIONS.md`: добавить `features/note/*` и `features/settings/*` в дерево
модулей; отметить, что shell-`VaultViewModel` и навигация по-прежнему в `composeApp`, и
что `SharedPrefsRepoSettingsStore` — public-исключение ради `SyncWorker`.

## Тестирование (TDD)

Задача — преимущественно перенос; существующие тесты и есть страховочная сеть. Порядок
по каждому юниту: **сначала переносим/пишем тест в новом модуле (red — не компилируется
или падает), затем переносим реализацию (green).**

| Юнит | Первый падающий тест | «Минимальный код, чтобы прошёл» | Изоляция |
|------|----------------------|--------------------------------|----------|
| `MdEdit` | `MdEditTest` в `note:impl` не компилируется (нет `MdEdit`/`EditState`) | перенести `MdEdit.kt` в `note:impl/domain` | чистые функции, без Compose |
| `RepoSettingsStore` контракт | `RepoSettingsStoreContractTest` + `FakeRepoSettingsStore` в `settings:impl` красный | перенести интерфейс в `settings:api` | fake-реализация в тесте |
| `SettingsViewModel` | `SettingsViewModelTest` в `settings:impl` красный | перенести VM в `settings:impl` | конструктор берёт `RepoSettingsStore` (fake) |
| `SettingsScreen` (stateless) | переписанный `SettingsScreenTest` (новая сигнатура) красный | внедрить stateless-`SettingsScreen` с `url`/`syncing`/`syncStatusText` | Robolectric compose-test, без Koin |
| `NoteScreen` | компиляция `note:impl` (провайдер) | перенести `MarkdownScreen` → `NoteScreen`, реализовать провайдер | stateless, всё параметрами |
| Интеграция DI/навигации | `:composeApp:testDebugUnitTest` (`VaultViewModelTest` и пр.) зелёные + `assembleDebug` | подключить модули, переписать `AppNavHost`/`AppModule` | — |

Финальная проверка:

```
./gradlew :features:note:impl:testDebugUnitTest \
          :features:settings:impl:testDebugUnitTest \
          :composeApp:testDebugUnitTest \
          :composeApp:assembleDebug
```

## Приёмочные тест-кейсы (ручной прогон после разработки)

### 1. Настройки: сохранение URL и синк

- **Изначальное состояние:** приложение открыто, репозиторий уже настроен, пользователь
  на списке заметок.
- **Шаги:** открыть настройки → изменить Repository URL → нажать «Save» → нажать
  «Sync now».
- **Ожидаемый результат:** появляется подтверждение «Saved ✓»; кнопка синка показывает
  статус «Syncing…», затем итог (например «Up to date»); после перезапуска приложения
  сохранённый URL подставлен в поле.

### 2. Настройки: AI-секция на месте

- **Изначальное состояние:** экран настроек открыт, AI включён.
- **Шаги:** проскроллить настройки до AI-секции → нажать выбор модели.
- **Ожидаемый результат:** AI-секция отображается ниже настроек репозитория; тап
  открывает экран выбора модели (навигация из shell не сломана).

### 3. Заметка: просмотр, wikilink, картинка

- **Изначальное состояние:** список заметок, есть заметка с wiki-ссылкой и встроенной
  картинкой.
- **Шаги:** открыть заметку → тапнуть по wiki-ссылке → вернуться назад → тапнуть по
  картинке.
- **Ожидаемый результат:** markdown отрендерен; wiki-ссылка открывает целевую заметку;
  «Назад» возвращает; тап по картинке открывает полноэкранный просмотр с зумом.

### 4. Заметка: редактирование и защита от потери правок

- **Изначальное состояние:** открыта заметка.
- **Шаги:** нажать «Edit» → изменить текст → применить форматирование из тулбара →
  нажать «Назад» (не сохраняя).
- **Ожидаемый результат:** появляется диалог о несохранённых правках с «Save»/«Discard»;
  «Save» сохраняет и выходит из режима правки, «Discard» отменяет изменения.

### 5. Онбординг: выбор репозитория пишет настройки

- **Изначальное состояние:** свежий вход, репозиторий не выбран.
- **Шаги:** пройти онбординг до выбора репозитория → подтвердить репозиторий.
- **Ожидаемый результат:** приложение переходит к списку заметок и запускает синк; в
  настройках виден выбранный URL (значит запись в `RepoSettingsStore` из онбординга
  работает без `SettingsViewModel`).
