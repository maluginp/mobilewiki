# Онбординг как самодостаточный feature-модуль + вынос credential-store

**Дата:** 2026-07-14
**Статус:** дизайн на ревью

## Цель

Закрыть весь флоу онбординга (welcome/вход → выбор репозитория → ручной URL →
валидация) внутри одного feature-модуля с единственной точкой входа, вместо того
чтобы размазывать маршруты и переходы по `composeApp`. Заодно переименовать
`:features:auth` → `:features:onboarding`, а долгоживущий credential-store
(`TokenStore`/`EncryptedTokenStore`) вынести в отдельный core-модуль, т.к. он
используется не флоу онбординга, а фоновым синком и стартом приложения.

## Исходное состояние

Онбординг сейчас разложен между двумя местами:

- **`composeApp/nav`** держит маршруты `Route.Login`, `Route.RepoPicker`,
  `Route.RepoManualUrl`, `Route.RepoValidate`, все переходы между ними
  (в `AppNavHost.entryProvider`), контейнер `OnboardingContainer`
  (`safeDrawingPadding`), решение стартового стека (`StartStack.kt`) и запись
  выбранного URL в `RepoSettingsStore`.
- **`:features:auth`** отдаёт 4 экрана через `AuthPresentationProvider`
  (`Login`/`RepoPicker`/`ManualUrl`/`RepoValidate`) и содержит `TokenStore`
  (api) + `EncryptedTokenStore` (impl, `public`).

`TokenStore`/`EncryptedTokenStore` потребляются вне онбординга: `SyncWorker`
(создаёт `EncryptedTokenStore` напрямую вне Koin), `MainActivity` (считает
`hasToken` для стартового стека), `AppModule`/`SyncConfigProvider` (токен для
синка). То есть это app-wide credential-store, а не шаг флоу.

## Архитектурное решение

Онбординг становится **чёрным ящиком с одним входом и вложенным бэкстеком**.
Единый бэкстек хоста остаётся источником правды для основного приложения, но для
онбординга это не нужно (там нет list-detail и нижней навигации) — он линейный и
имеет чёткую границу «начал → закончил».

### Целевая структура модулей

```
core/auth/                     TokenStore (commonMain) + EncryptedTokenStore (androidMain, public)
                               + expect/actual authPlatformModule. Пакет app.obsidianmd.auth.
features/onboarding/api/       OnboardingPresentationProvider + enum OnboardingStart
features/onboarding/impl/      вложенный NavDisplay флоу онбординга + VM/экраны (internal)
```

#### `:core:auth` (новый)

По образцу `core/analytics` — **один модуль без api/impl split**.

