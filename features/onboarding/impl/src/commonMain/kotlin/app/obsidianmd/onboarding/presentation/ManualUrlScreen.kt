package app.obsidianmd.onboarding.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import app.obsidianmd.resources.action_continue
import app.obsidianmd.resources.action_hide
import app.obsidianmd.resources.action_show
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.repo_pick_manual_hint
import app.obsidianmd.resources.repo_pick_manual_label
import app.obsidianmd.resources.repo_pick_manual_readonly_warning
import app.obsidianmd.resources.repo_pick_manual_title
import app.obsidianmd.resources.repo_pick_manual_token_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualUrlScreen(
    onSubmit: (url: String, token: String) -> Unit,
    onBack: (() -> Unit)?,
) {
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.repo_pick_manual_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
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
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onSubmit(url.trim(), token) },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(Res.string.action_continue)) }
        }
    }
}
