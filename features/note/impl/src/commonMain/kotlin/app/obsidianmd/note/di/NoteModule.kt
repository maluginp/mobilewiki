package app.obsidianmd.note.di

import app.obsidianmd.note.NotePresentationProvider
import app.obsidianmd.note.presentation.NotePresentationProviderImpl
import org.koin.dsl.module

/** DI фичи note. Platform-модуль не нужен: экран stateless, картинки приходят параметром. */
val noteModule = module {
    single<NotePresentationProvider> { NotePresentationProviderImpl() }
}
