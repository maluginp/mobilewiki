plugins {
    id("obsidian.feature.impl")
}

android {
    namespace = "app.obsidianmd.note.impl"
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
    }
}
