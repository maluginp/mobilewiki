# Settings & Note Feature Modules — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Вынести `settings` и `note` из `composeApp` в feature-модули `:features:settings:{api,impl}` и `:features:note:{api,impl}` по эталону `vault`/`ai`.

**Architecture:** Каждая фича отдаёт **stateless-экран** через `{Feature}PresentationProvider` (интерфейс в `:api`), реализацию биндит Koin-модуль фичи. Владельцы состояния/навигации (`VaultViewModel`, `SyncStatus`, `AppNavHost`, `ConflictDialog`, `decodeImage`, синк) **остаются** в `composeApp`. Настройки хранят URL через `RepoSettingsStore` (контракт в `:api`), реализация `SharedPrefsRepoSettingsStore` — **public** ради фонового `SyncWorker`.

**Tech Stack:** Kotlin Multiplatform, Compose, Koin, JUnit/Robolectric, Gradle convention-плагины `obsidian.feature.api` / `obsidian.feature.impl`.

## Global Constraints

- Пакеты сохраняются: `app.obsidianmd.settings` (существующий), `app.obsidianmd.note` (новый). Импорты моделей по проекту не меняются.
- Все классы `:impl` — `internal`, **кроме** `SharedPrefsRepoSettingsStore` (public, прецедент — public `EncryptedTokenStore` в `auth:impl`; его создаёт `SyncWorker` вне Koin).
- Наружу из `:impl` торчит только Koin-модуль фичи (`val <feature>Module`) + провайдер экрана через `:api`.
- Строки — только в `:core:translations`; никаких `composeResources` в фичах.
- Кросс-фичевые зависимости — только через `:*:api` (`note:impl` → `vault:api`).
- Модули лежат под `features/`; gradle-пути — `:features:<feature>:<api|impl>`.
- Тесты: `./gradlew :features:<feature>:impl:testDebugUnitTest`; сборка приложения: `./gradlew :composeApp:assembleDebug`.
- Analytics: чистый рефактор-перенос, user-visible поведение не меняется, события (`sync`, `note_open`, `note_save`) остаются в `VaultViewModel` в `composeApp` — новых событий не заводим.

---

## Структура файлов

**Создаём:**
```
features/settings/api/build.gradle.kts
features/settings/api/src/androidMain/AndroidManifest.xml            (namespace-only, как у vault:api)
features/settings/api/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt
features/settings/api/src/commonMain/kotlin/app/obsidianmd/settings/SettingsPresentationProvider.kt
features/settings/impl/build.gradle.kts
features/settings/impl/src/androidDebug/AndroidManifest.xml          (host activity для Robolectric compose-теста)
features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsScreen.kt
features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsViewModel.kt
features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsPresentationProviderImpl.kt
features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/di/SettingsModule.kt
features/settings/impl/src/androidMain/kotlin/app/obsidianmd/settings/di/SettingsModule.android.kt
features/settings/impl/src/androidMain/kotlin/app/obsidianmd/settings/data/SharedPrefsRepoSettingsStore.kt
features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/FakeRepoSettingsStore.kt
features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/RepoSettingsStoreContractTest.kt
features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/SettingsViewModelTest.kt
features/settings/impl/src/androidUnitTest/kotlin/app/obsidianmd/settings/SettingsScreenTest.kt

features/note/api/build.gradle.kts
features/note/api/src/androidMain/AndroidManifest.xml
features/note/api/src/commonMain/kotlin/app/obsidianmd/note/NotePresentationProvider.kt
features/note/impl/build.gradle.kts
features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/domain/MdEdit.kt
features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NoteScreen.kt
features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/EditorToolbar.kt
features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NotePresentationProviderImpl.kt
features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/di/NoteModule.kt
features/note/impl/src/commonTest/kotlin/app/obsidianmd/note/MdEditTest.kt

composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SyncStatusText.kt   (хелпер, переезжает из SettingsScreen.kt)
```

**Изменяем:** `settings.gradle.kts`, `composeApp/build.gradle.kts`, `composeApp/.../di/AppModule.kt`, `composeApp/.../nav/AppNavHost.kt`, `composeApp/.../BrainerApp.kt`, `docs/MODULE_CONVENTIONS.md`.

