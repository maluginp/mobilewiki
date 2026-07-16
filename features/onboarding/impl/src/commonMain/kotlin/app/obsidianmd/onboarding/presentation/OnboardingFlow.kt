package app.obsidianmd.onboarding.presentation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
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
