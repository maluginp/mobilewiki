package app.obsidianmd.onboarding.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.obsidianmd.analytics.Analytics
import app.obsidianmd.onboarding.AuthState
import app.obsidianmd.onboarding.AuthViewModel
import app.obsidianmd.onboarding.ManualConnectViewModel
import app.obsidianmd.onboarding.OnboardingPresentationProvider
import app.obsidianmd.onboarding.OnboardingStart
import app.obsidianmd.onboarding.RepoPickerViewModel
import app.obsidianmd.onboarding.RepoValidationViewModel
import app.obsidianmd.onboarding.ValidationState
import app.obsidianmd.settings.RepoSettingsStore
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Весь флоу онбординга в одном composable: свой вложенный бэкстек (Step), свои переходы и запись
 * выбранного репозитория. Наружу — только startAt (с чего начать) и onFinished (когда закончили).
 * Онбординг-экраны рисуются на всю ширину с учётом системных вставок (нет своего Scaffold).
 */
internal class OnboardingPresentationProviderImpl : OnboardingPresentationProvider {

    @Composable
    override fun Onboarding(startAt: OnboardingStart, onFinished: () -> Unit, onExit: (() -> Unit)?) {
        val settings = koinInject<RepoSettingsStore>()
        val backStack = rememberNavBackStack(onboardingSavedState, startStep(startAt))

        // «Назад» с экрана: если внутри флоу есть куда возвращаться — pop вложенного стека, иначе
        // (это корневой шаг) — выход наружу через onExit. null → кнопки «назад» нет.
        val backOrExit: (() -> Unit)? = if (backStack.size > 1) {
            { backStack.removeLastOrNull(); Unit }
        } else {
            onExit
        }

        Box(Modifier.safeDrawingPadding()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Step.Login> {
                        val vm: AuthViewModel = koinViewModel()
                        val state by vm.state.collectAsState()
                        LaunchedEffect(state) {
                            if (state is AuthState.Success) {
                                val hasRepo = !settings.getRemoteUrl().isNullOrBlank()
                                when (val action = afterSignIn(hasRepo)) {
                                    OnboardingAction.Finish -> onFinished()
                                    is OnboardingAction.Go -> backStack.add(action.step)
                                }
                            }
                        }
                        // Idle — приветствие с выбором режима; после старта авторизации тот же экран показывает код.
                        if (state is AuthState.Idle) {
                            WelcomeScreen(
                                onSignInGitHub = vm::login,
                                onConnectByUrl = { backStack.add(Step.ManualUrl) },
                                onUseLocal = {
                                    Analytics.event("repo_connected", mapOf("mode" to "local"))
                                    settings.setOnboardingDone(true)
                                    onFinished()
                                },
                            )
                        } else {
                            val uriHandler = LocalUriHandler.current
                            LoginScreen(state = state, onLogin = vm::login, onOpenUrl = { uriHandler.openUri(it) })
                        }
                    }
                    entry<Step.RepoPicker> {
                        val vm: RepoPickerViewModel = koinViewModel()
                        LaunchedEffect(Unit) { vm.load() }
                        val state by vm.state.collectAsState()
                        RepoPickerScreen(
                            state = state,
                            onChoose = { url -> backStack.add(Step.Validate(url)) },
                            onRetry = vm::load,
                            onEnterManually = { backStack.add(Step.ManualUrl) },
                            onBack = backOrExit,
                        )
                    }
                    entry<Step.ManualUrl> {
                        val vm: ManualConnectViewModel = koinViewModel()
                        ManualUrlScreen(
                            onSubmit = { url, token -> backStack.add(Step.Validate(vm.connect(url, token))) },
                            onBack = backOrExit,
                        )
                    }
                    entry<Step.Validate> { key ->
                        val vm: RepoValidationViewModel = koinViewModel()
                        LaunchedEffect(key.url) { vm.validate(key.url) }
                        val state by vm.state.collectAsState()
                        RepoValidationScreen(
                            state = state,
                            onContinue = {
                                settings.setRemoteUrl(key.url)
                                settings.setWritable((state as? ValidationState.Ok)?.canWrite ?: true)
                                settings.setOnboardingDone(true)
                                onFinished()
                            },
                            onRetry = { vm.validate(key.url) },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}
