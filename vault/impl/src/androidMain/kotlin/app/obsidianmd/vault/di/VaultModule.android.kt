package app.obsidianmd.vault.di

import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.vault.data.createVaultRepository
import app.obsidianmd.vault.data.vaultRootPath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

// Единственное место создания VaultRepository (нужен Android Context для пути хранилища).
actual val vaultPlatformModule: Module = module {
    single<VaultRepository> { createVaultRepository(vaultRootPath(androidContext())) }
    single<CoroutineDispatcher> { Dispatchers.IO }
}