- `commonMain`: `interface TokenStore` (переезжает из `auth/api`, без изменений).
- `androidMain`: `class EncryptedTokenStore(context) : TokenStore` (переезжает из
  `auth/impl`, остаётся `public` — создаётся `SyncWorker`'ом вне Koin-графа).
- DI: `expect val authPlatformModule: Module`; `actual` (androidMain) биндит
  `single<TokenStore> { EncryptedTokenStore(androidContext()) }` (переезжает из
  `AuthModule.android.kt`).
- **Пакет сохраняется `app.obsidianmd.auth`** — импорты `TokenStore`/
  `EncryptedTokenStore` в `SyncWorker`/`MainActivity`/`AppModule`/
  `SyncConfigProvider` не меняются вообще.

#### `:features:onboarding:api` (переименован из `auth/api`, минус TokenStore)

Пакет `app.obsidianmd.onboarding`. Один контракт вместо четырёхметодного:

```kotlin
interface OnboardingPresentationProvider {
    /** Весь флоу онбординга целиком; onFinished — когда пользователь онбординг завершил. */
    @Composable fun Onboarding(startAt: OnboardingStart, onFinished: () -> Unit)
}

/** С какого шага стартовать. RepoPicker — для «сменить репо из настроек». */
enum class OnboardingStart { Login, RepoPicker }
```

#### `:features:onboarding:impl` (переименован из `auth/impl`)

- Свой **вложенный `NavDisplay` + `rememberNavBackStack`**: internal sealed
  `Step` (`Login`/`RepoPicker`/`ManualUrl`/`Validate(url)`), свой `entryProvider`,
  свой `OnboardingContainer` (`safeDrawingPadding`) — всё переезжает из
  `AppNavHost`.
- Переходы внутри флоу (сейчас в хосте) переезжают внутрь:
  - `Login.onSignedIn` → если сохранённый репо уже есть (читает
    `RepoSettingsStore`) → `onFinished()`, иначе → шаг `RepoPicker`.
  - `RepoPicker.onChosen(url)` → шаг `Validate(url)`; `onEnterManually` → `ManualUrl`;
    `onBack` (если во вложенном стеке есть куда) → pop.
  - `ManualUrl.onSubmit(url)` → `Validate(url)`.
  - `Validate.onContinue` → пишет URL через `RepoSettingsStore.setRemoteUrl(url)`,
    затем `onFinished()`.
- Зависимости impl: `:core:auth` (`TokenStore`), `:features:settings:api`
  (`RepoSettingsStore`). Обе допустимы (core + чужой `*:api`).
- Internal-классы (`AuthViewModel`, `AuthState`, `RepoPickerViewModel`,
  `RepoValidationViewModel`, экраны) **меняют пакет на `app.obsidianmd.onboarding`,
  имена сохраняют** — они internal, переименование дало бы только churn.
- Публично меняется: `AuthPresentationProvider` → `OnboardingPresentationProvider`
  (+ `Impl`), `authModule(clientId)` → `onboardingModule(clientId)`.

### Изменения в `composeApp`

- **`nav/Route.kt`**: убрать `Login`/`RepoPicker`/`RepoManualUrl`/`RepoValidate`,
  добавить `@Serializable data class Onboarding(val startAt: OnboardingStart) : Route`.
  В `navSerializersModule` — одна подписка вместо четырёх. `OnboardingStart`
  сериализуется как enum-параметр маршрута.
- **`nav/StartStack.kt`**:
  - `startStack(hasToken, hasRepo)` → `[VaultList()]` если оба true, иначе
    `[Onboarding(if (!hasToken) OnboardingStart.Login else OnboardingStart.RepoPicker)]`.
  - `stackForChangeRepo()` → `[VaultList(), Onboarding(OnboardingStart.RepoPicker)]`.
  - `stackAfterRepoChosen()` — удаляется (модуль сам финиширует).
- **`nav/AppNavHost.kt`**: 4 entry + `OnboardingContainer` + `isOnboarding` +
  логика `resetTo(startStack(...))` после логина → **один** entry:
  ```kotlin
  entry<Route.Onboarding> { key ->
      onboarding.Onboarding(startAt = key.startAt, onFinished = {
          backStack.resetTo(listOf(Route.VaultList())); vm.sync()
      })
  }
  ```
  Хост перестаёт знать про Login/RepoPicker/URL/валидацию. `koinInject<AuthPresentationProvider>()`
  → `koinInject<OnboardingPresentationProvider>()`.
- **`BrainerApp`**: `authModule(GITHUB_CLIENT_ID)` → `authPlatformModule` +
  `onboardingModule(GITHUB_CLIENT_ID)`.
- **`settings.gradle.kts`**: `:features:auth:api`/`:impl` → `:core:auth` +
  `:features:onboarding:api`/`:impl`.
- **`composeApp/build.gradle.kts`**: соответствующие `api(...)`/`implementation(...)`.
- **Без изменений** (пакет `app.obsidianmd.auth` сохранён): `MainActivity`,
  `AppModule`, `SyncWorker`, `SyncConfigProvider`.

### Поток данных

```
MainActivity (IO): TokenStore.get() + RepoSettingsStore.getRemoteUrl()
   → startStack(hasToken, hasRepo)
       ├─ оба есть        → [VaultList()]
       └─ иначе            → [Onboarding(startAt)]
AppNavHost.entry<Onboarding> → OnboardingPresentationProvider.Onboarding(startAt, onFinished)
   (внутри: вложенный бэкстек, свои переходы, запись URL в RepoSettingsStore)
   onFinished → backStack.resetTo([VaultList()]) + vm.sync()
Настройки «сменить репо» → resetTo([VaultList(), Onboarding(RepoPicker)])
```

### Обработка ошибок / краевые случаи

- **Смена репо из настроек**: `Onboarding(RepoPicker)` лежит поверх `VaultList` в
  бэкстеке хоста; `onBack` на корне вложенного стека всплывает наверх (хост
  снимает `Onboarding`, возвращая `VaultList`). `onFinished` (после валидации) →
  `resetTo([VaultList()])`.
- **Повторный вход при уже выбранном репо**: развилка `Login.onSignedIn` при
  наличии репо сразу зовёт `onFinished` — RepoPicker не показывается.
- Восстановление состояния вложенного бэкстека — стандартный `rememberNavBackStack`
  внутри модуля (переживает поворот/пересоздание composition).

## Тестирование (TDD)

Рефактор в основном перемещает границы, логику почти не меняет. Главный
регресс-контроль — существующие тесты переезжают вместе с классами и продолжают
проходить: `AuthViewModelTest`, `RepoPickerViewModelTest`,
`RepoValidationViewModelTest`, `GitHubDeviceAuthTest`, `FilterReposTest`,
`GitHubReposTest`, `RepoAccessTest` (→ пакет `app.obsidianmd.onboarding`);
`TokenStoreContractTest` + `FakeTokenStore` (→ `:core:auth`, пакет
`app.obsidianmd.auth`); `LoginScreenTest` (→ `onboarding`).

Единственная **новая логика с поведением** — развилка старта/финиша, которую
раньше делал хост через `startStack`, а теперь делает модуль. Её нужно вынести в
чистую, тестируемую без UI единицу — `internal` функцию/маленький класс
(рабочее имя `OnboardingSteps`), которая по `(startAt, hasRepo, событие)`
возвращает «следующий шаг или finished».

- **Что тестируем**: маршрутизацию шагов онбординга без Compose.
- **Первый падающий тест**: `startAt=Login`, после `onSignedIn`, при `hasRepo=true`
  → результат `Finished` (а не шаг `RepoPicker`). Пока такой единицы нет — тест не
  компилируется/падает.
- **Минимальный код, чтобы прошёл**: функция с ветками —
  `Login + signedIn + hasRepo → Finished`;
  `Login + signedIn + !hasRepo → RepoPicker`;
  `RepoPicker + chosen(url) → Validate(url)`;
  `RepoPicker + enterManually → ManualUrl`;
  `ManualUrl + submit(url) → Validate(url)`;
  `Validate + continue → Finished`.
- **Изоляция**: единица принимает `hasRepo: Boolean` (или геттер репо) и событие,
  возвращает шаг — не зависит от Compose/навигации; `Impl` только рендерит шаги и
  дергает эту логику + сайд-эффекты (запись URL, `onFinished`).

Дополнительно — тест провайдера/вложенной навигации (Robolectric, в `:impl`, как
`LoginScreenTest`): проверить, что `Onboarding(startAt=RepoPicker)` показывает
экран выбора репо первым.

## Приёмочные тест-кейсы (ручной прогон после разработки)

### 1. Первый запуск — полный онбординг
- **Изначальное состояние**: приложение только установлено, вход не выполнялся, репо не выбрано.
- **Шаги**: открыть приложение → на экране входа авторизоваться в GitHub (device-code) → на списке репозиториев выбрать репо → дождаться валидации.
- **Ожидаемый результат**: попадаем в список заметок выбранного репозитория; системная кнопка «назад» не возвращает в онбординг.

### 2. Смена репозитория из настроек
- **Изначальное состояние**: вход выполнен, репо выбрано, открыт список заметок.
- **Шаги**: открыть Настройки → «Выбрать из GitHub» → выбрать другой репозиторий → дождаться валидации.
- **Ожидаемый результат**: возврат в список заметок уже нового репозитория; «назад» с экрана выбора (до выбора) возвращает в список заметок.

### 3. Повторный запуск залогиненного пользователя
- **Изначальное состояние**: онбординг ранее пройден (есть токен и репо).
- **Шаги**: полностью закрыть и снова открыть приложение.
- **Ожидаемый результат**: сразу открывается список заметок, экраны онбординга не показываются.

### 4. Ручной ввод URL репозитория
- **Изначальное состояние**: вход выполнен, репо не выбрано (экран выбора репозитория).
- **Шаги**: нажать «ввести вручную» → ввести валидный URL репозитория → дождаться валидации.
- **Ожидаемый результат**: попадаем в список заметок этого репозитория.

## YAGNI / за скоупом

- Не переименовываем internal-классы онбординга (`AuthViewModel` и т.п.) — только
  пакет и публичный контракт.
- Не трогаем пакет `app.obsidianmd.auth` у credential-store — сохранение имени
  обнуляет churn у потребителей токена.
- `:core:auth` — один модуль без api/impl (одна конвенция с `core/analytics`);
  разбивать на api/impl незачем — один интерфейс + одна платформенная реализация.
