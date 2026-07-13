package app.obsidianmd.nav

/** Стартовый бэкстек по состоянию авторизации/репозитория. */
fun startStack(hasToken: Boolean, hasRepo: Boolean): List<Route> = when {
    !hasToken -> listOf(Route.Login)
    !hasRepo -> listOf(Route.RepoPicker)
    else -> listOf(Route.VaultList())
}

/** После успешного выбора/валидации репо — онбординг недоступен назад. */
fun stackAfterRepoChosen(): List<Route> = listOf(Route.VaultList())

/** Смена репо из настроек: выбор репо поверх списка (есть куда вернуться). */
fun stackForChangeRepo(): List<Route> = listOf(Route.VaultList(), Route.RepoPicker)
