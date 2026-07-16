package app.obsidianmd.onboarding

import androidx.compose.runtime.Composable

/**
 * Единственная точка входа онбординга. Весь флоу (вход → выбор репозитория → ручной URL →
 * валидация) живёт внутри модуля со своим вложенным бэкстеком. Основной модуль знает только
 * про начало и конец: onFinished вызывается, когда пользователь онбординг завершил (репозиторий
 * выбран и сохранён).
 */
interface OnboardingPresentationProvider {
    @Composable
    fun Onboarding(startAt: OnboardingStart, onFinished: () -> Unit)
}

/** С какого шага начинать флоу. RepoPicker/ManualUrl — для сценария «сменить репозиторий из настроек». */
enum class OnboardingStart { Login, RepoPicker, ManualUrl }
