package app.obsidianmd.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_hide
import app.obsidianmd.resources.action_save
import app.obsidianmd.resources.action_show
import app.obsidianmd.resources.cd_edit_model
import app.obsidianmd.resources.settings_ai_enable
import app.obsidianmd.resources.settings_ai_enable_desc
import app.obsidianmd.resources.settings_base_url_desc
import app.obsidianmd.resources.settings_base_url_example
import app.obsidianmd.resources.settings_base_url_label
import app.obsidianmd.resources.settings_key_desc
import app.obsidianmd.resources.settings_key_label
import app.obsidianmd.resources.settings_model_desc
import app.obsidianmd.resources.settings_model_label
import app.obsidianmd.resources.settings_model_none
import app.obsidianmd.resources.settings_provider_label
import app.obsidianmd.resources.settings_saved
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * AI-часть экрана настроек. Stateless: получает state и колбэки, локально держит только
 * черновики ключа/base-URL и флаг «сохранено». Собственная кнопка «Сохранить» коммитит
 * черновики через onSaveKey/onSetCustomBaseUrl.
 */
@Composable
internal fun AiSettingsSectionContent(
    state: AiSettingsState,
    onSetAiEnabled: (Boolean) -> Unit,
    onSetProvider: (AiProvider) -> Unit,
    onSaveKey: (String) -> Unit,
    onSetCustomBaseUrl: (String) -> Unit,
    onEditModel: () -> Unit,
) {
    var key by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var baseUrl by remember(state.customBaseUrl) { mutableStateOf(state.customBaseUrl) }
    var saved by remember { mutableStateOf(false) }

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
        Button(
            onClick = { onSaveKey(key); onSetCustomBaseUrl(baseUrl); saved = true },
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

/** Stateful-обёртка: тянет AiSettingsViewModel из Koin и прокидывает его в stateless-контент. */
@Composable
internal fun AiSettingsSection(onEditModel: () -> Unit) {
    val vm: AiSettingsViewModel = koinViewModel()
    val state by vm.state.collectAsState()
    AiSettingsSectionContent(
        state = state,
        onSetAiEnabled = vm::setAiEnabled,
        onSetProvider = vm::setProvider,
        onSaveKey = vm::saveKey,
        onSetCustomBaseUrl = vm::setCustomBaseUrl,
        onEditModel = onEditModel,
    )
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

// ponytail: SettingField скопирован из composeApp; вынести в core-ui, если понадобится третьему потребителю
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