**Удаляем из `composeApp`:** `settings/RepoSettingsStore.kt`, `settings/SettingsViewModel.kt`, `settings/SharedPrefsRepoSettingsStore.kt`, `ui/SettingsScreen.kt`, `ui/MarkdownScreen.kt`, `ui/EditorToolbar.kt`, `editor/MdEdit.kt` + перенесённые тесты (`settings/*Test`, `settings/FakeRepoSettingsStore.kt`, `editor/MdEditTest.kt`, `ui/SettingsScreenTest.kt`).

Замечание по api-модулям: смотри существующий `features/vault/api/build.gradle.kts` и его `src/androidMain/AndroidManifest.xml` — новые api-модули копируют их 1:1, меняется только `namespace`.

---

### Task 1: Модуль `:features:settings` (api + impl), вынос настроек

**Files:**
- Create: все `features/settings/**` из списка выше
- Modify: `settings.gradle.kts`, `composeApp/build.gradle.kts`, `composeApp/src/androidMain/kotlin/app/obsidianmd/di/AppModule.kt`, `composeApp/src/androidMain/kotlin/app/obsidianmd/BrainerApp.kt`, `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt`
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SyncStatusText.kt`
- Delete: `composeApp/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt`, `.../settings/SettingsViewModel.kt`, `composeApp/src/androidMain/kotlin/app/obsidianmd/settings/SharedPrefsRepoSettingsStore.kt`, `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SettingsScreen.kt`, `composeApp/src/commonTest/kotlin/app/obsidianmd/settings/*`, `composeApp/src/androidUnitTest/kotlin/app/obsidianmd/ui/SettingsScreenTest.kt`
- Test: `features/settings/impl/src/commonTest/.../RepoSettingsStoreContractTest.kt`, `.../SettingsViewModelTest.kt`, `features/settings/impl/src/androidUnitTest/.../SettingsScreenTest.kt`

**Interfaces:**
- Produces: `interface RepoSettingsStore { fun getRemoteUrl(): String?; fun setRemoteUrl(url: String) }`; `interface SettingsPresentationProvider { @Composable fun Screen(syncing: Boolean, syncStatusText: String, onSync: () -> Unit, onNavigateBack: () -> Unit, onPickFromGitHub: () -> Unit, aiSection: @Composable () -> Unit) }`; `val settingsModule: Module`.
- Consumes: `:core:translations` (строки), `koinViewModel`, `androidContext`.

- [ ] **Step 1: Подключить модули в settings.gradle.kts**

В `settings.gradle.kts` после `include(":features:ai:impl")` добавить:
```kotlin
include(":features:settings:api")
include(":features:settings:impl")
```

- [ ] **Step 2: Создать `settings:api`**

`features/settings/api/build.gradle.kts` (копия `vault/api`, меняется namespace):
```kotlin
plugins {
    id("obsidian.feature.api")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "app.obsidianmd.settings.api" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.material3)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
```
`features/settings/api/src/androidMain/AndroidManifest.xml` — скопировать из `features/vault/api/src/androidMain/AndroidManifest.xml`.

`features/settings/api/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt`:
```kotlin
package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
}
```

`features/settings/api/src/commonMain/kotlin/app/obsidianmd/settings/SettingsPresentationProvider.kt`:
```kotlin
package app.obsidianmd.settings

import androidx.compose.runtime.Composable

/** Точка входа UI настроек для навигации основного модуля. Реализация — в :settings:impl. */
interface SettingsPresentationProvider {
    @Composable
    fun Screen(
        syncing: Boolean,
        syncStatusText: String,
        onSync: () -> Unit,
        onNavigateBack: () -> Unit,
        onPickFromGitHub: () -> Unit,
        aiSection: @Composable () -> Unit,
    )
}
```

- [ ] **Step 3: Создать `settings:impl` build + скелет**

`features/settings/impl/build.gradle.kts` (копия `vault/impl`, без okio):
```kotlin
plugins {
    id("obsidian.feature.impl")
}

