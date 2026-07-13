package app.obsidianmd.ui


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import app.obsidianmd.resources.action_hide
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.action_show
import app.obsidianmd.resources.action_sync_now
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.title_settings
import app.obsidianmd.resources.error_with_reason
import app.obsidianmd.resources.repo_pick_from_github
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
import app.obsidianmd.settings.SettingsState
import app.obsidianmd.sync.SyncResult
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onSave: (String) -> Unit,
    syncStatus: SyncStatus,
    onSync: () -> Unit,
    onNavigateBack: () -> Unit,
    onPickFromGitHub: () -> Unit = {},
    aiSection: @Composable () -> Unit = {},
) {
    // Локальный черновик — правки живут в поле до нажатия «Сохранить».
    var url by remember(state.url) { mutableStateOf(state.url) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(padding).padding(16.dp),
        ) {
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
        TextButton(onClick = onPickFromGitHub) {
            Text(stringResource(Res.string.repo_pick_from_github))
        }
        Button(
            onClick = { onSave(url); saved = true },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(Res.string.action_save)) }
        if (saved) {
            Text(
                stringResource(Res.string.settings_saved),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        aiSection()
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
