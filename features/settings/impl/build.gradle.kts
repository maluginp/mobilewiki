plugins {
    id("obsidian.feature.impl")
}

android {
    namespace = "app.obsidianmd.settings.impl"
    testOptions {
        unitTests { isIncludeAndroidResources = true } // Robolectric + Compose UI tests
    }
    // Debug-only host activity for Compose UI tests under Robolectric.
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:settings:api"))
            implementation(project(":core:translations"))
            // presentation-слой фичи: Compose UI (экран настроек)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended) // Icons.AutoMirrored.Filled.ArrowBack
            implementation(libs.androidx.lifecycle.viewmodel)
            // DI фичи (di/SettingsModule) — Koin в commonMain
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            // androidContext() для платформенного модуля
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