android {
    namespace = "app.obsidianmd.settings.impl"
    testOptions {
        unitTests { isIncludeAndroidResources = true } // Robolectric + Compose UI tests
    }
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:settings:api"))
            implementation(project(":core:translations"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.koin.core)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidUnitTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.robolectric)
        }
    }
}
```
`features/settings/impl/src/androidDebug/AndroidManifest.xml` — скопировать из `features/vault/impl/src/androidDebug/AndroidManifest.xml`.

- [ ] **Step 4: Перенести контракт-тест `RepoSettingsStore` (red)**

`features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/FakeRepoSettingsStore.kt` и `RepoSettingsStoreContractTest.kt` — перенести содержимое из `composeApp/src/commonTest/kotlin/app/obsidianmd/settings/` без изменений (пакет тот же).

Run: `./gradlew :features:settings:impl:compileDebugUnitTestKotlinAndroid`
Expected: FAIL — `FakeRepoSettingsStore` реализует `RepoSettingsStore`, интерфейс уже в `settings:api` (Step 2), поэтому компиляция контракт-теста должна пройти; если `RepoSettingsStore` ещё виден из composeApp — убедиться, что старый файл удаляется в Step 9.

- [ ] **Step 5: Перенести `SettingsViewModel` + тест (red → green)**

`features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsViewModel.kt` — перенести из composeApp, добавить `internal`:
```kotlin
package app.obsidianmd.settings.presentation

import androidx.lifecycle.ViewModel
import app.obsidianmd.settings.RepoSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class SettingsState(val url: String = "")

internal class SettingsViewModel(private val store: RepoSettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState(url = store.getRemoteUrl() ?: ""))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun save(url: String) {
        store.setRemoteUrl(url)
        _state.update { it.copy(url = url) }
    }
}
```
`features/settings/impl/src/commonTest/kotlin/app/obsidianmd/settings/SettingsViewModelTest.kt` — перенести из composeApp; поправить импорт на `app.obsidianmd.settings.presentation.SettingsViewModel`/`SettingsState` (тест лежит в том же модуле → видит `internal`). Пакет теста оставить `app.obsidianmd.settings`.

Run: `./gradlew :features:settings:impl:testDebugUnitTest --tests "*SettingsViewModelTest*" --tests "*RepoSettingsStoreContractTest*"`
Expected: PASS.

- [ ] **Step 6: Stateless `SettingsScreen` + переписанный UI-тест (red → green)**

`features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsScreen.kt` — перенести из `composeApp/ui/SettingsScreen.kt`, сделать `internal`, заменить параметры `state: SettingsState`/`syncStatus: SyncStatus` на `url: String`/`syncing: Boolean`/`syncStatusText: String`, **удалить** локальный `syncStatusText(...)` (уезжает в composeApp). Итоговая сигнатура и тело:
```kotlin
package app.obsidianmd.settings.presentation

// ... импорты Compose/material3 + ресурсы (как в оригинале, кроме app.obsidianmd.sync.* и SyncStatus) ...
import app.obsidianmd.resources.Res
// ресурсные импорты settings_* / action_* / sync_syncing больше не нужны в самом экране,
// т.к. текст статуса приходит готовой строкой; оставить только реально используемые.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    url: String,
    onSave: (String) -> Unit,
    syncing: Boolean,
    syncStatusText: String,
    onSync: () -> Unit,
    onNavigateBack: () -> Unit,
    onPickFromGitHub: () -> Unit = {},
    aiSection: @Composable () -> Unit = {},
) {
    var draft by remember(url) { mutableStateOf(url) }
    var saved by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { /* TopAppBar с title_settings + кнопкой назад — как в оригинале */ },
    ) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp)) {
            Text(stringResource(Res.string.settings_sync_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.settings_sync_desc), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Button(onClick = onSync, enabled = !syncing, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(Res.string.action_sync_now))
            }
            if (syncStatusText.isNotEmpty()) Text(syncStatusText, Modifier.padding(top = 8.dp))
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            SettingField(
                label = stringResource(Res.string.settings_repo_url_label),
                example = stringResource(Res.string.settings_repo_url_example),
                description = stringResource(Res.string.settings_repo_url_desc),
                value = draft,
                onValueChange = { draft = it; saved = false },
            )
            TextButton(onClick = onPickFromGitHub) { Text(stringResource(Res.string.repo_pick_from_github)) }
            Button(onClick = { onSave(draft); saved = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(Res.string.action_save))
            }
            if (saved) Text(stringResource(Res.string.settings_saved),
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            aiSection()
        }
    }
}
```
Приватный `SettingField(...)` — перенести из оригинала без изменений.

`features/settings/impl/src/androidUnitTest/kotlin/app/obsidianmd/settings/SettingsScreenTest.kt` — перенести из composeApp и переписать вызовы под новую сигнатуру:
```kotlin
// было: SettingsScreen(settings(), onSave = {}, syncStatus = SyncStatus.Idle, onSync = {}, onNavigateBack = {})
// стало:
SettingsScreen(url = "", onSave = {}, syncing = false, syncStatusText = "", onSync = {}, onNavigateBack = {})
```
Убрать импорты `SettingsState`/`SyncStatus`; убрать helper `settings(...)`. Три теста (`showsLabelAndDescriptionForEachSetting`, `saveShowsConfirmation`, `syncButtonTriggersSync`) остаются, только вызовы обновить: в `saveShowsConfirmation` использовать `url = "x"`. Пакет теста — `app.obsidianmd.settings` (видит `internal SettingsScreen`).

Run: `./gradlew :features:settings:impl:testDebugUnitTest --tests "*SettingsScreenTest*"`
Expected: PASS.

- [ ] **Step 7: `SharedPrefsRepoSettingsStore` (public) + DI фичи**

`features/settings/impl/src/androidMain/kotlin/app/obsidianmd/settings/data/SharedPrefsRepoSettingsStore.kt` — перенести из composeApp (класс остаётся **public**), поправить пакет на `app.obsidianmd.settings.data`:
```kotlin
package app.obsidianmd.settings.data

