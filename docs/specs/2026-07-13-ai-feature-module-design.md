# AI-чат как feature-модуль (`ai:api` / `ai:impl`)

Дата: 2026-07-13

## 1. Контекст и цель

AI-чат сейчас размазан по `composeApp`: движок (`ai/`), экраны (`ui/AiChatScreen`,
`ui/ModelPickerScreen`), конфиг в настройках (`settings/SettingsViewModel`,
`settings/RepoSettingsStore`, AI-секция в `ui/SettingsScreen`), DI (`di/AppModule`) и
glue в навигации (`nav/AppNavHost`).

Цель — вынести **всю** AI-функциональность, включая её настройки, в отдельный
feature-модуль по эталону `:auth` / `:vault` (см. `docs/MODULE_CONVENTIONS.md`):
`ai:api` — контракты и провайдер экранов, `ai:impl` — реализация (internal).

Не-цель: менять поведение AI-агента, клиента, формат хранения настроек существующих
пользователей, переписывать не связанный с AI код настроек/навигации.

## 2. Границы модуля

Пакет Kotlin в обоих модулях остаётся `app.obsidianmd.ai` (как `app.obsidianmd.auth`
в auth). Namespace различается: `ai:api` → `app.obsidianmd.ai.api`, `ai:impl` →
`app.obsidianmd.ai.impl`. Импорты `app.obsidianmd.ai.*` в `composeApp` не меняются —
меняется только модуль-источник.

Правила зависимостей (по `MODULE_CONVENTIONS.md`):

- `ai:api` → `vault:api`, `core:*`, `compose.runtime/foundation/material3` (нужен для
  `AiPresentationProvider` с `@Composable`).
- `ai:impl` → `ai:api`, `vault:api`, `core:analytics`, `core:translations`, ktor,
  koin, lifecycle-viewmodel, coroutines; androidMain — `androidx.security.crypto`,
  koin-android.
- `composeApp` → `api(project(":ai:api"))` + `implementation(project(":ai:impl"))`.

`ai:impl` **не** зависит от `composeApp`: HttpClient берётся из общего Koin-графа
(`get<HttpClient>()`), как в auth. Все классы `ai:impl` — `internal`; наружу торчат
только `aiModule` (Koin, public) и `AiPresentationProvider` (через `api`).

## 3. Что и куда переезжает

Из `composeApp` в `ai:impl` (без изменения логики; пакет `app.obsidianmd.ai`):

| Файл | Новый слой в `ai:impl` |
|------|------------------------|
| `ai/AiAgent.kt` | `domain/` |
| `ai/AiViewModel.kt` | `presentation/` (VM чата) |
| `ai/OpenRouterClient.kt` | `data/` |
| `ai/AiProvider.kt` → **в `ai:api`** | (api) |
| `ai/ChatClient.kt` → **в `ai:api`** | (api) |
| `ai/ApiKeyStore.kt` → **в `ai:api`** | (api) |
| `ai/ModelInfo` + `fetchModels` (см. `AiProvider.kt`/тесты `ModelInfoTest`) | `ModelInfo` → api, `fetchModels` → `data/` |
| `androidMain/ai/EncryptedApiKeyStore.kt` | `data/` (androidMain) |
| `ui/AiChatScreen.kt` | `presentation/` |
| `ui/ModelPickerScreen.kt` | `presentation/` |
| все тесты `commonTest/ai/*` + `androidUnitTest/ui/AiChatScreenTest`, `ModelPickerScreenTest` | соответствующие sourceSet'ы `ai:impl` |

Новое в `ai:api`: `AiSettingsStore`, `AiPresentationProvider`.
Новое в `ai:impl`: `AiSettingsViewModel` (`presentation/`), `AiSettingsSection`
composable (`presentation/`), `AiPresentationProviderImpl` (`presentation/`),
SharedPrefs-реализация `AiSettingsStore` (`data/`, androidMain), `di/AiModule.kt`
(commonMain) + `di/AiModule.android.kt` (`aiPlatformModule`).

## 4. Публичная поверхность `ai:api`

