package app.obsidianmd.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Драйвит stateless AiSettingsSectionContent напрямую (без Koin/VM), как auth-тесты бьют по
// stateless-экранам. Гарантирует локализованные метки/описания + видимость секции по флагу.
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiSettingsSectionContentTest {

    // Compose Resources needs an Android context to resolve strings; the ContentProvider
    // that normally sets it doesn't run under Robolectric. Its holder class is `internal`,
    // so set the static field by reflection (test-only; tied to the lib's impl).
    @Before
    fun initResourceContext() {
        val holder = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
        holder.getDeclaredField("ANDROID_CONTEXT").apply { isAccessible = true }
            .set(null, RuntimeEnvironment.getApplication())
    }

    private fun content(
        state: AiSettingsState,
        onSetAiEnabled: (Boolean) -> Unit = {},
        onEditModel: () -> Unit = {},
    ): @androidx.compose.runtime.Composable () -> Unit = {
        // Секция сама по себе — набор соседних composables; в реальном экране они лежат в
        // скроллящейся Column. Воспроизводим это, иначе элементы накладываются в корне.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            AiSettingsSectionContent(
                state = state,
                onSetAiEnabled = onSetAiEnabled,
                onSetProvider = {},
                onSaveKey = {},
                onSetCustomBaseUrl = {},
                onEditModel = onEditModel,
            )
        }
    }

    @Test
    fun showsKeyLabelAndDescriptionWhenAiEnabled() = runComposeUiTest {
        setContent { content(AiSettingsState(aiEnabled = true))() }
        onNodeWithText("API key").assertExists()
        onNodeWithText("Stored encrypted", substring = true).assertExists()
    }

    @Test
    fun providerDropdownShownWhenAiEnabled() = runComposeUiTest {
        setContent { content(AiSettingsState(aiEnabled = true))() }
        onNodeWithText("AI provider").assertExists()
        // по умолчанию — OpenRouter
        onNodeWithText("OpenRouter").assertExists()
    }

    @Test
    fun keySectionHiddenUntilAiEnabled() = runComposeUiTest {
        setContent { content(AiSettingsState(aiEnabled = false))() }
        onNodeWithText("Enable AI").assertExists()
        onNodeWithText("API key").assertDoesNotExist()
    }

    @Test
    fun modelRowShowsModelAndEditOpensPicker() = runComposeUiTest {
        var edited = false
        setContent {
            content(
                AiSettingsState(aiEnabled = true, aiModel = "openai/gpt-4o-mini"),
                onEditModel = { edited = true },
            )()
        }
        onNodeWithText("openai/gpt-4o-mini").performScrollTo().assertExists()
        onNodeWithContentDescription("Change model").performScrollTo().performClick()
        assert(edited)
    }

    @Test
    fun toggleTriggersOnSetAiEnabled() = runComposeUiTest {
        var enabled: Boolean? = null
        setContent { content(AiSettingsState(aiEnabled = false), onSetAiEnabled = { enabled = it })() }
        onNode(isToggleable()).performScrollTo().performClick()
        assert(enabled == true)
    }
}
