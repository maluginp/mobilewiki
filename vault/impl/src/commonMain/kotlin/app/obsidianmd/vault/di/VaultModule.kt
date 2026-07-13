package app.obsidianmd.vault.di

import app.obsidianmd.sync.SyncConfigProvider
import app.obsidianmd.vault.presentation.VaultViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * DI фичи vault. Общая часть — в commonMain; платформенные зависимости
 * (репозиторий на системной ФС, IO-диспетчер) — в [vaultPlatformModule].
 */
val vaultModule = module {
    includes(vaultPlatformModule)
    viewModel {
        VaultViewModel(
            repo = get(),
            io = get(),
            gitSync = get(),
            syncConfigProvider = get<SyncConfigProvider>()::provide,
            resolver = get(),
        )
    }
}

/** Платформенные байндинги vault (создание [app.obsidianmd.vault.VaultRepository], IO-диспетчер). */
expect val vaultPlatformModule: Module
