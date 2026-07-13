package app.obsidianmd.ui

import app.obsidianmd.vault.presentation.SyncStatus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.obsidianmd.ai.AiProvider
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_hide
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.action_show
import app.obsidianmd.resources.action_sync_now
import app.obsidianmd.resources.cd_edit_model
import app.obsidianmd.resources.error_with_reason
import app.obsidianmd.resources.repo_pick_from_github
import app.obsidianmd.resources.settings_ai_enable
import app.obsidianmd.resources.settings_ai_enable_desc
import app.obsidianmd.resources.settings_base_url_desc
import app.obsidianmd.resources.settings_base_url_example
import app.obsidianmd.resources.settings_base_url_label
import app.obsidianmd.resources.settings_key_desc
import app.obsidianmd.resources.settings_key_label
import app.obsidianmd.resources.settings_provider_label
import app.obsidianmd.resources.settings_model_desc
import app.obsidianmd.resources.settings_model_label
import app.obsidianmd.resources.settings_model_none
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

@Composable
fun SettingsScreen(
    state: SettingsState,
    onSave: (String) -> Unit,
    onSaveKey: (String) -> Unit,
    onSetAiEnabled: (Boolean) -> Unit,
    onEditModel: () -> Unit,
    syncStatus: SyncStatus,
    onSync: () -> Unit,
    onPickFromGitHub: () -> Unit = {},
    onSetProvider: (AiProvider) -> Unit = {},
    onSetCustomBaseUrl: (String) -> Unit = {},
) {
    // Локальные черновики полей — правки живут в поле до нажатия «Сохранить».
    // Модель сохраняется сразу при выборе на экране пикера, поэтому черновика для неё нет.
    var url by remember(state.url) { mutableStateOf(state.url) }
    var key by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var baseUrl by remember(state.customBaseUrl) { mutableStateOf(state.customBaseUrl) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
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

        HorizontalDivider(Modifier.padding(vertical = 24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.settings_ai_enable),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.aiEnabled, onCheckedChange = { onSetAiEnabled(it); saved = false })
        }
        Text(
            stringResource(Res.string.settings_ai_enable_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (state.aiEnabled) {
            ProviderDropdown(selected = state.provider, onSelect = { onSetProvider(it); saved = false })
            if (state.provider.needsBaseUrl) {
                SettingField(
                    label = stringResource(Res.string.settings_base_url_label),
                    example = stringResource(Res.string.settings_base_url_example),
                    description = stringResource(Res.string.settings_base_url_desc),
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                )
            }
            SettingField(
                label = stringResource(Res.string.settings_key_label),
                example = state.provider.keyExample,
                description = stringResource(Res.string.settings_key_desc),
                value = key,
                onValueChange = { key = it; saved = false },
                secret = true,
            )
            ModelRow(model = state.aiModel, onEdit = onEditModel)
        }
        Button(
            onClick = { onSave(url); onSaveKey(key); onSetCustomBaseUrl(baseUrl); saved = true },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(Res.string.action_save)) }
        if (saved) {
            Text(
                stringResource(Res.string.settings_saved),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// Текущая модель + карандаш; выбор — на отдельном экране ModelPickerScreen.
// Обычный Row (не ListItem) — чтобы левый край совпадал с остальными полями секции.
@Composable
private fun ModelRow(model: String, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.settings_model_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                model.ifBlank { stringResource(Res.string.settings_model_none) },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(Res.string.settings_model_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.cd_edit_model))
        }
    }
}

// Выбор провайдера: readOnly-поле, раскрывающее список известных провайдеров.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(selected: AiProvider, onSelect: (AiProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.settings_provider_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AiProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.label) },
                    onClick = { onSelect(provider); expanded = false },
                )
            }
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
