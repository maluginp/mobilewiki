# Scaffold KMP/Compose + просмотр md — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `aiteam-delivery-developing` to implement this plan task-by-task, strictly test-first. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать KMP/Compose-Android скелет приложения и дать просмотр локальных md-файлов (список → рендер).

**Architecture:** Один Gradle-проект, модуль `composeApp` (Kotlin Multiplatform, таргет `androidTarget`). Чистая логика vault (листинг/чтение через okio) живёт в `commonMain` и покрыта тестами на `FakeFileSystem`. UI на Compose Multiplatform + Material3, markdown рендерит готовая библиотека. Навигация — простое Compose-состояние без nav-либы.

**Tech Stack:** Kotlin 2.0.21, Compose Multiplatform 1.7.0, AGP 8.5.2, okio 3.9.1 (+fakefilesystem для тестов), kotlinx-coroutines 1.9.0, `com.mikepenz:multiplatform-markdown-renderer-m3` 0.27.1, kotlin-test.

## Global Constraints

- Стек: Kotlin Multiplatform + Compose Multiplatform + Coroutines (задан пользователем).
- Пакет: `app.obsidianmd`.
- minSdk 24, compileSdk/targetSdk 34.
- Вся не-UI логика (листинг/чтение файлов) — в `commonMain`, тестируема без Android.
- Чтение файлов только вне main-потока (`Dispatchers.IO` / инъекция диспатчера в тестах).
- Markdown НЕ парсим сами — используем библиотеку.
- Аналитика: в этом фундаментальном слайсе НЕ вводится — аналитического стека в проекте ещё нет, добавлять его спекулятивно = отдельный слайс. Явная граница, не пропуск. (ponytail: YAGNI)

---

### Task 1: Gradle-скаффолд KMP/Compose (собираемый скелет)

