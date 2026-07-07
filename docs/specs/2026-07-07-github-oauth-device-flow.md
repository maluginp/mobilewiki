# Спека: GitHub OAuth Device Flow — слайс 4

Дата: 2026-07-07 · Tracker: none (key-less)
Опирается на: движок docs/specs/2026-07-07-git-sync-engine.md и UI docs/specs/2026-07-07-sync-ui.md.

## Цель
Получить GitHub access token без ручного PAT: запросить device/user-код, показать код и
ссылку пользователю, опрашивать GitHub до выдачи токена, сохранить токен безопасно и
использовать его в движке синка (`SyncConfig.token`). Заменяет временный
`BuildConfig.SYNC_TOKEN`.

## Границы (scope) — вся вертикаль
**В слайсе:** логика device flow (Ktor), безопасное хранение токена (EncryptedSharedPreferences),
`AuthViewModel`, экран входа (`LoginScreen`), проводка токена из хранилища в синк.
**НЕ в слайсе:** refresh/протухание токена, кнопка logout в UI (метод `clear()` есть, кнопка —
позже), автосинк.

## Зависимости
Ktor client: `ktor-client-core`, `ktor-client-okhttp` (Android-движок), `ktor-client-content-negotiation`,
`ktor-serialization-kotlinx-json`; плагин `kotlinx-serialization`; `ktor-client-mock` в commonTest.
`client_id` — `BuildConfig.GITHUB_CLIENT_ID` из `local.properties` (ключ `github.clientId`), не коммитится.

## Эндпоинты GitHub
- Запрос кода: `POST https://github.com/login/device/code` (`client_id`, `scope=repo`),
  Accept: application/json → `device_code`, `user_code`, `verification_uri`, `interval`, `expires_in`.
- Опрос токена: `POST https://github.com/login/oauth/access_token`
  (`client_id`, `device_code`, `grant_type=urn:ietf:params:oauth:grant-type:device_code`) →
  `access_token` при успехе, либо `error`: `authorization_pending` / `slow_down` /
  `expired_token` / `access_denied`.

## Архитектура и интерфейсы (пакет `app.obsidianmd.auth`, commonMain)

```kotlin
@Serializable
data class DeviceAuthorization(
    val deviceCode: String, val userCode: String,
    val verificationUri: String, val interval: Int, val expiresIn: Int,
)

sealed interface AuthResult {
    data class Success(val token: String) : AuthResult
    data class Failed(val reason: String) : AuthResult
}

class GitHubDeviceAuth(private val http: HttpClient, private val clientId: String) {
    suspend fun requestDeviceCode(): DeviceAuthorization
    suspend fun poll(auth: DeviceAuthorization): AuthResult   // цикл с delay(interval)
}

interface TokenStore {
    fun save(token: String)
    fun get(): String?
    fun clear()
}
```

- `GitHubDeviceAuth` получает `HttpClient` извне (в тестах — Ktor `MockEngine`), поэтому HTTP
  подменяется без сети.
- `poll` реализует стейт-машину:
  `authorization_pending` → `delay(interval*1000)` и повтор; `slow_down` → `interval += 5`;
  успех → `Success(token)`; `expired_token`/`access_denied` → `Failed`; суммарное ожидание > `expiresIn`
  → `Failed("expired")`. `delay` под `runTest` исполняется в виртуальном времени (тесты мгновенны).
- `TokenStore` — Android-реализация `EncryptedTokenStore` на EncryptedSharedPreferences
  (androidMain); в тестах — `FakeTokenStore` (in-memory).

### AuthViewModel (commonMain, `app.obsidianmd.auth`)
```kotlin
sealed interface AuthState {
    data object Idle : AuthState
    data class AwaitingUser(val userCode: String, val verificationUri: String) : AuthState
    data object Success : AuthState
    data class Failed(val reason: String) : AuthState
}
class AuthViewModel(auth: GitHubDeviceAuth, store: TokenStore, scope, ...) {
    val state: StateFlow<AuthState>
    fun login()   // requestDeviceCode → AwaitingUser(...) → poll → на Success сохранить токен в store → Success
}
```

