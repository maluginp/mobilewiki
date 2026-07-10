package app.obsidianmd.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
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
import app.obsidianmd.resources.repo_pick_enter_manually
import app.obsidianmd.resources.repo_pick_error
import app.obsidianmd.resources.repo_pick_loading
import app.obsidianmd.resources.repo_pick_search
import org.jetbrains.compose.resources.stringResource

@Composable
fun RepoPickerScreen(
    state: RepoPickerState,
    onChoose: (String) -> Unit,
    onRetry: () -> Unit,
    onEnterManually: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (state) {
            RepoPickerState.Loading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(Res.string.repo_pick_loading), Modifier.padding(top = 16.dp))
            }
            is RepoPickerState.Error -> {
                Text(stringResource(Res.string.repo_pick_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(Res.string.action_retry))
                }
                TextButton(onClick = onEnterManually) {
                    Text(stringResource(Res.string.repo_pick_enter_manually))
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
                        RepoRow(repo, onChoose)
                    }
                    item {
                        TextButton(
                            onClick = onEnterManually,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Text(stringResource(Res.string.repo_pick_enter_manually))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onChoose: (String) -> Unit) {
    TextButton(onClick = { onChoose(repo.cloneUrl) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (repo.private) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            }
            Text(repo.fullName)
        }
    }
}
