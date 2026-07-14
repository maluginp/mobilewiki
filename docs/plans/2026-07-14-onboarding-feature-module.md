# Онбординг feature-модуль + credential-store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Закрыть весь флоу онбординга в отдельном feature-модуле `:features:onboarding` с единственной точкой входа (вложенный бэкстек), а долгоживущий credential-store вынести в новый `:core:auth`.

**Architecture:** Три независимо компилируемых и зелёных шага. (1) `TokenStore`/`EncryptedTokenStore`/`authPlatformModule` переезжают в новый core-модуль `:core:auth` (пакет `app.obsidianmd.auth` сохраняется — потребители токена не трогаем). (2) `:features:auth` механически переименовывается в `:features:onboarding` (пакет + публичные имена), интерфейс провайдера и оркестрация в host пока не меняются. (3) Провайдер схлопывается в `Onboarding(startAt, onFinished)` со своим `NavDisplay`/`rememberNavBackStack`; маршруты и переходы онбординга уходят из `composeApp`.

**Tech Stack:** Kotlin Multiplatform, Compose, Navigation3 (nav3), Koin, kotlinx.serialization, JUnit/kotlin-test, Robolectric (Compose UI tests).

## Global Constraints

- Все классы в `:impl` — `internal`; наружу торчат только Koin-модуль фичи и `{Feature}PresentationProvider`. Исключение: `EncryptedTokenStore` — `public` (создаётся `SyncWorker` вне Koin).
- Все строки — в `:core:translations`, никаких `composeResources` в фичах.
- Кросс-фичевые зависимости — только через `:*:api` / `:core:*`; `:impl` не зависит от чужого `:impl`.
- Каждый `:feature:impl` объявляет свой Koin-модуль в `di/` (commonMain); платформенные байндинги — через `expect/actual <feature>PlatformModule`.
- Пакет credential-store остаётся `app.obsidianmd.auth` (обнуляет churn у `SyncWorker`/`MainActivity`/`AppModule`/`SyncConfigProvider`).
- Пакет флоу-модуля — `app.obsidianmd.onboarding`.
- Это рефактор без пользовательской фичи → аналитика не добавляется (нет нового измеримого поведения; поведение онбординга не меняется).
- Тесты: `./gradlew :<module>:testDebugUnitTest`; сборка: `./gradlew :composeApp:assembleDebug`.

---

### Task 1: Вынести credential-store в `:core:auth`

Чистый перенос. Проверка — полная сборка и существующие тесты зелёные (для rename/move «сначала падающий тест» неприменим; регресс-контроль — существующий suite).

**Files:**
- Create: `core/auth/build.gradle.kts`
- Create: `core/auth/src/commonMain/kotlin/app/obsidianmd/auth/TokenStore.kt` (перенос из `features/auth/api/...`)
- Create: `core/auth/src/androidMain/kotlin/app/obsidianmd/auth/EncryptedTokenStore.kt` (перенос из `features/auth/impl/src/androidMain/...`)
- Create: `core/auth/src/commonMain/kotlin/app/obsidianmd/auth/di/AuthPlatformModule.kt` (`expect val authPlatformModule`)
- Create: `core/auth/src/androidMain/kotlin/app/obsidianmd/auth/di/AuthPlatformModule.android.kt` (`actual`, перенос из `AuthModule.android.kt`)
- Create: `core/auth/src/commonTest/kotlin/app/obsidianmd/auth/FakeTokenStore.kt` (перенос)
- Create: `core/auth/src/commonTest/kotlin/app/obsidianmd/auth/TokenStoreContractTest.kt` (перенос)
- Delete: `features/auth/api/src/commonMain/kotlin/app/obsidianmd/auth/TokenStore.kt`
- Delete: `features/auth/impl/src/androidMain/kotlin/app/obsidianmd/auth/EncryptedTokenStore.kt`
- Delete: `features/auth/impl/src/commonTest/kotlin/app/obsidianmd/auth/TokenStoreContractTest.kt`
- Modify: `features/auth/impl/src/commonMain/kotlin/app/obsidianmd/auth/di/AuthModule.kt` (убрать `expect val authPlatformModule` — теперь в core; `includes(authPlatformModule)` берёт core-версию через импорт)
- Delete: `features/auth/impl/src/androidMain/kotlin/app/obsidianmd/auth/di/AuthModule.android.kt` (actual переехал в core)
- Modify: `settings.gradle.kts` (добавить `include(":core:auth")`)
- Modify: `features/auth/api/build.gradle.kts` — не нужен (TokenStore ушёл; api остаётся compose-провайдером)
- Modify: `features/auth/impl/build.gradle.kts` (добавить `implementation(project(":core:auth"))`; убрать `androidx.security.crypto` из androidMain — переехал в core)
- Modify: `composeApp/build.gradle.kts` (добавить `implementation(project(":core:auth"))`)

