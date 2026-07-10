# Спека: Онбординг + выбор репозитория из GitHub

Дата: 2026-07-09 · Tracker: none (key-less)
Опирается на: device flow docs/specs/2026-07-07-github-oauth-device-flow.md,
настройки docs/specs/2026-07-07-repo-settings.md, синк docs/specs/2026-07-07-git-sync-engine.md.

## Цель
Сделать понятный первый запуск: объяснить, что делает приложение → предложить войти через
GitHub → удобно провести device-авторизацию (код можно скопировать и вставить в браузере) →
показать список доступных пользователю GitHub-репозиториев и дать выбрать нужный (или ввести URL
вручную). Выбранный репозиторий становится `remoteUrl` для синка. В настройках репозиторий можно
сменить тем же способом: вручную или выбором из GitHub.

## Границы (scope)
**В слайсе:** экран приветствия; переработанный экран device-авторизации с копированием кода;
клиент GitHub «список моих репозиториев»; экран/VM выбора репозитория (с поиском и ручным вводом);
новое условие гейтинга в `MainActivity` (внутрь пускаем только когда есть токен И выбран репо);
кнопка «Выбрать из GitHub» в настройках.
**НЕ в слайсе:** logout/смена аккаунта; пагинация репозиториев (>100); создание нового репо;
проверка прав на запись; онбординг-туры/подсказки поверх основного экрана.

## Эндпоинт GitHub
- `GET https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member`
  с заголовком `Authorization: Bearer <token>`, Accept: `application/vnd.github+json` →
  массив объектов; берём поля `full_name`, `clone_url`, `private`.
- ponytail: одна страница, 100 самых свежих. Потолок назван; пагинацию добавить только если
  у кого-то реально >100 релевантных репо.

## Архитектура и интерфейсы

### 1. Клиент репозиториев (commonMain, `app.obsidianmd.auth`)
```kotlin
@Serializable
data class GitHubRepo(
    @SerialName("full_name") val fullName: String,
    @SerialName("clone_url") val cloneUrl: String,
    val private: Boolean = false,
)

interface RepoList {
    suspend fun list(token: String): List<GitHubRepo>
}

class GitHubRepos(private val http: HttpClient) : RepoList {
    override suspend fun list(token: String): List<GitHubRepo>  // GET /user/repos
}
```
`HttpClient` инжектится извне (в тестах — Ktor `MockEngine`), поэтому HTTP подменяется без сети.
Тот же клиент, что уже создаётся в `MainActivity`. Сетевую ошибку `list` пробрасывает исключением —
её ловит VM.

### 2. RepoPickerViewModel (commonMain, `app.obsidianmd.auth`)
```kotlin
sealed interface RepoPickerState {
    data object Loading : RepoPickerState
    data class Loaded(val repos: List<GitHubRepo>) : RepoPickerState
    data class Error(val reason: String) : RepoPickerState
}
class RepoPickerViewModel(
    private val repos: RepoList,
    private val token: () -> String?,
    private val onPick: (String) -> Unit,   // сохранить cloneUrl как remoteUrl
    private val scope: CoroutineScope,
) {
    val state: StateFlow<RepoPickerState>
    fun load()                       // Loading → Loaded | Error
    fun pick(cloneUrl: String)       // вызывает onPick(cloneUrl)
}
```
Фильтрация списка по строке поиска — чистая функция `filterRepos(repos, query): List<GitHubRepo>`
(подстрока в `fullName`, без регистра), тестируется отдельно.

### 3. UI (commonMain, `app.obsidianmd.ui`)
- **WelcomeScreen** — что делает приложение (читай/редактируй Obsidian-волт из GitHub-репозитория,
  синхронизация по git) + кнопка «Войти через GitHub» (→ `AuthViewModel.login()`). Новые строки
  `onboarding_*`.
- **LoginScreen (переработка `AwaitingUser`)** — `userCode` крупно/моноширинно в карточке; кнопка
  «Скопировать код» (Compose `LocalClipboardManager`, без новых зависимостей) с подтверждением
  «Скопировано»; кнопка «Открыть GitHub» (→ `onOpenUrl(verificationUri)`); ниже прогресс
  «Ожидаем подтверждения…». Логика auth не меняется.
- **RepoPickerScreen(state, query, onSearch, onPick, manualUrl, onManualChange, onManualSave)** —
  поле поиска, список репозиториев (`full_name`, значок замка для private), тап → `onPick(cloneUrl)`;
  секция «Ввести вручную» с полем URL и кнопкой сохранить; на `Error` — текст + повтор.

