package app.obsidianmd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.obsidianmd.ui.MarkdownScreen
import app.obsidianmd.ui.VaultListScreen
import app.obsidianmd.ui.VaultViewModel

@Composable
fun App(vm: VaultViewModel) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    MaterialTheme {
        Surface {
            if (state.selected == null) {
                VaultListScreen(state, onOpen = vm::open)
            } else {
                MarkdownScreen(state.content, onBack = vm::back)
            }
        }
    }
}
