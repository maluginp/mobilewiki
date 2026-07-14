plugins {
    id("obsidian.feature.api")
    // api фичи отдаёт @Composable-провайдер экранов онбординга → нужен compose
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "app.obsidianmd.onboarding.api" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
