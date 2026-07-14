package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart

/** Стартовый бэкстек по состоянию авторизации/репозитория. */
fun startStack(hasToken: Boolean, hasRepo: Boolean): List<Route> = when {
    hasToken && hasRepo -> listOf(Route.VaultList())
    !hasToken -> listOf(Route.Onboarding(OnboardingStart.Login))
    else -> listOf(Route.Onboarding(OnboardingStart.RepoPicker))
}

/** Смена репо из настроек: онбординг с шага выбора репо поверх списка (есть куда вернуться). */
fun stackForChangeRepo(): List<Route> = listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.RepoPicker))
