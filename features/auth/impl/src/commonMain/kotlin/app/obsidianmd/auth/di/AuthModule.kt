package app.obsidianmd.auth.di

import app.obsidianmd.auth.AuthPresentationProvider
import app.obsidianmd.auth.AuthViewModel
import app.obsidianmd.auth.GitHubDeviceAuth
import app.obsidianmd.auth.GitHubRepoAccess
import app.obsidianmd.auth.GitHubRepos
import app.obsidianmd.auth.RepoPickerViewModel
import app.obsidianmd.auth.RepoValidationViewModel
import app.obsidianmd.auth.TokenStore
import app.obsidianmd.auth.presentation.AuthPresentationProviderImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI фичи auth. GitHub client id — конфигурация приложения, приходит из app (BuildConfig).
 * HttpClient берётся из общего графа (движок регистрируется в app). Хранилище токена —
 * в платформенном [authPlatformModule] (нужен Context).
 */
fun authModule(githubClientId: String): Module = module {
    includes(authPlatformModule)
    single<AuthPresentationProvider> { AuthPresentationProviderImpl() }
    viewModel { AuthViewModel(GitHubDeviceAuth(get(), githubClientId), get()) }
    viewModel { RepoPickerViewModel(repos = GitHubRepos(get()), token = get<TokenStore>()::get) }
    viewModel { RepoValidationViewModel(access = GitHubRepoAccess(get()), token = get<TokenStore>()::get) }
}

/** Платформенные байндинги auth (создание [TokenStore]). */
expect val authPlatformModule: Module
