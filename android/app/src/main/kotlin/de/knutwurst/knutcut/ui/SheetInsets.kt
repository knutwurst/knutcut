package de.knutwurst.knutcut.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Extra bottom padding a bottom sheet's content needs to stay clear of the navigation bar.
 *
 * The activity isn't edge-to-edge, so window insets don't reach Compose on every Android version.
 * Material3 pads a sheet by the navigation bar inset Compose sees; where that is 0 (Android 12 with
 * the three-button bar) the sheet runs under the bar and its bottom buttons sit behind the system
 * buttons. This reads the bar height from the activity window and returns only the part Compose
 * hasn't padded already, so devices where the sheet was fine get no extra space.
 */
@Composable
fun sheetNavBarPadding(): Dp {
    val density = LocalDensity.current
    val composeBottom = WindowInsets.navigationBars.getBottom(density)
    val decor = LocalContext.current.findActivity()?.window?.decorView
    val systemBottom = decor?.let { ViewCompat.getRootWindowInsets(it) }
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    return with(density) { navBarShortfall(systemBottom, composeBottom).toDp() }
}

/** The navigation bar height still uncovered: what the system reports minus what Compose already pads. */
internal fun navBarShortfall(systemPx: Int, composePx: Int): Int = (systemPx - composePx).coerceAtLeast(0)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
