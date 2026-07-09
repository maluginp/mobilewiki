package app.obsidianmd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
fun WelcomeScreen(onSignIn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(Res.string.onboarding_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(onClick = onSignIn, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(Res.string.onboarding_sign_in))
        }
    }
}
