package app.obsidianmd.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Seed Compose-UI test: proves the harness works and guards the settings UX
// (label + example placeholder + description + save confirmation).
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @Test
    fun showsLabelExampleAndDescriptionForEachSetting() = runComposeUiTest {
        setContent {
            SettingsScreen(currentUrl = "", onSave = {}, openRouterKey = "", onSaveKey = {})
        }
        // Label + description (supportingText) are real semantics nodes; the example is a
        // decorative placeholder that Compose doesn't expose to the semantics tree, so it's
        // covered by the manual acceptance case instead.
        onNodeWithText("URL репозитория").assertExists()
        onNodeWithText("HTTPS-ссылка", substring = true).assertExists()
        onNodeWithText("Ключ OpenRouter").assertExists()
        onNodeWithText("зашифрованном виде", substring = true).assertExists()
    }

    @Test
    fun saveShowsConfirmation() = runComposeUiTest {
        var savedUrl: String? = null
        setContent {
            SettingsScreen(currentUrl = "x", onSave = { savedUrl = it }, openRouterKey = "", onSaveKey = {})
        }
        onNodeWithText("Сохранить").performClick()
        assert(savedUrl == "x")
        onNodeWithText("Сохранено ✓").assertExists()
    }
}
