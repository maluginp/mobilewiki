plugins {
    id("obsidian.feature.impl")
}

android { namespace = "app.obsidianmd.ai.impl" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":ai:api"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
