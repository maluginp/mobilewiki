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
(`val <feature>Module`) и подключает его в `BrainerApp.onKoinStartup`.
Внутри — байндинги фичи: репозитории, ViewModel и т.п. **Создание
зависимостей (в т.ч. `createVaultRepository`) живёт только в этом Koin-модуле**,
нигде в приложении напрямую не вызывается.

Кросс-фичевые зависимости передаются через контракты в `:*:api` (напр.
`SyncConfigProvider` в `:sync:api`): основной модуль байндит реализацию,
фича получает её через `get()`, не завися от settings/auth.

## Слои внутри `:impl` (пакеты, не модули)

- `data/` — репозитории, stores, сетевые клиенты (`internal`), фабрики наружу
- `domain/` — модели-логика, UseCase (`internal`)
- `presentation/` — Composable-экраны + ViewModel (провайдеры для UI)
- `di/` — Koin-модуль фичи (`val <feature>Module`)

## Новый модуль — чеклист

1. `feature/api/build.gradle.kts`: `id("obsidian.feature.api")` + `android { namespace = ... }`
2. `feature/impl/build.gradle.kts`: `id("obsidian.feature.impl")` + namespace +
   `implementation(project(":feature:api"))`
3. Koin-модуль в `impl` → `di/<feature>Module.kt`
4. `settings.gradle.kts`: `include(":feature:api", ":feature:impl")`
5. `composeApp`: `api(project(":feature:api"))` + `implementation(project(":feature:impl"))`;
   в `BrainerApp.onKoinStartup` добавить `<feature>Module`

## Заметки по эталону vault

- Пакет api оставлен `app.obsidianmd.vault` — импорты моделей по проекту не менялись.
- Реализация репозитория скрыта за `internal OkioVaultRepository`; создаётся только в `di/vaultModule`.
- `di/vaultModule` байндит `VaultRepository` и `VaultViewModel`; подключён в `BrainerApp`.
- `SyncConfig` (нужен settings + токен) отдаётся в vault через `SyncConfigProvider` из `:sync:api`,
  реализацию байндит `composeApp` — vault не зависит от settings/auth.
- `createVaultRepository(fs, root)` — тест-сид для конструирования репозитория на FakeFileSystem.
