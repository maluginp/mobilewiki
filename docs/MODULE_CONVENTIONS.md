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
| `:composeApp`   | все `:*:impl` (единственное место, где они сходятся) |

Кросс-фичевые вызовы — только через `:B:api`. Проверка правил пока на
конвенциях (без плагина-энфорсера — добавить, если начнут нарушать).

## Слои внутри `:impl` (пакеты, не модули)

- `data/` — репозитории, stores, сетевые клиенты (`internal`), фабрики наружу
- `domain/` — модели-логика, UseCase (`internal`)
- `presentation/` — Composable-экраны + ViewModel (провайдеры для UI)

## Новый модуль — чеклист

1. `feature/api/build.gradle.kts`: `id("obsidian.feature.api")` + `android { namespace = ... }`
2. `feature/impl/build.gradle.kts`: `id("obsidian.feature.impl")` + namespace +
   `implementation(project(":feature:api"))`
3. `settings.gradle.kts`: `include(":feature:api", ":feature:impl")`
4. `composeApp`: `implementation(project(":feature:impl"))`, зарегистрировать DI/навигацию

## Заметки по эталону vault

- Пакет api оставлен `app.obsidianmd.vault` — импорты моделей по проекту не менялись.
- Реализация репозитория скрыта за `internal OkioVaultRepository`; наружу — `createVaultRepository(...)`.
- `presentation` владеет своими ресурсами (`composeResources`, свой `Res`).
- DI (создание репозитория из пути, wiring `VaultViewModel`) остаётся в `composeApp`:
  расположение vault и sync-конфиг — app-level. Зрелый impl может отдавать свой Koin-модуль.
