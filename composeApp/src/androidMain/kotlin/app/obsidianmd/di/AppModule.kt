package app.obsidianmd.di

import app.obsidianmd.BuildConfig
import app.obsidianmd.ai.AiAgent
import app.obsidianmd.ai.AiViewModel
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.ai.EncryptedApiKeyStore
import app.obsidianmd.ai.OpenRouterClient
import app.obsidianmd.ai.fetchModels
import app.obsidianmd.auth.AuthViewModel
import app.obsidianmd.auth.EncryptedTokenStore
import app.obsidianmd.auth.GitHubDeviceAuth
import app.obsidianmd.auth.GitHubRepoAccess
import app.obsidianmd.auth.GitHubRepos
import app.obsidianmd.auth.RepoPickerViewModel
import app.obsidianmd.auth.RepoValidationViewModel
import app.obsidianmd.auth.TokenStore
import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.settings.SharedPrefsRepoSettingsStore
import app.obsidianmd.sync.GitSync
import app.obsidianmd.sync.JGitSync
import app.obsidianmd.sync.SyncConfig
import app.obsidianmd.sync.SyncConfigProvider
import app.obsidianmd.sync.UiConflictResolver
import app.obsidianmd.vault.VaultRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<TokenStore> { EncryptedTokenStore(androidContext()) }
    single<RepoSettingsStore> { SharedPrefsRepoSettingsStore(androidContext()) }
    single<ApiKeyStore> { EncryptedApiKeyStore(androidContext()) }
    single<GitSync> { JGitSync() }
    single { UiConflictResolver() }
    // SyncConfig собирается тут (знаем настройки + токен); localPath — из vault-репозитория.
    single<SyncConfigProvider> {
        val settingsStore = get<RepoSettingsStore>()
        val tokenStore = get<TokenStore>()
        val repo = get<VaultRepository>()
        SyncConfigProvider {
            settingsStore.getRemoteUrl()?.takeIf { it.isNotBlank() }?.let { url ->
                SyncConfig(remoteUrl = url, localPath = repo.rootPath, token = tokenStore.get())
            }
        }
    }
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            // LLM-ответ (тем более с прогоном тулов) не влезает в дефолтные ~10 с OkHttp.
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                socketTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
            }
        }
    }

    viewModel {
        val apiKeyStore = get<ApiKeyStore>()
        val http = get<HttpClient>()
        SettingsViewModel(
            store = get(),
            apiKeyStore = apiKeyStore,
            fetchModels = { provider, base ->
                fetchModels(http, apiKeyStore.getKey(provider.id).orEmpty(), provider.resolvedModelsUrl(base))
            },
        )
    }
    viewModel { AuthViewModel(GitHubDeviceAuth(get(), BuildConfig.GITHUB_CLIENT_ID), get()) }
    viewModel { RepoPickerViewModel(repos = GitHubRepos(get()), token = get<TokenStore>()::get) }
    viewModel { RepoValidationViewModel(access = GitHubRepoAccess(get()), token = get<TokenStore>()::get) }
    // AI VM is parameterized: (model, key, chatUrl) come from the current provider+settings;
    // changing model or provider → new VM (chat resets).
    viewModel { (model: String, key: String, chatUrl: String) ->
        val http = get<HttpClient>()
        val repo = get<VaultRepository>()
        AiViewModel { history, approver ->
            AiAgent(OpenRouterClient(http, key, model, chatUrl), repo, approver).ask(history)
        }
    }
}
