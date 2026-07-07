package app.obsidianmd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.auth.AuthState

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
            AuthState.Idle -> Button(onClick = onLogin) { Text("Войти через GitHub") }
            is AuthState.AwaitingUser -> {
                Text("Код: ${state.userCode}")
                Button(
                    onClick = { onOpenUrl(state.verificationUri) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Открыть GitHub") }
                Text("Ожидание подтверждения…", Modifier.padding(top = 16.dp))
            }
            AuthState.Success -> Text("Вход выполнен")
            is AuthState.Failed -> {
                Text("Ошибка входа: ${state.reason}")
                Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) { Text("Повторить") }
            }
        }
    }
}