import android.content.Context
import app.obsidianmd.settings.RepoSettingsStore

class SharedPrefsRepoSettingsStore(context: Context) : RepoSettingsStore {
    private val prefs = context.getSharedPreferences("obsidian_settings", Context.MODE_PRIVATE)
    override fun getRemoteUrl(): String? = prefs.getString("remote_url", null)
    override fun setRemoteUrl(url: String) { prefs.edit().putString("remote_url", url).apply() }
}
```

`features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/presentation/SettingsPresentationProviderImpl.kt`:
```kotlin
package app.obsidianmd.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.obsidianmd.settings.SettingsPresentationProvider
import org.koin.compose.viewmodel.koinViewModel

internal class SettingsPresentationProviderImpl : SettingsPresentationProvider {
    @Composable
    override fun Screen(
        syncing: Boolean,
        syncStatusText: String,
        onSync: () -> Unit,
        onNavigateBack: () -> Unit,
        onPickFromGitHub: () -> Unit,
        aiSection: @Composable () -> Unit,
    ) {
        val vm: SettingsViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        SettingsScreen(
            url = state.url,
            onSave = vm::save,
            syncing = syncing,
            syncStatusText = syncStatusText,
            onSync = onSync,
            onNavigateBack = onNavigateBack,
            onPickFromGitHub = onPickFromGitHub,
            aiSection = aiSection,
        )
    }
}
```

`features/settings/impl/src/commonMain/kotlin/app/obsidianmd/settings/di/SettingsModule.kt`:
```kotlin
package app.obsidianmd.settings.di

import app.obsidianmd.settings.SettingsPresentationProvider
import app.obsidianmd.settings.presentation.SettingsPresentationProviderImpl
import app.obsidianmd.settings.presentation.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    includes(settingsPlatformModule)
    single<SettingsPresentationProvider> { SettingsPresentationProviderImpl() }
    viewModel { SettingsViewModel(store = get()) }
}

/** Платформенные байндинги настроек (создание [app.obsidianmd.settings.RepoSettingsStore]). */
expect val settingsPlatformModule: Module
```

`features/settings/impl/src/androidMain/kotlin/app/obsidianmd/settings/di/SettingsModule.android.kt`:
```kotlin
package app.obsidianmd.settings.di

import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.settings.data.SharedPrefsRepoSettingsStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val settingsPlatformModule: Module = module {
    single<RepoSettingsStore> { SharedPrefsRepoSettingsStore(androidContext()) }
}
```

Run: `./gradlew :features:settings:impl:compileDebugKotlinAndroid`
Expected: PASS.

- [ ] **Step 8: `syncStatusText` helper в composeApp**

`composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SyncStatusText.kt` — перенести функцию `syncStatusText(status: SyncStatus): String` из старого `SettingsScreen.kt` (использует `SyncStatus` + `SyncResult`, оба в composeApp), сделать её `internal`:
```kotlin
package app.obsidianmd.ui

