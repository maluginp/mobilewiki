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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
