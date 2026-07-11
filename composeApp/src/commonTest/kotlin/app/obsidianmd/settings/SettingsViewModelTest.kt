package app.obsidianmd.settings

import app.obsidianmd.ai.AiProvider
import app.obsidianmd.ai.FakeApiKeyStore
import app.obsidianmd.ai.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    // init/setAiEnabled запускают загрузку моделей на viewModelScope (Main) — подменяем диспатчер.
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initial_url_from_store() {
        val store = FakeRepoSettingsStore().apply { setRemoteUrl("https://a.git") }
        val vm = SettingsViewModel(store)
        assertEquals("https://a.git", vm.state.value.url)
    }

    @Test
    fun initial_url_empty_when_unset() {
        assertEquals("", SettingsViewModel(FakeRepoSettingsStore()).state.value.url)
    }

    @Test
    fun save_persists_and_updates_state() {
        val store = FakeRepoSettingsStore()
        val vm = SettingsViewModel(store)
        vm.save("https://b.git")
        assertEquals("https://b.git", store.getRemoteUrl())
        assertEquals("https://b.git", vm.state.value.url)
    }

    @Test
    fun ai_disabled_by_default() {
        assertFalse(SettingsViewModel(FakeRepoSettingsStore()).state.value.aiEnabled)
    }

    @Test
    fun ai_enabled_initial_from_store() {
        val store = FakeRepoSettingsStore().apply { setAiEnabled(true) }
        assertTrue(SettingsViewModel(store).state.value.aiEnabled)
    }

    @Test
    fun set_ai_enabled_persists_and_updates_state() {
        val store = FakeRepoSettingsStore()
        val vm = SettingsViewModel(store)
        vm.setAiEnabled(true)
        assertTrue(store.isAiEnabled())
        assertTrue(vm.state.value.aiEnabled)
    }

    @Test
    fun enabling_ai_loads_models_into_state() = runTest(dispatcher) {
        val models = listOf(ModelInfo("openai/gpt-4o", "GPT-4o"))
        val vm = SettingsViewModel(FakeRepoSettingsStore(), fetchModels = { models })
        vm.setAiEnabled(true)
        advanceUntilIdle()
        assertEquals(models, vm.state.value.models)
        assertFalse(vm.state.value.modelsLoading)
    }

    @Test
    fun switching_provider_swaps_key_and_model_and_persists() = runTest(dispatcher) {
        val store = FakeRepoSettingsStore()
        val keys = FakeApiKeyStore().apply {
            saveKey("openrouter", "sk-or")
            saveKey("provod", "sk_prov")
        }
        store.setAiModel("provod", "gpt-5.5")
        val vm = SettingsViewModel(store, apiKeyStore = keys)
        // старт — провайдер по умолчанию (OpenRouter) с его ключом
        assertEquals(AiProvider.OPENROUTER, vm.state.value.provider)
        assertEquals("sk-or", vm.state.value.apiKey)

        vm.setProvider(AiProvider.PROVOD)
        assertEquals(AiProvider.PROVOD, vm.state.value.provider)
        assertEquals("sk_prov", vm.state.value.apiKey)
        assertEquals("gpt-5.5", vm.state.value.aiModel)
        assertEquals("provod", store.getProvider())
    }

    @Test
    fun key_and_model_are_saved_under_current_provider() {
        val store = FakeRepoSettingsStore()
        val keys = FakeApiKeyStore()
        val vm = SettingsViewModel(store, apiKeyStore = keys)
        vm.setProvider(AiProvider.PROVOD)
        vm.saveKey("sk_new")
        vm.setAiModel("qwen-max")
        assertEquals("sk_new", keys.getKey("provod"))
        assertEquals("qwen-max", store.getAiModel("provod"))
        assertNull(keys.getKey("openrouter"))
    }

    @Test
    fun reload_refetches_even_when_already_loaded() = runTest(dispatcher) {
        var round = 0
        val vm = SettingsViewModel(
            FakeRepoSettingsStore().apply { setAiEnabled(true) },
            fetchModels = { round++; listOf(ModelInfo("m$round")) },
        )
        advanceUntilIdle()
        assertEquals("m1", vm.state.value.models.single().id)
        vm.reloadModels()
        advanceUntilIdle()
        assertEquals("m2", vm.state.value.models.single().id)
    }
}
