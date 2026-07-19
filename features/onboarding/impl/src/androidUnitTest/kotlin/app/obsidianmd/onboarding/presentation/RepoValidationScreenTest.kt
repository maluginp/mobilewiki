package app.obsidianmd.onboarding.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import app.obsidianmd.onboarding.ValidationState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoValidationScreenTest {

    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun denied_shows_read_failed_and_blocks_continue() = runComposeUiTest {
        setContent {
            RepoValidationScreen(ValidationState.Denied("no access"), onContinue = {}, onRetry = {}, onBack = {})
        }
        onNodeWithText("Read access").assertExists()
        onNodeWithText("not available").assertExists()      // пункт чтения не прошёл
        onNodeWithText("Continue").assertIsNotEnabled()      // продолжить заблокировано
    }

    @Test
    fun readonly_shows_warning_and_allows_continue() = runComposeUiTest {
        setContent {
            RepoValidationScreen(ValidationState.Ok(canWrite = false), onContinue = {}, onRetry = {}, onBack = {})
        }
        onNodeWithText("Read-only access").assertExists()    // warning-заголовок
        onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun full_access_has_no_warning_and_allows_continue() = runComposeUiTest {
        setContent {
            RepoValidationScreen(ValidationState.Ok(canWrite = true), onContinue = {}, onRetry = {}, onBack = {})
        }
        onNodeWithText("Read-only access").assertDoesNotExist()
        onNodeWithText("Continue").assertIsEnabled()
    }
}
