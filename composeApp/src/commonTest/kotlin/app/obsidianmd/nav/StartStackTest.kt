package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart
import kotlin.test.Test
import kotlin.test.assertEquals

class StartStackTest {
    // Онбординг завершён (любой режим, включая локальный) → сразу список заметок.
    @Test fun done_starts_at_vault_list() {
        assertEquals(listOf(Route.VaultList()), startStack(onboardingDone = true, hasToken = false))
        assertEquals(listOf(Route.VaultList()), startStack(onboardingDone = true, hasToken = true))
    }

    // Не завершён, но есть токен GitHub → возобновляем на выборе репозитория.
    @Test fun not_done_with_token_resumes_repo_picker() {
        assertEquals(
            listOf(Route.Onboarding(OnboardingStart.RepoPicker)),
            startStack(onboardingDone = false, hasToken = true),
        )
    }

    // Не завершён, токена нет → приветствие с выбором режима (шаг Login).
    @Test fun not_done_no_token_starts_at_login() {
        assertEquals(
            listOf(Route.Onboarding(OnboardingStart.Login)),
            startStack(onboardingDone = false, hasToken = false),
        )
    }

    // Смена репо из настроек — онбординг (выбор репо) поверх списка, чтобы «назад» возвращал в приложение.
    @Test fun change_repo_keeps_vault_list_underneath() {
        assertEquals(
            listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.RepoPicker)),
            stackForChangeRepo(),
        )
    }

    // Смена репо через ручной ввод — экран ManualUrl поверх списка.
    @Test fun change_repo_manual_opens_manual_over_vault() {
        assertEquals(
            listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.ManualUrl)),
            stackForChangeRepoManual(),
        )
    }
}
