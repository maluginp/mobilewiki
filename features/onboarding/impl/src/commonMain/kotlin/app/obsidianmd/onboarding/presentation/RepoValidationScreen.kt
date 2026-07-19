package app.obsidianmd.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.obsidianmd.onboarding.AccessChecklist
import app.obsidianmd.onboarding.CheckStatus
import app.obsidianmd.onboarding.ValidationState
import app.obsidianmd.onboarding.accessChecklist
import app.obsidianmd.resources.Res
import app.obsidianmd.resources.action_back
import app.obsidianmd.resources.action_continue
import app.obsidianmd.resources.action_retry
import app.obsidianmd.resources.repo_check_checking
import app.obsidianmd.resources.repo_check_denied_body
import app.obsidianmd.resources.repo_check_denied_hint1
import app.obsidianmd.resources.repo_check_denied_hint2
import app.obsidianmd.resources.repo_check_denied_hint3
import app.obsidianmd.resources.repo_check_denied_title
import app.obsidianmd.resources.repo_check_item_read
import app.obsidianmd.resources.repo_check_item_write
import app.obsidianmd.resources.repo_check_ok_title
import app.obsidianmd.resources.repo_check_readonly_body
import app.obsidianmd.resources.repo_check_readonly_title
import app.obsidianmd.resources.repo_check_status_fail
import app.obsidianmd.resources.repo_check_status_ok
import app.obsidianmd.resources.repo_check_status_pending
import app.obsidianmd.resources.repo_check_unknown_body
import app.obsidianmd.resources.repo_check_unknown_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RepoValidationScreen(
    state: ValidationState,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val checklist = accessChecklist(state)
    if (checklist == null) { // Checking — крутится индикатор
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(stringResource(Res.string.repo_check_checking), Modifier.padding(top = 16.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Spacer(Modifier.height(48.dp))

        // Заголовок по итогу проверки чтения — оно определяет, можно ли работать с репозиторием.
        val (title, titleColor) = when (checklist.read) {
            CheckStatus.Passed -> stringResource(Res.string.repo_check_ok_title) to MaterialTheme.colorScheme.primary
            CheckStatus.Failed -> stringResource(Res.string.repo_check_denied_title) to MaterialTheme.colorScheme.error
            CheckStatus.Pending -> stringResource(Res.string.repo_check_unknown_title) to MaterialTheme.colorScheme.onSurface
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = titleColor)

        // Отдельные пункты проверки — что прошло, что нет.
        CheckRow(checklist.read, stringResource(Res.string.repo_check_item_read), Modifier.padding(top = 20.dp))
        CheckRow(checklist.write, stringResource(Res.string.repo_check_item_write), Modifier.padding(top = 12.dp))

        // Пояснения под чек-листом в зависимости от того, что не прошло.
        when (checklist.read) {
            CheckStatus.Failed -> {
                Text(stringResource(Res.string.repo_check_denied_body), Modifier.padding(top = 20.dp))
                Text(stringResource(Res.string.repo_check_denied_hint1), Modifier.padding(top = 8.dp))
                Text(stringResource(Res.string.repo_check_denied_hint2), Modifier.padding(top = 4.dp))
                Text(stringResource(Res.string.repo_check_denied_hint3), Modifier.padding(top = 4.dp))
            }
            CheckStatus.Pending -> Text(stringResource(Res.string.repo_check_unknown_body), Modifier.padding(top = 20.dp))
            CheckStatus.Passed -> Unit
        }

        if (checklist.readOnlyWarning) ReadOnlyWarning(Modifier.padding(top = 20.dp))

        Spacer(Modifier.weight(1f))

        // «Продолжить» блокируется, когда чтение недоступно.
        Button(
            onClick = onContinue,
            enabled = checklist.canContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(Res.string.action_continue)) }
        if (checklist.showRetry) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
            ) { Text(stringResource(Res.string.action_retry)) }
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(Res.string.action_back))
        }
    }
}

@Composable
private fun CheckRow(status: CheckStatus, label: String, modifier: Modifier = Modifier) {
    val icon: ImageVector
    val tint: androidx.compose.ui.graphics.Color
    val statusText: String
    when (status) {
        CheckStatus.Passed -> {
            icon = Icons.Filled.CheckCircle; tint = MaterialTheme.colorScheme.primary
            statusText = stringResource(Res.string.repo_check_status_ok)
        }
        CheckStatus.Failed -> {
            icon = Icons.Filled.Cancel; tint = MaterialTheme.colorScheme.error
            statusText = stringResource(Res.string.repo_check_status_fail)
        }
        CheckStatus.Pending -> {
            icon = Icons.Filled.RemoveCircleOutline; tint = MaterialTheme.colorScheme.onSurfaceVariant
            statusText = stringResource(Res.string.repo_check_status_pending)
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = statusText, tint = tint)
        Text(label, Modifier.padding(start = 12.dp))
        Spacer(Modifier.weight(1f))
        Text(statusText, color = tint, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReadOnlyWarning(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    stringResource(Res.string.repo_check_readonly_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    stringResource(Res.string.repo_check_readonly_body),
                    Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
