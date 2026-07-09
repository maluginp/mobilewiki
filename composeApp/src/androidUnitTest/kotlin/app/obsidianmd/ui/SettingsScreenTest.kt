package app.obsidianmd.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isToggleable
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
// (localized label + description strings + save confirmation + moved Sync action).
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
    fun showsLabelAndDescriptionForEachSetting() = runComposeUiTest {
        setContent {
            SettingsScreen("", {}, "", {}, syncStatus = SyncStatus.Idle, onSync = {},
                aiEnabled = true, onSetAiEnabled = {})
        }
        // Label + description (supportingText) are real semantics nodes; the example is a
        // decorative placeholder Compose doesn't expose to the semantics tree, so it's
        // covered by the manual acceptance case instead.
        onNodeWithText("Repository URL").assertExists()
        onNodeWithText("HTTPS link", substring = true).assertExists()
        onNodeWithText("OpenRouter key").assertExists()
        onNodeWithText("Stored encrypted", substring = true).assertExists()
    }

    @Test
    fun keySectionHiddenUntilAiEnabled() = runComposeUiTest {
        setContent {
            SettingsScreen("", {}, "", {}, syncStatus = SyncStatus.Idle, onSync = {},
                aiEnabled = false, onSetAiEnabled = {})
        }
        onNodeWithText("Enable AI").assertExists()
        onNodeWithText("OpenRouter key").assertDoesNotExist()
    }

    @Test
    fun saveShowsConfirmation() = runComposeUiTest {
        var savedUrl: String? = null
        setContent {
            SettingsScreen("x", onSave = { savedUrl = it }, openRouterKey = "", onSaveKey = {},
                syncStatus = SyncStatus.Idle, onSync = {}, aiEnabled = false, onSetAiEnabled = {})
        }
        onNodeWithText("Save").performScrollTo().performClick()
        assert(savedUrl == "x")
        onNodeWithText("Saved ✓").assertExists()
    }

    @Test
    fun syncButtonTriggersSync() = runComposeUiTest {
        var synced = false
        setContent {
            SettingsScreen("", {}, "", {}, syncStatus = SyncStatus.Idle, onSync = { synced = true },
                aiEnabled = false, onSetAiEnabled = {})
        }
        onNodeWithText("Sync now").performScrollTo().performClick()
        assert(synced)
    }

    @Test
    fun toggleTriggersOnSetAiEnabled() = runComposeUiTest {
        var enabled: Boolean? = null
        setContent {
            SettingsScreen("", {}, "", {}, syncStatus = SyncStatus.Idle, onSync = {},
                aiEnabled = false, onSetAiEnabled = { enabled = it })
        }
        onNode(isToggleable()).performScrollTo().performClick()
        assert(enabled == true)
    }
}
