package app.obsidianmd.onboarding

import app.obsidianmd.onboarding.presentation.OnboardingAction
import app.obsidianmd.onboarding.presentation.Step
import app.obsidianmd.onboarding.presentation.initialStep
import app.obsidianmd.onboarding.presentation.postSignInAction
import app.obsidianmd.onboarding.presentation.showWelcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // Экран приветствия (с выбором режима) — только для обычного онбординга. Для смены репо на
    // GitHub (start == RepoPicker) сразу показываем экран входа в GitHub, без приветствия.
    @Test fun welcome_shown_only_for_initial_onboarding_idle() {
        assertTrue(showWelcome(OnboardingStart.Login, AuthState.Idle))
        assertFalse(showWelcome(OnboardingStart.RepoPicker, AuthState.Idle))
    }

    @Test fun welcome_never_shown_once_login_started() {
        assertFalse(showWelcome(OnboardingStart.Login, AuthState.Success))
        assertFalse(showWelcome(OnboardingStart.RepoPicker, AuthState.Success))
    }
}