Setup/конфиг свёрнуты сюда — это первый независимо проверяемый результат: проект собирается.

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `composeApp/build.gradle.kts`
- Create: `composeApp/src/androidMain/AndroidManifest.xml`
- Create: `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt`
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`
- Create: `local.properties` (путь к Android SDK; в .gitignore)
- Modify: `.gitignore`

**Interfaces:**
- Produces: собираемый модуль `:composeApp`, composable `App()` (заглушка), точка входа `MainActivity`.

- [ ] **Step 1: Bootstrap Gradle wrapper (системного gradle нет)**

```bash
brew install gradle
gradle wrapper --gradle-version 8.9 --distribution-type bin
```
Ожидается: появились `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{properties,jar}`.

- [ ] **Step 2: local.properties + .gitignore**

`local.properties` (не коммитим):
```properties
sdk.dir=/Users/pmalyugin/Library/Android/sdk
```
Дописать в `.gitignore`:
```
local.properties
.gradle/
build/
*.iml
.idea/
```

- [ ] **Step 3: gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048M -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: gradle/libs.versions.toml**

```toml
[versions]
kotlin = "2.0.21"
agp = "8.5.2"
compose-mp = "1.7.0"
androidx-activity = "1.9.2"
coroutines = "1.9.0"
okio = "3.9.1"
markdown = "0.27.1"
android-compileSdk = "34"
android-minSdk = "24"

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
okio-fakefilesystem = { module = "com.squareup.okio:okio-fakefilesystem", version.ref = "okio" }
markdown-renderer-m3 = { module = "com.mikepenz:multiplatform-markdown-renderer-m3", version.ref = "markdown" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-mp" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 5: settings.gradle.kts**

```kotlin
rootProject.name = "obsidian-git-md"

pluginManagement {
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
```

- [ ] **Step 6: root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
```

- [ ] **Step 7: composeApp/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
            implementation(libs.markdown.renderer.m3)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "app.obsidianmd"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.obsidianmd"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
```

- [ ] **Step 8: AndroidManifest.xml**

`composeApp/src/androidMain/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Obsidian md"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name="app.obsidianmd.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 9: App.kt (заглушка) + MainActivity.kt**

`composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`:
```kotlin
package app.obsidianmd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun App() {
    MaterialTheme {
        Text("Obsidian md")
    }
}
```

`composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt`:
```kotlin
package app.obsidianmd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
```

- [ ] **Step 10: Собрать — проверить, что скелет компилируется**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`, создан APK в `composeApp/build/outputs/apk/debug/`.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: KMP/Compose scaffold (composeApp builds)"
```

---

### Task 2: Модель MdFile + VaultRepository.listMarkdownFiles

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/vault/MdFile.kt`
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/vault/VaultRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/vault/VaultRepositoryTest.kt`

**Interfaces:**
- Produces: `data class MdFile(val name: String, val path: String)`; `class VaultRepository(fs: FileSystem, root: Path)` с `fun listMarkdownFiles(): List<MdFile>`.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package app.obsidianmd.vault

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultRepositoryTest {
    private val root = "/vault".toPath()

    private fun repoWith(vararg files: String): VaultRepository {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        files.forEach { fs.write(root / it) { writeUtf8("x") } }
        return VaultRepository(fs, root)
    }

    @Test
    fun lists_only_md_files_sorted_by_name() {
        val repo = repoWith("b.md", "a.md", "note.txt", "image.png")
        val names = repo.listMarkdownFiles().map { it.name }
        assertEquals(listOf("a.md", "b.md"), names)
    }

    @Test
    fun empty_vault_returns_empty_list() {
        val repo = repoWith()
        assertEquals(emptyList(), repo.listMarkdownFiles())
    }
}
```

- [ ] **Step 2: Прогнать — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: FAIL — `VaultRepository` / `MdFile` не определены (ошибка компиляции).

- [ ] **Step 3: Минимальная реализация**

`MdFile.kt`:
```kotlin
package app.obsidianmd.vault

data class MdFile(val name: String, val path: String)
```

`VaultRepository.kt`:
```kotlin
package app.obsidianmd.vault

import okio.FileSystem
import okio.Path

class VaultRepository(
    private val fs: FileSystem,
    private val root: Path,
) {
    fun listMarkdownFiles(): List<MdFile> {
        if (!fs.exists(root)) return emptyList()
        return fs.list(root)
            .filter { fs.metadata(it).isRegularFile && it.name.endsWith(".md") }
            .sortedBy { it.name }
            .map { MdFile(name = it.name, path = it.toString()) }
    }
}
```

- [ ] **Step 4: Прогнать — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/vault composeApp/src/commonTest
git commit -m "feat: VaultRepository.listMarkdownFiles + tests"
```

---

### Task 3: VaultRepository.readFile

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/vault/VaultRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/vault/VaultRepositoryTest.kt` (добавить кейс)

**Interfaces:**
- Produces: `fun VaultRepository.readFile(path: String): String`.

- [ ] **Step 1: Написать падающий тест (добавить в VaultRepositoryTest)**

```kotlin
    @Test
    fun reads_file_content() {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "a.md") { writeUtf8("# Hello") }
        val repo = VaultRepository(fs, root)
        assertEquals("# Hello", repo.readFile((root / "a.md").toString()))
    }
```

- [ ] **Step 2: Прогнать — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: FAIL — `readFile` не определён.

- [ ] **Step 3: Минимальная реализация (добавить в VaultRepository)**

```kotlin
    fun readFile(path: String): String =
        fs.read(path.toPath()) { readUtf8() }
```
Импорт вверху файла: `import okio.Path.Companion.toPath`.

- [ ] **Step 4: Прогнать — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.vault.VaultRepositoryTest"`
Expected: PASS (все 3 теста).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat: VaultRepository.readFile + test"
```

---

### Task 4: VaultViewModel (состояние + корутины)

Простой класс с `StateFlow`, без androidx-lifecycle (ponytail: две экранные фазы — либа не нужна). Скоуп и диспатчер инъектируются для теста.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/app/obsidianmd/ui/VaultViewModelTest.kt`

**Interfaces:**
- Consumes: `VaultRepository` (Task 2–3).
- Produces: `class VaultViewModel(repo, scope, io)`; `data class VaultState(files, selected, content, loading)`; методы `refresh()`, `open(MdFile)`, `back()`.

- [ ] **Step 1: Написать падающий тест**

```kotlin
package app.obsidianmd.ui

import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VaultViewModelTest {
    private val root = "/vault".toPath()

    private fun vm(scope: kotlinx.coroutines.CoroutineScope, io: kotlinx.coroutines.CoroutineDispatcher): VaultViewModel {
        val fs = FakeFileSystem()
        fs.createDirectories(root)
        fs.write(root / "a.md") { writeUtf8("# A") }
        return VaultViewModel(VaultRepository(fs, root), scope, io)
    }

    @Test
    fun refresh_loads_files() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(this, io)
        model.refresh()
        advanceUntilIdle()
        assertEquals(listOf("a.md"), model.state.value.files.map { it.name })
    }

    @Test
    fun open_loads_content_back_clears_it() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val model = vm(this, io)
        model.refresh(); advanceUntilIdle()
        model.open(model.state.value.files.first()); advanceUntilIdle()
        assertEquals("# A", model.state.value.content)
        model.back()
        assertNull(model.state.value.selected)
    }
}
```

- [ ] **Step 2: Прогнать — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: FAIL — `VaultViewModel` не определён.

- [ ] **Step 3: Минимальная реализация**

```kotlin
package app.obsidianmd.ui

import app.obsidianmd.vault.MdFile
import app.obsidianmd.vault.VaultRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VaultState(
    val files: List<MdFile> = emptyList(),
    val selected: MdFile? = null,
    val content: String = "",
    val loading: Boolean = false,
)

class VaultViewModel(
    private val repo: VaultRepository,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(VaultState())
    val state: StateFlow<VaultState> = _state.asStateFlow()

    fun refresh() {
        scope.launch {
            val files = withContext(io) { repo.listMarkdownFiles() }
            _state.value = _state.value.copy(files = files)
        }
    }

    fun open(file: MdFile) {
        scope.launch {
            _state.value = _state.value.copy(selected = file, loading = true)
            val text = withContext(io) { repo.readFile(file.path) }
            _state.value = _state.value.copy(content = text, loading = false)
        }
    }

    fun back() {
        _state.value = _state.value.copy(selected = null, content = "")
    }
}
```

- [ ] **Step 4: Прогнать — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "app.obsidianmd.ui.VaultViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/app/obsidianmd/ui composeApp/src/commonTest/kotlin/app/obsidianmd/ui
git commit -m "feat: VaultViewModel with StateFlow + tests"
```

---

### Task 5: Compose-экраны (список + рендер markdown)

UI — ручная приёмка (юнит-тестов Compose в этом слайсе нет).

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/VaultListScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/app/obsidianmd/ui/MarkdownScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/obsidianmd/App.kt`

**Interfaces:**
- Consumes: `VaultViewModel`, `VaultState`, `MdFile`.
- Produces: `VaultListScreen(state, onOpen)`, `MarkdownScreen(content, onBack)`, обновлённый `App(vm)`.

- [ ] **Step 1: VaultListScreen.kt**

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.vault.MdFile

@Composable
fun VaultListScreen(state: VaultState, onOpen: (MdFile) -> Unit) {
    if (state.files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет файлов")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.files) { file ->
            Text(
                file.name,
                Modifier.fillMaxWidth().clickable { onOpen(file) }.padding(16.dp),
            )
        }
    }
}
```

- [ ] **Step 2: MarkdownScreen.kt**

```kotlin
package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MarkdownScreen(content: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Markdown(content)
        }
    }
}
```

- [ ] **Step 3: Обновить App.kt (переключение по selected)**

```kotlin
package app.obsidianmd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel

