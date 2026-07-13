# AI feature-модуль (`ai:api` / `ai:impl`) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Вынести весь AI-чат (движок, экраны, настройки AI, DI) из `composeApp` в отдельный feature-модуль `ai:api` + `ai:impl` по эталону `:auth`/`:vault`.

**Architecture:** `ai:api` — контракты и `AiPresentationProvider` (@Composable-вход), пакет `app.obsidianmd.ai`, namespace `app.obsidianmd.ai.api`. `ai:impl` — вся реализация `internal`, пакет `app.obsidianmd.ai`, namespace `app.obsidianmd.ai.impl`; наружу торчат только `aiModule` (Koin) и `AiPresentationProvider`. HttpClient и `VaultRepository` берутся из общего Koin-графа. Импорты `app.obsidianmd.ai.*` в `composeApp` не меняются — меняется только модуль-источник.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, Ktor, androidx.security.crypto, kotlinx.serialization, Robolectric.

## Global Constraints

- Все классы в `:ai:impl` — `internal`; public наружу только `aiModule` и `AiPresentationProvider` (через `api`). (`MODULE_CONVENTIONS.md` §54)
- Kotlin-пакет в обоих модулях — `app.obsidianmd.ai` (импорты по проекту не меняются). Namespace: api `app.obsidianmd.ai.api`, impl `app.obsidianmd.ai.impl`.
- Слои внутри `impl` — пакеты `data/`, `domain/`, `presentation/`, `di/`.
- Строки — только в `:core:translations` (пакет `app.obsidianmd.resources`), новых строк не добавляем (переиспользуем существующие).
- Правила зависимостей: `:ai:api` → `:*:api`+`:core:*`; `:ai:impl` → свой api, чужие api, core — не чужой impl; `composeApp` → api через `api(...)`, impl через `implementation(...)`.
- DI фичи — `val aiModule` в commonMain + `expect/actual aiPlatformModule` для платформенных байндингов (нужен `Context`).
- Тесты гоняются: `./gradlew :ai:impl:testDebugUnitTest` и `./gradlew :composeApp:testDebugUnitTest`.
- Аналитика: это рефакторинг, поведение не меняется — существующие события (`ai_message`, `ai_response`, `ai_error`, `ai_write_approved/rejected`, `ai_toggled`, `ai_provider_changed`, `ai_model_changed`) переезжают вместе с кодом дословно; новых событий не вводим.
- Единственное видимое UX-изменение: две кнопки «Сохранить» — под repo-URL (только url) и под AI-секцией (ключ + base URL). Провайдер/модель/switch сохраняются сразу, как сейчас.

---

### Task 1: Модуль `:ai:api` и перенос чистых контрактов

Создаём api-модуль и переносим в него типы, у которых нет реализации-зависимостей: `ChatClient`, `ApiKeyStore`, `AiProvider`, `ModelInfo`, `DEFAULT_MODEL`. Плюс новые интерфейсы `AiSettingsStore` и `AiPresentationProvider` (пустые тела появятся в Task 3/5 — здесь только сигнатуры, чтобы api собирался). `ModelInfo`+`DEFAULT_MODEL` сейчас лежат в `OpenRouterClient.kt` — их выносим в api, а `fetchModels`+`OpenRouterClient` остаются для Task 2.

**Files:**
- Create: `settings.gradle.kts` (modify) — добавить `include(":ai:api")`, `include(":ai:impl")`
- Create: `ai/api/build.gradle.kts`
- Create: `ai/api/src/commonMain/kotlin/app/obsidianmd/ai/ChatClient.kt` (move from composeApp)
- Create: `ai/api/src/commonMain/kotlin/app/obsidianmd/ai/ApiKeyStore.kt` (move)
- Create: `ai/api/src/commonMain/kotlin/app/obsidianmd/ai/AiProvider.kt` (move; включает `ModelInfo` и `DEFAULT_MODEL`, перенесённые из `OpenRouterClient.kt`)
- Create: `ai/api/src/commonMain/kotlin/app/obsidianmd/ai/AiSettingsStore.kt` (new)
- Create: `ai/api/src/commonMain/kotlin/app/obsidianmd/ai/AiPresentationProvider.kt` (new)
- Modify: `composeApp/build.gradle.kts` — добавить `api(project(":ai:api"))`
- Modify: `composeApp/.../ai/OpenRouterClient.kt` — убрать `ModelInfo`/`DEFAULT_MODEL` (уехали в api)

**Interfaces:**
- Produces: `interface ChatClient`, `interface ApiKeyStore`, `enum AiProvider`, `data class ModelInfo`, `const val DEFAULT_MODEL`, `interface AiSettingsStore`, `interface AiPresentationProvider`.

- [ ] **Step 1: Создать `ai/api/build.gradle.kts`** (копия `auth/api/build.gradle.kts` — api с compose для провайдера)

