plugins {
    id("obsidian.feature.api")
}

android { namespace = "app.obsidianmd.sync.api" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