**Interfaces:**
- Produces: `interface app.obsidianmd.auth.TokenStore { save(String); get(): String?; clear() }`; `class app.obsidianmd.auth.EncryptedTokenStore(Context): TokenStore` (public); `expect val app.obsidianmd.auth.di.authPlatformModule: Module` (actual биндит `single<TokenStore> { EncryptedTokenStore(androidContext()) }`).
- Consumes: `libs.koin.core`, `libs.androidx.security.crypto`, `libs.koin.android`.

- [ ] **Step 1: Создать модуль `:core:auth` и подключить в settings**

`core/auth/build.gradle.kts`:
```kotlin
plugins {
    id("obsidian.feature.api")
}

android { namespace = "app.obsidianmd.core.auth" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

В `settings.gradle.kts` добавить строку сразу перед `include(":features:auth:api")`:
```kotlin
include(":core:auth")
```

- [ ] **Step 2: Перенести файлы токена в `:core:auth` (пакет не меняется)**

Переместить дословно (пакет остаётся `app.obsidianmd.auth`):
- `features/auth/api/.../auth/TokenStore.kt` → `core/auth/src/commonMain/kotlin/app/obsidianmd/auth/TokenStore.kt`
- `features/auth/impl/src/androidMain/.../auth/EncryptedTokenStore.kt` → `core/auth/src/androidMain/kotlin/app/obsidianmd/auth/EncryptedTokenStore.kt`
- `features/auth/impl/src/commonTest/.../auth/FakeTokenStore.kt` → `core/auth/src/commonTest/kotlin/app/obsidianmd/auth/FakeTokenStore.kt`
- `features/auth/impl/src/commonTest/.../auth/TokenStoreContractTest.kt` → `core/auth/src/commonTest/kotlin/app/obsidianmd/auth/TokenStoreContractTest.kt`

`core/auth/src/commonMain/kotlin/app/obsidianmd/auth/di/AuthPlatformModule.kt`:
```kotlin
package app.obsidianmd.auth.di

import org.koin.core.module.Module

/** Платформенные байндинги credential-store (создание [app.obsidianmd.auth.TokenStore]). */
expect val authPlatformModule: Module
```

`core/auth/src/androidMain/kotlin/app/obsidianmd/auth/di/AuthPlatformModule.android.kt`:
```kotlin
package app.obsidianmd.auth.di

import app.obsidianmd.auth.EncryptedTokenStore
import app.obsidianmd.auth.TokenStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

// Единственное место создания TokenStore (нужен Android Context для EncryptedSharedPreferences).
actual val authPlatformModule: Module = module {
    single<TokenStore> { EncryptedTokenStore(androidContext()) }
}
```

- [ ] **Step 3: Убрать перенесённое из `:features:auth`**

Удалить файлы (перечислены в **Files → Delete**). В `features/auth/impl/src/commonMain/.../di/AuthModule.kt`:
- удалить строку `expect val authPlatformModule: Module` (в конце файла),
- добавить импорт `import app.obsidianmd.auth.di.authPlatformModule` (теперь из `:core:auth`),
- `includes(authPlatformModule)` — без изменений (резолвится в core-версию).

В `features/auth/impl/build.gradle.kts` в `commonMain.dependencies` добавить:
```kotlin
implementation(project(":core:auth"))
```
и убрать из `androidMain.dependencies` строку `implementation(libs.androidx.security.crypto)`.

В `composeApp/build.gradle.kts` в `commonMain.dependencies` добавить (рядом с блоком фич):
```kotlin
implementation(project(":core:auth"))
```

- [ ] **Step 4: Собрать и прогнать тесты — всё зелёное**

Run:
```bash
./gradlew :core:auth:testDebugUnitTest :features:auth:impl:testDebugUnitTest :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL; `TokenStoreContractTest` проходит в `:core:auth`; тесты `:features:auth:impl` проходят; APK собирается. Импорты `app.obsidianmd.auth.TokenStore`/`EncryptedTokenStore` в composeApp компилируются без правок.