```kotlin
plugins {
    id("obsidian.feature.api")
    // api отдаёт @Composable-провайдер экранов AI → нужен compose
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "app.obsidianmd.ai.api" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":vault:api")) // AiPresentationProvider не тянет vault, но ModelInfo/типы нейтральны; см. примечание
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

Примечание: если по факту `:ai:api` не использует `:vault:api` — убрать эту строку (api должен быть минимальным). Оставить только `compose.runtime`.

- [ ] **Step 2: Прописать модуль в `settings.gradle.kts`**

Добавить после `include(":auth:impl")`:
```kotlin
include(":ai:api")
include(":ai:impl")
```

- [ ] **Step 3: Перенести `ChatClient.kt` и `ApiKeyStore.kt` в api дословно**

`git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ai/ChatClient.kt ai/api/src/commonMain/kotlin/app/obsidianmd/ai/ChatClient.kt`
`git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ai/ApiKeyStore.kt ai/api/src/commonMain/kotlin/app/obsidianmd/ai/ApiKeyStore.kt`
Пакет `app.obsidianmd.ai` не меняется.

- [ ] **Step 4: Перенести `AiProvider.kt` в api и втянуть в него `ModelInfo`+`DEFAULT_MODEL`**

`git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ai/AiProvider.kt ai/api/src/commonMain/kotlin/app/obsidianmd/ai/AiProvider.kt`
Из `composeApp/.../ai/OpenRouterClient.kt` вырезать блок `const val DEFAULT_MODEL = ...` и `data class ModelInfo(...)` и вставить в `ai/api/.../AiProvider.kt` (оба нужны `AiProvider` и остаются чистыми data/const). `OpenRouterClient.kt` пока остаётся в composeApp (переедет в Task 2) — он импортирует `ModelInfo`/`DEFAULT_MODEL` из того же пакета, импорт не меняется.

- [ ] **Step 5: Создать `AiSettingsStore.kt` (api)**

```kotlin
package app.obsidianmd.ai

/** Настройки AI (провайдер/модель/вкл-выкл/base URL). Реализация — в :ai:impl (SharedPrefs). */
interface AiSettingsStore {
    fun isAiEnabled(): Boolean
    fun setAiEnabled(enabled: Boolean)
    fun getProvider(): String?
    fun setProvider(id: String)
    fun getCustomBaseUrl(): String
    fun setCustomBaseUrl(url: String)
    fun getAiModel(provider: String): String
    fun setAiModel(provider: String, model: String)
}
```

- [ ] **Step 6: Создать `AiPresentationProvider.kt` (api)**

```kotlin
package app.obsidianmd.ai

import androidx.compose.runtime.Composable

/**
 * Точка входа UI фичи AI для навигации основного модуля. Реализация — в :ai:impl (internal),
 * подключается через DI. Основной модуль не знает об экранах/ViewModel'ях фичи.
 */
interface AiPresentationProvider {
    /** true — если AI включён (для нижней навигации Brain↔AI в хосте). */
    @Composable fun aiEnabled(): Boolean

    /** Экран чата: сам собирает VM из конфига/ключа; не сконфигурирован → заглушка с onOpenSettings. */
    @Composable fun Chat(
        onOpenFile: (path: String) -> Unit,
        onOpenSettings: () -> Unit,
        bottomBar: @Composable () -> Unit,
    )

    /** Экран выбора модели. */
    @Composable fun ModelPicker(onNavigateBack: () -> Unit)

    /** AI-секция для встраивания в экран настроек composeApp. onEditModel → переход к ModelPicker. */
    @Composable fun SettingsSection(onEditModel: () -> Unit)
}
```

- [ ] **Step 7: Добавить зависимость composeApp на api**

В `composeApp/build.gradle.kts` в `commonMain.dependencies` рядом с `api(project(":auth:api"))` добавить:
```kotlin
api(project(":ai:api"))
```

- [ ] **Step 8: Проверить сборку api**

Run: `./gradlew :ai:api:compileDebugKotlinAndroid`
Expected: PASS (модуль собирается). composeApp пока НЕ собирается (Task 2 доберёт impl) — это ожидаемо.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts ai/api composeApp/build.gradle.kts composeApp/src/commonMain/kotlin/app/obsidianmd/ai/OpenRouterClient.kt
git commit -m "feat(ai): создать модуль :ai:api с контрактами AI"
```

---

### Task 2: Модуль `:ai:impl` и перенос движка + его тестов

Переносим движок (`OpenRouterClient`+`fetchModels`, `AiAgent`, `AiViewModel`) и все `commonTest/ai/*`-тесты. Классы движка помечаем `internal` и раскладываем по слоям. `composeApp` пока ссылается на них — временно сломается, поэтому этот таск завершается зелёными тестами `:ai:impl`, а полная сборка composeApp чинится в Task 6–7.

**Files:**
- Create: `ai/impl/build.gradle.kts`
- Move: `composeApp/.../ai/OpenRouterClient.kt` → `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/data/OpenRouterClient.kt`
- Move: `composeApp/.../ai/AiAgent.kt` → `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/domain/AiAgent.kt`
- Move: `composeApp/.../ai/AiViewModel.kt` → `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/AiViewModel.kt`
- Move tests: `composeApp/src/commonTest/kotlin/app/obsidianmd/ai/{AiAgentTest,AiProviderTest,AiTestFixtures,AiViewModelTest,ApiKeyStoreContractTest,FakeApiKeyStore,ModelInfoTest,OpenRouterClientTest}.kt` → `ai/impl/src/commonTest/kotlin/app/obsidianmd/ai/`

**Interfaces:**
- Consumes: `ChatClient`, `ApiKeyStore`, `AiProvider`, `ModelInfo`, `DEFAULT_MODEL` (из `:ai:api`), `VaultRepository`/`resolveWikiLink`/`SkillMeta` (из `:vault:api`).
- Produces: `internal class OpenRouterClient`, `internal class AiAgent`, `internal class AiViewModel`, `internal suspend fun fetchModels(...)`.

- [ ] **Step 1: Создать `ai/impl/build.gradle.kts`** (по `auth/impl` — с ktor+serialization+robolectric)

