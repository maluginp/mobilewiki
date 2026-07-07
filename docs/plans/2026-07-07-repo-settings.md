# Repo Settings Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Задавать URL репозитория в приложении (экран настроек) вместо `BuildConfig.SYNC_REMOTE_URL`.

**Architecture:** `RepoSettingsStore` (Android: SharedPreferences) хранит URL; `SettingsViewModel` + `SettingsScreen` его редактируют; `VaultViewModel` берёт конфиг через `syncConfigProvider: () -> SyncConfig?`, собираемый из настроек + токена. Логика тестируется на фейках.

**Tech Stack:** Compose Multiplatform, kotlinx-coroutines, SharedPreferences.

## Global Constraints

- Пакеты: `app.obsidianmd.settings`, UI в `app.obsidianmd.ui`.
- URL — не секрет (обычный SharedPreferences); токен остаётся в EncryptedSharedPreferences.
- `VaultViewModel.sync()` читает `syncConfigProvider()` при каждом вызове.
- `BuildConfig.SYNC_REMOTE_URL` удаляется; `GITHUB_CLIENT_ID` остаётся.
- Ветка синка — дефолт `main` (в настройки не выносится). Аналитика — не вводится (нет стека).

---

### Task 1: RepoSettingsStore + FakeRepoSettingsStore

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/settings/FakeRepoSettingsStore.kt` (+ контракт-тест)

**Interfaces:**
- Produces: `interface RepoSettingsStore { getRemoteUrl(): String?; setRemoteUrl(url: String) }`; тестовый `FakeRepoSettingsStore`.

- [ ] **Step 1: Падающий тест контракта**

`composeApp/src/commonTest/kotlin/app/obsidianmd/settings/FakeRepoSettingsStore.kt`:
```kotlin
package app.obsidianmd.settings

class FakeRepoSettingsStore : RepoSettingsStore {
    private var url: String? = null
    override fun getRemoteUrl(): String? = url
    override fun setRemoteUrl(url: String) { this.url = url }
}
```
`composeApp/src/commonTest/kotlin/app/obsidianmd/settings/RepoSettingsStoreContractTest.kt`:
```kotlin
package app.obsidianmd.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepoSettingsStoreContractTest {
    @Test
    fun empty_then_set_get() {
        val store = FakeRepoSettingsStore()
        assertNull(store.getRemoteUrl())
        store.setRemoteUrl("https://github.com/u/r.git")
        assertEquals("https://github.com/u/r.git", store.getRemoteUrl())
    }
}
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.settings.RepoSettingsStoreContractTest"`
Expected: FAIL — интерфейса нет (ошибка компиляции).

- [ ] **Step 3: Реализация интерфейса**

`composeApp/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt`:
```kotlin
package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
}
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.settings.RepoSettingsStoreContractTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/settings/RepoSettingsStore.kt composeApp/src/commonTest/kotlin/app/obsidianmd/settings
git commit -m "feat: RepoSettingsStore interface + FakeRepoSettingsStore + contract test"
```

---

### Task 2: SettingsViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/settings/SettingsViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `RepoSettingsStore`. Produces: `class SettingsViewModel(store)` c `url: StateFlow<String>`, `save(url)`.

- [ ] **Step 1: Падающий тест**

```kotlin
package app.obsidianmd.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest {
    @Test
    fun initial_url_from_store() {
        val store = FakeRepoSettingsStore().apply { setRemoteUrl("https://a.git") }
        val vm = SettingsViewModel(store)
        assertEquals("https://a.git", vm.url.value)
    }

    @Test
    fun initial_url_empty_when_unset() {
        assertEquals("", SettingsViewModel(FakeRepoSettingsStore()).url.value)
    }

    @Test
    fun save_persists_and_updates_state() {
        val store = FakeRepoSettingsStore()
        val vm = SettingsViewModel(store)
        vm.save("https://b.git")
        assertEquals("https://b.git", store.getRemoteUrl())
        assertEquals("https://b.git", vm.url.value)
    }
}
```

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.settings.SettingsViewModelTest"`
Expected: FAIL — `SettingsViewModel` нет.

- [ ] **Step 3: Реализация**

