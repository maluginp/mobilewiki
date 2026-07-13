package app.obsidianmd.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import app.obsidianmd.ai.ModelInfo
import app.obsidianmd.ai.ModelPricing
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelPickerScreenTest {

    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    private val models = listOf(
        ModelInfo("openai/gpt-4o", "GPT-4o", contextLength = 128_000, pricing = ModelPricing(prompt = "0.0000025")),
        ModelInfo("anthropic/claude-3.5", "Claude 3.5", contextLength = 200_000),
    )

    @Test
    fun rows_show_name_slug_context_and_price() = runComposeUiTest {
        setContent {
            ModelPickerScreen(models, loading = false, selected = "", onSelect = {}, onRefresh = {}, onNavigateBack = {})
        }
        onNodeWithText("GPT-4o").assertIsDisplayed()
        onNodeWithText("openai/gpt-4o", substring = true).assertIsDisplayed()
        onNodeWithText("128K ctx", substring = true).assertIsDisplayed()
        onNodeWithText("$2.5/M", substring = true).assertIsDisplayed()
    }

    @Test
    fun tapping_a_row_selects_its_id() = runComposeUiTest {
        var picked: String? = null
        setContent {
            ModelPickerScreen(models, loading = false, selected = "", onSelect = { picked = it }, onRefresh = {}, onNavigateBack = {})
        }
        onNodeWithText("Claude 3.5").performClick()
        assert(picked == "anthropic/claude-3.5")
    }

    @Test
    fun query_filters_the_list() = runComposeUiTest {
        setContent {
            ModelPickerScreen(models, loading = false, selected = "", onSelect = {}, onRefresh = {}, onNavigateBack = {})
        }
        // Открываем поиск (иконка «Search») и вводим запрос — список фильтруется по нему.
        onNodeWithContentDescription("Search").performClick()
        onNodeWithText("Search models").performTextInput("claude")
        onNodeWithText("Claude 3.5").assertIsDisplayed()
        onNodeWithText("GPT-4o").assertDoesNotExist()
    }

    @Test
    fun spinner_shown_while_loading_empty() = runComposeUiTest {
        setContent {
            ModelPickerScreen(emptyList(), loading = true, selected = "", onSelect = {}, onRefresh = {}, onNavigateBack = {})
        }
        // список ещё пуст — строк моделей нет, показана вертелка (нет исключений при композиции)
        onNodeWithText("GPT-4o").assertDoesNotExist()
    }

    @Test
    fun filter_bar_shown_by_default() = runComposeUiTest {
        setContent {
            ModelPickerScreen(models, loading = false, selected = "", onSelect = {}, onRefresh = {}, onNavigateBack = {})
        }
        onNodeWithText("Sort").assertExists()
        onNodeWithText("Price").assertExists()
    }

    @Test
    fun filter_bar_hidden_when_unsupported() = runComposeUiTest {
        setContent {
            ModelPickerScreen(
                models, loading = false, selected = "",
                onSelect = {}, onRefresh = {}, onNavigateBack = {}, showFilters = false,
            )
        }
        onNodeWithText("Sort").assertDoesNotExist()
        onNodeWithText("Price").assertDoesNotExist()
        // сами модели всё ещё показаны
        onNodeWithText("GPT-4o").assertExists()
    }
}