```kotlin
plugins {
    id("obsidian.feature.impl")
    alias(libs.plugins.kotlinSerialization) // @Serializable модели OpenRouter
}

android {
    namespace = "app.obsidianmd.ai.impl"
    testOptions {
        unitTests { isIncludeAndroidResources = true } // Robolectric + Compose UI tests
    }
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":ai:api"))
            implementation(project(":vault:api"))
            implementation(project(":core:analytics"))
            implementation(project(":core:translations"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.security.crypto) // EncryptedApiKeyStore
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.robolectric)
        }
    }
}
```

- [ ] **Step 2: Скопировать debug-манифест для Robolectric UI-тестов**

`mkdir -p ai/impl/src/androidDebug && cp auth/impl/src/androidDebug/AndroidManifest.xml ai/impl/src/androidDebug/AndroidManifest.xml`

- [ ] **Step 3: Перенести файлы движка (git mv) и пометить классы `internal`**

```bash
git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ai/OpenRouterClient.kt ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/data/OpenRouterClient.kt
git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ai/AiAgent.kt      ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/domain/AiAgent.kt
git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ai/AiViewModel.kt  ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/AiViewModel.kt
```
В перенесённых файлах: пакет остаётся `app.obsidianmd.ai`; добавить `internal` к `class OpenRouterClient`, `class AiAgent`, `class AiViewModel`, `fun fetchModels`, `fun interface WriteApprover`, `sealed interface AiResult`, `data class ChatTurn`, `sealed interface AiStatus`, `data class AiState`. `ChatMessage` (используется api? нет — только внутри) тоже `internal`.

- [ ] **Step 4: Перенести тесты движка (git mv)**

```bash
git mv composeApp/src/commonTest/kotlin/app/obsidianmd/ai/*.kt ai/impl/src/commonTest/kotlin/app/obsidianmd/ai/
```
(переносятся `AiAgentTest`, `AiProviderTest`, `AiTestFixtures`, `AiViewModelTest`, `ApiKeyStoreContractTest`, `FakeApiKeyStore`, `ModelInfoTest`, `OpenRouterClientTest`). Пакет не меняется; тела не трогаем.

- [ ] **Step 5: Запустить тесты `:ai:impl` — сначала убедиться, что падают на компиляции при неверном `internal`, затем зелёные**

Run: `./gradlew :ai:impl:testDebugUnitTest`
Expected: PASS. Если тест в `commonTest` не видит `internal`-класс — он в том же модуле, `internal` виден; при ошибке видимости проверить, что тест реально в `:ai:impl`, а не остался в composeApp.

- [ ] **Step 6: Commit**

```bash
git add ai/impl
git commit -m "feat(ai): перенести движок AI и тесты в :ai:impl"
```

---

### Task 3: `AiSettingsStore` (реализация + контракт-тест) и ужатие `RepoSettingsStore`

AI-настройки переезжают в отдельный SharedPreferences-файл `ai_settings` (без миграции — пользователей нет). `RepoSettingsStore` в composeApp ужимается до url.

**Files:**
- Create test: `ai/impl/src/commonTest/kotlin/app/obsidianmd/ai/FakeAiSettingsStore.kt`
- Create test: `ai/impl/src/commonTest/kotlin/app/obsidianmd/ai/AiSettingsStoreContractTest.kt`
- Create: `ai/impl/src/androidMain/kotlin/app/obsidianmd/ai/data/SharedPrefsAiSettingsStore.kt`
- Modify: `composeApp/.../settings/RepoSettingsStore.kt` — убрать AI-методы
- Modify: `composeApp/.../settings/SharedPrefsRepoSettingsStore.kt` — убрать AI-методы
- Modify: `composeApp/src/commonTest/.../settings/FakeRepoSettingsStore.kt` — убрать AI-поля
- Modify: `composeApp/src/commonTest/.../settings/RepoSettingsStoreContractTest.kt` — убрать AI-кейсы

**Interfaces:**
- Consumes: `AiSettingsStore` (api).
- Produces: `internal class SharedPrefsAiSettingsStore(context) : AiSettingsStore`, `FakeAiSettingsStore` (test).

- [ ] **Step 1: Написать `FakeAiSettingsStore` (in-memory) и падающий контракт-тест**

`FakeAiSettingsStore.kt`:
```kotlin
package app.obsidianmd.ai

class FakeAiSettingsStore : AiSettingsStore {
    private var aiEnabled = false
    private var provider: String? = null
    private var customBaseUrl = ""
    private val models = mutableMapOf<String, String>()
    override fun isAiEnabled() = aiEnabled
    override fun setAiEnabled(enabled: Boolean) { aiEnabled = enabled }
    override fun getProvider() = provider
    override fun setProvider(id: String) { provider = id }
    override fun getCustomBaseUrl() = customBaseUrl
    override fun setCustomBaseUrl(url: String) { customBaseUrl = url }
    override fun getAiModel(provider: String) = models[provider] ?: ""
    override fun setAiModel(provider: String, model: String) { models[provider] = model }
}
```

