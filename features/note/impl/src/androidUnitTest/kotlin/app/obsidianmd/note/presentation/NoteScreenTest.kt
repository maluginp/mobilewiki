package app.obsidianmd.note.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteScreenTest {

    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun readOnly_hides_edit_action() = runComposeUiTest {
        setContent {
            NoteScreenContent(
                title = "note.md", content = "# hello",
                files = emptyList(), documents = emptyList(),
                loadImage = { null }, onOpenPath = {}, onNavigateBack = {}, onSave = {},
                readOnly = true,
            )
        }
        onNodeWithContentDescription("Edit").assertDoesNotExist()
    }

    @Test
    fun readWrite_shows_edit_action() = runComposeUiTest {
        setContent {
            NoteScreenContent(
                title = "note.md", content = "# hello",
                files = emptyList(), documents = emptyList(),
                loadImage = { null }, onOpenPath = {}, onNavigateBack = {}, onSave = {},
                readOnly = false,
            )
        }
        onNodeWithContentDescription("Edit").assertIsDisplayed()
    }
}
