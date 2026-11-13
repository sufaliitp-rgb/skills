package com.sandhyagems.app.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sandhyagems.app.App

abstract class BaseActivity : Activity() {
    val app: App get() = application as App

    /** Wraps a callback so a late network answer is ignored once the screen is gone. */
    fun <T> alive(callback: (T) -> Unit): (T) -> Unit = { value ->
        if (!isFinishing && !isDestroyed) callback(value)
    }

    /**
     * Draws behind the status and navigation bars (required on Android 15) and
     * pads [root] so nothing is hidden under them or under the keyboard.
     */
    @Suppress("DEPRECATION")
    protected fun applyInsets(root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = Color.TRANSPARENT
        // Before Android 8 the navigation buttons are always white, so keep a dark bar behind them.
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 26) Color.TRANSPARENT else Color.BLACK
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