- [ ] **Step 5: Commit**

```bash
git add core/auth settings.gradle.kts features/auth composeApp/build.gradle.kts
git commit -m "refactor(auth): вынести TokenStore/EncryptedTokenStore в :core:auth"
```

---

### Task 2: Переименовать `:features:auth` → `:features:onboarding`

Механический ренейм модуля, пакета и публичных имён. Интерфейс провайдера (4 метода) и оркестрация в `AppNavHost` НЕ меняются — только имена. Проверка — сборка + существующие тесты зелёные.

**Files:**
- Rename dir: `features/auth/` → `features/onboarding/` (со всем содержимым)
- Modify (пакет `app.obsidianmd.auth` → `app.obsidianmd.onboarding`) во всех `.kt` под `features/onboarding/`, КРОМЕ ничего (TokenStore уже ушёл): `AuthViewModel.kt`, `RepoAccess.kt`, `GitHubDeviceAuth.kt`, `RepoValidationViewModel.kt`, `RepoPickerViewModel.kt`, `GitHubRepos.kt`, `di/AuthModule.kt`, `presentation/*.kt`, все тесты.
- Rename type: `AuthPresentationProvider` → `OnboardingPresentationProvider` (в `api` + `Impl`)
- Rename fun: `authModule(...)` → `onboardingModule(...)`
- Modify: `features/onboarding/api/build.gradle.kts` (`namespace = "app.obsidianmd.onboarding.api"`)
- Modify: `features/onboarding/impl/build.gradle.kts` (`namespace`, `implementation(project(":features:onboarding:api"))`, `implementation(project(":core:auth"))`)
- Modify: `settings.gradle.kts` (`:features:auth:*` → `:features:onboarding:*`)
- Modify: `composeApp/build.gradle.kts` (`:features:auth:*` → `:features:onboarding:*`)
- Modify: `composeApp/.../nav/AppNavHost.kt` (импорт + `koinInject<OnboardingPresentationProvider>()`)
- Modify: `composeApp/.../BrainerApp.kt` (импорт `onboardingModule`, замена в `modules(...)`, добавить `authPlatformModule` из core)

**Interfaces:**
- Consumes: `TokenStore`/`authPlatformModule` из `:core:auth` (Task 1).
- Produces: `interface app.obsidianmd.onboarding.OnboardingPresentationProvider` (пока с 4 методами `Login`/`RepoPicker`/`ManualUrl`/`RepoValidate` — идентичны прежним); `fun app.obsidianmd.onboarding.di.onboardingModule(githubClientId: String): Module`.

- [ ] **Step 1: Переименовать каталоги и пакеты**

```bash
git mv features/auth features/onboarding
```
Заменить во всех `features/onboarding/**/*.kt` пакет и импорты `app.obsidianmd.auth` → `app.obsidianmd.onboarding` (кроме импортов самого `app.obsidianmd.auth.TokenStore` — он остаётся из `:core:auth`). Проверить, что импорт токена `import app.obsidianmd.auth.TokenStore` в `AuthViewModel.kt`/`di/AuthModule.kt` остался как есть.

Переименовать файлы каталогов исходников: физически `.../kotlin/app/obsidianmd/auth/` → `.../kotlin/app/obsidianmd/onboarding/` внутри `features/onboarding/`:
```bash
cd features/onboarding
for m in api/src/commonMain impl/src/commonMain impl/src/commonTest impl/src/androidUnitTest impl/src/androidMain; do
  [ -d "$m/kotlin/app/obsidianmd/auth" ] && git mv "$m/kotlin/app/obsidianmd/auth" "$m/kotlin/app/obsidianmd/onboarding"
done
cd -
```
(После Task 1 в `features/onboarding` НЕ остаётся `EncryptedTokenStore`/`AuthModule.android.kt`/`TokenStore`/`TokenStoreContractTest`/`FakeTokenStore`… стоп — `FakeTokenStore` нужен `AuthViewModelTest`. Он был перенесён в core в Task 1. Значит для тестов онбординга нужна своя копия — см. Step 2.)

