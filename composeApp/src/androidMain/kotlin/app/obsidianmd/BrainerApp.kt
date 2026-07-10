package app.obsidianmd

import android.app.Application
import app.obsidianmd.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.androix.startup.KoinStartup // sic: Koin 4.0.1 ships this package name misspelled
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

@OptIn(KoinExperimentalAPI::class)
class BrainerApp : Application(), KoinStartup {
    // Koin is started by the androidx App Startup Initializer (KoinInitializer), not in onCreate.
    override fun onKoinStartup() = koinConfiguration {
        androidContext(this@BrainerApp)
        modules(appModule)
    }
}