@Composable
fun App(vm: VaultViewModel) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    MaterialTheme {
        Surface {
            if (state.selected == null) {
                VaultListScreen(state, onOpen = vm::open)
            } else {
                MarkdownScreen(state.content, onBack = vm::back)
            }
        }
    }
}
```

- [ ] **Step 4: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain
git commit -m "feat: Compose screens (list + markdown render)"
```

---

### Task 6: Проводка на Android — seed vault + создание ViewModel

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/obsidianmd/VaultBootstrap.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/obsidianmd/MainActivity.kt`

**Interfaces:**
- Consumes: `VaultRepository`, `VaultViewModel`, `App(vm)`.
- Produces: рабочее приложение с сид-файлами при первом запуске.

- [ ] **Step 1: VaultBootstrap.kt — создать vault и сид-файлы**

```kotlin
package app.obsidianmd

import android.content.Context
import app.obsidianmd.vault.VaultRepository
import okio.Path
import okio.Path.Companion.toPath
import okio.FileSystem

fun vaultRoot(context: Context): Path =
    (context.filesDir.resolve("vault")).absolutePath.toPath()

fun ensureSampleVault(root: Path, fs: FileSystem = FileSystem.SYSTEM) {
    if (!fs.exists(root)) fs.createDirectories(root)
    val welcome = root / "welcome.md"
    if (!fs.exists(welcome)) {
        fs.write(welcome) {
            writeUtf8(
                """
                # Welcome

                Это **демо**-заметка с [ссылкой](https://obsidian.md) и `кодом`.

                - пункт 1
                - пункт 2
                """.trimIndent()
            )
        }
    }
    val notes = root / "notes.md"
    if (!fs.exists(notes)) {
        fs.write(notes) { writeUtf8("# Notes\n\nВторой файл.") }
    }
}

