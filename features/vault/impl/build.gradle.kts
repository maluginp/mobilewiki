plugins {
    id("obsidian.feature.impl")
}

android {
    namespace = "app.obsidianmd.vault.impl"
    testOptions {
        unitTests { isIncludeAndroidResources = true } // Robolectric + Compose UI tests
    }
    // Debug-only host activity for Compose UI tests under Robolectric.
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:vault:api"))
            implementation(project(":core:translations"))
            implementation(libs.okio)
            // presentation-слой фичи: Compose UI (экран списка)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            // DI фичи (di/VaultModule) — Koin в commonMain
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            // androidContext() для платформенного модуля
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidUnitTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.robolectric)
        }
    }
}
