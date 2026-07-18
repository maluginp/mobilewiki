package app.obsidianmd.settings.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Seed Compose-UI test: proves the harness works and guards the settings UX
// (текущий режим репозитория, предупреждение при смене + выбор режима, Sync action).
// AI-часть секции проверяется в :ai:impl (AiSettingsSectionContentTest).
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    // Compose Resources needs an Android context to resolve strings; the ContentProvider
    // that normally sets it doesn't run under Robolectric. Its holder class is `internal`,
    // so set the static field by reflection (test-only; tied to the lib's impl).
    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun showsLocalModeWhenNoRepo() = runComposeUiTest {
        setContent {
            SettingsScreen(url = "", syncing = false, syncStatusText = "", onSync = {}, onNavigateBack = {})
        }
        onNodeWithText("Current repository").assertExists()
        onNodeWithText("Local storage (no sync)").assertExists()
    }

    @Test
    fun changeRepoShowsSingleDialogWithWarningAndDescribedModes() = runComposeUiTest {
        setContent {
            SettingsScreen(url = "https://a.git", syncing = false, syncStatusText = "", onSync = {}, onNavigateBack = {})
        }
        onNodeWithText("Change repository").performScrollTo().performClick()
        // Один диалог: предупреждение сверху + все режимы с описаниями, без промежуточного «Continue».
        onNodeWithText("Unsynced notes may be lost", substring = true).assertExists()
        onNodeWithText("sync over git", substring = true).assertExists()        // описание GitHub
        onNodeWithText("access token", substring = true).assertExists()         // описание ручного ввода
        onNodeWithText("only on this device", substring = true).assertExists()  // описание локального
        onAllNodesWithText("Continue").assertCountEquals(0)
    }

    @Test
    fun syncButtonTriggersSync() = runComposeUiTest {
        var synced = false
        setContent {
            SettingsScreen(url = "https://a.git", syncing = false,
                syncStatusText = "", onSync = { synced = true }, onNavigateBack = {})
        }
        onNodeWithText("Sync now").performScrollTo().performClick()
        assert(synced)
    }

    @Test
    fun hidesSyncSectionInLocalMode() = runComposeUiTest {
        setContent {
            SettingsScreen(url = "", syncing = false, syncStatusText = "", onSync = {}, onNavigateBack = {})
        }
        // В локальном режиме (репозиторий не выбран) синхронизировать нечего.
        onAllNodesWithText("Sync now").assertCountEquals(0)
    }
}
