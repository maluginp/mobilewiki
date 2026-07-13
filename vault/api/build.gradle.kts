plugins {
    id("obsidian.feature.api")
}

android { namespace = "app.obsidianmd.vault.api" }

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
