package app.obsidianmd.nav

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import app.obsidianmd.onboarding.OnboardingStart
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Все пункты назначения приложения. Параметры живут в маршруте, а не во флагах. */
@Serializable
sealed interface Route : NavKey {
    // Онбординг — весь флоу внутри :features:onboarding, host держит один маршрут
    @Serializable data class Onboarding(val startAt: OnboardingStart) : Route

    // Основное приложение
    @Serializable data class VaultList(val dir: String = "") : Route
    @Serializable data class Note(val path: String) : Route
    @Serializable data object Settings : Route
    @Serializable data object ModelPicker : Route
    @Serializable data object AiChat : Route
}

/**
 * Nav3 сериализует бэкстек через полиморфизм NavKey — регистрируем каждый маршрут явно.
 * Один модуль переиспользуется и в конфиге бэкстека, и в тестах.
 */
val navSerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Route.Onboarding::class, Route.Onboarding.serializer())
        subclass(Route.VaultList::class, Route.VaultList.serializer())
        subclass(Route.Note::class, Route.Note.serializer())
        subclass(Route.Settings::class, Route.Settings.serializer())
        subclass(Route.ModelPicker::class, Route.ModelPicker.serializer())
        subclass(Route.AiChat::class, Route.AiChat.serializer())
    }
}

/** Конфиг сохранения бэкстека для rememberNavBackStack. */
val navSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = navSerializersModule
}
