package app.obsidianmd.ai.di

import app.obsidianmd.ai.AiAgent
import app.obsidianmd.ai.AiPresentationProvider
import app.obsidianmd.ai.AiPresentationProviderImpl
import app.obsidianmd.ai.AiSettingsViewModel
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.ai.OpenRouterClient
import app.obsidianmd.ai.fetchModels
import app.obsidianmd.vault.VaultRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI фичи AI. HttpClient и VaultRepository берутся из общего графа (движок/репозиторий
 * регистрируются в app/vault). Хранилища (ключ, настройки) — в платформенном [aiPlatformModule].
 */
val aiModule = module {
    includes(aiPlatformModule)
    single<AiPresentationProvider> { AiPresentationProviderImpl() }
    // Настройки AI: провайдер/модель/ключ + ленивый список моделей.
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
    // Параметризованный VM чата: (model, key, chatUrl) — смена провайдера/модели создаёт новый VM.
    viewModel { (model: String, key: String, chatUrl: String) ->
        val http = get<HttpClient>()
        val repo = get<VaultRepository>()
        AiViewModel { history, approver ->
            AiAgent(OpenRouterClient(http, key, model, chatUrl), repo, approver).ask(history)
        }
    }
}

/** Платформенные байндинги AI (создание ApiKeyStore/AiSettingsStore — нужен Context). */
expect val aiPlatformModule: Module
