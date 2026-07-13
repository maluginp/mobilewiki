plugins {
    id("obsidian.feature.impl")
}

android { namespace = "app.obsidianmd.core.translations" }

// Единый generated Res на весь проект. Пакет оставлен app.obsidianmd.resources —
// существующие импорты app.obsidianmd.resources.* не меняются.
compose.resources {
    publicResClass = true
    packageOfResClass = "app.obsidianmd.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // compose-плагин требует рантайм на classpath (Composable-ов тут нет)
            implementation(compose.runtime)
            // api — чтобы Res и рантайм ресурсов были видны модулям-потребителям
            api(compose.components.resources)
        }
    }
}
