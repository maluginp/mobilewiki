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
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_hide
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.action_show
import app.obsidianmd.resources.action_sync_now
import app.obsidianmd.resources.error_with_reason
import app.obsidianmd.resources.settings_key_desc
import app.obsidianmd.resources.settings_key_example
import app.obsidianmd.resources.settings_key_label
import app.obsidianmd.resources.settings_repo_url_desc
import app.obsidianmd.resources.settings_repo_url_example
import app.obsidianmd.resources.settings_repo_url_label
import app.obsidianmd.resources.settings_saved
import app.obsidianmd.resources.settings_sync_desc
import app.obsidianmd.resources.settings_sync_title
import app.obsidianmd.resources.sync_done_cloned
import app.obsidianmd.resources.sync_synced
import app.obsidianmd.resources.sync_synced_conflicts
import app.obsidianmd.resources.sync_syncing
import app.obsidianmd.resources.sync_up_to_date
import app.obsidianmd.sync.SyncResult
import org.jetbrains.compose.resources.stringResource

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
        Text(stringResource(Res.string.settings_sync_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.settings_sync_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onSync,
            enabled = syncStatus !is SyncStatus.Running,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(Res.string.action_sync_now)) }
        val status = syncStatusText(syncStatus)
        if (status.isNotEmpty()) Text(status, Modifier.padding(top = 8.dp))

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        SettingField(
            label = stringResource(Res.string.settings_repo_url_label),
            example = stringResource(Res.string.settings_repo_url_example),
            description = stringResource(Res.string.settings_repo_url_desc),
            value = url,
            onValueChange = { url = it; saved = false },
        )
        SettingField(
            label = stringResource(Res.string.settings_key_label),
            example = stringResource(Res.string.settings_key_example),
            description = stringResource(Res.string.settings_key_desc),
            value = key,
            onValueChange = { key = it; saved = false },
            secret = true,
        )
        Button(
            onClick = { onSave(url); onSaveKey(key); saved = true },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(Res.string.action_save)) }
        if (saved) {
            Text(
                stringResource(Res.string.settings_saved),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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
            {
                TextButton(onClick = { visible = !visible }) {
                    Text(stringResource(if (visible) Res.string.action_hide else Res.string.action_show))
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
internal fun syncStatusText(status: SyncStatus): String = when (status) {
    SyncStatus.Idle -> ""
    SyncStatus.Running -> stringResource(Res.string.sync_syncing)
    is SyncStatus.Done -> when (val r = status.result) {
        is SyncResult.Cloned -> stringResource(Res.string.sync_done_cloned)
        is SyncResult.UpToDate -> stringResource(Res.string.sync_up_to_date)
        is SyncResult.Synced ->
            if (r.conflictsResolved > 0) {
                stringResource(Res.string.sync_synced_conflicts, r.conflictsResolved)
            } else {
                stringResource(Res.string.sync_synced)
            }
        is SyncResult.Failed -> stringResource(Res.string.error_with_reason, r.reason)
    }
}
