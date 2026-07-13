package app.obsidianmd.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
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
                state = VaultState(loading = true, entries = emptyList()),
                onOpenFile = {}, onOpenFolder = {},
                query = "", results = emptyList(),
                scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
                onRefresh = {},
            )
        }
        onNodeWithText("No files").assertDoesNotExist()
    }

    @Test
    fun no_files_shown_when_loaded_and_empty() = runComposeUiTest {
        setContent {
            VaultListScreen(
                state = VaultState(loading = false, entries = emptyList()),
                onOpenFile = {}, onOpenFolder = {},
                query = "", results = emptyList(),
                scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
                onRefresh = {},
            )
        }
        onNodeWithText("No files").assertIsDisplayed()
    }
}