- [ ] **Step 2: Вернуть локальный `FakeTokenStore` для тестов онбординга**

`AuthViewModelTest` использует `FakeTokenStore`, который переехал в `:core:auth` (не виден из onboarding-тестов). Создать локальную копию (5 строк, дублирование тест-фикстуры оправдано — не тянем публикацию test-fixtures):

`features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/FakeTokenStore.kt`:
```kotlin
package app.obsidianmd.onboarding

import app.obsidianmd.auth.TokenStore

class FakeTokenStore : TokenStore {
    private var token: String? = null
    override fun save(token: String) { this.token = token }
    override fun get(): String? = token
    override fun clear() { token = null }
}
```

- [ ] **Step 3: Переименовать публичные имена (provider + module)**

- `features/onboarding/api/.../AuthPresentationProvider.kt`: тип `AuthPresentationProvider` → `OnboardingPresentationProvider` (тело/методы без изменений). Файл переименовать: `git mv` в `OnboardingPresentationProvider.kt`.
- `presentation/AuthPresentationProviderImpl.kt`: класс `AuthPresentationProviderImpl` → `OnboardingPresentationProviderImpl`, `: AuthPresentationProvider` → `: OnboardingPresentationProvider`. `git mv` в `OnboardingPresentationProviderImpl.kt`.
- `di/AuthModule.kt`: `fun authModule(...)` → `fun onboardingModule(...)`; `single<AuthPresentationProvider> { AuthPresentationProviderImpl() }` → `single<OnboardingPresentationProvider> { OnboardingPresentationProviderImpl() }`. `git mv` в `OnboardingModule.kt`.

- [ ] **Step 4: Обновить build-файлы и агрегатор**

`features/onboarding/api/build.gradle.kts`: `namespace = "app.obsidianmd.onboarding.api"`.
`features/onboarding/impl/build.gradle.kts`: `namespace = "app.obsidianmd.onboarding.impl"`; `implementation(project(":features:onboarding:api"))`; оставить `implementation(project(":core:auth"))`.

`settings.gradle.kts` — заменить:
```kotlin
include(":features:auth:api")
include(":features:auth:impl")
```
на:
```kotlin
include(":features:onboarding:api")
include(":features:onboarding:impl")
```

`composeApp/build.gradle.kts` — заменить:
```kotlin
api(project(":features:auth:api"))
implementation(project(":features:auth:impl"))
```
на:
```kotlin
api(project(":features:onboarding:api"))
implementation(project(":features:onboarding:impl"))
```

`composeApp/.../nav/AppNavHost.kt`:
```kotlin
// было
import app.obsidianmd.auth.AuthPresentationProvider
val auth = koinInject<AuthPresentationProvider>()
// стало
import app.obsidianmd.onboarding.OnboardingPresentationProvider
val auth = koinInject<OnboardingPresentationProvider>()
```
(остальные вызовы `auth.Login(...)`/`auth.RepoPicker(...)`/… не трогаем — интерфейс тот же.)

`composeApp/.../BrainerApp.kt`:
```kotlin
// импорты
import app.obsidianmd.auth.di.authPlatformModule
import app.obsidianmd.onboarding.di.onboardingModule
// modules(...)
modules(appModule, vaultModule, authPlatformModule, onboardingModule(BuildConfig.GITHUB_CLIENT_ID), aiModule, settingsModule, noteModule)
```
(`authPlatformModule` теперь подключается явно — раньше его подтягивал `authModule` через `includes`; `onboardingModule` больше его не включает, т.к. байндинг токена ушёл в core. Оставить `includes(authPlatformModule)` в `onboardingModule` тоже допустимо — Koin схлопывает дубли; для явности подключаем в app и убираем `includes` из onboardingModule.)

Убрать `includes(authPlatformModule)` из `onboardingModule` и импорт `authPlatformModule` там (переехал в core, подключается в BrainerApp).

- [ ] **Step 5: Собрать и прогнать тесты — всё зелёное**

