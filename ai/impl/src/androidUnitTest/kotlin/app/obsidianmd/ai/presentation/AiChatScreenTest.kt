package app.obsidianmd.ai

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.obsidianmd.vault.VaultFile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiChatScreenTest {

    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    private val user = ChatTurn("user", "Hello")
    private val ai = ChatTurn("assistant", "Hi there")

    @Test
    fun emptyStateShownWhenNoMessages() = runComposeUiTest {
        setContent {
            AiChatScreen(emptyList(), AiStatus.Idle, null, {}, {}, {})
        }
        onNodeWithText("Ask your notes anything").assertExists()
    }

    @Test
    fun typingIndicatorVisibleOnlyWhileThinking() = runComposeUiTest {
        setContent {
            AiChatScreen(listOf(user), AiStatus.Thinking, null, {}, {}, {})
        }
        onNodeWithTag("typing").assertExists()
    }

    @Test
    fun noTypingIndicatorWhenIdle() = runComposeUiTest {
        setContent {
            AiChatScreen(listOf(user, ai), AiStatus.Idle, null, {}, {}, {})
        }
        onNodeWithTag("typing").assertDoesNotExist()
    }

    @Test
    fun sendDisabledWhenEmptyAndWhileThinking() = runComposeUiTest {
        setContent {
            AiChatScreen(emptyList(), AiStatus.Idle, null, {}, {}, {})
        }
        onNodeWithContentDescription("Send message").assertIsNotEnabled()
    }

    @Test
    fun sendDisabledWhileThinkingEvenWithText() = runComposeUiTest {
        setContent {
            AiChatScreen(listOf(user), AiStatus.Thinking, null, {}, {}, {})
        }
        onNodeWithTag("chat_input").performTextInput("more")
        onNodeWithContentDescription("Send message").assertIsNotEnabled()
    }

    @Test
    fun sendEnablesAndFiresOnSend() = runComposeUiTest {
        var sent: String? = null
        setContent {
            AiChatScreen(emptyList(), AiStatus.Idle, null, onSend = { sent = it }, {}, {})
        }
        onNodeWithTag("chat_input").performTextInput("what is up")
        onNodeWithContentDescription("Send message").assertIsEnabled().performClick()
        assert(sent == "what is up")
        onNodeWithText("what is up").assertDoesNotExist() // field cleared after send
    }

    @Test
    fun assistantMessageHasAvatarUserDoesNot() = runComposeUiTest {
        setContent {
            AiChatScreen(listOf(user, ai), AiStatus.Idle, null, {}, {}, {})
        }
        // one assistant bubble → exactly one avatar; user bubble has none
        onAllNodesWithContentDescription("AI assistant").assertCountEquals(1)
    }

    @Test
    fun assistantMarkdownIsRenderedNotRaw() = runComposeUiTest {
        setContent {
            AiChatScreen(listOf(ChatTurn("assistant", "**bold** text")), AiStatus.Idle, null, {}, {}, {})
        }
        onNodeWithText("bold", substring = true).assertExists()      // parsed
        onNodeWithText("**bold**", substring = true).assertDoesNotExist() // no raw markers
    }

    @Test
    fun wikilinkToExistingFileRendersAsLinkLabel() = runComposeUiTest {
        val files = listOf(VaultFile(relPath = "welcome.md", absPath = "/v/welcome.md", name = "welcome.md"))
        setContent {
            AiChatScreen(
                listOf(ChatTurn("assistant", "see [[welcome]]")),
                AiStatus.Idle, null, {}, {}, {},
                files = files,
            )
        }
        onNodeWithText("welcome", substring = true).assertExists()        // rendered as link label
        onNodeWithText("[[welcome]]", substring = true).assertDoesNotExist() // brackets stripped
    }

    @Test
    fun wikilinkToMissingFileStaysRaw() = runComposeUiTest {
        setContent {
            AiChatScreen(
                listOf(ChatTurn("assistant", "see [[ghost]]")),
                AiStatus.Idle, null, {}, {}, {},
                files = emptyList(),
            )
        }
        onNodeWithText("[[ghost]]", substring = true).assertExists() // unresolved → left as-is
    }

    @Test
    fun pendingWriteShowsDialogWithFileName() = runComposeUiTest {
        setContent {
            AiChatScreen(listOf(user), AiStatus.Idle, "notes/todo.md" to "body", {}, {}, {})
        }
        onNodeWithText("notes/todo.md", substring = true).assertExists()
        onNodeWithText("Apply").assertExists()
        onNodeWithText("Reject").assertExists()
    }
}
