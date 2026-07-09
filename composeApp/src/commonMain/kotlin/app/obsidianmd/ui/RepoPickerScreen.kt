package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.auth.GitHubRepo
import app.obsidianmd.auth.RepoPickerState
import app.obsidianmd.auth.filterRepos
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_retry
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.repo_pick_error
import app.obsidianmd.resources.repo_pick_loading
import app.obsidianmd.resources.repo_pick_manual_hint
import app.obsidianmd.resources.repo_pick_manual_label
import app.obsidianmd.resources.repo_pick_search
import org.jetbrains.compose.resources.stringResource

@Composable
fun RepoPickerScreen(
    state: RepoPickerState,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
    onManualSave: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (state) {
            RepoPickerState.Loading -> Text(stringResource(Res.string.repo_pick_loading))
            is RepoPickerState.Error -> {
                Text(stringResource(Res.string.repo_pick_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(Res.string.action_retry))
                }
            }
            is RepoPickerState.Loaded -> {
                var query by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(Res.string.repo_pick_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(filterRepos(state.repos, query)) { repo ->
                        RepoRow(repo, onPick)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        ManualEntry(onManualSave)
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onPick: (String) -> Unit) {
    TextButton(onClick = { onPick(repo.cloneUrl) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (repo.private) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            }
            Text(repo.fullName)
        }
    }
}

@Composable
private fun ManualEntry(onManualSave: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(Res.string.repo_pick_manual_label)) },
        placeholder = { Text(stringResource(Res.string.repo_pick_manual_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onManualSave(url) },
        enabled = url.isNotBlank(),
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(stringResource(Res.string.action_save)) }
}