`AiSettingsStoreContractTest.kt` (первый падающий тест — на `FakeAiSettingsStore`, задаёт контракт):
```kotlin
package app.obsidianmd.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSettingsStoreContractTest {
    private fun store(): AiSettingsStore = FakeAiSettingsStore()

    @Test fun ai_disabled_by_default() { assertFalse(store().isAiEnabled()) }

    @Test fun ai_enabled_persists() {
        val s = store(); s.setAiEnabled(true); assertTrue(s.isAiEnabled())
    }

    @Test fun model_is_per_provider() {
        val s = store()
        s.setAiModel("openrouter", "a"); s.setAiModel("provod", "b")
        assertEquals("a", s.getAiModel("openrouter"))
        assertEquals("b", s.getAiModel("provod"))
    }

    @Test fun base_url_persists() {
        val s = store(); s.setCustomBaseUrl("https://h/v1"); assertEquals("https://h/v1", s.getCustomBaseUrl())
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает (нет `AiSettingsStore`/`FakeAiSettingsStore`)**

Run: `./gradlew :ai:impl:testDebugUnitTest --tests "*AiSettingsStoreContractTest*"`
Expected: FAIL на компиляции, пока файлы не добавлены (после добавления — PASS, т.к. `FakeAiSettingsStore` тривиален). Это фиксирует контракт для реальной реализации в Step 4.

- [ ] **Step 3: Реализовать `SharedPrefsAiSettingsStore` (androidMain, свой файл `ai_settings`)**

```kotlin
package app.obsidianmd.ai

import android.content.Context

internal class SharedPrefsAiSettingsStore(context: Context) : AiSettingsStore {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    override fun isAiEnabled(): Boolean = prefs.getBoolean("ai_enabled", false)
    override fun setAiEnabled(enabled: Boolean) { prefs.edit().putBoolean("ai_enabled", enabled).apply() }
    override fun getProvider(): String? = prefs.getString("ai_provider", null)
    override fun setProvider(id: String) { prefs.edit().putString("ai_provider", id).apply() }
    override fun getCustomBaseUrl(): String = prefs.getString("ai_custom_base_url", null) ?: ""
    override fun setCustomBaseUrl(url: String) { prefs.edit().putString("ai_custom_base_url", url).apply() }
    override fun getAiModel(provider: String): String = prefs.getString(modelKey(provider), null) ?: ""
    override fun setAiModel(provider: String, model: String) { prefs.edit().putString(modelKey(provider), model).apply() }

    // ponytail: отдельный файл ai_settings, без миграции старых ключей из obsidian_settings —
    // пользователей ещё нет. Миграция — если появятся установленные сборки.
    private fun modelKey(provider: String) =
        if (provider == "openrouter") "ai_model" else "ai_model_$provider"
}
```

- [ ] **Step 4: Прогнать контракт-тест (Fake) зелёным**

Run: `./gradlew :ai:impl:testDebugUnitTest --tests "*AiSettingsStoreContractTest*"`
Expected: PASS.

- [ ] **Step 5: Ужать `RepoSettingsStore` до url**

`composeApp/.../settings/RepoSettingsStore.kt`:
```kotlin
package app.obsidianmd.settings

interface RepoSettingsStore {
    fun getRemoteUrl(): String?
    fun setRemoteUrl(url: String)
}
```
`SharedPrefsRepoSettingsStore.kt` — удалить все AI-методы, оставить `remote_url` (файл `obsidian_settings` не трогаем). `FakeRepoSettingsStore.kt` — удалить AI-поля/методы, оставить url. `RepoSettingsStoreContractTest.kt` — удалить AI-кейсы (провайдер/модель/aiEnabled/baseUrl), оставить url-кейсы.

- [ ] **Step 6: Прогнать composeApp-тесты стора (AI-кейсы ушли, url-кейсы зелёные)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*RepoSettingsStoreContractTest*"`
Expected: PASS (composeApp ещё не собирается целиком из-за движка — если таск-раннер требует полной компиляции модуля, этот шаг закрывается вместе с Task 7; тогда здесь только правим файлы и коммитим, финальный прогон в Task 7).

- [ ] **Step 7: Commit**

```bash
git add ai/impl composeApp/src/commonMain/kotlin/app/obsidianmd/settings composeApp/src/commonTest/kotlin/app/obsidianmd/settings
git commit -m "feat(ai): AiSettingsStore (отдельный prefs) + ужать RepoSettingsStore до url"
```

---

### Task 4: `AiSettingsViewModel` (ai:impl) + ужатие `SettingsViewModel`

AI-логику конфига переносим из `SettingsViewModel` в новый `AiSettingsViewModel` (internal, ai:impl). AI-кейсы из `SettingsViewModelTest` переезжают в `AiSettingsViewModelTest`. `SettingsViewModel`/`SettingsState` в composeApp ужимаются до url.

**Files:**
- Create: `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/AiSettingsViewModel.kt`
- Create test: `ai/impl/src/commonTest/kotlin/app/obsidianmd/ai/AiSettingsViewModelTest.kt` (AI-кейсы из SettingsViewModelTest)
- Modify: `composeApp/.../settings/SettingsViewModel.kt` — только url
- Modify: `composeApp/src/commonTest/.../settings/SettingsViewModelTest.kt` — только url-кейсы

**Interfaces:**
- Consumes: `AiSettingsStore`, `ApiKeyStore`, `AiProvider`, `ModelInfo` (api); `FakeAiSettingsStore`, `FakeApiKeyStore` (test).
- Produces: `internal class AiSettingsViewModel`, `internal data class AiSettingsState(provider, customBaseUrl, apiKey, aiEnabled, aiModel, models, modelsLoading)`.

- [ ] **Step 1: Написать падающий `AiSettingsViewModelTest` (перенести AI-кейсы дословно, поменяв тип VM/State/Store)**

Скопировать из `composeApp/.../SettingsViewModelTest.kt` тесты 48–124 (`ai_disabled_by_default`, `ai_enabled_initial_from_store`, `set_ai_enabled_persists_and_updates_state`, `enabling_ai_loads_models_into_state`, `switching_provider_swaps_key_and_model_and_persists`, `key_and_model_are_saved_under_current_provider`, `custom_base_url_persists_and_feeds_model_fetch`, `reload_refetches_even_when_already_loaded`). Заменить `SettingsViewModel`→`AiSettingsViewModel`, `SettingsState`→`AiSettingsState`, `FakeRepoSettingsStore`→`FakeAiSettingsStore`, конструктор — `AiSettingsViewModel(store = ..., apiKeyStore = ..., fetchModels = ...)`. Первый падающий тест — `switching_provider_swaps_key_and_model_and_persists`.

