plugins {
    id("obsidian.feature.impl")
    // @Serializable-модели ответов GitHub (device-auth / repos)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "app.obsidianmd.onboarding.impl"
    testOptions {
        unitTests { isIncludeAndroidResources = true } // Robolectric + Compose UI tests
    }
    // Debug-only host activity for Compose UI tests under Robolectric.
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:onboarding:api"))
            implementation(project(":core:auth"))
            implementation(project(":features:settings:api"))
            implementation(project(":features:sync:api"))
            implementation(project(":core:analytics"))
            implementation(project(":core:translations"))
            // вложенный бэкстек флоу онбординга
            implementation(libs.navigation3.ui)
            implementation(libs.koin.compose)
            // presentation-слой фичи: экраны онбординга
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            // ViewModel'и фичи
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            // GitHub device-auth / repos / access — поверх общего HttpClient (движок из app)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // DI фичи (authModule) — Koin в commonMain
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.robolectric)
        }
    }
}