import androidx.compose.runtime.Composable
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.error_with_reason
import app.obsidianmd.resources.sync_done_cloned
import app.obsidianmd.resources.sync_synced
import app.obsidianmd.resources.sync_synced_conflicts
import app.obsidianmd.resources.sync_syncing
import app.obsidianmd.resources.sync_up_to_date
import app.obsidianmd.sync.SyncResult
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun syncStatusText(status: SyncStatus): String = when (status) {
    SyncStatus.Idle -> ""
    SyncStatus.Running -> stringResource(Res.string.sync_syncing)
    is SyncStatus.Done -> when (val r = status.result) {
        is SyncResult.Cloned -> stringResource(Res.string.sync_done_cloned)
        is SyncResult.UpToDate -> stringResource(Res.string.sync_up_to_date)
        is SyncResult.Synced ->
            if (r.conflictsResolved > 0) stringResource(Res.string.sync_synced_conflicts, r.conflictsResolved)
            else stringResource(Res.string.sync_synced)
        is SyncResult.Failed -> stringResource(Res.string.error_with_reason, r.reason)
    }
}
```

- [ ] **Step 9: Удалить старые settings-файлы из composeApp**

```bash
git rm composeApp/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt \
       composeApp/src/commonMain/kotlin/app/obsidianmd/settings/SettingsViewModel.kt \
       composeApp/src/androidMain/kotlin/app/obsidianmd/settings/SharedPrefsRepoSettingsStore.kt \
       composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SettingsScreen.kt \
       composeApp/src/commonTest/kotlin/app/obsidianmd/settings/FakeRepoSettingsStore.kt \
       composeApp/src/commonTest/kotlin/app/obsidianmd/settings/RepoSettingsStoreContractTest.kt \
       composeApp/src/commonTest/kotlin/app/obsidianmd/settings/SettingsViewModelTest.kt \
       composeApp/src/androidUnitTest/kotlin/app/obsidianmd/ui/SettingsScreenTest.kt
```

- [ ] **Step 10: Подключить `settings` в composeApp (build + DI + BrainerApp)**

`composeApp/build.gradle.kts` — в `commonMain.dependencies` рядом с ai-строками добавить:
```kotlin
api(project(":features:settings:api"))
implementation(project(":features:settings:impl"))
```

`composeApp/src/androidMain/kotlin/app/obsidianmd/di/AppModule.kt` — удалить строки:
```kotlin
import app.obsidianmd.settings.SettingsViewModel        // удалить
import app.obsidianmd.settings.SharedPrefsRepoSettingsStore  // удалить
// ...
single<RepoSettingsStore> { SharedPrefsRepoSettingsStore(androidContext()) }  // удалить
viewModel { SettingsViewModel(store = get()) }                                // удалить
```
`import app.obsidianmd.settings.RepoSettingsStore` — **оставить** (используется в `SyncConfigProvider`, теперь резолвится из `settingsModule`). Импорт `org.koin.core.module.dsl.viewModel` можно убрать, если больше не используется в файле.

`composeApp/src/androidMain/kotlin/app/obsidianmd/BrainerApp.kt`:
```kotlin
import app.obsidianmd.settings.di.settingsModule
// ...
modules(appModule, vaultModule, authModule(BuildConfig.GITHUB_CLIENT_ID), aiModule, settingsModule)
```

- [ ] **Step 11: Переписать `AppNavHost` под провайдер настроек**

В `composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt`:

Импорты — убрать `app.obsidianmd.settings.SettingsViewModel`, `app.obsidianmd.ui.SettingsScreen`; добавить `app.obsidianmd.settings.RepoSettingsStore`, `app.obsidianmd.settings.SettingsPresentationProvider`, `app.obsidianmd.ui.syncStatusText`.

Заменить получение VM/провайдеров:
```kotlin
// было: val settingsVm: SettingsViewModel = koinViewModel()
val settingsStore = koinInject<RepoSettingsStore>()
val settingsPresentation = koinInject<SettingsPresentationProvider>()
// убрать: val settings by settingsVm.state.collectAsState()
```

`Route.Login` — `hasRepo` считать через store:
```kotlin
auth.Login(onSignedIn = {
    backStack.resetTo(startStack(hasToken = true, hasRepo = !settingsStore.getRemoteUrl().isNullOrBlank()))
})
```

`Route.RepoValidate.onContinue`:
```kotlin
onContinue = {
    settingsStore.setRemoteUrl(key.url)
    backStack.resetTo(stackAfterRepoChosen())
    vm.sync()
},
```

`Route.Settings` — заменить `SettingsScreen(...)` на:
```kotlin
entry<Route.Settings> {
    settingsPresentation.Screen(
        syncing = state.syncStatus is SyncStatus.Running,
        syncStatusText = syncStatusText(state.syncStatus),
        onSync = vm::sync,
        onNavigateBack = { backStack.removeLastOrNull() },
        onPickFromGitHub = { backStack.resetTo(stackForChangeRepo()) },
        aiSection = { ai.SettingsSection(onEditModel = { backStack.add(Route.ModelPicker) }) },
    )
}
```

- [ ] **Step 12: Прогнать тесты и сборку**

Run:
```bash
./gradlew :features:settings:impl:testDebugUnitTest :composeApp:testDebugUnitTest :composeApp:assembleDebug
```
Expected: PASS (все три). Если `RepoSettingsStore` даёт «duplicate class» — проверить, что Step 9 удалил старый файл.

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "feat(settings): вынести настройки в feature-модуль :features:settings"
```

