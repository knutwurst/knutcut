package de.knutwurst.knutcut.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.knutwurst.knutcut.ui.theme.KnutcutTheme

class MainActivity : ComponentActivity() {

    private val vm: KnutcutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KnutcutTheme(vm.themeMode) { MainScreen(vm) } }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Each shared/opened file is loaded in turn; loadDesign appends after the first.
        readShared(intent).forEach { vm.loadDesign(it) }
    }

    @Suppress("DEPRECATION")
    private fun readShared(intent: Intent?): List<String> {
        intent ?: return emptyList()
        val out = ArrayList<String>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { readUri(it)?.let(out::add) }
                if (out.isEmpty()) intent.getStringExtra(Intent.EXTRA_TEXT)?.let(out::add)
            }
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { readUri(it)?.let(out::add) }
            Intent.ACTION_VIEW -> intent.data?.let { readUri(it)?.let(out::add) }
        }
        return out
    }

    private fun readUri(uri: Uri): String? =
        runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
}
