package app.obsidianmd.auth

import androidx.compose.runtime.Composable

/**
 * Точка входа UI онбординга/авторизации для навигации основного модуля. Реализация — в :auth:impl
 * (internal), подключается через DI. Основной модуль не знает о конкретных экранах и ViewModel'ях
 * фичи — только даёт навигационные колбэки.
 */
interface AuthPresentationProvider {
    /** Вход: в состоянии Idle — приветствие с кнопкой, иначе device-code. onSignedIn — при успехе. */
    @Composable
    fun Login(onSignedIn: () -> Unit)

    /** Выбор репозитория из GitHub. onChosen получает clone URL. */
    @Composable
    fun RepoPicker(
        onChosen: (url: String) -> Unit,
        onEnterManually: () -> Unit,
        onBack: (() -> Unit)?,
    )

    /** Ручной ввод URL репозитория. */
    @Composable
    fun ManualUrl(onSubmit: (url: String) -> Unit, onBack: () -> Unit)

    /** Проверка доступа к выбранному репозиторию; onContinue — когда можно продолжить. */
    @Composable
    fun RepoValidate(url: String, onContinue: () -> Unit, onBack: () -> Unit)
}
