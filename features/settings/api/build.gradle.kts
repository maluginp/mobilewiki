plugins {
    id("obsidian.feature.api")
    // api фичи отдаёт @Composable-провайдер экрана → нужен compose
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { namespace = "app.obsidianmd.settings.api" }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
