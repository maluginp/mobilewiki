package app.obsidianmd.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    currentUrl: String,
    onSave: (String) -> Unit,
    openRouterKey: String,
    onSaveKey: (String) -> Unit,
    onBack: () -> Unit,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    var key by remember(openRouterKey) { mutableStateOf(openRouterKey) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }

        Text("URL репозитория")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Button(onClick = { onSave(url) }) { Text("Сохранить") }

        Text("Ключ OpenRouter", Modifier.padding(top = 24.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Button(onClick = { onSaveKey(key) }) { Text("Сохранить ключ") }
    }
}
