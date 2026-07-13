package app.obsidianmd.auth.di

import app.obsidianmd.auth.EncryptedTokenStore
import app.obsidianmd.auth.TokenStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

// Единственное место создания TokenStore (нужен Android Context для EncryptedSharedPreferences).
actual val authPlatformModule: Module = module {
    single<TokenStore> { EncryptedTokenStore(androidContext()) }
}
