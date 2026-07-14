package app.obsidianmd.onboarding.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import app.obsidianmd.onboarding.OnboardingPresentationProvider
import app.obsidianmd.onboarding.AuthState
import app.obsidianmd.onboarding.AuthViewModel
import app.obsidianmd.onboarding.RepoPickerViewModel
import app.obsidianmd.onboarding.RepoValidationViewModel
import org.koin.compose.viewmodel.koinViewModel

internal class OnboardingPresentationProviderImpl : OnboardingPresentationProvider {

    @Composable
    override fun Login(onSignedIn: () -> Unit) {
        val vm: AuthViewModel = koinViewModel()
        val state by vm.state.collectAsState()
        LaunchedEffect(state) { if (state is AuthState.Success) onSignedIn() }
        // Idle — приветствие с кнопкой; после старта авторизации тот же экран показывает код.
        if (state is AuthState.Idle) {
            WelcomeScreen(onSignIn = vm::login)
        } else {
            val uriHandler = LocalUriHandler.current
            LoginScreen(state = state, onLogin = vm::login, onOpenUrl = { uriHandler.openUri(it) })
        }
    }

    @Composable
    override fun RepoPicker(
        onChosen: (String) -> Unit,
        onEnterManually: () -> Unit,
        onBack: (() -> Unit)?,
    ) {
        val vm: RepoPickerViewModel = koinViewModel()
        LaunchedEffect(Unit) { vm.load() }
        val state by vm.state.collectAsState()
        RepoPickerScreen(
            state = state,
            onChoose = onChosen,
            onRetry = vm::load,
            onEnterManually = onEnterManually,
            onBack = onBack,
        )
    }

    @Composable
    override fun ManualUrl(onSubmit: (String) -> Unit, onBack: () -> Unit) {
        ManualUrlScreen(onSubmit = onSubmit, onBack = onBack)
    }

    @Composable
    override fun RepoValidate(url: String, onContinue: () -> Unit, onBack: () -> Unit) {
        val vm: RepoValidationViewModel = koinViewModel()
        LaunchedEffect(url) { vm.validate(url) }
        val state by vm.state.collectAsState()
        RepoValidationScreen(
            state = state,
            onContinue = onContinue,
            onRetry = { vm.validate(url) },
            onBack = onBack,
        )
    }
}
