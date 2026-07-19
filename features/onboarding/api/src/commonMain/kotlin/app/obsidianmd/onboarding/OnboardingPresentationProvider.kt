package app.obsidianmd.onboarding

import androidx.compose.runtime.Composable

/**
 * Единственная точка входа онбординга. Весь флоу (вход → выбор репозитория → ручной URL →
 * валидация) живёт внутри модуля со своим вложенным бэкстеком. Основной модуль знает только
 * про начало и конец: onFinished вызывается, когда пользователь онбординг завершил (репозиторий
 * выбран и сохранён).
 */
interface OnboardingPresentationProvider {
    /**
     * @param onExit выход «назад» с корневого шага флоу наружу (в стек-владелец). null — онбординг
     *   открыт как корень приложения, возвращаться некуда (кнопки «назад» на корневом шаге нет).
     */
    @Composable
    fun Onboarding(startAt: OnboardingStart, onFinished: () -> Unit, onExit: (() -> Unit)?)
}

/** С какого шага начинать флоу. RepoPicker/ManualUrl — для сценария «сменить репозиторий из настроек». */
enum class OnboardingStart { Login, RepoPicker, ManualUrl }