---

### Task 2: Модуль `:features:note` (api + impl), вынос экрана заметки

**Files:**
- Create: все `features/note/**` из списка выше
- Modify: `settings.gradle.kts`, `composeApp/build.gradle.kts`, `composeApp/.../BrainerApp.kt`, `composeApp/.../nav/AppNavHost.kt`
- Delete: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/MarkdownScreen.kt`, `.../ui/EditorToolbar.kt`, `.../editor/MdEdit.kt`, `composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt`
- Test: `features/note/impl/src/commonTest/kotlin/app/obsidianmd/note/MdEditTest.kt`

**Interfaces:**
- Consumes: `:features:vault:api` (`DocRef`, `VaultFile`, `MdBlock`, `renderNote`), `:core:translations`, markdown-либа (`libs.markdown.renderer.m3`).
- Produces: `interface NotePresentationProvider { @Composable fun NoteScreen(title, content, files, documents, loadImage, onOpenPath, onNavigateBack, onSave, bottomBar) }`; `val noteModule: Module`.

- [ ] **Step 1: Подключить модули в settings.gradle.kts**

```kotlin
include(":features:note:api")
include(":features:note:impl")
```

- [ ] **Step 2: Создать `note:api`**

`features/note/api/build.gradle.kts`:
```kotlin
plugins {
    id("obsidian.feature.api")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "app.obsidianmd.note.api" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:vault:api")) // DocRef / VaultFile в сигнатуре экрана
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.material3)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
```
`features/note/api/src/androidMain/AndroidManifest.xml` — скопировать из `features/vault/api/src/androidMain/AndroidManifest.xml`.

`features/note/api/src/commonMain/kotlin/app/obsidianmd/note/NotePresentationProvider.kt`:
```kotlin
package app.obsidianmd.note

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.obsidianmd.vault.DocRef
import app.obsidianmd.vault.VaultFile

/** Точка входа UI заметки для навигации основного модуля. Реализация — в :note:impl. */
interface NotePresentationProvider {
    @Composable
    fun NoteScreen(
        title: String,
        content: String,
        files: List<VaultFile>,
        documents: List<DocRef>,
        loadImage: (String) -> ImageBitmap?,
        onOpenPath: (String) -> Unit,
        onNavigateBack: () -> Unit,
        onSave: (String) -> Unit,
        bottomBar: @Composable () -> Unit,
    )
}
```

- [ ] **Step 3: Создать `note:impl` build**

`features/note/impl/build.gradle.kts`:
```kotlin
plugins {
    id("obsidian.feature.impl")
}