Run:
```bash
./gradlew :features:onboarding:impl:testDebugUnitTest :composeApp:testDebugUnitTest :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL; тесты онбординга (`AuthViewModelTest`, `RepoPickerViewModelTest`, `RepoValidationViewModelTest`, `GitHubDeviceAuthTest`, `FilterReposTest`, `GitHubReposTest`, `RepoAccessTest`, `LoginScreenTest`) проходят в новом модуле; APK собирается.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(onboarding): переименовать :features:auth в :features:onboarding"
```

---

### Task 3: Схлопнуть провайдер в single-entry с вложенным бэкстеком

Единственный шаг с новой логикой → строгий TDD. Развилка «после входа: есть репо → finish, иначе → выбор репо» (раньше делал host через `startStack`) переезжает в модуль и получает первый падающий тест.

**Files:**
- Modify: `features/onboarding/api/.../OnboardingPresentationProvider.kt` (4 метода → 1 + `enum OnboardingStart`)
- Create: `features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingFlow.kt` (Step, OnboardingAction, `afterSignIn`, сериализаторы)
- Modify: `features/onboarding/impl/.../presentation/OnboardingPresentationProviderImpl.kt` (вложенный `NavDisplay`)
- Create: `features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/AfterSignInTest.kt`
- Create: `features/onboarding/impl/src/androidUnitTest/kotlin/app/obsidianmd/onboarding/presentation/OnboardingStartTest.kt`
- Modify: `features/onboarding/impl/build.gradle.kts` (добавить `:features:settings:api`, `libs.navigation3.ui`, `libs.koin.compose`)
- Modify: `composeApp/.../nav/Route.kt` (4 маршрута → `Onboarding(startAt)`)
- Modify: `composeApp/.../nav/StartStack.kt`
- Modify: `composeApp/.../nav/AppNavHost.kt` (4 entry + `OnboardingContainer` + `isOnboarding` → 1 entry)

**Interfaces:**
- Consumes: `RepoSettingsStore` из `:features:settings:api` (`getRemoteUrl(): String?`, `setRemoteUrl(url: String)`); экраны `WelcomeScreen`/`LoginScreen`/`RepoPickerScreen`/`ManualUrlScreen`/`RepoValidationScreen` + VM (internal, из Task 2).
- Produces: `interface OnboardingPresentationProvider { @Composable fun Onboarding(startAt: OnboardingStart, onFinished: () -> Unit) }`; `enum class OnboardingStart { Login, RepoPicker }`; `internal fun afterSignIn(hasRepo: Boolean): OnboardingAction`.

- [ ] **Step 1: Написать падающий тест на развилку `afterSignIn`**

`features/onboarding/impl/src/commonTest/kotlin/app/obsidianmd/onboarding/AfterSignInTest.kt`:
```kotlin
package app.obsidianmd.onboarding

import app.obsidianmd.onboarding.presentation.OnboardingAction
import app.obsidianmd.onboarding.presentation.Step
import app.obsidianmd.onboarding.presentation.afterSignIn
import kotlin.test.Test
import kotlin.test.assertEquals

class AfterSignInTest {
    @Test
    fun signedIn_withRepo_finishes() {
        assertEquals(OnboardingAction.Finish, afterSignIn(hasRepo = true))
    }

    @Test
    fun signedIn_withoutRepo_goesToRepoPicker() {
        assertEquals(OnboardingAction.Go(Step.RepoPicker), afterSignIn(hasRepo = false))
    }
}
```

- [ ] **Step 2: Прогнать — падает (не компилируется: нет `afterSignIn`/`Step`/`OnboardingAction`)**

Run:
```bash
./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.AfterSignInTest"
```
Expected: FAIL — unresolved reference `afterSignIn`/`Step`/`OnboardingAction`.

- [ ] **Step 3: Минимальный код — `OnboardingFlow.kt`**

