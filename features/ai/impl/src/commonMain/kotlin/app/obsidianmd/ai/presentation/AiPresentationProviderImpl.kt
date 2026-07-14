package app.obsidianmd.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.ai_open_settings
import app.obsidianmd.resources.ai_unavailable
import app.obsidianmd.vault.VaultFile
import app.obsidianmd.vault.VaultRepository
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal class AiPresentationProviderImpl : AiPresentationProvider {

    @Composable
    override fun aiEnabled(): Boolean {
        val vm: AiSettingsViewModel = koinViewModel()
        return vm.state.collectAsState().value.aiEnabled
    }

    @Composable
    override fun Chat(
        onOpenFile: (String) -> Unit,
        onOpenSettings: () -> Unit,
    ) {
        val settingsVm: AiSettingsViewModel = koinViewModel()
        val settings by settingsVm.state.collectAsState()
        val aiVm = rememberAiViewModel(settings)
        if (aiVm != null) {
            val repo: VaultRepository = koinInject()
            // allFiles() — блокирующее I/O; грузим вне главного потока, один раз на репозиторий.
            val files by produceState(initialValue = emptyList<VaultFile>(), repo) {
                value = withContext(Dispatchers.IO) { repo.allFiles() }
            }
            val aiState by aiVm.state.collectAsState()
            AiChatScreen(
                messages = aiState.messages,
                status = aiState.status,
                pendingWrite = aiState.pendingWrite,
                onSend = aiVm::send,
                onApprove = aiVm::approveWrite,
                onReject = aiVm::rejectWrite,
                files = files,
                onOpenFile = onOpenFile,
            )
        } else {
            Box(Modifier.safeDrawingPadding()) { AiUnavailable(onOpenSettings) }
        }
    }

    @Composable
    override fun ModelPicker(onNavigateBack: () -> Unit) {
        val vm: AiSettingsViewModel = koinViewModel()
        LaunchedEffect(Unit) { vm.ensureModels() }
        val s by vm.state.collectAsState()
        ModelPickerScreen(
            models = s.models,
            loading = s.modelsLoading,
            selected = s.aiModel,
            onSelect = { vm.setAiModel(it); onNavigateBack() },
            onRefresh = vm::reloadModels,
            onNavigateBack = onNavigateBack,
            showFilters = s.provider.supportsModelFilters,
        )
    }

    @Composable
    override fun SettingsSection(onEditModel: () -> Unit) {
        AiSettingsSection(onEditModel)
    }
}

/**
 * AI-ViewModel, привязанный к провайдеру/модели/URL: смена любого пересоздаёт клиент.
 * null — если ключ не задан или AI выключен (тогда экран чата покажет заглушку).
 */
@Composable
private fun rememberAiViewModel(settings: AiSettingsState): AiViewModel? {
    val apiKeyStore: ApiKeyStore = koinInject()
    val provider = settings.provider
    val aiModel = settings.aiModel
    val aiKey = remember(settings.aiEnabled, aiModel, provider, settings.apiKey) {
        apiKeyStore.getKey(provider.id)?.takeIf { it.isNotBlank() && settings.aiEnabled }
    }
    val chatUrl = provider.resolvedChatUrl(settings.customBaseUrl)
    return aiKey?.let { key ->
        koinViewModel(key = "${provider.id}:$aiModel:$chatUrl") { parametersOf(aiModel, key, chatUrl) }
    }
}

@Composable
private fun AiUnavailable(onOpenSettings: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text(stringResource(Res.string.ai_unavailable), color = MaterialTheme.colorScheme.error)
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(Res.string.ai_open_settings))
        }
    }
}