```kotlin
package app.obsidianmd.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val store: RepoSettingsStore) {
    private val _url = MutableStateFlow(store.getRemoteUrl() ?: "")
    val url: StateFlow<String> = _url.asStateFlow()

    fun save(url: String) {
        store.setRemoteUrl(url)
        _url.value = url
    }
}
```

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.settings.SettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/settings/SettingsViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/settings/SettingsViewModelTest.kt
git commit -m "feat: SettingsViewModel (url state + save)"
```

---

### Task 3: VaultViewModel — syncConfigProvider

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt` (правка существующих sync-тестов)

**Interfaces:**
- Изменяет `VaultViewModel`: параметр `syncConfig: SyncConfig? = null` → `syncConfigProvider: () -> SyncConfig? = { null }`.

- [ ] **Step 1: Обновить тесты под провайдер (RED)**

В `VaultViewModelTest` заменить в трёх sync-тестах передачу конфига:
- `sync_success_sets_done_and_refreshes`: `syncConfig = syncConfig()` → `syncConfigProvider = { syncConfig() }`.
- `sync_without_config_fails_without_calling_engine`: `syncConfig = null` → `syncConfigProvider = { null }`.
- `sync_conflict_exposes_pending_then_resolves`: `syncConfig = syncConfig()` → `syncConfigProvider = { syncConfig() }`.

- [ ] **Step 2: Прогнать — RED**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: FAIL — параметра `syncConfigProvider` ещё нет (ошибка компиляции).

- [ ] **Step 3: Реализация (правка VaultViewModel)**

Заменить параметр конструктора:
```kotlin
    private val syncConfigProvider: () -> app.obsidianmd.sync.SyncConfig? = { null },
```
(вместо `private val syncConfig: app.obsidianmd.sync.SyncConfig? = null,`)
и в `sync()` заменить `val cfg = syncConfig` на:
```kotlin
        val cfg = syncConfigProvider()
```
Остальное в `sync()` без изменений.

- [ ] **Step 4: Прогнать — GREEN**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt
git commit -m "refactor: VaultViewModel takes syncConfigProvider (runtime config)"
```

---

### Task 4: SettingsScreen + навигация

UI — ручная приёмка (юнит-тестов Compose нет).

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultListScreen.kt` (кнопка «Настройки»)
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt` (навигация + SettingsViewModel)

**Interfaces:**
- Consumes: `SettingsViewModel`, `VaultViewModel`. Produces: `SettingsScreen(currentUrl, onSave, onBack)`; `VaultListScreen(..., onOpenSettings)`.

- [ ] **Step 1: SettingsScreen.kt**

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(currentUrl: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var text by remember(currentUrl) { mutableStateOf(currentUrl) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("URL репозитория")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Button(onClick = { onSave(text) }) { Text("Сохранить") }
    }
}
```

- [ ] **Step 2: VaultListScreen — кнопка «Настройки»**

В сигнатуру `VaultListScreen` добавить параметр `onOpenSettings: () -> Unit` и кнопку рядом с
«Синхронизировать» (в том же `Column`, до списка):
```kotlin
        TextButton(onClick = onOpenSettings, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Настройки")
        }
```
(добавить import `androidx.compose.material3.TextButton`.)

- [ ] **Step 3: App.kt — навигация к настройкам**

Изменить `App(vm)` → `App(vm, settingsVm)`; добавить состояние `showSettings`:
```kotlin
@Composable
fun App(vm: VaultViewModel, settingsVm: app.obsidianmd.settings.SettingsViewModel) {
    val state by vm.state.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val conflict by vm.pendingConflict.collectAsState()
    val url by settingsVm.url.collectAsState()
    var showSettings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }
    MaterialTheme {
        Surface {
            when {
                showSettings -> SettingsScreen(
                    currentUrl = url,
                    onSave = { settingsVm.save(it); showSettings = false },
                    onBack = { showSettings = false },
                )
                state.selected == null -> VaultListScreen(
                    state, syncStatus, onSync = vm::sync, onOpen = vm::open,
                    onOpenSettings = { showSettings = true },
                )
                else -> MarkdownScreen(state.content, onBack = vm::back)
            }
            conflict?.let { ConflictDialog(it, onChoose = vm::resolveConflict) }
        }
    }
}
```
(нужны import для `mutableStateOf`/`remember`/`setValue`/`getValue`/`LaunchedEffect` — как в текущем App.kt.)

