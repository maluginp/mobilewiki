package app.obsidianmd.onboarding.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_back
import app.obsidianmd.resources.action_continue
import app.obsidianmd.resources.action_hide
import app.obsidianmd.resources.action_show
import app.obsidianmd.resources.repo_pick_manual_hint
import app.obsidianmd.resources.repo_pick_manual_label
import app.obsidianmd.resources.repo_pick_manual_readonly_warning
import app.obsidianmd.resources.repo_pick_manual_title
import app.obsidianmd.resources.repo_pick_manual_token_label
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ManualUrlScreen(
    onSubmit: (url: String, token: String) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(Res.string.repo_pick_manual_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(Res.string.repo_pick_manual_label)) },
            placeholder = { Text(stringResource(Res.string.repo_pick_manual_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(Res.string.repo_pick_manual_token_label)) },
            singleLine = true,
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { tokenVisible = !tokenVisible }) {
                    Text(stringResource(if (tokenVisible) Res.string.action_hide else Res.string.action_show))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (token.isBlank()) {
            Text(
                stringResource(Res.string.repo_pick_manual_readonly_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onSubmit(url.trim(), token) },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(Res.string.action_continue)) }
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(Res.string.action_back)) }
    }
}
