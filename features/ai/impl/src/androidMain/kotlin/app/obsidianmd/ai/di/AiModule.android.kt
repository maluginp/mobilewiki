package app.obsidianmd.ai.di

import app.obsidianmd.ai.AiSettingsStore
import app.obsidianmd.ai.ApiKeyStore
import app.obsidianmd.ai.SharedPrefsAiSettingsStore
import app.obsidianmd.ai.data.EncryptedApiKeyStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val aiPlatformModule: Module = module {
    single<ApiKeyStore> { EncryptedApiKeyStore(androidContext()) }
    single<AiSettingsStore> { SharedPrefsAiSettingsStore(androidContext()) }
}
