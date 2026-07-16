package app.obsidianmd.onboarding.di

import app.obsidianmd.onboarding.OnboardingPresentationProvider
import app.obsidianmd.onboarding.AuthViewModel
import app.obsidianmd.onboarding.GitHubDeviceAuth
import app.obsidianmd.onboarding.GitHubRepos
import app.obsidianmd.onboarding.RepoPickerViewModel
import app.obsidianmd.onboarding.RepoValidationViewModel
import app.obsidianmd.auth.TokenStore
import app.obsidianmd.onboarding.presentation.OnboardingPresentationProviderImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI фичи onboarding. GitHub client id — конфигурация приложения, приходит из app (BuildConfig).
 * HttpClient берётся из общего графа (движок регистрируется в app). Хранилище токена
 * (TokenStore) регистрирует :core:auth (authPlatformModule) — подключается в app.
 */
fun onboardingModule(githubClientId: String): Module = module {
    single<OnboardingPresentationProvider> { OnboardingPresentationProviderImpl() }
    viewModel { AuthViewModel(GitHubDeviceAuth(get(), githubClientId), get()) }
    viewModel { RepoPickerViewModel(repos = GitHubRepos(get()), token = get<TokenStore>()::get) }
    viewModel { RepoValidationViewModel(access = get(), token = get<TokenStore>()::get) }
}
