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
import app.obsidianmd.createRepository
import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.settings.SettingsViewModel
import app.obsidianmd.settings.SharedPrefsRepoSettingsStore
import app.obsidianmd.sync.GitSync
import app.obsidianmd.sync.JGitSync
import app.obsidianmd.sync.SyncConfig
import app.obsidianmd.sync.UiConflictResolver
import app.obsidianmd.ui.VaultViewModel
import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.vaultRoot
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<TokenStore> { EncryptedTokenStore(androidContext()) }
    single<RepoSettingsStore> { SharedPrefsRepoSettingsStore(androidContext()) }
    single<ApiKeyStore> { EncryptedApiKeyStore(androidContext()) }
    single<VaultRepository> { createRepository(androidContext()) }
    single<GitSync> { JGitSync() }
    single { UiConflictResolver() }
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    viewModel {
        val apiKeyStore = get<ApiKeyStore>()
        val http = get<HttpClient>()
        SettingsViewModel(
            store = get(),
            apiKeyStore = apiKeyStore,
            fetchModels = { provider -> fetchModels(http, apiKeyStore.getKey(provider.id).orEmpty(), provider.modelsUrl) },
        )
    }
    viewModel { AuthViewModel(GitHubDeviceAuth(get(), BuildConfig.GITHUB_CLIENT_ID), get()) }
    viewModel {
        val settingsStore = get<RepoSettingsStore>()
        val tokenStore = get<TokenStore>()
        val root = vaultRoot(androidContext())
        VaultViewModel(
            repo = get(),
            io = Dispatchers.IO,
            gitSync = get(),
            syncConfigProvider = {
                settingsStore.getRemoteUrl()?.takeIf { it.isNotBlank() }?.let { url ->
                    SyncConfig(remoteUrl = url, localPath = root.toString(), token = tokenStore.get())
                }
            },
            resolver = get(),
        )
    }
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
