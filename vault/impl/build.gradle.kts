plugins {
    id("obsidian.feature.impl")
}

android { namespace = "app.obsidianmd.vault.impl" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":vault:api"))
            implementation(project(":sync:api"))
            implementation(project(":core:analytics"))
            implementation(project(":core:translations"))
            implementation(libs.okio)
            // presentation-слой фичи: Compose UI + ViewModel
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        androidMain.dependencies {
            // DI фичи (di/VaultModule) — Koin + androidContext
            implementation(libs.koin.core)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
