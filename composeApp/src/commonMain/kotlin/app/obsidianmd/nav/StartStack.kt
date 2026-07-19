package app.obsidianmd.nav

import app.obsidianmd.onboarding.OnboardingStart

/** Стартовый бэкстек: завершённый онбординг ведёт в приложение, иначе — в нужный шаг онбординга. */
fun startStack(onboardingDone: Boolean, hasToken: Boolean): List<Route> = when {
    onboardingDone -> listOf(Route.VaultList())
    hasToken -> listOf(Route.Onboarding(OnboardingStart.RepoPicker))
    else -> listOf(Route.Onboarding(OnboardingStart.Login))
}

/** Смена репо из настроек: онбординг (выбор репо) поверх Настроек — «назад» возвращает в Настройки. */
fun stackForChangeRepo(): List<Route> =
    listOf(Route.VaultList(), Route.Settings, Route.Onboarding(OnboardingStart.RepoPicker))

/** Смена репо из настроек через ручной ввод URL — экран ManualUrl поверх Настроек. */
fun stackForChangeRepoManual(): List<Route> =
    listOf(Route.VaultList(), Route.Settings, Route.Onboarding(OnboardingStart.ManualUrl))