### UI (commonMain, `app.obsidianmd.ui`)
- `LoginScreen(state, onLogin, onOpenUrl)` — кнопка «Войти через GitHub»; в состоянии
  `AwaitingUser` показывает `userCode` крупно + кнопку «Открыть GitHub» (→ `onOpenUrl(verificationUri)`)
  + «Ожидание подтверждения…»; `Failed` → текст ошибки + повтор.
- Android: `onOpenUrl` открывает браузер через Intent (`ACTION_VIEW`).

### Проводка (androidMain)
- `MainActivity`: создать `EncryptedTokenStore`; если `store.get() == null` → показать `LoginScreen`
  (через `AuthViewModel`); иначе собрать `SyncConfig(token = store.get())` и показать основной экран.
- `build.gradle.kts`: `buildConfigField GITHUB_CLIENT_ID` из `github.clientId`; **удалить**
  `SYNC_TOKEN` (токен теперь из хранилища; `SYNC_REMOTE_URL` остаётся).
- Ktor okhttp-движок в `androidMain`.

## Тестирование (TDD)

### commonTest (Ktor MockEngine + runTest)
1. **requestDeviceCode.** *Первый падающий тест:* MockEngine на `/login/device/code` отдаёт JSON
   (`device_code/user_code/verification_uri/interval/expires_in`) → `requestDeviceCode()` возвращает
   заполненный `DeviceAuthorization`. Падает — класса нет.
2. **poll — pending → success.** MockEngine на `/oauth/access_token` отдаёт `authorization_pending`
   дважды, затем `access_token` → `poll` возвращает `Success(token)`. Проверяем, что виртуальное
   время продвинулось на ~2*interval (delay сработал).
3. **poll — slow_down.** Ответ `slow_down` затем токен → интервал увеличен (успех получен, без
   зацикливания).
4. **poll — expired_token.** Ответ `expired_token` → `Failed`.
5. **AuthViewModel.** Фейковый `GitHubDeviceAuth` (без сети) + `FakeTokenStore`: `login()` →
   `state` проходит `AwaitingUser(userCode,...)` → `Success`, и токен сохранён в store.
6. **TokenStore (FakeTokenStore).** save→get возвращает токен; clear→get == null.

### Ручная приёмка (Android — EncryptedSharedPreferences и UI не юнит-тестятся)
См. приёмочные кейсы.

## Приёмочные тест-кейсы (ручные, после разработки)
1. **Вход через GitHub (happy path)**
   - Изначальное состояние: `github.clientId` задан; токена в хранилище нет; свежий запуск.
   - Шаги: открыть приложение → показан `LoginScreen`; нажать «Войти через GitHub»; на экране
     появляется код; нажать «Открыть GitHub», ввести код на сайте, подтвердить доступ; вернуться
     в приложение.
   - Ожидаемый результат: приложение обнаруживает выдачу токена, переходит на основной экран;
     токен сохранён.
2. **Токен сохраняется между запусками**
   - Изначальное состояние: вход уже выполнен (токен в хранилище).
   - Шаги: закрыть и снова открыть приложение.
   - Ожидаемый результат: `LoginScreen` не показывается — сразу основной экран; синк использует
     сохранённый токен.
3. **Синхронизация с полученным токеном**
   - Изначальное состояние: вход выполнен; `SYNC_REMOTE_URL` указывает на приватный GitHub-репо.
   - Шаги: нажать «Синхронизировать».
   - Ожидаемый результат: clone/синк проходит с OAuth-токеном (не PAT), список наполняется.
4. **Отказ/таймаут входа**
   - Изначальное состояние: `LoginScreen`, начат вход.
   - Шаги: не подтверждать доступ до истечения кода (или отклонить).
   - Ожидаемый результат: показана ошибка входа с возможностью повторить; приложение не падает.
