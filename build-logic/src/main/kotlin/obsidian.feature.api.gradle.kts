import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Convention для :feature:api — контракты фичи. Только KMP + Android-таргет, без Compose.
// Каждый api-модуль задаёт свой android.namespace.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
}

// ponytail: SDK-версии захардкожены (совпадают с каталогом) — единый источник тут, не тянем каталог в плагин.
android {
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
