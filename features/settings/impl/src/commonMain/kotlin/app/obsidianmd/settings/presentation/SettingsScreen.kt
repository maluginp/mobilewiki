package app.obsidianmd.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_cancel
import app.obsidianmd.resources.action_sync_now
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.settings_repo_change
import app.obsidianmd.resources.settings_repo_change_warning
import app.obsidianmd.resources.settings_repo_current
import app.obsidianmd.resources.settings_repo_local
import app.obsidianmd.resources.settings_repo_mode_github
import app.obsidianmd.resources.settings_repo_mode_github_desc
import app.obsidianmd.resources.settings_repo_mode_local
import app.obsidianmd.resources.settings_repo_mode_local_desc
import app.obsidianmd.resources.settings_repo_mode_manual
import app.obsidianmd.resources.settings_repo_mode_manual_desc
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
        // Синхронизировать нечего, пока репозиторий не выбран (локальный режим).
        if (url.isNotBlank()) {
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
        }

        Text(stringResource(Res.string.settings_repo_current), style = MaterialTheme.typography.titleMedium)
        Text(
            url.ifBlank { stringResource(Res.string.settings_repo_local) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        var showChange by remember { mutableStateOf(false) }
        Button(onClick = { showChange = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.settings_repo_change))
        }

        // Один диалог: предупреждение сверху + выбор типа с кратким описанием (без промежуточного шага).
        if (showChange) {
            ChangeRepoDialog(
                onDismiss = { showChange = false },
                onPickFromGitHub = { showChange = false; onPickFromGitHub() },
                onConnectManually = { showChange = false; onConnectManually() },
                onUseLocal = { showChange = false; onUseLocal() },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        aiSection()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeRepoDialog(
    onDismiss: () -> Unit,
    onPickFromGitHub: () -> Unit,
    onConnectManually: () -> Unit,
    onUseLocal: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_repo_change)) },
        text = {
            Column {
                RepoChangeWarning()
                Spacer(Modifier.height(12.dp))
                RepoModeRow(
                    title = stringResource(Res.string.settings_repo_mode_github),
                    desc = stringResource(Res.string.settings_repo_mode_github_desc),
                    onClick = onPickFromGitHub,
                )
                RepoModeRow(
                    title = stringResource(Res.string.settings_repo_mode_manual),
                    desc = stringResource(Res.string.settings_repo_mode_manual_desc),
                    onClick = onConnectManually,
                )
                RepoModeRow(
                    title = stringResource(Res.string.settings_repo_mode_local),
                    desc = stringResource(Res.string.settings_repo_mode_local_desc),
                    onClick = onUseLocal,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

/** Плашка-предупреждение: смена репозитория может привести к потере несинхронизированных заметок. */
@Composable
private fun RepoChangeWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(Res.string.settings_repo_change_warning),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Строка выбора типа репозитория: название + краткое описание, вся строка кликабельна. */
@Composable
private fun RepoModeRow(title: String, desc: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
