# Спека: экран настроек репозитория — слайс 5

Дата: 2026-07-07 · Tracker: none (key-less)
Опирается на: движок/UI синка и OAuth-слайсы (docs/specs/2026-07-07-*).

## Цель
Дать задавать URL git-репозитория прямо в приложении и хранить его, убрав временный
`BuildConfig.SYNC_REMOTE_URL`. Синк использует сохранённый URL + токен из `TokenStore`.

## Границы (scope)
**В слайсе:** `RepoSettingsStore` (+ Android SharedPreferences реализация), `SettingsViewModel`,
`SettingsScreen`, навигация к настройкам, переход `VaultViewModel` на `syncConfigProvider`,
удаление `BuildConfig.SYNC_REMOTE_URL`.
**НЕ в слайсе:** настройка ветки (остаётся дефолт `main`), валидация формата URL, автосинк,
редактирование/поиск.

## Проектные решения
- URL репозитория — не секрет → обычный `SharedPreferences` (токен по-прежнему в
  `EncryptedSharedPreferences`).
- Чтобы правка URL действовала без перезапуска, `VaultViewModel` получает
  `syncConfigProvider: () -> SyncConfig?` вместо статического `SyncConfig?`; `sync()` вызывает
  провайдер при каждом синке.
- Навигация — простое Compose-состояние (`showSettings`) в `App`, без nav-библиотеки (YAGNI).

## Архитектура и интерфейсы

### RepoSettingsStore (commonMain, `app.obsidianmd.settings`)
```kotlin
interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
}
```
- Android: `SharedPrefsRepoSettingsStore(context)` на обычном `SharedPreferences`.
- Тесты: `FakeRepoSettingsStore` (in-memory).

### SettingsViewModel (commonMain, `app.obsidianmd.settings`)
```kotlin
class SettingsViewModel(private val store: RepoSettingsStore) {
    val url: StateFlow<String>            // инициализируется store.getRemoteUrl() ?: ""
    fun save(url: String)                 // store.setRemoteUrl(url); обновить StateFlow
}
```

### SettingsScreen (commonMain, `app.obsidianmd.ui`)
`SettingsScreen(currentUrl, onSave, onBack)` — поле ввода URL (prefill), «Сохранить»
(→ `onSave(text)`), «Назад» (→ `onBack`).

### VaultViewModel (изменение)
Заменить параметр `syncConfig: SyncConfig? = null` на
`syncConfigProvider: () -> SyncConfig? = { null }`. В `sync()`:
```kotlin
val cfg = syncConfigProvider()
if (cfg == null) { _syncStatus.value = Done(Failed("репозиторий не настроен")); return }
```
Остальная логика (Running → engine.sync → refresh → Done) не меняется.

### App / навигация
- `VaultListScreen` получает `onOpenSettings: () -> Unit`, добавляется кнопка «Настройки».
- `App` держит `var showSettings by remember`; при `showSettings` рендерит `SettingsScreen`
  (сборка `SettingsViewModel`), иначе — текущий поток.

### Android-проводка (MainActivity)
- Создать `SharedPrefsRepoSettingsStore`.
- `syncConfigProvider = { settingsStore.getRemoteUrl()?.takeIf { it.isNotBlank() }?.let {
  SyncConfig(remoteUrl = it, localPath = root.toString(), token = tokenStore.get()) } }`.
- `build.gradle.kts`: удалить `buildConfigField SYNC_REMOTE_URL` (остаётся `GITHUB_CLIENT_ID`).

## Тестирование (TDD)

### commonTest (`runTest` где нужно)
1. **RepoSettingsStore (FakeRepoSettingsStore) — контракт.**
   *Первый падающий тест:* пусто → `getRemoteUrl() == null`; после `setRemoteUrl("https://x")`
   → `getRemoteUrl() == "https://x"`. Падает — интерфейса нет.
2. **SettingsViewModel.**
   - начальный `url` == сохранённый в store (или "" если пусто);
   - `save("https://y")` → `store.getRemoteUrl() == "https://y"` и `url.value == "https://y"`.
3. **VaultViewModel (обновлённый провайдер).**
   - `syncConfigProvider = { null }` → `sync()` → `Done(Failed(...))`, движок не вызван;
   - `syncConfigProvider = { cfg }` (фейк движок) → движок вызван, `Done(Synced)`.
   (правка существующих sync-тестов: `syncConfig = X` → `syncConfigProvider = { X }`.)

### Ручная приёмка (Compose/SharedPreferences — не юнит-тестятся)
См. кейсы ниже.

## Приёмочные тест-кейсы (ручные, после разработки)
1. **Задать URL и синкнуть**
   - Изначальное состояние: вход выполнен; URL репозитория не задан.
   - Шаги: открыть «Настройки»; ввести URL приватного репо; «Сохранить»; «Назад»;
     нажать «Синхронизировать».
   - Ожидаемый результат: синк использует введённый URL (clone/список наполняется), ошибки
     «не настроен» нет.
2. **Пустой URL → «не настроен»**
   - Изначальное состояние: URL не задан (или очищен).
   - Шаги: нажать «Синхронизировать».
   - Ожидаемый результат: статус «репозиторий не настроен», приложение не падает.
3. **URL сохраняется между запусками**
   - Изначальное состояние: URL задан ранее.
   - Шаги: закрыть и открыть приложение; открыть «Настройки».
   - Ожидаемый результат: поле показывает ранее сохранённый URL; синк работает без повторного
     ввода.
