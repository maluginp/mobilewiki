package app.obsidianmd.onboarding

import app.obsidianmd.onboarding.presentation.OnboardingAction
import app.obsidianmd.onboarding.presentation.Step
import app.obsidianmd.onboarding.presentation.afterSignIn
import app.obsidianmd.onboarding.presentation.startStep
import kotlin.test.Test
import kotlin.test.assertEquals

class AfterSignInTest {
    @Test
    fun signedIn_withRepo_finishes() {
        assertEquals(OnboardingAction.Finish, afterSignIn(hasRepo = true))
    }

    @Test
    fun signedIn_withoutRepo_goesToRepoPicker() {
        assertEquals(OnboardingAction.Go(Step.RepoPicker), afterSignIn(hasRepo = false))
    }

    @Test
    fun startStep_login_maps_to_login_step() {
        assertEquals(Step.Login, startStep(OnboardingStart.Login))
    }

    @Test
    fun startStep_repoPicker_maps_to_repo_picker_step() {
        assertEquals(Step.RepoPicker, startStep(OnboardingStart.RepoPicker))
    }
}
