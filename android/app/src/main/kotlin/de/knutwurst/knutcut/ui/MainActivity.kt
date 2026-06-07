package de.knutwurst.knutcut.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import de.knutwurst.knutcut.data.Settings
import de.knutwurst.knutcut.ui.theme.KnutcutTheme

class MainActivity : ComponentActivity() {

    private val vm: KnutcutViewModel by viewModels()

    // Apply the chosen UI language before the activity inflates, so all resources use it.
    override fun attachBaseContext(newBase: Context) {
        val lang = Settings(newBase).appLanguage
        LocaleUtil.applyDefault(lang)
        super.attachBaseContext(LocaleUtil.wrap(newBase, lang))
    }

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

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let(uris::add)
                // A plain shared SVG string (no stream) is already in memory — load it directly.
                if (uris.isEmpty()) { intent.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.loadDesign(it) }; return }
            }
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::addAll)
            Intent.ACTION_VIEW -> intent.data?.let(uris::add)
        }
        // Reads happen off the UI thread inside importUris (with a size cap).
        vm.importUris(uris)
    }
}