- [ ] **Step 2: Запустить — FAIL (нет `AiSettingsViewModel`)**

Run: `./gradlew :ai:impl:testDebugUnitTest --tests "*AiSettingsViewModelTest*"`
Expected: FAIL «unresolved reference: AiSettingsViewModel».

- [ ] **Step 3: Реализовать `AiSettingsViewModel` (перенос логики из старого SettingsViewModel)**

Вынести из `composeApp/.../SettingsViewModel.kt` всё AI: поля state (provider/customBaseUrl/apiKey/aiEnabled/aiModel/models/modelsLoading), init-загрузку моделей, `setAiEnabled`, `setProvider`, `setCustomBaseUrl`, `saveKey`, `setAiModel`, `ensureModels`, `reloadModels`, `loadModels`. Класс/стейт — `internal`. Конструктор:
```kotlin
internal class AiSettingsViewModel(
    private val store: AiSettingsStore,
    private val apiKeyStore: ApiKeyStore,
    private val fetchModels: suspend (AiProvider, String) -> List<ModelInfo>,
) : ViewModel() { /* тело — перенесённая AI-логика */ }
```
`Analytics.event(...)` вызовы переносятся дословно.

- [ ] **Step 4: Прогнать — PASS**

Run: `./gradlew :ai:impl:testDebugUnitTest --tests "*AiSettingsViewModelTest*"`
Expected: PASS.

- [ ] **Step 5: Ужать `SettingsViewModel`/`SettingsState` (composeApp) до url и почистить тест**

```kotlin
package app.obsidianmd.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsState(val url: String = "")

class SettingsViewModel(private val store: RepoSettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState(url = store.getRemoteUrl() ?: ""))
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    fun save(url: String) { store.setRemoteUrl(url); _state.update { it.copy(url = url) } }
}
```
`SettingsViewModelTest.kt` — оставить только `initial_url_from_store`, `initial_url_empty_when_unset`, `save_persists_and_updates_state`; удалить AI-кейсы и неиспользуемые импорты/`dispatcher`, если больше не нужны.

- [ ] **Step 6: Commit**

```bash
git add ai/impl composeApp/src/commonMain/kotlin/app/obsidianmd/settings/SettingsViewModel.kt composeApp/src/commonTest/kotlin/app/obsidianmd/settings/SettingsViewModelTest.kt
git commit -m "feat(ai): AiSettingsViewModel в :ai:impl + ужать SettingsViewModel до url"
```

---

### Task 5: Экраны фичи + `AiSettingsSection` + `AiPresentationProviderImpl` + перенос UI-тестов

Переносим `AiChatScreen`, `ModelPickerScreen`, `EncryptedApiKeyStore` и UI-тесты в `ai:impl`; извлекаем AI-секцию настроек в `AiSettingsSection`; собираем `AiPresentationProviderImpl` (внутри — прежний glue `rememberAiViewModel` + заглушка `AiUnavailable`).

**Files:**
- Move: `composeApp/.../ui/AiChatScreen.kt` → `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/AiChatScreen.kt`
- Move: `composeApp/.../ui/ModelPickerScreen.kt` → `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/ModelPickerScreen.kt`
- Move: `composeApp/src/androidMain/.../ai/EncryptedApiKeyStore.kt` → `ai/impl/src/androidMain/kotlin/app/obsidianmd/ai/data/EncryptedApiKeyStore.kt` (+ `internal`)
- Move UI tests: `composeApp/src/androidUnitTest/.../ui/{AiChatScreenTest,ModelPickerScreenTest}.kt` → `ai/impl/src/androidUnitTest/kotlin/app/obsidianmd/ai/presentation/`
- Create: `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/AiSettingsSection.kt` (извлечь AI-секцию + `ProviderDropdown` + `ModelRow` из `SettingsScreen.kt`)
- Create: `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/AiPresentationProviderImpl.kt`
- Modify: `composeApp/.../ui/SettingsScreen.kt` — удалить AI-секцию/`ProviderDropdown`/`ModelRow`, добавить свою кнопку сохранения url

**Interfaces:**
- Consumes: `AiSettingsViewModel`, `AiViewModel`, `AiSettingsStore`, `ApiKeyStore`, `AiProvider`, `VaultRepository` (для `allFiles` в чате), `koinViewModel`/`koinInject`.
- Produces: `internal class AiPresentationProviderImpl : AiPresentationProvider`, `internal fun AiSettingsSection(...)`, `internal class EncryptedApiKeyStore`.

- [ ] **Step 1: Перенести экраны и `EncryptedApiKeyStore` (git mv), сделать `internal`**

```bash
git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ui/AiChatScreen.kt    ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/AiChatScreen.kt
git mv composeApp/src/commonMain/kotlin/app/obsidianmd/ui/ModelPickerScreen.kt ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/presentation/ModelPickerScreen.kt
git mv composeApp/src/androidMain/kotlin/app/obsidianmd/ai/EncryptedApiKeyStore.kt ai/impl/src/androidMain/kotlin/app/obsidianmd/ai/data/EncryptedApiKeyStore.kt
```
Сменить пакет экранов `app.obsidianmd.ui` → `app.obsidianmd.ai` (composeApp-импорты `app.obsidianmd.ui.AiChatScreen`/`ModelPickerScreen` уйдут — эти экраны больше не вызываются напрямую, только через провайдер, см. Task 7). Пометить `internal fun AiChatScreen`, `internal fun ModelPickerScreen`, `internal class EncryptedApiKeyStore`.