`features/onboarding/impl/src/commonMain/kotlin/app/obsidianmd/onboarding/presentation/OnboardingFlow.kt`:
```kotlin
package app.obsidianmd.onboarding.presentation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Шаги вложенного бэкстека онбординга. */
@Serializable
internal sealed interface Step : NavKey {
    @Serializable data object Login : Step
    @Serializable data object RepoPicker : Step
    @Serializable data object ManualUrl : Step
    @Serializable data class Validate(val url: String) : Step
}

internal sealed interface OnboardingAction {
    data class Go(val step: Step) : OnboardingAction
    data object Finish : OnboardingAction
}

/** Единственная развилка, которую раньше делал host через startStack: вход завершён. */
internal fun afterSignIn(hasRepo: Boolean): OnboardingAction =
    if (hasRepo) OnboardingAction.Finish else OnboardingAction.Go(Step.RepoPicker)

internal val onboardingSavedState: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Step.Login::class, Step.Login.serializer())
            subclass(Step.RepoPicker::class, Step.RepoPicker.serializer())
            subclass(Step.ManualUrl::class, Step.ManualUrl.serializer())
            subclass(Step.Validate::class, Step.Validate.serializer())
        }
    }
}
```

- [ ] **Step 4: Прогнать — тест зелёный**

Run:
```bash
./gradlew :features:onboarding:impl:testDebugUnitTest --tests "app.obsidianmd.onboarding.AfterSignInTest"
```
Expected: PASS (2 теста).

- [ ] **Step 5: Расширить контракт `api` до single-entry**

`features/onboarding/api/.../OnboardingPresentationProvider.kt` — заменить тело на:
```kotlin
package app.obsidianmd.onboarding

import androidx.compose.runtime.Composable

/**
 * Единственная точка входа онбординга. Весь флоу (вход → выбор репо → ручной URL →
 * валидация) живёт внутри модуля со своим вложенным бэкстеком. onFinished — когда
 * пользователь онбординг завершил (репо выбран и сохранён).
 */
interface OnboardingPresentationProvider {
    @Composable
    fun Onboarding(startAt: OnboardingStart, onFinished: () -> Unit)
}

/** С какого шага стартовать. RepoPicker — для «сменить репо из настроек». */
enum class OnboardingStart { Login, RepoPicker }
```

- [ ] **Step 6: Переписать `OnboardingPresentationProviderImpl` на вложенный NavDisplay**

Добавить в `features/onboarding/impl/build.gradle.kts` в `commonMain.dependencies`:
```kotlin
implementation(project(":features:settings:api"))
implementation(libs.navigation3.ui)
implementation(libs.koin.compose)
```

`features/onboarding/impl/.../presentation/OnboardingPresentationProviderImpl.kt`:
```kotlin
package app.obsidianmd.onboarding.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.obsidianmd.onboarding.AuthState
import app.obsidianmd.onboarding.AuthViewModel
import app.obsidianmd.onboarding.OnboardingPresentationProvider
import app.obsidianmd.onboarding.OnboardingStart
import app.obsidianmd.onboarding.RepoPickerViewModel
import app.obsidianmd.onboarding.RepoValidationViewModel
import app.obsidianmd.settings.RepoSettingsStore
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal class OnboardingPresentationProviderImpl : OnboardingPresentationProvider {

    @Composable
    override fun Onboarding(startAt: OnboardingStart, onFinished: () -> Unit) {
        val settings = koinInject<RepoSettingsStore>()
        val start: Step = if (startAt == OnboardingStart.Login) Step.Login else Step.RepoPicker
        val backStack = rememberNavBackStack(onboardingSavedState, start)

        Box(Modifier.safeDrawingPadding()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Step.Login> {
                        val vm: AuthViewModel = koinViewModel()
                        val state by vm.state.collectAsState()
                        LaunchedEffect(state) {
                            if (state is AuthState.Success) {
                                val hasRepo = !settings.getRemoteUrl().isNullOrBlank()
                                when (val action = afterSignIn(hasRepo)) {
                                    OnboardingAction.Finish -> onFinished()
                                    is OnboardingAction.Go -> backStack.add(action.step)
                                }
                            }
                        }
                        if (state is AuthState.Idle) {
                            WelcomeScreen(onSignIn = vm::login)
                        } else {
                            val uriHandler = LocalUriHandler.current
                            LoginScreen(state = state, onLogin = vm::login, onOpenUrl = { uriHandler.openUri(it) })
                        }
                    }
                    entry<Step.RepoPicker> {
                        val vm: RepoPickerViewModel = koinViewModel()
                        LaunchedEffect(Unit) { vm.load() }
                        val state by vm.state.collectAsState()
                        RepoPickerScreen(
                            state = state,
                            onChoose = { url -> backStack.add(Step.Validate(url)) },
                            onRetry = vm::load,
                            onEnterManually = { backStack.add(Step.ManualUrl) },
                            onBack = if (backStack.size > 1) ({ backStack.removeLastOrNull(); Unit }) else null,
                        )
                    }
                    entry<Step.ManualUrl> {
                        ManualUrlScreen(
                            onSubmit = { url -> backStack.add(Step.Validate(url)) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Step.Validate> { key ->
                        val vm: RepoValidationViewModel = koinViewModel()
                        LaunchedEffect(key.url) { vm.validate(key.url) }
                        val state by vm.state.collectAsState()
                        RepoValidationScreen(
                            state = state,
                            onContinue = { settings.setRemoteUrl(key.url); onFinished() },
                            onRetry = { vm.validate(key.url) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 7: Упростить навигацию в composeApp**

`composeApp/.../nav/Route.kt` — заменить блок «Онбординг» и подписки:
```kotlin
import app.obsidianmd.onboarding.OnboardingStart
// ...
// Онбординг
@Serializable data class Onboarding(val startAt: OnboardingStart) : Route
```
Убрать `Login`/`RepoPicker`/`RepoManualUrl`/`RepoValidate`. В `navSerializersModule` заменить 4 их `subclass(...)` на:
```kotlin
subclass(Route.Onboarding::class, Route.Onboarding.serializer())
```

`composeApp/.../nav/StartStack.kt`:
```kotlin
package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart

/** Стартовый бэкстек по состоянию авторизации/репозитория. */
fun startStack(hasToken: Boolean, hasRepo: Boolean): List<Route> = when {
    hasToken && hasRepo -> listOf(Route.VaultList())
    !hasToken -> listOf(Route.Onboarding(OnboardingStart.Login))
    else -> listOf(Route.Onboarding(OnboardingStart.RepoPicker))
}

/** Смена репо из настроек: онбординг с шага выбора репо поверх списка (есть куда вернуться). */
fun stackForChangeRepo(): List<Route> = listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.RepoPicker))
```
(`stackAfterRepoChosen()` удалить — модуль сам финиширует.)

`composeApp/.../nav/AppNavHost.kt`:
- удалить приватный `Route.isOnboarding` (больше не используется),
- удалить приватную `OnboardingContainer` (переехала в модуль),
- заменить 4 entry (`Login`/`RepoPicker`/`RepoManualUrl`/`RepoValidate`) на один:
```kotlin
entry<Route.Onboarding> { key ->
    auth.Onboarding(
        startAt = key.startAt,
        onFinished = {
            backStack.resetTo(listOf(Route.VaultList()))
            vm.sync()
        },
    )
}
```
- импорт: убрать `OnboardingStart`-независимые ссылки на старые маршруты; `auth` уже `OnboardingPresentationProvider` (Task 2). Проверить, что `Route.VaultList`/`resetTo` доступны.

- [ ] **Step 8: Написать Robolectric-тест старта с RepoPicker**

`features/onboarding/impl/src/androidUnitTest/kotlin/app/obsidianmd/onboarding/presentation/OnboardingStartTest.kt` — по образцу `LoginScreenTest`: смонтировать `OnboardingPresentationProviderImpl().Onboarding(startAt = OnboardingStart.RepoPicker, onFinished = {})` в Koin-окружении с фейковыми VM/`RepoSettingsStore` и проверить, что первым показан экран выбора репозитория (например, `onNodeWithText(<заголовок пикера>).assertExists()`).

```kotlin
package app.obsidianmd.onboarding.presentation

import androidx.compose.ui.test.junit4.createComposeRule
// + Koin test setup как в LoginScreenTest, фейк RepoSettingsStore { getRemoteUrl()=null }
import app.obsidianmd.onboarding.OnboardingStart
import kotlin.test.Test

