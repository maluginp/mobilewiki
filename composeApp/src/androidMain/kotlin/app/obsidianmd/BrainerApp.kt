package app.obsidianmd

import android.app.Application
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import app.obsidianmd.ai.di.aiModule
import app.obsidianmd.auth.di.authModule
import app.obsidianmd.di.appModule
import app.obsidianmd.note.di.noteModule
import app.obsidianmd.settings.di.settingsModule
import app.obsidianmd.vault.di.vaultModule
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup // sic: Koin 4.0.1 ships this package name misspelled
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

@OptIn(KoinExperimentalAPI::class)
class BrainerApp : Application(), KoinStartup {
    override fun onCreate() {
        super.onCreate()
        val config = AppMetricaConfig.newConfigBuilder("d904e507-955c-4cf6-b1fc-56838a1452aa").build()
        AppMetrica.activate(this, config)
    }

    // Koin is started by the androidx App Startup Initializer (KoinInitializer), not in onCreate.
    override fun onKoinStartup() = koinConfiguration {
        androidContext(this@BrainerApp)
        modules(appModule, vaultModule, authModule(BuildConfig.GITHUB_CLIENT_ID), aiModule, settingsModule, noteModule)
    }
}