### 4. Проводка (androidMain, `MainActivity`)
Гейтинг переписываем в три состояния (реактивно от токена и `remoteUrl`):
1. токена нет → `WelcomeScreen` / `LoginScreen` (текущий device flow);
2. токен есть, `remoteUrl` пуст → `RepoPickerScreen` (собрать `RepoPickerViewModel` с `GitHubRepos`,
   `token = store::get`, `onPick = settingsVm::save`);
3. токен есть, `remoteUrl` задан → основной `App` (без изменений).
`remoteUrl` читаем из `settingsVm.url` (уже `StateFlow`), так что после выбора репо экран сам
переключится на волт.

### 5. Настройки (`SettingsScreen`)
Рядом с полем ручного ввода URL — кнопка «Выбрать из GitHub», открывающая `RepoPickerScreen`
(тот же composable/VM). Выбор пишет URL в то же поле (через `settingsVm.save`). Ручной ввод — как есть.

## Тестирование (TDD)

### commonTest (Ktor MockEngine + runTest)
1. **GitHubRepos.list — парсинг.** *Первый падающий тест:* MockEngine на `/user/repos` отдаёт JSON
   с двумя репо → `list(token)` возвращает 2 `GitHubRepo` с верными `fullName`/`cloneUrl`/`private`.
   Падает — класса нет. Минимальный код: `GitHubRepos` с одним GET и десериализацией.
2. **GitHubRepos.list — пустой ответ.** `[]` → пустой список.
3. **GitHubRepos.list — HTTP-ошибка.** MockEngine отдаёт 401 → `list` бросает исключение.
4. **filterRepos.** Список из 3 репо, запрос-подстрока (разный регистр) → остаются совпадения;
   пустой запрос → весь список.
5. **RepoPickerViewModel.load.** Фейковый `RepoList` возвращает 2 репо → `state`: Loading→Loaded(2).
6. **RepoPickerViewModel.load — ошибка.** Фейк бросает → `state`: Loading→Error(reason).
7. **RepoPickerViewModel.pick.** `pick("https://…git")` → вызван `onPick` c этим cloneUrl.

### Ручная приёмка (Android — UI и EncryptedSharedPreferences не юнит-тестятся)
См. приёмочные кейсы.

## Приёмочные тест-кейсы (ручные, после разработки)

1. **Первый запуск: приветствие → вход → выбор репо (happy path)**
   - Изначальное состояние: свежая установка; токена нет; `remoteUrl` не задан.
   - Шаги: открыть приложение → виден экран приветствия с объяснением и кнопкой «Войти через
     GitHub»; нажать её → появляется код; нажать «Скопировать код» → «Открыть GitHub», вставить код,
     подтвердить доступ; вернуться в приложение → показан список репозиториев; выбрать нужный.
   - Ожидаемый результат: выбранный репозиторий сохранён как remoteUrl; приложение переходит к
     волту и синхронизирует его.

2. **Копирование кода**
   - Изначальное состояние: экран device-авторизации с показанным кодом.
   - Шаги: нажать «Скопировать код»; переключиться в браузер и вставить из буфера.
   - Ожидаемый результат: вставляется ровно показанный код; в приложении видно подтверждение
     «Скопировано».

3. **Ручной ввод репозитория в онбординге**
   - Изначальное состояние: вход выполнен; `remoteUrl` не задан; открыт экран выбора репо.
   - Шаги: в секции «Ввести вручную» ввести URL репозитория и сохранить.
   - Ожидаемый результат: URL сохранён как remoteUrl; переход к волту.

4. **Поиск по списку репозиториев**
   - Изначальное состояние: экран выбора репо со списком.
   - Шаги: ввести часть имени репозитория в поле поиска.
   - Ожидаемый результат: список сужается до совпадений по имени.

5. **Смена репозитория из настроек (выбор из GitHub)**
   - Изначальное состояние: приложение работает с уже выбранным репо; открыты «Настройки».
   - Шаги: нажать «Выбрать из GitHub» → выбрать другой репозиторий.
   - Ожидаемый результат: поле URL обновилось на новый repo; после «Синхронизировать» волт
     переключается на новый репозиторий.

6. **Ошибка загрузки списка репозиториев**
   - Изначальное состояние: токен есть, но запрос к GitHub падает (например, нет сети).
   - Шаги: открыть экран выбора репо.
   - Ожидаемый результат: показана ошибка с возможностью повторить; приложение не падает; остаётся
     доступным ручной ввод URL.
