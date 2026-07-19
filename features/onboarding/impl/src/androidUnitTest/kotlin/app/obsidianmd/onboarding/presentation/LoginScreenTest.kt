package app.obsidianmd.onboarding.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.obsidianmd.onboarding.AuthState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginScreenTest {

    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun instructions_shown_while_awaiting_user() = runComposeUiTest {
        setContent {
            LoginScreen(
                state = AuthState.AwaitingUser(userCode = "ABCD-1234", verificationUri = "https://github.com/login/device"),
                onLogin = {}, onOpenUrl = {},
            )
        }
        onNodeWithText("How to authorize", substring = true).assertIsDisplayed()
    }

    @Test
    fun no_instructions_before_start() = runComposeUiTest {
        setContent {
            LoginScreen(state = AuthState.Idle, onLogin = {}, onOpenUrl = {})
        }
        onNodeWithText("Sign in with GitHub").assertIsDisplayed()
        onNodeWithText("How to authorize", substring = true).assertDoesNotExist()
    }

    @Test
    fun shows_back_arrow_when_onBack_provided() = runComposeUiTest {
        var backed = false
        setContent {
            LoginScreen(
                state = AuthState.AwaitingUser(userCode = "AB-12", verificationUri = "https://github.com/login/device"),
                onLogin = {}, onOpenUrl = {}, onBack = { backed = true },
            )
        }
        onNodeWithContentDescription("Back").assertExists().performClick()
        assert(backed)
    }

    @Test
    fun no_back_arrow_without_onBack() = runComposeUiTest {
        setContent {
            LoginScreen(state = AuthState.Idle, onLogin = {}, onOpenUrl = {})
        }
        onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}