- [ ] **Step 4: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain
git commit -m "feat: SettingsScreen + navigation from vault list"
```

---

### Task 5: Android-проводка — SharedPrefs store, provider, убрать SYNC_REMOTE_URL

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/obsidianmd/settings/SharedPrefsRepoSettingsStore.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt`
- Modify: `composeApp/build.gradle.kts` (убрать `SYNC_REMOTE_URL`)

**Interfaces:**
- Consumes: `RepoSettingsStore`, `SettingsViewModel`, `VaultViewModel` (провайдер), `SyncConfig`, `TokenStore`.

- [ ] **Step 1: SharedPrefsRepoSettingsStore.kt**

```kotlin
package app.obsidianmd.settings

import android.content.Context

class SharedPrefsRepoSettingsStore(context: Context) : RepoSettingsStore {
    private val prefs = context.getSharedPreferences("obsidian_settings", Context.MODE_PRIVATE)
    override fun getRemoteUrl(): String? = prefs.getString("remote_url", null)
    override fun setRemoteUrl(url: String) { prefs.edit().putString("remote_url", url).apply() }
}
```

- [ ] **Step 2: build.gradle.kts — убрать SYNC_REMOTE_URL**

Удалить строку:
```kotlin
        buildConfigField("String", "SYNC_REMOTE_URL", "\"${localProp("sync.remoteUrl")}\"")
```
(оставить только `GITHUB_CLIENT_ID`.)

- [ ] **Step 3: MainActivity — provider из настроек + SettingsViewModel**

В `MainActivity`: создать `settingsStore = SharedPrefsRepoSettingsStore(applicationContext)`,
`settingsVm = SettingsViewModel(settingsStore)`. Заменить построение `VaultViewModel` на
провайдер и передать `settingsVm` в `App`:
```kotlin
        val settingsStore = app.obsidianmd.settings.SharedPrefsRepoSettingsStore(applicationContext)
        val settingsVm = app.obsidianmd.settings.SettingsViewModel(settingsStore)
        // ...внутри ветки loggedIn:
        val vm = VaultViewModel(
            repo, lifecycleScope, Dispatchers.IO,
            gitSync = JGitSync(),
            syncConfigProvider = {
                settingsStore.getRemoteUrl()?.takeIf { it.isNotBlank() }?.let { url ->
                    SyncConfig(remoteUrl = url, localPath = root.toString(), token = store.get())
                }
            },
            resolver = UiConflictResolver(),
        )
        App(vm, settingsVm)
```
(убрать прежнее чтение `BuildConfig.SYNC_REMOTE_URL`.)

- [ ] **Step 4: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Ручная приёмка**

Прогнать кейсы из спеки (задать URL → синк; пустой URL → «не настроен»; URL сохраняется между
запусками).

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/androidMain
git commit -m "feat: wire repo settings store into sync config provider; drop SYNC_REMOTE_URL"
```

---

## Self-review

**Покрытие спеки:**
- `RepoSettingsStore` + фейк → Task 1; Android SharedPreferences → Task 5.
- `SettingsViewModel` → Task 2.
- `VaultViewModel` провайдер конфига → Task 3.
- `SettingsScreen` + навигация → Task 4.
- Проводка провайдера, удаление `SYNC_REMOTE_URL` → Task 5.
- Приёмочные кейсы → в спеке; Task 5 Step 5.
- Аналитика → неприменима (нет стека).

**Placeholder-скан:** реальный код/команды в каждом шаге; заглушек нет.

**Согласованность типов:** `RepoSettingsStore{getRemoteUrl():String?, setRemoteUrl(url)}`;
`SettingsViewModel(store){url:StateFlow<String>, save(url)}`;
`VaultViewModel(..., syncConfigProvider: () -> SyncConfig?)`;
`SettingsScreen(currentUrl,onSave,onBack)`; `VaultListScreen(state,syncStatus,onSync,onOpen,onOpenSettings)`;
`App(vm, settingsVm)`. Имена согласованы Task 1→5. `SYNC_REMOTE_URL` удаляется, `GITHUB_CLIENT_ID` остаётся.
