package app.obsidianmd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.obsidianmd.auth.AuthState
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_retry
import app.obsidianmd.resources.login_code_copied
import app.obsidianmd.resources.login_copy_code
import app.obsidianmd.resources.login_error
import app.obsidianmd.resources.login_open_github
import app.obsidianmd.resources.login_sign_in
import app.obsidianmd.resources.login_signed_in
import app.obsidianmd.resources.login_waiting
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    state: AuthState,
    onLogin: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            AuthState.Idle -> Button(onClick = onLogin) { Text(stringResource(Res.string.login_sign_in)) }
            is AuthState.AwaitingUser -> {
                val clipboard = LocalClipboardManager.current
                var copied by remember(state.userCode) { mutableStateOf(false) }
                Card {
                    Text(
                        state.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(state.userCode)); copied = true },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(stringResource(if (copied) Res.string.login_code_copied else Res.string.login_copy_code))
                }
                Button(
                    onClick = { onOpenUrl(state.verificationUri) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(Res.string.login_open_github)) }
                CircularProgressIndicator(Modifier.padding(top = 24.dp))
                Text(stringResource(Res.string.login_waiting), Modifier.padding(top = 8.dp))
            }
            AuthState.Success -> Text(stringResource(Res.string.login_signed_in))
            is AuthState.Failed -> {
                Text(stringResource(Res.string.login_error, state.reason))
                Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(Res.string.action_retry))
                }
            }
        }
    }
}
