package app.obsidianmd.settings.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangeRepoScreenTest {

    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun showsWarningAndDescribedModes() = runComposeUiTest {
        setContent {
            ChangeRepoScreen(onPickFromGitHub = {}, onConnectManually = {}, onUseLocal = {}, onNavigateBack = {})
        }
        onNodeWithText("Unsynced notes may be lost", substring = true).assertExists()
        onNodeWithText("sync over git", substring = true).assertExists()        // описание GitHub
        onNodeWithText("access token", substring = true).assertExists()         // описание ручного ввода
        onNodeWithText("only on this device", substring = true).assertExists()  // описание локального
    }

    @Test
    fun tappingModeTriggersItsCallback() = runComposeUiTest {
        var picked = false
        setContent {
            ChangeRepoScreen(
                onPickFromGitHub = { picked = true }, onConnectManually = {}, onUseLocal = {}, onNavigateBack = {},
            )
        }
        onNodeWithText("sync over git", substring = true).performClick() // строка GitHub кликабельна целиком
        assert(picked)
    }
}
