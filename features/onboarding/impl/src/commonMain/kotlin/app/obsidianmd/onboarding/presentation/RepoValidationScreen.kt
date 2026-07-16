package app.obsidianmd.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.onboarding.ValidationState
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_back
import app.obsidianmd.resources.action_continue
import app.obsidianmd.resources.action_retry
import app.obsidianmd.resources.repo_check_checking
import app.obsidianmd.resources.repo_check_denied_body
import app.obsidianmd.resources.repo_check_denied_hint1
import app.obsidianmd.resources.repo_check_denied_hint2
import app.obsidianmd.resources.repo_check_denied_hint3
import app.obsidianmd.resources.repo_check_denied_title
import app.obsidianmd.resources.repo_check_ok_body
import app.obsidianmd.resources.repo_check_ok_title
import app.obsidianmd.resources.repo_check_unknown_body
import app.obsidianmd.resources.repo_check_unknown_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RepoValidationScreen(
    state: ValidationState,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    if (state is ValidationState.Checking) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.repo_check_checking), Modifier.padding(top = 16.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))
        when (state) {
            ValidationState.Ok -> {
                Text(
                    stringResource(Res.string.repo_check_ok_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(Res.string.repo_check_ok_body), Modifier.padding(top = 12.dp))
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(Res.string.action_continue), onContinue)
            }
            is ValidationState.Denied -> {
                Text(
                    stringResource(Res.string.repo_check_denied_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(Res.string.repo_check_denied_body),
                    Modifier.padding(top = 12.dp),
                )
                Text(stringResource(Res.string.repo_check_denied_hint1), Modifier.padding(top = 8.dp))
                Text(stringResource(Res.string.repo_check_denied_hint2), Modifier.padding(top = 4.dp))
                Text(stringResource(Res.string.repo_check_denied_hint3), Modifier.padding(top = 4.dp))
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(Res.string.action_retry), onRetry)
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(Res.string.action_back))
                }
            }
            is ValidationState.Unknown -> {
                Text(
                    stringResource(Res.string.repo_check_unknown_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(Res.string.repo_check_unknown_body), Modifier.padding(top = 12.dp))
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(Res.string.action_continue), onContinue)
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(Res.string.action_back))
                }
            }
            ValidationState.Checking -> Unit // обработано выше
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(text) }
}