fun createRepository(context: Context): VaultRepository {
    val root = vaultRoot(context)
    ensureSampleVault(root)
    return VaultRepository(FileSystem.SYSTEM, root)
}
```

- [ ] **Step 2: Обновить MainActivity.kt**

```kotlin
package app.obsidianmd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.obsidianmd.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = createRepository(applicationContext)
        val vm = VaultViewModel(repo, lifecycleScope, Dispatchers.IO)
        setContent { App(vm) }
    }
}
```

Примечание: `lifecycleScope` требует `androidx.lifecycle:lifecycle-runtime-ktx`; если его нет транзитивно от `activity-compose`, добавить в `androidMain.dependencies`:
`implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")` (и строку в libs.versions.toml).

- [ ] **Step 3: Собрать**

Run: `./gradlew :composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Ручная приёмка (эмулятор/устройство)**

Прогнать приёмочные тест-кейсы из спеки:
1. Список из `notes.md`, `welcome.md` (по алфавиту).
2. Тап по `welcome.md` → отрендеренный markdown (заголовок, список, bold, ссылка, код).
3. «Назад» → список, без краша.
4. (Проверка пустого vault — при необходимости вручную очистить папку.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain
git commit -m "feat: wire Android entry point + seed sample vault"
```

---

## Self-review

**Покрытие спеки:**
- Собираемый KMP/Compose проект → Task 1.
- Экран списка .md, сортировка, пустой vault → Task 2 (логика) + Task 5 (UI) + Task 6 (данные).
- Рендер markdown → Task 5 (`Markdown()` из библиотеки).
- Чтение вне UI-потока → Task 3 (readFile) + Task 4 (`withContext(io)`).
- Тесты на не-UI логику (листинг/чтение/VM) → Task 2, 3, 4.
- Аналитика → сознательно вне слайса (Global Constraints), не пропуск.

**Placeholder-скан:** реальный код и команды в каждом шаге, TODO/«обработать ошибки» нет.

**Согласованность типов:** `MdFile(name, path)`, `VaultRepository(fs, root)` с `listMarkdownFiles()/readFile(path)`, `VaultViewModel(repo, scope, io)` с `state/refresh()/open()/back()`, `App(vm)` — имена/сигнатуры совпадают между Task 2→6.
