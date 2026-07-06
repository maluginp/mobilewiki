package app.obsidianmd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import app.obsidianmd.ui.VaultViewModel
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = createRepository(applicationContext)
        val vm = VaultViewModel(repo, lifecycleScope, Dispatchers.IO)
        setContent { App(vm) }
    }
}