class OnboardingStartTest {
    // @get:Rule val composeRule = createComposeRule()
    @Test
    fun startAtRepoPicker_showsRepoPickerFirst() {
        // setContent { OnboardingPresentationProviderImpl().Onboarding(OnboardingStart.RepoPicker) {} }
        // composeRule.onNodeWithText(<строка заголовка пикера из :core:translations>).assertExists()
    }
}
```
Заполнить по фактическому API `LoginScreenTest` (Koin-модуль, фейки VM). Строку заголовка взять из ресурсов пикера.

- [ ] **Step 9: Прогнать всё — зелёное**

Run:
```bash
./gradlew :features:onboarding:impl:testDebugUnitTest :composeApp:testDebugUnitTest :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL; `AfterSignInTest`, `OnboardingStartTest` и все перенесённые тесты проходят; APK собирается.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(onboarding): единый вход Onboarding(startAt, onFinished) со вложенным бэкстеком"
```

---

### Task 4: Обновить `docs/MODULE_CONVENTIONS.md`

Документация модульной карты должна отражать новую структуру.

**Files:**
- Modify: `docs/MODULE_CONVENTIONS.md`

- [ ] **Step 1: Обновить карту модулей и заметки**

- В блоке «## Модули»: строки `features/auth/api` и `features/auth/impl` → `features/onboarding/api` (контракт `OnboardingPresentationProvider` + `OnboardingStart`) и `features/onboarding/impl` (вложенный бэкстек флоу онбординга). Добавить `core/auth/` (TokenStore + EncryptedTokenStore + authPlatformModule, пакет `app.obsidianmd.auth`).
- В «## Инкапсуляция :impl» в исключении про `public`: `EncryptedTokenStore (:auth:impl)` → `EncryptedTokenStore (:core:auth)`.
- Убрать/поправить упоминание, что навигацию онбординга рисует composeApp: теперь онбординг — самодостаточный чёрный ящик с одним входом и своим бэкстеком; host держит один `Route.Onboarding`.

- [ ] **Step 2: Commit**

```bash
git add docs/MODULE_CONVENTIONS.md
git commit -m "docs(modules): :core:auth + :features:onboarding в конвенциях"
```

---

## Self-review

**Spec coverage:**
- `:core:auth` (TokenStore/EncryptedTokenStore/authPlatformModule, пакет сохранён) → Task 1. ✅
- Переименование `:features:auth`→`:features:onboarding` (пакет + публичные имена) → Task 2. ✅
- Вложенный бэкстек, single-entry `Onboarding(startAt, onFinished)`, `OnboardingStart` → Task 3 (Steps 5–6). ✅
- Route.kt / StartStack.kt / AppNavHost.kt (4→1, удаление `stackAfterRepoChosen`/`isOnboarding`/`OnboardingContainer`) → Task 3 (Step 7). ✅
- Запись URL через `RepoSettingsStore` внутри модуля → Task 3 (Step 6, `Step.Validate.onContinue`). ✅
- Развилка `Login.onSignedIn` (hasRepo → finish/picker) + первый падающий тест → Task 3 (Steps 1–4, `afterSignIn`). ✅
- Robolectric-тест старта с RepoPicker → Task 3 (Step 8). ✅
- Перенос существующих тестов (регресс) → Task 1 (token-тесты в core), Task 2 (flow-тесты в onboarding). ✅
- `MODULE_CONVENTIONS.md` → Task 4. ✅
- Приёмочные тест-кейсы из спеки — ручной прогон после разработки (вне плана, гоняются на этапе delivery). ✅

**Placeholder scan:** тест `OnboardingStartTest` (Task 3 Step 8) — единственное место с «заполнить по образцу `LoginScreenTest`»: точный Koin-boilerplate и строка-заголовок берутся из фактического файла-образца в момент реализации (в плане нельзя воспроизвести неизвестный сейчас Koin-setup соседнего теста, не прочитав его). Это не логика, а тестовая обвязка по существующему шаблону. Остальные шаги содержат реальный код и команды.

**Type consistency:** `afterSignIn(hasRepo: Boolean): OnboardingAction` — сигнатура едина в тесте (Task 3 Step 1) и коде (Step 3). `OnboardingAction.Finish`/`OnboardingAction.Go(Step)`, `Step.{Login,RepoPicker,ManualUrl,Validate(url)}` — согласованы между `OnboardingFlow.kt` и `Impl`. `OnboardingStart.{Login,RepoPicker}` — едина в api, `StartStack.kt`, `Route.Onboarding`. `RepoSettingsStore.getRemoteUrl()/setRemoteUrl()` — по факту `:features:settings:api`. `resetTo`/`Route.VaultList` — существующие в AppNavHost.
