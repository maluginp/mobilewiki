package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import app.obsidianmd.sync.SyncResult

@Composable
fun SettingsScreen(
    currentUrl: String,
    onSave: (String) -> Unit,
    openRouterKey: String,
    onSaveKey: (String) -> Unit,
    syncStatus: SyncStatus,
    onSync: () -> Unit,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var key by remember(openRouterKey) { mutableStateOf(openRouterKey) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SettingField(
            label = "Repository URL",
            example = "https://github.com/username/my-vault.git",
            description = "HTTPS link to the Git repository with your notes. The app clones it " +
                "and keeps changes in sync.",
            value = url,
            onValueChange = { url = it; saved = false },
        )
        SettingField(
            label = "OpenRouter key",
            example = "sk-or-v1-abc123…",
            description = "API key from openrouter.ai/keys for the AI chat. Stored encrypted " +
                "on this device only.",
            value = key,
            onValueChange = { key = it; saved = false },
            secret = true,
        )
        Button(
            onClick = { onSave(url); onSaveKey(key); saved = true },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Save") }
        if (saved) {
            Text(
                "Saved ✓",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        Text("Synchronization", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pull the latest notes from the repository and push your local changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onSync,
            enabled = syncStatus !is SyncStatus.Running,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Sync now") }
        val status = syncStatusText(syncStatus)
        if (status.isNotEmpty()) Text(status, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SettingField(
    label: String,
    example: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
) {
    var visible by remember { mutableStateOf(!secret) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(example) },
        supportingText = { Text(description) },
        singleLine = true,
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } }
        } else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

internal fun syncStatusText(status: SyncStatus): String = when (status) {
    SyncStatus.Idle -> ""
    SyncStatus.Running -> "Syncing…"
    is SyncStatus.Done -> when (val r = status.result) {
        is SyncResult.Cloned -> "Done: cloned"
        is SyncResult.UpToDate -> "Up to date"
        is SyncResult.Synced ->
            "Synced" + if (r.conflictsResolved > 0) " (conflicts: ${r.conflictsResolved})" else ""
        is SyncResult.Failed -> "Error: ${r.reason}"
    }
}
