package app.obsidianmd.auth.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.onboarding_body
import app.obsidianmd.resources.onboarding_sign_in
import app.obsidianmd.resources.onboarding_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WelcomeScreen(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(Res.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.onboarding_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                stringResource(Res.string.onboarding_sign_in),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