```kotlin
// Чистые типы конфига — переезжают как есть.
enum class AiProvider(/* ... как сейчас ... */) { OPENROUTER, PROVOD, CUSTOM; /* ... */ }
data class ModelInfo(/* ... как сейчас ... */)

interface ApiKeyStore {           // как сейчас
    fun getKey(provider: String): String?
    fun saveKey(provider: String, key: String)
}

// AI-срез, отрезанный от RepoSettingsStore.
interface AiSettingsStore {
    fun isAiEnabled(): Boolean
    fun setAiEnabled(enabled: Boolean)
    fun getProvider(): String?
    fun setProvider(id: String)
    fun getCustomBaseUrl(): String
    fun setCustomBaseUrl(url: String)
    fun getAiModel(provider: String): String
    fun setAiModel(provider: String, model: String)
}

// Точка входа UI фичи для навигации основного модуля (реализация internal, через DI).
interface AiPresentationProvider {
    /** true — если AI включён (для нижней навигации Brain↔AI в хосте). Реактивно. */
    @Composable fun aiEnabled(): Boolean

    /** Экран чата. Сам собирает свой VM из конфига/ключа; если не сконфигурирован —
     *  рисует заглушку с кнопкой onOpenSettings. */
    @Composable fun Chat(
        onOpenFile: (path: String) -> Unit,
        onOpenSettings: () -> Unit,
        bottomBar: @Composable () -> Unit,
    )

    /** Экран выбора модели. */
    @Composable fun ModelPicker(onNavigateBack: () -> Unit)

    /** AI-секция для встраивания в общий экран настроек composeApp.
     *  onEditModel — переход на экран выбора модели (Route.ModelPicker остаётся в app). */
    @Composable fun SettingsSection(onEditModel: () -> Unit)
}
```

`allFiles` для автодополнения путей в чате раньше приходил из `VaultViewModel.state`.
Так как чат теперь строит свой VM внутри, список файлов он берёт из `VaultRepository`
(`vault:api`) напрямую внутри `AiPresentationProviderImpl` (той же зависимостью, что
уже есть у `AiAgent`). Основной модуль больше не прокидывает `files` в чат.

## 5. Расщепление настроек

`RepoSettingsStore` (в `composeApp`) теряет AI-методы, остаётся:

```kotlin
interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
}
```

`SharedPrefsRepoSettingsStore` ужимается до url. AI-методы уезжают в реализацию
`AiSettingsStore` в `ai:impl` (androidMain), в **отдельный, независимый**
SharedPreferences-файл (своё имя, напр. `ai_settings`). Миграции нет —
пользователей ещё нет, разработка локальная (старые AI-настройки просто не читаются).
API-ключ и так в отдельном `EncryptedApiKeyStore` (переезжает как есть).

`SettingsViewModel` / `SettingsState` (composeApp) ужимаются до не-AI части:

```kotlin
data class SettingsState(val url: String = "")
class SettingsViewModel(private val store: RepoSettingsStore) : ViewModel() {
    // state.url + save(url); sync-статус приходит из VaultViewModel как сейчас.
}
```

Вся AI-логика конфига (провайдер/ключ/модель/aiEnabled/base URL + ленивая загрузка
моделей `loadModels`/`ensureModels`/`reloadModels`) переезжает в **`AiSettingsViewModel`**
(`ai:impl`, internal), поверх `AiSettingsStore` + `ApiKeyStore` + `fetchModels`.
`AiSettingsSection` и `ModelPicker` (через провайдер) используют этот VM
(`koinViewModel` внутри impl). Хост наблюдает `aiEnabled()` тоже через него.

`SettingsScreen` (composeApp) оставляет: sync-секцию, поле repo-URL с кнопкой
«Сохранить» (только url) и «Выбрать из GitHub». На месте прежней AI-секции —
`aiProvider.SettingsSection(onEditModel = { backStack.add(Route.ModelPicker) })`.

### UX-заметка (единственное видимое изменение) — на твоё ревью

Сейчас одна кнопка «Сохранить» коммитит url + ключ + base URL разом. После разделения
`SettingsSection` владеет своими черновиками (ключ, base URL) и получает **свою**
кнопку «Сохранить» для них; кнопка в app-части сохраняет только repo-URL. Провайдер,
модель и switch как и сейчас сохраняются сразу при изменении. Итог: на экране две
кнопки «Сохранить» — под репозиторием и под AI-секцией. Это осознанное следствие
переноса; если хочешь сохранить ровно одну кнопку — скажи, тогда app-кнопка будет
триггерить flush черновиков секции через переданный колбэк (чуть больше связности).

