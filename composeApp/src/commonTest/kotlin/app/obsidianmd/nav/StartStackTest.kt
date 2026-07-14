package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart
import kotlin.test.Test
import kotlin.test.assertEquals

class StartStackTest {
    // Нет токена → онбординг с шага входа.
    @Test fun no_token_starts_at_login() {
        assertEquals(listOf(Route.Onboarding(OnboardingStart.Login)), startStack(hasToken = false, hasRepo = false))
    }

    // Токен есть, репо нет → онбординг с шага выбора репо.
    @Test fun token_without_repo_starts_at_repo_picker() {
        assertEquals(listOf(Route.Onboarding(OnboardingStart.RepoPicker)), startStack(hasToken = true, hasRepo = false))
    }

    // Токен + репо → сразу список заметок (корень), онбординг не монтируется.
    @Test fun token_and_repo_starts_at_vault_list() {
        assertEquals(listOf(Route.VaultList()), startStack(hasToken = true, hasRepo = true))
    }

    // Смена репо из настроек — онбординг (выбор репо) поверх списка, чтобы «назад» возвращал в приложение.
    @Test fun change_repo_keeps_vault_list_underneath() {
        assertEquals(
            listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.RepoPicker)),
            stackForChangeRepo(),
        )
    }
}
