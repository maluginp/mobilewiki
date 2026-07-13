plugins {
    id("obsidian.feature.impl")
    alias(libs.plugins.kotlinSerialization)
}
android {
    namespace = "app.obsidianmd.ai.impl"
    testOptions {
        unitTests { isIncludeAndroidResources = true }
    }
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":ai:api"))
            implementation(project(":vault:api"))
            implementation(project(":core:analytics"))
            implementation(project(":core:translations"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.security.crypto)
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
