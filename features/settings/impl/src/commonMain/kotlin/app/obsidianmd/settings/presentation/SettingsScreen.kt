package app.obsidianmd.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_cancel
import app.obsidianmd.resources.action_continue
import app.obsidianmd.resources.action_sync_now
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.settings_repo_change
import app.obsidianmd.resources.settings_repo_change_warning
import app.obsidianmd.resources.settings_repo_current
import app.obsidianmd.resources.settings_repo_local
import app.obsidianmd.resources.settings_repo_mode_github
import app.obsidianmd.resources.settings_repo_mode_local
import app.obsidianmd.resources.settings_repo_mode_manual
import app.obsidianmd.resources.settings_sync_desc
import app.obsidianmd.resources.settings_sync_title
import app.obsidianmd.resources.title_settings
import org.jetbrains.compose.resources.stringResource

// Stateless-экран настроек: статус синка приходит готовой строкой (feature не знает про SyncStatus).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    url: String,
    syncing: Boolean,
    syncStatusText: String,
    onSync: () -> Unit,
    onNavigateBack: () -> Unit,
    onPickFromGitHub: () -> Unit = {},
    onConnectManually: () -> Unit = {},
    onUseLocal: () -> Unit = {},
    aiSection: @Composable () -> Unit = {},
) {
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
            enabled = !syncing,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(Res.string.action_sync_now)) }
        if (syncStatusText.isNotEmpty()) Text(syncStatusText, Modifier.padding(top = 8.dp))

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        Text(stringResource(Res.string.settings_repo_current), style = MaterialTheme.typography.titleMedium)
        Text(
            url.ifBlank { stringResource(Res.string.settings_repo_local) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        var showWarning by remember { mutableStateOf(false) }
        var showModes by remember { mutableStateOf(false) }
        Button(onClick = { showWarning = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.settings_repo_change))
        }

        if (showWarning) {
            AlertDialog(
                onDismissRequest = { showWarning = false },
                title = { Text(stringResource(Res.string.settings_repo_change)) },
                text = { Text(stringResource(Res.string.settings_repo_change_warning)) },
                confirmButton = {
                    TextButton(onClick = { showWarning = false; showModes = true }) {
                        Text(stringResource(Res.string.action_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWarning = false }) { Text(stringResource(Res.string.action_cancel)) }
                },
            )
        }
        if (showModes) {
            AlertDialog(
                onDismissRequest = { showModes = false },
                title = { Text(stringResource(Res.string.settings_repo_change)) },
                text = {
                    Column {
                        TextButton(
                            onClick = { showModes = false; onPickFromGitHub() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(Res.string.settings_repo_mode_github)) }
                        TextButton(
                            onClick = { showModes = false; onConnectManually() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(Res.string.settings_repo_mode_manual)) }
                        TextButton(
                            onClick = { showModes = false; onUseLocal() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(Res.string.settings_repo_mode_local)) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModes = false }) { Text(stringResource(Res.string.action_cancel)) }
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        aiSection()
        }
    }
}
