plugins {
    `kotlin-dsl`
}

// Плагины-«маркеры» на classpath, чтобы convention-плагины могли применять их через id(...).
// Версии дублируют gradle/libs.versions.toml — держим синхронно (precompiled-плагины не видят каталог).
dependencies {
    implementation("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:2.0.21")
    implementation("com.android.library:com.android.library.gradle.plugin:8.5.2")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.7.0")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.0.21")
}
