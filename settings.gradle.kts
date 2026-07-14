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
include(":core:auth")
include(":core:translations")
include(":features:sync:api")
include(":features:vault:api")
include(":features:vault:impl")
include(":features:onboarding:api")
include(":features:onboarding:impl")
include(":features:ai:api")
include(":features:ai:impl")
include(":features:settings:api")
include(":features:settings:impl")
include(":features:note:api")
include(":features:note:impl")
