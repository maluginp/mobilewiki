rootProject.name = "obsidian-git-md"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":composeApp")
include(":core:analytics")
include(":core:translations")
include(":sync:api")
include(":vault:api")
include(":vault:impl")
include(":auth:api")
include(":auth:impl")