- [ ] **Step 2: Перенести UI-тесты экранов**

```bash
git mv composeApp/src/androidUnitTest/kotlin/app/obsidianmd/ui/AiChatScreenTest.kt     ai/impl/src/androidUnitTest/kotlin/app/obsidianmd/ai/presentation/AiChatScreenTest.kt
git mv composeApp/src/androidUnitTest/kotlin/app/obsidianmd/ui/ModelPickerScreenTest.kt ai/impl/src/androidUnitTest/kotlin/app/obsidianmd/ai/presentation/ModelPickerScreenTest.kt
```
Поправить пакет тестов на `app.obsidianmd.ai.presentation` и импорты экранов (теперь тот же пакет `app.obsidianmd.ai`).

- [ ] **Step 3: Извлечь `AiSettingsSection` из `SettingsScreen.kt`**

Создать `AiSettingsSection.kt`: перенести из `SettingsScreen` весь AI-блок (Switch «включить AI» + описание, `if (aiEnabled) { ProviderDropdown; base URL SettingField; key SettingField; ModelRow }`) вместе с приватными `ProviderDropdown` и `ModelRow`. Секция получает свой VM и свою кнопку «Сохранить» (ключ + base URL):
```kotlin
package app.obsidianmd.ai

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AiSettingsSection(onEditModel: () -> Unit) {
    val vm: AiSettingsViewModel = koinViewModel()
    val state by vm.state.collectAsState()
    // локальные черновики key/baseUrl (перенести из SettingsScreen) + кнопка «Сохранить»,
    // вызывающая vm.saveKey(key) и vm.setCustomBaseUrl(baseUrl).
    // Switch → vm.setAiEnabled; ProviderDropdown → vm.setProvider; ModelRow onEdit → onEditModel.
    // Использует те же Res.string.settings_* строки.
}
```
`SettingField` сейчас приватный в `SettingsScreen.kt` — он нужен обеим сторонам. Вариант (ponytail): сделать копию `SettingField` внутри `AiSettingsSection.kt` (маленький компонент, дублирование дешевле нового shared-модуля). `// ponytail: SettingField скопирован в секцию; вынести в core-ui, если понадобится третьему потребителю`.

- [ ] **Step 4: Собрать `AiPresentationProviderImpl` (перенести glue из AppNavHost)**

```kotlin
package app.obsidianmd.ai

import androidx.compose.runtime.Composable
// ... compose/koin импорты

internal class AiPresentationProviderImpl : AiPresentationProvider {

    @Composable
    override fun aiEnabled(): Boolean {
        val vm: AiSettingsViewModel = koinViewModel()
        return vm.state.collectAsState().value.aiEnabled
    }

    @Composable
    override fun Chat(onOpenFile: (String) -> Unit, onOpenSettings: () -> Unit, bottomBar: @Composable () -> Unit) {
        val settingsVm: AiSettingsViewModel = koinViewModel()
        val settings by settingsVm.state.collectAsState()
        val aiVm = rememberAiViewModel(settings)              // перенести из AppNavHost
        val repo: VaultRepository = koinInject()
        if (aiVm != null) {
            val aiState by aiVm.state.collectAsState()
            AiChatScreen(
                messages = aiState.messages, status = aiState.status, pendingWrite = aiState.pendingWrite,
                onSend = aiVm::send, onApprove = aiVm::approveWrite, onReject = aiVm::rejectWrite,
                files = repo.listAllFiles(),                   // взять список файлов из репозитория
                onOpenFile = onOpenFile, bottomBar = bottomBar,
            )
        } else {
            AiUnavailable(onOpenSettings)                      // перенести из AppNavHost
        }
    }

    @Composable
    override fun ModelPicker(onNavigateBack: () -> Unit) {
        val vm: AiSettingsViewModel = koinViewModel()
        val s by vm.state.collectAsState()
        LaunchedEffect(Unit) { vm.ensureModels() }
        ModelPickerScreen(
            models = s.models, loading = s.modelsLoading, selected = s.aiModel,
            onSelect = { vm.setAiModel(it); onNavigateBack() },
            onRefresh = vm::reloadModels, onNavigateBack = onNavigateBack,
            showFilters = s.provider.supportsModelFilters,
        )
    }

    @Composable
    override fun SettingsSection(onEditModel: () -> Unit) = AiSettingsSection(onEditModel)
}
```
`rememberAiViewModel(settings: AiSettingsState)` и `AiUnavailable` — перенести из `AppNavHost.kt` в этот файл как приватные `@Composable`, поменяв тип `SettingsState`→`AiSettingsState`. Для `files`: проверить сигнатуру получения списка файлов из `VaultRepository` (в AppNavHost это было `state.allFiles` из VaultViewModel; в провайдере взять эквивалент из `VaultRepository`). Если у `VaultRepository` нет готового метода — вызвать существующий, которым пользуется `VaultViewModel.loadDocuments()/allFiles`; уточнить при реализации.

- [ ] **Step 5: Почистить `SettingsScreen.kt` (composeApp)**

