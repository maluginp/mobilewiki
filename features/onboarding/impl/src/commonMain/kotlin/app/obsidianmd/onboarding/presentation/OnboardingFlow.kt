package app.obsidianmd.onboarding.presentation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import app.obsidianmd.onboarding.AuthState
import app.obsidianmd.onboarding.OnboardingStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Шаги вложенного бэкстека онбординга. */
internal sealed interface Step : NavKey {
    @Serializable data object Login : Step
    @Serializable data object RepoPicker : Step
    @Serializable data object ManualUrl : Step
    @Serializable data class Validate(val url: String) : Step
}

internal sealed interface OnboardingAction {
    data class Go(val step: Step) : OnboardingAction
    data object Finish : OnboardingAction
}

/** Единственная развилка, которую раньше делал host через startStack: вход завершён. */
internal fun afterSignIn(hasRepo: Boolean): OnboardingAction =
    if (hasRepo) OnboardingAction.Finish else OnboardingAction.Go(Step.RepoPicker)

/** С какого шага стартует вложенный бэкстек по внешнему [OnboardingStart]. */
internal fun startStep(start: OnboardingStart): Step = when (start) {
    OnboardingStart.Login -> Step.Login
    OnboardingStart.RepoPicker -> Step.RepoPicker
    OnboardingStart.ManualUrl -> Step.ManualUrl
}

/**
 * С какого шага реально начинать. Для выбора GitHub-репо ([OnboardingStart.RepoPicker]) сперва
 * нужна авторизация: если токена нет — начинаем со входа, иначе сразу с выбора репо.
 */
internal fun initialStep(start: OnboardingStart, hasToken: Boolean): Step =
    if (start == OnboardingStart.RepoPicker && !hasToken) Step.Login else startStep(start)

/**
 * Куда идти после успешного входа. Если онбординг открыт ради выбора GitHub-репо — всегда к выбору
 * репо (даже если репозиторий уже задан, ведь пользователь пришёл его менять); иначе обычная
 * развилка [afterSignIn].
 */
internal fun postSignInAction(start: OnboardingStart, hasRepo: Boolean): OnboardingAction =
    if (start == OnboardingStart.RepoPicker) OnboardingAction.Go(Step.RepoPicker) else afterSignIn(hasRepo)

/**
 * Показывать ли экран приветствия (с выбором режима: GitHub / URL / локально). Только для обычного
 * онбординга в состоянии [AuthState.Idle]. Если онбординг открыт ради выбора GitHub-репо
 * ([OnboardingStart.RepoPicker]) — приветствие не показываем, сразу ведём на вход в GitHub.
 */
internal fun showWelcome(start: OnboardingStart, state: AuthState): Boolean =
    state is AuthState.Idle && start != OnboardingStart.RepoPicker

/**
 * Запускать ли вход в GitHub сразу, без экрана с кнопкой «Sign in». Так для смены репо на GitHub
 * ([OnboardingStart.RepoPicker]) в состоянии [AuthState.Idle] мы ведём прямо к экрану авторизации
 * (device-flow с кодом), а не показываем промежуточную кнопку.
 */
internal fun autoStartGitHubAuth(start: OnboardingStart, state: AuthState): Boolean =
    state is AuthState.Idle && start == OnboardingStart.RepoPicker

/** Полиморфная сериализация шагов для вложенного [androidx.navigation3.runtime.NavBackStack]. */
internal val onboardingSavedState: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Step.Login::class, Step.Login.serializer())
            subclass(Step.RepoPicker::class, Step.RepoPicker.serializer())
            subclass(Step.ManualUrl::class, Step.ManualUrl.serializer())
            subclass(Step.Validate::class, Step.Validate.serializer())
        }
    }
}
