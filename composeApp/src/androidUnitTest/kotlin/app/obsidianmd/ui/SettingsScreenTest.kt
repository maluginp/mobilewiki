package app.obsidianmd.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Seed Compose-UI test: proves the harness works and guards the settings UX
// (label + example placeholder + description + save confirmation + moved Sync action).
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @Test
    fun showsLabelAndDescriptionForEachSetting() = runComposeUiTest {
        setContent {
            SettingsScreen("", {}, "", {}, syncStatus = SyncStatus.Idle, onSync = {})
        }
        // Label + description (supportingText) are real semantics nodes; the example is a
        // decorative placeholder that Compose doesn't expose to the semantics tree, so it's
        // covered by the manual acceptance case instead.
        onNodeWithText("Repository URL").assertExists()
        onNodeWithText("HTTPS link", substring = true).assertExists()
        onNodeWithText("OpenRouter key").assertExists()
        onNodeWithText("Stored encrypted", substring = true).assertExists()
    }

    @Test
    fun saveShowsConfirmation() = runComposeUiTest {
        var savedUrl: String? = null
        setContent {
            SettingsScreen("x", onSave = { savedUrl = it }, openRouterKey = "", onSaveKey = {},
                syncStatus = SyncStatus.Idle, onSync = {})
        }
        onNodeWithText("Save").performClick()
        assert(savedUrl == "x")
        onNodeWithText("Saved ✓").assertExists()
    }

    @Test
    fun syncButtonTriggersSync() = runComposeUiTest {
        var synced = false
        setContent {
            SettingsScreen("", {}, "", {}, syncStatus = SyncStatus.Idle, onSync = { synced = true })
        }
        onNodeWithText("Sync now").performScrollTo().performClick()
        assert(synced)
    }
}