android {
    namespace = "app.obsidianmd.note.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:note:api"))
            implementation(project(":features:vault:api"))
            implementation(project(":core:translations"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

- [ ] **Step 4: Перенести `MdEdit` + `MdEditTest` (red → green)**

`features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/domain/MdEdit.kt` — перенести содержимое `composeApp/editor/MdEdit.kt`, сменить пакет на `app.obsidianmd.note.domain`, пометить `MdEdit` и `EditState` как `internal`.

`features/note/impl/src/commonTest/kotlin/app/obsidianmd/note/MdEditTest.kt` — перенести `composeApp/src/commonTest/.../editor/MdEditTest.kt`, поправить импорт на `app.obsidianmd.note.domain.MdEdit`/`EditState` (тест в том же модуле → видит `internal`).

Run: `./gradlew :features:note:impl:testDebugUnitTest --tests "*MdEditTest*"`
Expected: PASS.

- [ ] **Step 5: Перенести `EditorToolbar` и `NoteScreen`**

`features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/EditorToolbar.kt` — перенести `composeApp/ui/EditorToolbar.kt`, пакет `app.obsidianmd.note.presentation`, пометить `internal`, импорт `EditState` → `app.obsidianmd.note.domain.EditState`.

`features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NoteScreen.kt` — перенести `composeApp/ui/MarkdownScreen.kt`: пакет `app.obsidianmd.note.presentation`; функцию переименовать `MarkdownScreen` → `NoteScreen` и пометить `internal`; приватные `DocumentPickerDialog`/`ZoomableImage` оставить; импорты `app.obsidianmd.editor.*` → `app.obsidianmd.note.domain.*`; импорты `app.obsidianmd.vault.*` (`DocRef`, `MdBlock`, `VaultFile`, `renderNote`) остаются (из `vault:api`). `loadImage`/`onOpenPath`/остальные параметры — без изменений.

Run: `./gradlew :features:note:impl:compileDebugKotlinAndroid`
Expected: PASS.

- [ ] **Step 6: Провайдер + DI**

`features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/presentation/NotePresentationProviderImpl.kt`:
```kotlin
package app.obsidianmd.note.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import app.obsidianmd.note.NotePresentationProvider
import app.obsidianmd.vault.DocRef
import app.obsidianmd.vault.VaultFile

internal class NotePresentationProviderImpl : NotePresentationProvider {
    @Composable
    override fun NoteScreen(
        title: String,
        content: String,
        files: List<VaultFile>,
        documents: List<DocRef>,
        loadImage: (String) -> ImageBitmap?,
        onOpenPath: (String) -> Unit,
        onNavigateBack: () -> Unit,
        onSave: (String) -> Unit,
        bottomBar: @Composable () -> Unit,
    ) {
        NoteScreen(
            title = title, content = content, files = files, documents = documents,
            loadImage = loadImage, onOpenPath = onOpenPath, onNavigateBack = onNavigateBack,
            onSave = onSave, bottomBar = bottomBar,
        )
    }
}
```
> Примечание: internal `NoteScreen` (Step 5) и метод интерфейса `NoteScreen` различаются по контексту (свободная функция vs override); если компилятор ругается на неоднозначность — вызвать функцию с явным импортом или переименовать свободную в `NoteScreenContent`.

`features/note/impl/src/commonMain/kotlin/app/obsidianmd/note/di/NoteModule.kt`:
```kotlin
package app.obsidianmd.note.di

import app.obsidianmd.note.NotePresentationProvider
import app.obsidianmd.note.presentation.NotePresentationProviderImpl
import org.koin.dsl.module

/** DI фичи note. Platform-модуль не нужен: экран stateless, картинки приходят параметром. */
val noteModule = module {
    single<NotePresentationProvider> { NotePresentationProviderImpl() }
}
```

Run: `./gradlew :features:note:impl:compileDebugKotlinAndroid`
Expected: PASS.

- [ ] **Step 7: Удалить старые note-файлы из composeApp**

```bash
git rm composeApp/src/commonMain/kotlin/app/obsidianmd/ui/MarkdownScreen.kt \
       composeApp/src/commonMain/kotlin/app/obsidianmd/ui/EditorToolbar.kt \
       composeApp/src/commonMain/kotlin/app/obsidianmd/editor/MdEdit.kt \
       composeApp/src/commonTest/kotlin/app/obsidianmd/editor/MdEditTest.kt
```

- [ ] **Step 8: Подключить `note` в composeApp (build + BrainerApp + AppNavHost)**

`composeApp/build.gradle.kts` — добавить:
```kotlin
api(project(":features:note:api"))
implementation(project(":features:note:impl"))
```
и **удалить** `implementation(libs.markdown.renderer.m3)` (переехала в `note:impl`; проверить `grep -rn "com.mikepenz\|markdown" composeApp/src` — если нет других использований).

`composeApp/.../BrainerApp.kt`:
```kotlin
import app.obsidianmd.note.di.noteModule
// ...
modules(appModule, vaultModule, authModule(BuildConfig.GITHUB_CLIENT_ID), aiModule, settingsModule, noteModule)
```

`composeApp/.../nav/AppNavHost.kt`:
- Импорты: убрать `app.obsidianmd.ui.MarkdownScreen`; добавить `app.obsidianmd.note.NotePresentationProvider`.
- Рядом с другими провайдерами: `val notePresentation = koinInject<NotePresentationProvider>()`.
- `Route.Note` — заменить `MarkdownScreen(...)` на `notePresentation.NoteScreen(...)` (аргументы 1:1, включая `loadImage = { path -> decodeImage(vm.bytesOf(path)) }`).

- [ ] **Step 9: Прогнать тесты и сборку**

Run:
```bash
./gradlew :features:note:impl:testDebugUnitTest :composeApp:testDebugUnitTest :composeApp:assembleDebug
```
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(note): вынести экран заметки в feature-модуль :features:note"
```

---

### Task 3: Документация и финальная проверка

**Files:**
- Modify: `docs/MODULE_CONVENTIONS.md`

- [ ] **Step 1: Обновить дерево модулей**

В `docs/MODULE_CONVENTIONS.md` в блок дерева добавить после `features/ai/impl/`:
```
features/settings/api/       контракт RepoSettingsStore + SettingsPresentationProvider
features/settings/impl/      экран настроек, SettingsViewModel, SharedPrefsRepoSettingsStore (public)
features/note/api/           контракт NotePresentationProvider (экран заметки)
features/note/impl/          NoteScreen (markdown-просмотр/правка), EditorToolbar, MdEdit
```

- [ ] **Step 2: Отметить исключения**

Добавить в раздел про инкапсуляцию `:impl` строку: «Исключение: `SharedPrefsRepoSettingsStore` (`:settings:impl`) — public, т.к. фоновый `SyncWorker` создаёт его напрямую вне Koin (как public `EncryptedTokenStore` в `:auth:impl`)». В заметку про эталон/shell — уточнить, что `VaultViewModel`, `SyncStatus`, `ConflictDialog` и `decodeImage` остаются в `composeApp` (shell), а `note`/`settings` отдают только stateless-экраны.

- [ ] **Step 3: Финальная полная проверка**

Run:
```bash
./gradlew :features:settings:impl:testDebugUnitTest \
          :features:note:impl:testDebugUnitTest \
          :composeApp:testDebugUnitTest \
          :composeApp:assembleDebug
```
Expected: PASS (все).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs(modules): settings/note feature-модули в MODULE_CONVENTIONS"
```

---

## Self-review

- **Покрытие спеки:** `note:api`/`note:impl` — Task 2; `settings:api`/`settings:impl` — Task 1; изменения composeApp (AppModule, AppNavHost, BrainerApp, syncStatusText, build, settings.gradle) — Task 1 (settings) + Task 2 (note); документация — Task 3; приёмочные кейсы — проверяются на финальной сборке (Task 3) + ручной прогон из спеки. ✔
- **Публичность store:** `SharedPrefsRepoSettingsStore` public (Task 1 Step 7) — покрывает требование `SyncWorker`. ✔
- **Тип-консистентность:** `SettingsPresentationProvider.Screen(syncing, syncStatusText, …)` — сигнатура одна и та же в api (Task 1 Step 2), impl (Step 6–7) и вызове AppNavHost (Step 11); `NotePresentationProvider.NoteScreen(...)` — одинакова в api (Task 2 Step 2), impl (Step 6) и AppNavHost (Step 8). ✔
- **Placeholder-скан:** конкретный код в каждом шаге; исключение — `TopAppBar`-блок `SettingsScreen` («как в оригинале») и приватный `SettingField` переносятся дословно из существующего файла, что явно указано. ✔
- **Analytics:** события синка/заметки остаются в `VaultViewModel` (composeApp), не трогаются — рефактор без изменения поведения. ✔
