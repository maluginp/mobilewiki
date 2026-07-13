# Модульная структура

Фичи разбиты на `:feature:api` (контракты) и `:feature:impl` (реализация).
Эталон — `:vault`.

## Модули

```
build-logic/                 convention-плагины (obsidian.feature.api / .impl)
core/analytics/              общий core-модуль (expect/actual Analytics)
core/translations/           все строковые ресурсы (единый generated Res)
sync/api/                    контракты синка + UiConflictResolver
vault/api/                   контракты vault: модели + interface VaultRepository
vault/impl/                  data / domain / presentation
composeApp/                  агрегатор: DI, навигация, зависит от всех :impl
```

## Строки/переводы

Все строки — в `:core:translations` (никаких `composeResources` в фичах).
Пакет generated-класса оставлен `app.obsidianmd.resources`, поэтому импорты
`app.obsidianmd.resources.Res` / `.<string>` едины по проекту. Модуль отдаёт
ресурсы через `api(compose.components.resources)`; любой модуль с UI просто
подключает `implementation(project(":core:translations"))`.
Новую строку добавляй в `core/translations/.../values/strings.xml`.

## Правила зависимостей

| Модуль        | Может зависеть от                                    |
|---------------|------------------------------------------------------|
| `:feature:api`  | другие `:*:api`, `:core:*`                          |
| `:feature:impl` | свой `:*:api`, чужие `:*:api`, `:core:*` — **не** чужой `impl` |
| `:composeApp`   | api-модули через `api(...)`, impl-модули через `implementation(...)` |

Кросс-фичевые вызовы — только через `:B:api`. Проверка правил пока на
конвенциях (без плагина-энфорсера — добавить, если начнут нарушать).

## DI

Каждый `:feature:impl` объявляет свой Koin-модуль в пакете `di/`
(`val <feature>Module` в **commonMain**) и подключает его в `BrainerApp.onKoinStartup`.
Внутри — байндинги фичи: репозитории, ViewModel и т.п. **Создание
зависимостей (в т.ч. `createVaultRepository`) живёт только в этом Koin-модуле**,
нигде в приложении напрямую не вызывается.

Платформенные байндинги (нужен `Context`, `Dispatchers.IO` и т.п. — их нет в
commonMain) выносятся в `expect val <feature>PlatformModule: Module` с `actual`
на платформе; общий модуль подключает их через `includes(...)`.

Кросс-фичевые зависимости передаются через контракты в `:*:api` (напр.
`SyncConfigProvider` в `:sync:api`): основной модуль байндит реализацию,
фича получает её через `get()`, не завися от settings/auth.

## Инкапсуляция `:impl`

**Все классы в `:impl` — `internal`.** Наружу торчит только Koin-модуль фичи
(`val <feature>Module`, public) — его подключает основной модуль. Экраны отдаются
через `{Feature}PresentationProvider` (интерфейс в `api` с `@Composable`-методом),
реализация — internal, собирается в Koin-модуле. Основной модуль зовёт провайдер
через `koinInject()`, не зная о конкретных экранах.

## Слои внутри `:impl` (пакеты, не модули)

- `data/` — репозитории, stores, сетевые клиенты (`internal`)
- `domain/` — модели-логика, UseCase (`internal`)
- `presentation/` — Composable-экраны (`internal`) + `{Feature}PresentationProviderImpl`
- `di/` — Koin-модуль фичи (`val <feature>Module` в commonMain + `expect/actual <feature>PlatformModule`)

VM уровня оболочки (навигация между экранами фич, просмотр, синк) живёт в основном
модуле, а не в фиче: фича отдаёт данные (репозиторий через `api`) и экраны (провайдер).

## Новый модуль — чеклист

1. `feature/api/build.gradle.kts`: `id("obsidian.feature.api")` + `android { namespace = ... }`
2. `feature/impl/build.gradle.kts`: `id("obsidian.feature.impl")` + namespace +
   `implementation(project(":feature:api"))`
3. Koin-модуль в `impl` → `di/<feature>Module.kt` (commonMain) + `di/<feature>Module.<platform>.kt` (actual)
4. `settings.gradle.kts`: `include(":feature:api", ":feature:impl")`
5. `composeApp`: `api(project(":feature:api"))` + `implementation(project(":feature:impl"))`;
   в `BrainerApp.onKoinStartup` добавить `<feature>Module`

## Экраны фичи

Экран отдаётся через интерфейс в `api`:

```kotlin
interface VaultPresentationProvider {
    @Composable fun ListScreen(/* api-типы + колбэки навигации */)
}
```

`api`-модуль с провайдером применяет compose-плагины (`org.jetbrains.compose` +
`kotlin.plugin.compose`) и зависит от `compose.runtime/ui/material3`. Реализация
(`internal VaultPresentationProviderImpl`) рендерит internal-экран и биндится в
`vaultModule`. UI-тесты экрана (Robolectric) живут в `:impl`.

## Заметки по эталону vault

- Пакет api оставлен `app.obsidianmd.vault` — импорты моделей по проекту не менялись.
- Все классы `:vault:impl` — `internal`; наружу: `vaultModule` (Koin) + `VaultPresentationProvider` (экран).
- `vaultPlatformModule` (androidMain) — единственное место создания `VaultRepository`.
- `VaultViewModel` — оболочка заметок (просмотр/поиск/синк) — живёт в `composeApp`, поверх
  `VaultRepository` (api) и `SyncConfigProvider` из `:sync:api` (реализацию байндит composeApp,
  vault не зависит от settings/auth).
- Тесты: репозиторий и экран — в `:vault:impl` (видят `internal`); VM и AI — в `composeApp`
  через `FakeVaultRepository` (in-memory реализация `VaultRepository` для тестов).
