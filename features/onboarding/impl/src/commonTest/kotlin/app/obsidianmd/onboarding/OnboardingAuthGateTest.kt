package app.obsidianmd.onboarding

import app.obsidianmd.onboarding.presentation.OnboardingAction
import app.obsidianmd.onboarding.presentation.Step
import app.obsidianmd.onboarding.presentation.initialStep
import app.obsidianmd.onboarding.presentation.postSignInAction
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingAuthGateTest {

    // Выбор GitHub-репо без токена → сперва вход; с токеном → сразу выбор репо.
    @Test fun repo_picker_without_token_starts_at_login() {
        assertEquals(Step.Login, initialStep(OnboardingStart.RepoPicker, hasToken = false))
    }

    @Test fun repo_picker_with_token_starts_at_repo_picker() {
        assertEquals(Step.RepoPicker, initialStep(OnboardingStart.RepoPicker, hasToken = true))
    }

    @Test fun login_and_manual_ignore_token() {
        assertEquals(Step.Login, initialStep(OnboardingStart.Login, hasToken = false))
        assertEquals(Step.ManualUrl, initialStep(OnboardingStart.ManualUrl, hasToken = false))
    }

    // После входа: цель «выбрать GitHub-репо» → всегда к выбору репо (даже если репо уже задан).
    @Test fun after_login_for_repo_picker_always_goes_to_picker() {
        assertEquals(OnboardingAction.Go(Step.RepoPicker), postSignInAction(OnboardingStart.RepoPicker, hasRepo = true))
        assertEquals(OnboardingAction.Go(Step.RepoPicker), postSignInAction(OnboardingStart.RepoPicker, hasRepo = false))
    }

    // Обычный онбординг (Login): есть репо → завершаем, нет → выбираем репо.
    @Test fun after_login_for_initial_flow_uses_has_repo() {
        assertEquals(OnboardingAction.Finish, postSignInAction(OnboardingStart.Login, hasRepo = true))
        assertEquals(OnboardingAction.Go(Step.RepoPicker), postSignInAction(OnboardingStart.Login, hasRepo = false))
    }
}
