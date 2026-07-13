package app.obsidianmd.vault.di

import app.obsidianmd.vault.VaultPresentationProvider
import app.obsidianmd.vault.presentation.VaultPresentationProviderImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DI фичи vault. Общая часть — в commonMain; создание репозитория (нужен Context) —
 * в платформенном [vaultPlatformModule].
 */
val vaultModule = module {
    includes(vaultPlatformModule)
    single<VaultPresentationProvider> { VaultPresentationProviderImpl() }
}

/** Платформенные байндинги vault (создание [app.obsidianmd.vault.VaultRepository]). */
expect val vaultPlatformModule: Module
