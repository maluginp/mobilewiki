package app.obsidianmd.auth.di

import org.koin.core.module.Module

/** Платформенные байндинги credential-store (создание [app.obsidianmd.auth.TokenStore]). */
expect val authPlatformModule: Module
