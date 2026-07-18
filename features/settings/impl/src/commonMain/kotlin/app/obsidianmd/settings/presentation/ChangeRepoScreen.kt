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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.settings_repo_change
import app.obsidianmd.resources.settings_repo_change_warning
import app.obsidianmd.resources.settings_repo_mode_github
import app.obsidianmd.resources.settings_repo_mode_github_desc
import app.obsidianmd.resources.settings_repo_mode_local
import app.obsidianmd.resources.settings_repo_mode_local_desc
import app.obsidianmd.resources.settings_repo_mode_manual
import app.obsidianmd.resources.settings_repo_mode_manual_desc
import org.jetbrains.compose.resources.stringResource

/**
 * Отдельный экран смены репозитория: сверху предупреждение о потере несинхронизированных данных,
 * ниже — список типов подключения с кратким описанием. Один экран вместо двух диалогов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangeRepoScreen(
    onPickFromGitHub: () -> Unit,
    onConnectManually: () -> Unit,
    onUseLocal: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_repo_change)) },
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
            RepoChangeWarning()
            Spacer(Modifier.height(16.dp))
            RepoModeRow(
                title = stringResource(Res.string.settings_repo_mode_github),
                desc = stringResource(Res.string.settings_repo_mode_github_desc),
                onClick = onPickFromGitHub,
            )
            HorizontalDivider()
            RepoModeRow(
                title = stringResource(Res.string.settings_repo_mode_manual),
                desc = stringResource(Res.string.settings_repo_mode_manual_desc),
                onClick = onConnectManually,
            )
            HorizontalDivider()
            RepoModeRow(
                title = stringResource(Res.string.settings_repo_mode_local),
                desc = stringResource(Res.string.settings_repo_mode_local_desc),
                onClick = onUseLocal,
            )
        }
    }
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
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Строка выбора типа репозитория: название + краткое описание, вся строка кликабельна. */
@Composable
private fun RepoModeRow(title: String, desc: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
