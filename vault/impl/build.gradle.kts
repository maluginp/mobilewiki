plugins {
    id("obsidian.feature.impl")
}

android { namespace = "app.obsidianmd.vault.impl" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":vault:api"))
            implementation(libs.okio)
            // presentation-слой фичи: Compose UI + ViewModel
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
        }
    }
}
