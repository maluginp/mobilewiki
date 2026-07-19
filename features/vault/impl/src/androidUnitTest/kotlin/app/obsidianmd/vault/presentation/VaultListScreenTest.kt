package app.obsidianmd.vault.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.obsidianmd.vault.VaultEntry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultListScreenTest {

    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    @Test
    fun loader_shown_instead_of_no_files_while_loading() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = emptyList(), loading = true, refreshing = false,
                query = "", results = emptyList(),
                title = "Notes",
                onQueryChange = {},
                onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {},
            )
        }
        onNodeWithText("No files").assertDoesNotExist()
    }

    @Test
    fun no_files_shown_when_loaded_and_empty() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = emptyList(), loading = false, refreshing = false,
                query = "", results = emptyList(),
                title = "Notes",
                onQueryChange = {},
                onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {},
            )
        }
        onNodeWithText("No files").assertIsDisplayed()
    }

    @Test
    fun fab_menu_shows_create_options() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = emptyList(), loading = false, refreshing = false,
                query = "", results = emptyList(), title = "Notes",
                onQueryChange = {}, onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {},
            )
        }
        onNodeWithContentDescription("Create").performClick()
        onNodeWithText("New note").assertIsDisplayed()
        onNodeWithText("New folder").assertIsDisplayed()
    }

    @Test
    fun readOnly_hides_create_fab() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = emptyList(), loading = false, refreshing = false,
                query = "", results = emptyList(), title = "Notes",
                onQueryChange = {}, onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {}, readOnly = true,
            )
        }
        onNodeWithContentDescription("Create").assertDoesNotExist()
    }

    @Test
    fun readWrite_shows_create_fab() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = emptyList(), loading = false, refreshing = false,
                query = "", results = emptyList(), title = "Notes",
                onQueryChange = {}, onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {}, readOnly = false,
            )
        }
        onNodeWithContentDescription("Create").assertIsDisplayed()
    }

    @Test
    fun create_button_disabled_for_blank_and_duplicate() = runComposeUiTest {
        setContent {
            VaultListScreen(
                entries = listOf(VaultEntry("note.md", "/vault/note.md", isFolder = false)),
                loading = false, refreshing = false,
                query = "", results = emptyList(), title = "Notes",
                onQueryChange = {}, onOpenFile = {}, onOpenFolder = {}, onRefresh = {},
                onOpenSettings = {}, onBack = null,
                onCreateNote = {}, onCreateFolder = {},
            )
        }
        onNodeWithContentDescription("Create").performClick()
        onNodeWithText("New note").performClick()
        // пусто → «Create» неактивна
        onNodeWithText("Create").assertIsNotEnabled()
        // дубликат (note → note.md, уже есть) → «Create» неактивна
        onNode(hasSetTextAction()).performTextInput("note")
        onNodeWithText("Create").assertIsNotEnabled()
    }
}
