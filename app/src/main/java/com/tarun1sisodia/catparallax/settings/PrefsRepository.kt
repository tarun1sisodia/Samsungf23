package com.tarun1sisodia.catparallax.settings

import android.content.Context
import android.content.Intent

/**
 * Single source of truth for: ON/OFF state, sensitivity, battery saver,
 * per-layer visibility.
 *
 * The PRD sketch said DataStore; SharedPreferences is used deliberately:
 * the wallpaper engine needs synchronous reads inside onVisibilityChanged,
 * and keeping the app dependency-free keeps the APK tiny and CI simple.
 * The repository façade is identical, so swapping the backing store later
 * would not touch callers.
 */
class PrefsRepository(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("cat_parallax_prefs", Context.MODE_PRIVATE)

    /** Master ON/OFF — the widget/tile kill switch. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(value) {
            sp.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    /** Tilt-to-offset multiplier (0.2 – 3.0). */
    var sensitivity: Float
        get() = sp.getFloat(KEY_SENSITIVITY, 1f)
        set(value) {
            sp.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0.2f, 3f)).apply()
        }

    /** No idle animations + slower sensor polling. */
    var batterySaver: Boolean
        get() = sp.getBoolean(KEY_BATTERY_SAVER, false)
        set(value) {
            sp.edit().putBoolean(KEY_BATTERY_SAVER, value).apply()
        }

    fun isLayerEnabled(id: String): Boolean = sp.getBoolean("layer_$id", true)

    fun setLayerEnabled(id: String, enabled: Boolean) {
        sp.edit().putBoolean("layer_$id", enabled).apply()
    }

    fun disabledLayerIds(): Set<String> =
        TOGGLEABLE_LAYERS.filterNot { isLayerEnabled(it) }.toSet()

    companion object {
        /** Broadcast: prefs changed (sent to our own package). */
        const val ACTION_STATE_CHANGED = "com.tarun1sisodia.catparallax.ACTION_STATE_CHANGED"

        /** Layer ids exposed in Settings (matches assets/cat/default_cat.json). */
        val TOGGLEABLE_LAYERS = listOf("stars", "hills", "whiskers", "tail", "fg")

        val LAYER_LABELS = mapOf(
            "stars" to "Stars",
            "hills" to "Distant hills",
            "whiskers" to "Whiskers",
            "tail" to "Tail",
            "fg" to "Foreground bokeh"
        )

        fun notifyStateChanged(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_STATE_CHANGED).setPackage(context.packageName)
            )
        }
    }
}
