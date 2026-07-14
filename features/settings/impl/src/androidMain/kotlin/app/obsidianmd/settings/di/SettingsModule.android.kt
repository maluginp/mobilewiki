package app.obsidianmd.settings.di

import app.obsidianmd.settings.RepoSettingsStore
import app.obsidianmd.settings.data.SharedPrefsRepoSettingsStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val settingsPlatformModule: Module = module {
    single<RepoSettingsStore> { SharedPrefsRepoSettingsStore(androidContext()) }
}
