plugins {
    id("obsidian.feature.api")
}

android { namespace = "app.obsidianmd.core.analytics" }

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation(libs.appmetrica.analytics)
        }
    }
}
