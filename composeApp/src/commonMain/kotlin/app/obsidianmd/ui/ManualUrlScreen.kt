package app.obsidianmd.ui

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
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_back
import app.obsidianmd.resources.action_continue
import app.obsidianmd.resources.repo_pick_manual_hint
import app.obsidianmd.resources.repo_pick_manual_label
import app.obsidianmd.resources.repo_pick_manual_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ManualUrlScreen(
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
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
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onSubmit(url.trim()) },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(Res.string.action_continue)) }
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(Res.string.action_back)) }
    }
}
