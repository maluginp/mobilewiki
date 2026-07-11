package app.obsidianmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.obsidianmd.ai.ModelInfo
import app.obsidianmd.ai.contextLabel
import app.obsidianmd.ai.priceLabel
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.models_empty
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    models: List<ModelInfo>,
    loading: Boolean,
    selected: String,
    query: String,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val filtered = models.filter {
        query.isBlank() || it.id.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
    }
    // Первая загрузка (список пуст) — вертелка по центру. Дальше обновление показывает индикатор
    // самого pull-to-refresh, список не мигает.
    if (loading && models.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    PullToRefreshBox(isRefreshing = loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.models_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@PullToRefreshBox
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { m ->
                ListItem(
                    headlineContent = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val meta = listOf(m.contextLabel(), m.priceLabel()).filter { it.isNotEmpty() }
                        Text(
                            (listOf(m.id) + meta).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        if (m.id == selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onSelect(m.id) },
                )
            }
        }
    }
}
