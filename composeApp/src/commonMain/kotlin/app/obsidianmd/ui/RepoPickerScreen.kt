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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.obsidianmd.auth.GitHubRepo
import app.obsidianmd.auth.RepoPickerState
import app.obsidianmd.auth.filterRepos
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_retry
import app.obsidianmd.resources.cd_back
import app.obsidianmd.resources.repo_pick_enter_manually
import app.obsidianmd.resources.repo_pick_error
import app.obsidianmd.resources.repo_pick_loading
import app.obsidianmd.resources.repo_pick_not_found
import app.obsidianmd.resources.repo_pick_search
import app.obsidianmd.resources.repo_pick_search_hint
import app.obsidianmd.resources.repo_pick_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoPickerScreen(
    state: RepoPickerState,
    onChoose: (String) -> Unit,
    onRetry: () -> Unit,
    onEnterManually: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.repo_pick_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when (state) {
            RepoPickerState.Loading -> Column(
                Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(Res.string.repo_pick_loading), Modifier.padding(top = 16.dp))
            }

            is RepoPickerState.Error -> Column(Modifier.padding(innerPadding).padding(16.dp)) {
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
                val filtered = filterRepos(state.repos, query)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = innerPadding) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(Res.string.repo_pick_search)) },
                            placeholder = { Text(stringResource(Res.string.repo_pick_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    stringResource(Res.string.repo_pick_not_found),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = onEnterManually, modifier = Modifier.padding(top = 16.dp)) {
                                    Text(stringResource(Res.string.repo_pick_enter_manually))
                                }
                            }
                        }
                    } else {
                        items(filtered) { repo -> RepoRow(repo, onChoose) }
                        item {
                            TextButton(
                                onClick = onEnterManually,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) {
                                Text(stringResource(Res.string.repo_pick_enter_manually))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoRow(repo: GitHubRepo, onChoose: (String) -> Unit) {
    TextButton(onClick = { onChoose(repo.cloneUrl) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (repo.private) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            }
            Text(repo.fullName)
        }
    }
}
