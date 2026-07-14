package app.obsidianmd.settings.di

import app.obsidianmd.settings.SettingsPresentationProvider
import app.obsidianmd.settings.presentation.SettingsPresentationProviderImpl
import app.obsidianmd.settings.presentation.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI фичи настроек. Общая часть — в commonMain; создание [app.obsidianmd.settings.RepoSettingsStore]
 * (нужен Context) — в платформенном [settingsPlatformModule].
 */
val settingsModule = module {
    includes(settingsPlatformModule)
    single<SettingsPresentationProvider> { SettingsPresentationProviderImpl() }
    viewModel { SettingsViewModel(store = get()) }
}

/** Платформенные байндинги настроек (создание [app.obsidianmd.settings.RepoSettingsStore]). */
expect val settingsPlatformModule: Module
