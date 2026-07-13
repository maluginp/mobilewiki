package app.obsidianmd.vault.di

import app.obsidianmd.sync.SyncConfigProvider
import app.obsidianmd.vault.VaultRepository
import app.obsidianmd.vault.data.createVaultRepository
import app.obsidianmd.vault.data.vaultRootPath
import app.obsidianmd.vault.presentation.VaultViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** DI фичи vault: единственное место, где создаётся [VaultRepository]. */
val vaultModule = module {
    single<VaultRepository> { createVaultRepository(vaultRootPath(androidContext())) }
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
