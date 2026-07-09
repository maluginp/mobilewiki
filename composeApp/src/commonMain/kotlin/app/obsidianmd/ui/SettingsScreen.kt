package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    currentUrl: String,
    onSave: (String) -> Unit,
    openRouterKey: String,
    onSaveKey: (String) -> Unit,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var key by remember(openRouterKey) { mutableStateOf(openRouterKey) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SettingField(
            label = "URL репозитория",
            example = "https://github.com/username/my-vault.git",
            description = "HTTPS-ссылка на Git-репозиторий с заметками. Приложение склонирует его " +
                "и будет синхронизировать изменения.",
            value = url,
            onValueChange = { url = it; saved = false },
        )
        SettingField(
            label = "Ключ OpenRouter",
            example = "sk-or-v1-abc123…",
            description = "API-ключ с openrouter.ai/keys для AI-чата. Хранится в зашифрованном виде " +
                "только на этом устройстве.",
            value = key,
            onValueChange = { key = it; saved = false },
            secret = true,
        )
        Button(
            onClick = { onSave(url); onSaveKey(key); saved = true },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Сохранить") }
        if (saved) {
            Text(
                "Сохранено ✓",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
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
            { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Скрыть" else "Показать") } }
        } else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