## 6. DI

`ai:impl/di/AiModule.kt` (commonMain):

```kotlin
val aiModule = module {
    includes(aiPlatformModule)                 // ApiKeyStore, AiSettingsStore (нужен Context)
    single<AiPresentationProvider> { AiPresentationProviderImpl() }
    viewModel { AiSettingsViewModel(get(), get(), fetchModels = { p, base -> /* get<HttpClient>() */ }) }
    // Параметризованный VM чата (model, key, chatUrl) — как сейчас в AppModule,
    // поверх get<HttpClient>() + get<VaultRepository>().
    viewModel { (model: String, key: String, chatUrl: String) -> AiViewModel { history, approver ->
        AiAgent(OpenRouterClient(get(), key, model, chatUrl), get(), approver).ask(history)
    } }
}
expect val aiPlatformModule: Module           // android: EncryptedApiKeyStore, SharedPrefs AiSettingsStore
```

Из `AppModule` (composeApp) убираются: `ApiKeyStore`/`EncryptedApiKeyStore`, `AiAgent`,
`AiViewModel`, `fetchModels`, AI-часть биндинга `SettingsViewModel`. Остаётся общий
`HttpClient` (его теперь потребляет и `aiModule`), `RepoSettingsStore` (только url),
`SyncConfigProvider` (без изменений). `SettingsViewModel` биндится без `apiKeyStore`/
`fetchModels`.

`BrainerApp.onKoinStartup`: `modules(appModule, vaultModule, authModule(...), aiModule)`.

## 7. Прочие изменения в composeApp

- `nav/AppNavHost.kt`: entry `AiChat` → `aiProvider.Chat(onOpenFile, onOpenSettings, bottomBar)`;
  entry `ModelPicker` → `aiProvider.ModelPicker(onNavigateBack)`; entry `Settings`
  теряет AI-колбэки, добавляет вызов `SettingsSection`. Удаляются локальные
  `rememberAiViewModel`, `AiUnavailable`. `BrainAiBottomBar` берёт видимость из
  `aiProvider.aiEnabled()` вместо `settings.aiEnabled`. `Route`-и не меняются.
- `MainActivity.kt`: прогрев вне main-потока — `koin.get<ApiKeyStore>()` (импорт
  `app.obsidianmd.ai.ApiKeyStore` не меняется, теперь из `ai:api`). Опционально —
  прогрев `AiSettingsStore` там же.
- `settings.gradle.kts`: `include(":ai:api")`, `include(":ai:impl")`.
- `composeApp/build.gradle.kts`: `api(project(":ai:api"))` +
  `implementation(project(":ai:impl"))`. Убрать из composeApp ktor/security-crypto,
  если они больше нигде не нужны (проверить: sync/auth используют свои).
- `ai/api/build.gradle.kts`, `ai/impl/build.gradle.kts` — по образцу auth.

## 8. Обработка ошибок

Без изменений: `AiResult.Failed(reason)` → `AiStatus.Failed`; таймауты HttpClient
(120 с) наследуются от общего клиента; отсутствие/пустой ключ или выключенный AI →
`Chat` рисует заглушку «AI unavailable» с кнопкой в настройки (прежняя логика
`rememberAiViewModel`, теперь внутри `AiPresentationProviderImpl`). Пустой список
моделей не кэшируется (как сейчас).

## 9. Тестирование (TDD)

Переезжают 1:1 (меняется только модуль/sourceSet, не тело): `AiAgentTest`,
`AiViewModelTest`, `AiProviderTest`, `ModelInfoTest`, `OpenRouterClientTest`,
`ApiKeyStoreContractTest` (+ `FakeApiKeyStore`, `AiTestFixtures`), UI-тесты
`AiChatScreenTest`, `ModelPickerScreenTest` (Robolectric — в `ai:impl`
`androidUnitTest`, как UI-тесты auth).

Новые / изменённые юниты и их первый падающий тест:

- **`AiSettingsStore` (контракт)** — по образцу `RepoSettingsStoreContractTest`.
  Первый падающий тест: `setAiEnabled(true)` затем `isAiEnabled()` возвращает `true`
  на свежем сторе (падает, пока метода/реализации нет). Минимум для прохода —
  перенести AI-ключи в реализацию `AiSettingsStore`. Тестируется изолированно через
  in-memory `FakeAiSettingsStore`.
