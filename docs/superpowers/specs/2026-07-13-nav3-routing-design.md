# Navigation 3 — маршрутная навигация

Дата: 2026-07-13

## Проблема

Навигация размазана по двум слоям, оба на булевых флагах:

1. **`Gate`** (`composeApp/androidMain/MainActivity.kt`): `loggedIn` / `hasRepo` /
   `changingRepo` + вложенный `enum RepoStep { List, Manual, Validate }`.
   Онбординг: Welcome → Login → RepoPicker → Manual/Validate → основной экран.
2. **`App`** (`composeApp/commonMain/App.kt`): `showSettings`, `showModelPicker`,
   `showAi`, `noteFromAi`, `state.selected`, `editing`, `state.atRoot` плюс
   переплетённые `back` / `handleBack` и ручной `PlatformBackHandler`.

Следствия: невозможно выразить историю (папки, «заметка из AI-чата» лечатся
флагами вроде `noteFromAi`), логика «назад» дублируется и хрупкая, добавление
экрана трогает несколько несвязанных `when`.

## Решение

Одна маршрутная навигация на **Compose Multiplatform Navigation 3** (KMP), хост
в `commonMain`. Обе поверхности (онбординг + основное приложение) — один бэкстек.

### Зависимости и тулчейн

- `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1` (stable; runtime
  подтягивается транзитивно).
- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` — VM,
  привязанные к записи бэкстека (по необходимости).
- **Требует Compose Multiplatform ≥ 1.10.** Сейчас проект на 1.7.0 → апгрейд до
  1.10.x. Это тянет за собой Kotlin `2.0.21 → ≥ 2.1` и compose-compiler.
- Точные совместимые версии (Compose MP / Kotlin / compose-compiler / AGP)
  пиннятся на **первом шаге плана как спайк** — собрать проект на новом тулчейне
  до написания навигации. Это главный риск задачи; чинится отдельно от Nav3.

### Маршруты

Единая `@Serializable`-иерархия в `commonMain`, каждый маршрут реализует `NavKey`:

```kotlin
@Serializable sealed interface Route : NavKey {
    @Serializable data object Welcome : Route
    @Serializable data object Login : Route
    @Serializable data object RepoPicker : Route
    @Serializable data object RepoManualUrl : Route
    @Serializable data class  RepoValidate(val url: String) : Route
    @Serializable data class  VaultList(val dir: String = "") : Route
    @Serializable data class  Note(val path: String) : Route
    @Serializable data object Settings : Route
    @Serializable data object ModelPicker : Route
    @Serializable data object AiChat : Route
}
```

Параметры (папка, путь заметки, URL репо) живут **в маршруте**, а не в
`remember { mutableStateOf }`.

### Хост

Один `NavDisplay` + `rememberNavBackStack(config, <startRoute>)` в `commonMain`
заменяет и `Gate`, и булеву кашу в `App`. Для non-JVM/web таргетов Nav3 требует
полиморфной сериализации `NavKey` — настраивается в `SavedStateConfiguration`
(см. kotlinlang доку). Проект пока Android-only, но конфиг делаем сразу
корректным, раз навигация в `commonMain`.

Что растворяется:

| Сейчас | Nav3 |
|---|---|
| `loggedIn` / `hasRepo` / `changingRepo` / `RepoStep` | маршруты в стеке |
| `showSettings` / `showModelPicker` / `showAi` | `navigate(Settings)` и т.п. |
| `noteFromAi` + особый back | пуш `AiChat → Note`; back возвращается сам |
| `state.atRoot` / `upFolder` | каждая папка — пуш `VaultList(dir)` |
| `back` / `handleBack` / `PlatformBackHandler` | back держит Nav3 / NavDisplay |

### Что НЕ меняется

- Все экранные composable в `commonMain/ui` (`MarkdownScreen`, `SettingsScreen`,
  `AiChatScreen`, `ModelPickerScreen`, `RepoPickerScreen`, `ManualUrlScreen`,
  `RepoValidationScreen`, `WelcomeScreen`, `LoginScreen`, VaultList из
  `vault:impl`) — берут параметры/колбэки, Nav3 просто их вызывает.
- ViewModel'и и их состояние. Меняется только оркестрация.
- Режим правки + защита от потери правок — остаются **локальным** `boolean`
  внутри экрана `Note` (это режим, не пункт назначения). Диалог `showUnsaved`
  сохраняется.

### Особые случаи

- **Онбординг-гейт.** Стартовый бэкстек вычисляется на старте из `TokenStore`
  и настроек репо (как сейчас в `MainActivity`, off-main-thread):
  - нет токена → `[Welcome]`
  - токен, нет репо → `[VaultList, RepoPicker]` (или стек так, чтобы «назад»
    вёл куда нужно; при первом выборе репо выхода нет — стек без предыдущего)
  - токен + репо → `[VaultList()]`
  Логаут / смена репо из настроек → правка бэкстека (сброс к нужному корню),
  а не флаг `changingRepo`.
- **Нижняя навигация Brain ↔ AI.** Переключение — замена верхнего корневого
  маршрута (`VaultList` ↔ `AiChat`), не пуш. Появляется только при `aiEnabled`
  и скрыта при открытой клавиатуре — поведение сохраняем.
- **Поиск** в списке и в пикере моделей — локальное состояние экрана/тулбара,
  не маршрут.
- **AI недоступен** (`aiVm == null`) при попытке открыть чат — экран-заглушка
  с переходом в `Settings` (как сейчас).
- **Системная «назад»** — целиком на Nav3/NavDisplay; ручной
  `PlatformBackHandler` для навигации убирается (может остаться для не-нав
  случаев, если такие есть — проверить при реализации).

### TopAppBar

Заголовок и `navigationIcon`/`actions` сейчас вычисляются из флагов. Станут
функцией текущего верхнего маршрута (`when (backStack.last())`), либо каждый
экран сам объявляет свой AppBar. Выбор конкретного подхода — на этапе плана;
предпочтение: заголовок и действия по текущему маршруту в хосте, чтобы не
дублировать Scaffold в каждом экране.

## Тестирование

- Существующие Robolectric Compose-тесты экранов (`AiChatScreenTest`,
  `SettingsScreenTest`, `ModelPickerScreenTest`, `LoginScreenTest`) не должны
  сломаться — экраны не меняются.
- Навигационную логику (вычисление стартового стека из auth/repo, переходы
  logout/смена репо) вынести в тестируемую чистую функцию/редьюсер, покрыть
  unit-тестом в `commonTest`. Nav-хост как таковой (NavDisplay) не тестируем —
  тестируем решение «какой стек».

## Не в объёме (YAGNI)

- iOS/desktop/web таргеты — конфиг сериализации делаем корректным, но таргеты
  не добавляем.
- Адаптивные layouts (list-detail) из `adaptive-navigation3` — не сейчас.
- Диплинки.

## Риски

1. **Апгрейд Compose MP 1.7 → 1.10 + Kotlin** — главный. Изолируется в спайк
   первым шагом; Nav3 пишется только после зелёной сборки.
2. Nav3 KMP относительно новый — если всплывёт блокер на 1.1.1, фолбэк —
   `org.jetbrains.androidx.navigation:navigation-compose` 2.9.x (Nav2-модель,
   те же типобезопасные маршруты, тот же commonMain), но это уже не Nav3.
