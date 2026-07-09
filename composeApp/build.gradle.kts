import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key, "")

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended) // ponytail: bundles all icons; slim to material-icons-core if APK size matters
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.jgit)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.work.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.robolectric)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "app.obsidianmd.resources"
}

android {
    namespace = "app.obsidianmd"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures { buildConfig = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // ponytail: needed so Robolectric can run Compose UI tests
        }
    }

    // Debug-only host activity for Compose UI tests under Robolectric (not merged into release).
    sourceSets.getByName("debug").manifest.srcFile("src/androidDebug/AndroidManifest.xml")

    defaultConfig {
        applicationId = "app.obsidianmd"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${localProp("github.clientId")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
