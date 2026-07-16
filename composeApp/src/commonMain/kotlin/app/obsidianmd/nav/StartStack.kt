package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart

/** Стартовый бэкстек: завершённый онбординг ведёт в приложение, иначе — в нужный шаг онбординга. */
fun startStack(onboardingDone: Boolean, hasToken: Boolean): List<Route> = when {
    onboardingDone -> listOf(Route.VaultList())
    hasToken -> listOf(Route.Onboarding(OnboardingStart.RepoPicker))
    else -> listOf(Route.Onboarding(OnboardingStart.Login))
}

/** Смена репо из настроек: онбординг с шага выбора репо поверх списка (есть куда вернуться). */
fun stackForChangeRepo(): List<Route> = listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.RepoPicker))

/** Смена репо из настроек через ручной ввод URL — экран ManualUrl поверх списка. */
fun stackForChangeRepoManual(): List<Route> = listOf(Route.VaultList(), Route.Onboarding(OnboardingStart.ManualUrl))
