plugins {
    id("obsidian.feature.impl")
}

android {
    namespace = "app.obsidianmd.note.impl"
    testOptions {
        unitTests { isIncludeAndroidResources = true } // Robolectric + Compose UI tests
    }
    // Debug-only host activity for Compose UI tests under Robolectric.
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":features:note:api"))
            implementation(project(":features:vault:api"))
            implementation(project(":core:translations"))
            // presentation-слой фичи: Compose UI (экран заметки) + markdown-рендер
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.markdown.renderer.m3)
            // DI фичи (di/NoteModule) — Koin в commonMain
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidUnitTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.robolectric)
        }
    }
}
