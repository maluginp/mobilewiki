package app.obsidianmd.nav

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Все пункты назначения приложения. Параметры живут в маршруте, а не во флагах. */
@Serializable
sealed interface Route : NavKey {
    // Онбординг
    @Serializable data object Login : Route
    @Serializable data object RepoPicker : Route
    @Serializable data object RepoManualUrl : Route
    @Serializable data class RepoValidate(val url: String) : Route

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
        subclass(Route.Login::class, Route.Login.serializer())
        subclass(Route.RepoPicker::class, Route.RepoPicker.serializer())
        subclass(Route.RepoManualUrl::class, Route.RepoManualUrl.serializer())
        subclass(Route.RepoValidate::class, Route.RepoValidate.serializer())
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
