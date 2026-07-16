package app.obsidianmd.onboarding

import app.obsidianmd.onboarding.presentation.Step
import app.obsidianmd.onboarding.presentation.startStep
import kotlin.test.Test
import kotlin.test.assertEquals

class StartStepTest {
    @Test fun login_maps_to_login() = assertEquals(Step.Login, startStep(OnboardingStart.Login))
    @Test fun repo_picker_maps_to_repo_picker() = assertEquals(Step.RepoPicker, startStep(OnboardingStart.RepoPicker))
    @Test fun manual_url_maps_to_manual_url() = assertEquals(Step.ManualUrl, startStep(OnboardingStart.ManualUrl))
}
