package app.obsidianmd.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class StartStackTest {
    // Нет токена → экран логина (он же показывает кнопку входа в состоянии Idle).
    @Test fun no_token_starts_at_login() {
        assertEquals(listOf(Route.Login), startStack(hasToken = false, hasRepo = false))
    }

    // Токен есть, репо нет → выбор репо; выхода назад нет (репо обязателен) → один экран в стеке.
    @Test fun token_without_repo_starts_at_repo_picker() {
        assertEquals(listOf(Route.RepoPicker), startStack(hasToken = true, hasRepo = false))
    }

    // Токен + репо → сразу список заметок (корень).
    @Test fun token_and_repo_starts_at_vault_list() {
        assertEquals(listOf(Route.VaultList()), startStack(hasToken = true, hasRepo = true))
    }

    // После выбора/валидации репо — чистый стек со списком (онбординг не вернуть кнопкой назад).
    @Test fun after_repo_chosen_resets_to_vault_list() {
        assertEquals(listOf(Route.VaultList()), stackAfterRepoChosen())
    }

    // Смена репо из настроек — поверх списка, чтобы «назад» возвращал в приложение.
    @Test fun change_repo_keeps_vault_list_underneath() {
        assertEquals(listOf(Route.VaultList(), Route.RepoPicker), stackForChangeRepo())
    }
}
