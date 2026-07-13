plugins {
    id("obsidian.feature.impl")
}

android { namespace = "app.obsidianmd.vault.impl" }

compose.resources {
    publicResClass = true
    packageOfResClass = "app.obsidianmd.vault.presentation.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":vault:api"))
            implementation(project(":sync:api"))
            implementation(project(":core:analytics"))
            implementation(libs.okio)
            // presentation-слой фичи: Compose UI + ViewModel
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
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
