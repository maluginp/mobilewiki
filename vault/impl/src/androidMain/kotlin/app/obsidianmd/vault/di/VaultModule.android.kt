package app.obsidianmd.vault.di

import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.vault.data.OkioVaultRepository
import app.obsidianmd.vault.data.vaultRootPath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

// Единственное место создания VaultRepository (нужен Android Context для пути хранилища).
actual val vaultPlatformModule: Module = module {
    single<VaultRepository> { OkioVaultRepository(FileSystem.SYSTEM, vaultRootPath(androidContext()).toPath()) }
    single<CoroutineDispatcher> { Dispatchers.IO }
}
