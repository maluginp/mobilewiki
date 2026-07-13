plugins {
    id("obsidian.feature.api")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    // @Serializable для ModelInfo/ModelPricing
    alias(libs.plugins.kotlinSerialization)
}

android { namespace = "app.obsidianmd.ai.api" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