Удалить из `SettingsScreen`: параметры `onSaveKey/onSetAiEnabled/onEditModel/onSetProvider/onSetCustomBaseUrl`, черновики `key`/`baseUrl`, AI-блок, приватные `ProviderDropdown`/`ModelRow` (уехали в секцию). Оставить: sync-секцию, repo-URL `SettingField` + кнопку «Сохранить» (только `onSave(url)`), «Выбрать из GitHub». Добавить в конец composable-параметр и вызов AI-секции через провайдер (провайдер прокидывается из `AppNavHost`, Task 7) — либо принять `aiSection: @Composable () -> Unit` слотом:
```kotlin
fun SettingsScreen(
    state: SettingsState, onSave: (String) -> Unit,
    syncStatus: SyncStatus, onSync: () -> Unit,
    onNavigateBack: () -> Unit, onPickFromGitHub: () -> Unit = {},
    aiSection: @Composable () -> Unit = {},
) { /* ...repo url + Save... ; HorizontalDivider ; aiSection() */ }
```

- [ ] **Step 6: Прогнать UI-тесты `:ai:impl`**

Run: `./gradlew :ai:impl:testDebugUnitTest`
Expected: PASS (все тесты движка + settings-store + settings-vm + UI-тесты экранов).

- [ ] **Step 7: Commit**

```bash
git add ai/impl composeApp/src/commonMain/kotlin/app/obsidianmd/ui/SettingsScreen.kt
git commit -m "feat(ai): экраны + AiSettingsSection + AiPresentationProviderImpl в :ai:impl"
```

---

### Task 6: DI — `aiModule` / `aiPlatformModule`, чистка `AppModule`, подключение в `BrainerApp`

**Files:**
- Create: `ai/impl/src/commonMain/kotlin/app/obsidianmd/ai/di/AiModule.kt`
- Create: `ai/impl/src/androidMain/kotlin/app/obsidianmd/ai/di/AiModule.android.kt`
- Modify: `composeApp/.../di/AppModule.kt` — убрать AI-байндинги, поправить `SettingsViewModel`-байндинг
- Modify: `composeApp/.../BrainerApp.kt` — добавить `aiModule`

**Interfaces:**
- Consumes: `HttpClient`, `VaultRepository` из общего графа.
- Produces: `val aiModule`, `expect/actual val aiPlatformModule`.

- [ ] **Step 1: `AiModule.kt` (commonMain)**

```kotlin
package app.obsidianmd.ai.di

import app.obsidianmd.ai.*
import io.ktor.client.HttpClient
import app.obsidianmd.vault.VaultRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aiModule = module {
    includes(aiPlatformModule) // ApiKeyStore, AiSettingsStore (нужен Context)
    single<AiPresentationProvider> { AiPresentationProviderImpl() }
    viewModel {
        val http = get<HttpClient>()
        val keys = get<ApiKeyStore>()
        AiSettingsViewModel(
            store = get(),
            apiKeyStore = keys,
            fetchModels = { provider, base ->
                fetchModels(http, keys.getKey(provider.id).orEmpty(), provider.resolvedModelsUrl(base))
            },
        )
    }
    // Параметризованный VM чата — перенос из AppModule.
    viewModel { (model: String, key: String, chatUrl: String) ->
        AiViewModel { history, approver ->
            AiAgent(OpenRouterClient(get(), key, model, chatUrl), get(), approver).ask(history)
        }
    }
}

expect val aiPlatformModule: Module
```

- [ ] **Step 2: `AiModule.android.kt` (actual)**

```kotlin
package app.obsidianmd.ai.di

import android.content.Context
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.ai.AiSettingsStore
import app.obsidianmd.ai.EncryptedApiKeyStore
import app.obsidianmd.ai.SharedPrefsAiSettingsStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val aiPlatformModule: Module = module {
    single<ApiKeyStore> { EncryptedApiKeyStore(androidContext()) }
    single<AiSettingsStore> { SharedPrefsAiSettingsStore(androidContext()) }
}
```

- [ ] **Step 3: Почистить `AppModule.kt`**

Убрать импорты и байндинги: `AiAgent`, `AiViewModel`, `ApiKeyStore`, `EncryptedApiKeyStore`, `OpenRouterClient`, `fetchModels`, параметризованный `viewModel { (model,key,chatUrl) -> ... }`. Заменить `single<ApiKeyStore> { EncryptedApiKeyStore(...) }` — удалить (уехало в `aiPlatformModule`). Байндинг `SettingsViewModel` упростить до `viewModel { SettingsViewModel(store = get()) }`. `HttpClient` single оставить (его теперь потребляет `aiModule`).

- [ ] **Step 4: Подключить `aiModule` в `BrainerApp.kt`**

```kotlin
modules(appModule, vaultModule, authModule(BuildConfig.GITHUB_CLIENT_ID), aiModule)
```
+ добавить `import app.obsidianmd.ai.di.aiModule`. В `composeApp/build.gradle.kts` добавить `implementation(project(":ai:impl"))`.

- [ ] **Step 5: Проверить DI-граф сборкой (частично — полный прогон в Task 7)**

