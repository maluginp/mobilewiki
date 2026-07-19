package app.obsidianmd.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_sync_now
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.settings_repo_change
import app.obsidianmd.resources.settings_repo_current
import app.obsidianmd.resources.settings_repo_editable
import app.obsidianmd.resources.settings_repo_local
import app.obsidianmd.resources.settings_repo_readonly
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
    writable: Boolean = true,
    onSync: () -> Unit,
    onNavigateBack: () -> Unit,
    onChangeRepository: () -> Unit = {},
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
        if (url.isBlank()) {
            Text(
                stringResource(Res.string.settings_repo_local),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            // Путь до git-репозитория + пометка доступа (можно ли редактировать).
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            RepoAccessBadge(writable, Modifier.padding(top = 6.dp))
        }

        // Смена репозитория — на отдельном экране (предупреждение + выбор типа с описанием).
        Button(onClick = onChangeRepository, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.settings_repo_change))
        }

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        aiSection()
        }
    }
}

/** Пометка доступа к репозиторию: «доступно для редактирования» или «только чтение». */
@Composable
private fun RepoAccessBadge(writable: Boolean, modifier: Modifier = Modifier) {
    val icon = if (writable) Icons.Filled.Edit else Icons.Filled.Visibility
    val label = stringResource(if (writable) Res.string.settings_repo_editable else Res.string.settings_repo_readonly)
    val tint = if (writable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