- **`AiSettingsViewModel`** — AI-кейсы, вырезанные из `SettingsViewModelTest`.
  Первый падающий тест: `setProvider(P)` подтягивает ключ и модель этого провайдера и
  сбрасывает список моделей. Минимум — перенести логику из старого `SettingsViewModel`.
  Изоляция: `FakeAiSettingsStore` + `FakeApiKeyStore` + стаб `fetchModels`.
- **`SettingsViewModel` (ужатый, composeApp)** — оставить только тест на `save(url)`
  → `state.url` обновился и ушёл в стор. Первый падающий тест — тот же кейс после
  удаления AI-полей (компиляция падает на ссылках на AI — это и есть «красный»).
  Минимум — убрать AI-поля/методы.
- **`RepoSettingsStore` (ужатый контракт)** — оставить кейсы url; AI-кейсы удалить
  (они переехали в `AiSettingsStore`-контракт).

Изоляция обеспечивается тем, что каждый VM зависит только от узких интерфейсов
(`AiSettingsStore`, `ApiKeyStore`, `fetchModels`-лямбда, `VaultRepository`), все
подменяемы фейками без поднятия графа.

## 10. Приёмочные тест-кейсы (ручной прогон после разработки)

**1. Включение AI и ответ в чате**
- Изначальное состояние: приложение с подключённым репозиторием, AI выключен, валидный
  API-ключ провайдера под рукой.
- Шаги: Настройки → включить AI → выбрать провайдера → ввести ключ → «Сохранить» (AI) →
  назад → в нижней навигации нажать вкладку AI → отправить сообщение.
- Ожидаемый результат: появляется вкладка AI; чат показывает «думает», затем ответ
  ассистента; упомянутые заметки кликабельны (wikilink).

**2. Смена модели/провайдера пересоздаёт чат**
- Изначальное состояние: AI включён и настроен, в чате есть история сообщений.
- Шаги: Настройки → сменить модель (или провайдера) → назад → открыть вкладку AI.
- Ожидаемый результат: чат открыт с новой моделью/провайдером, история сброшена
  (новый VM), новое сообщение отвечается корректно.

**3. AI включён, но ключ не задан → заглушка**
- Изначальное состояние: AI включён, ключ провайдера пуст.
- Шаги: открыть вкладку AI.
- Ожидаемый результат: экран «AI unavailable» с кнопкой «Открыть настройки»; нажатие
  ведёт в Настройки.

**4. Подтверждение записи в vault**
- Изначальное состояние: AI настроен, открыт чат.
- Шаги: попросить ассистента создать/изменить заметку так, чтобы он вызвал write-tool.
- Ожидаемый результат: показывается диалог подтверждения записи (имя + содержимое);
  «Подтвердить» пишет файл, «Отклонить» — нет.

## 11. Порядок реализации (для плана)

1. Скелет `ai:api` (+ перенос `AiProvider`/`ModelInfo`/`ChatClient`/`ApiKeyStore`,
   новые `AiSettingsStore`/`AiPresentationProvider`), правки `settings.gradle`/
   `composeApp/build.gradle`.
2. Скелет `ai:impl` + перенос движка и его тестов (компиляция + зелёные тесты).
3. `AiSettingsStore` (контракт-тест → реализация SharedPrefs) + расщепление
   `RepoSettingsStore`.
4. `AiSettingsViewModel` (тесты из `SettingsViewModelTest`) + ужатый `SettingsViewModel`.
5. Экраны в `ai:impl` + `AiPresentationProviderImpl` + `AiSettingsSection` + UI-тесты.
6. `aiModule` / `aiPlatformModule`, чистка `AppModule`, `BrainerApp`.
7. Правки `AppNavHost` / `SettingsScreen` / `MainActivity`; прогон всех тестов + приёмка.

## 12. Риски

- **Отдельный prefs-файл без миграции**: старые локальные AI-настройки не читаются
  (осознанно — пользователей нет). Достаточно один раз перевключить AI в настройках.
- **Циклы зависимостей DI**: `aiModule` тянет `HttpClient` и `VaultRepository` из
  общего графа — тот же паттерн, что уже работает для auth/sync, риск низкий.
- **UX двух кнопок «Сохранить»** — открытый вопрос §5, решается на ревью спеки.
