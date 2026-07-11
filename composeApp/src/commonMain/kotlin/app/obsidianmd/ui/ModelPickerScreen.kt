package app.obsidianmd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.obsidianmd.ai.ContextFilter
import app.obsidianmd.ai.ModelInfo
import app.obsidianmd.ai.PriceFilter
import app.obsidianmd.ai.contextLabel
import app.obsidianmd.ai.filterModels
import app.obsidianmd.ai.priceLabel
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.filter_any
import app.obsidianmd.resources.filter_ctx_128k
import app.obsidianmd.resources.filter_ctx_1m
import app.obsidianmd.resources.filter_ctx_32k
import app.obsidianmd.resources.filter_price_free
import app.obsidianmd.resources.filter_price_under_1
import app.obsidianmd.resources.filter_price_under_5
import app.obsidianmd.resources.model_filter_context
import app.obsidianmd.resources.model_filter_price
import app.obsidianmd.resources.models_empty
import org.jetbrains.compose.resources.StringResource
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
    var price by remember { mutableStateOf(PriceFilter.ANY) }
    var context by remember { mutableStateOf(ContextFilter.ANY) }
    val filtered = models.filterModels(query, price, context)

    // Первая загрузка (список пуст) — вертелка по центру. Дальше обновление показывает индикатор
    // самого pull-to-refresh, список не мигает.
    if (loading && models.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        FilterBar(price, context, onPrice = { price = it }, onContext = { context = it })
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
}

@Composable
private fun FilterBar(
    price: PriceFilter,
    context: ContextFilter,
    onPrice: (PriceFilter) -> Unit,
    onContext: (ContextFilter) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterMenuChip(
            label = stringResource(Res.string.model_filter_price),
            active = price != PriceFilter.ANY,
            selectedLabel = stringResource(price.label()),
            options = PriceFilter.entries,
            optionLabel = { stringResource(it.label()) },
            onSelect = onPrice,
        )
        FilterMenuChip(
            label = stringResource(Res.string.model_filter_context),
            active = context != ContextFilter.ANY,
            selectedLabel = stringResource(context.label()),
            options = ContextFilter.entries,
            optionLabel = { stringResource(it.label()) },
            onSelect = onContext,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterMenuChip(
    label: String,
    active: Boolean,
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(if (active) "$label: $selectedLabel" else label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}

private fun PriceFilter.label(): StringResource = when (this) {
    PriceFilter.ANY -> Res.string.filter_any
    PriceFilter.FREE -> Res.string.filter_price_free
    PriceFilter.UNDER_1 -> Res.string.filter_price_under_1
    PriceFilter.UNDER_5 -> Res.string.filter_price_under_5
}

private fun ContextFilter.label(): StringResource = when (this) {
    ContextFilter.ANY -> Res.string.filter_any
    ContextFilter.K32 -> Res.string.filter_ctx_32k
    ContextFilter.K128 -> Res.string.filter_ctx_128k
    ContextFilter.M1 -> Res.string.filter_ctx_1m
}