Run: `./gradlew :ai:impl:compileDebugKotlinAndroid`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add ai/impl composeApp/src/androidMain/kotlin/app/obsidianmd/di/AppModule.kt composeApp/src/androidMain/kotlin/app/obsidianmd/BrainerApp.kt composeApp/build.gradle.kts
git commit -m "feat(ai): aiModule/aiPlatformModule + чистка AppModule + подключение в BrainerApp"
```

---

### Task 7: Прошивка навигации/настроек/MainActivity + полный прогон и приёмка

**Files:**
- Modify: `composeApp/.../nav/AppNavHost.kt`
- Modify: `composeApp/.../MainActivity.kt`

**Interfaces:**
- Consumes: `AiPresentationProvider` (koinInject).

- [ ] **Step 1: `AppNavHost.kt` — перевести AiChat/ModelPicker/Settings на провайдер**

- Добавить `val ai = koinInject<AiPresentationProvider>()`.
- Удалить из хоста: `rememberAiViewModel`, `AiUnavailable`, импорты `AiViewModel`/`ApiKeyStore`/`AiChatScreen`/`ModelPickerScreen`/`SettingsViewModel`(если AI-части ушли)/`ModelInfo`. Удалить `val aiVm = rememberAiViewModel(settings)`.
- `entry<Route.AiChat>`:
```kotlin
entry<Route.AiChat> {
    ai.Chat(
        onOpenFile = { path -> backStack.add(Route.Note(path)) },
        onOpenSettings = { backStack.removeLastOrNull(); backStack.add(Route.Settings) },
        bottomBar = { BrainAiBottomBar(onAi = true, ai, backStack) },
    )
}
```
- `entry<Route.ModelPicker>`: `ai.ModelPicker(onNavigateBack = { backStack.removeLastOrNull() })`.
- `entry<Route.Settings>`: убрать AI-колбэки, передать `aiSection = { ai.SettingsSection(onEditModel = { backStack.add(Route.ModelPicker) }) }`:
```kotlin
entry<Route.Settings> {
    SettingsScreen(
        state = settings, onSave = { settingsVm.save(it) },
        syncStatus = state.syncStatus, onSync = vm::sync,
        onNavigateBack = { backStack.removeLastOrNull() },
        onPickFromGitHub = { backStack.resetTo(stackForChangeRepo()) },
        aiSection = { ai.SettingsSection(onEditModel = { backStack.add(Route.ModelPicker) }) },
    )
}
```
- `BrainAiBottomBar` — заменить сигнатуру `settings: SettingsState` на `ai: AiPresentationProvider` и видимость `if (!settings.aiEnabled ...)` на `if (!ai.aiEnabled() ...)`. Обновить оба вызова из VaultList/Note-энтри (передать `ai` вместо `settings`).
- `settings.url.isNotBlank()` в `Route.Login.onSignedIn` берётся из `SettingsViewModel` (url остался) — не трогаем.

- [ ] **Step 2: `MainActivity.kt` — импорт ApiKeyStore не меняется (теперь из :ai:api)**

Строка `koin.get<ApiKeyStore>()` остаётся; проверить, что импорт `app.obsidianmd.ai.ApiKeyStore` резолвится из `:ai:api`. Никаких правок кода, если импорт-строка та же.

- [ ] **Step 3: Полная сборка + все тесты**

Run:
```bash
./gradlew :ai:api:compileDebugKotlinAndroid :ai:impl:testDebugUnitTest :composeApp:testDebugUnitTest :composeApp:assembleDebug
```
Expected: всё PASS, APK собирается.

- [ ] **Step 4: Прогнать приёмочные тест-кейсы (ручной прогон из спеки §10)**

1) Включение AI → появляется таб → чат отвечает, wikilink кликабельны.
2) Смена модели/провайдера → чат пересоздаётся, история сброшена.
3) AI включён без ключа → заглушка «AI unavailable» с кнопкой в настройки.
4) Запись в vault → диалог подтверждения (подтвердить пишет, отклонить нет).
Отметить результат каждого; при провале — чинить до перехода дальше.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/nav/AppNavHost.kt composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt
git commit -m "feat(ai): перевести навигацию/настройки на AiPresentationProvider"
```

- [ ] **Step 6: Обновить `docs/MODULE_CONVENTIONS.md`**

Добавить `:ai` в список модулей (по образцу `:auth`/`:vault`). Commit: `docs: добавить :ai в MODULE_CONVENTIONS`.

---

## Self-review

**Покрытие спеки:**
- §2 границы/пакеты/namespace → Task 1 (api), Task 2 (impl).
- §3 перенос файлов → Task 2 (движок), Task 5 (экраны, EncryptedApiKeyStore), Task 1 (типы).
- §4 поверхность api → Task 1 (ChatClient/ApiKeyStore/AiProvider/ModelInfo/AiSettingsStore/AiPresentationProvider).
- §5 расщепление настроек + 2 кнопки + отдельный prefs → Task 3 (store), Task 4 (VM), Task 5 (Section/Screen).
- §6 DI → Task 6.
- §7 nav/MainActivity/BrainerApp/gradle/settings → Task 1 (settings.gradle/composeApp deps), Task 6 (BrainerApp), Task 7 (nav/MainActivity).
- §8 обработка ошибок → сохранена в перенесённом коде (Task 2/5, `AiUnavailable`/`AiResult.Failed`).
- §9 тесты → Task 2 (перенос), Task 3 (store-контракт), Task 4 (VM), Task 5 (UI).
- §10 приёмка → Task 7 Step 4.

**Плейсхолдеры:** два места помечены «уточнить при реализации» — (a) нужна ли `:vault:api` в `ai:api` (Task 1 Step 1), (b) метод получения списка файлов из `VaultRepository` для чата (Task 5 Step 4). Оба — конкретные проверки в коде на момент реализации, не размытые требования; развилки описаны.

**Согласованность типов:** `AiSettingsState`/`AiSettingsViewModel` (impl) vs `SettingsState`/`SettingsViewModel` (composeApp, только url) — не пересекаются. `AiPresentationProvider` — единый набор методов в Task 1 (api) и Task 5 (impl). `SettingsScreen` новая сигнатура (Task 5 Step 5) совпадает с вызовом в Task 7 Step 1. Prefs-файлы: `obsidian_settings` (repo url, не трогаем) vs `ai_settings` (новый) vs `obsidian_secure` (ключ, как есть).
