package app.obsidianmd.di

import app.obsidianmd.auth.TokenStore
import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.sync.GitSync
import app.obsidianmd.sync.JGitSync
import app.obsidianmd.sync.SyncConfig
import app.obsidianmd.sync.SyncConfigProvider
import app.obsidianmd.sync.UiConflictResolver
import app.obsidianmd.ui.VaultViewModel
import app.obsidianmd.vault.VaultRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // TokenStore регистрируется в authModule (:auth:impl), RepoSettingsStore — в settingsModule
    // (:settings:impl); здесь оба используются через общий граф.
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

    // Оболочка заметок: просмотр/поиск/синк поверх VaultRepository (из :vault:impl) и sync-контрактов.
    viewModel {
        VaultViewModel(
            repo = get(),
            io = Dispatchers.IO,
            gitSync = get(),
            syncConfigProvider = get<SyncConfigProvider>()::provide,
            resolver = get(),
        )
    }
}
