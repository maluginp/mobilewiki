import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Convention для :feature:impl — реализация фичи (data/domain/presentation). KMP + Android + Compose.
// Каждый impl-модуль задаёт свой android.namespace и подключает свой :feature:api.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
}

android {
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
