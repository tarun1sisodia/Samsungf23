package com.tarun1sisodia.catparallax.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Engine-side receiver for PrefsRepository.ACTION_STATE_CHANGED, sent by the
 * widget / QS tile / Settings whenever the ON/OFF state, sensitivity,
 * battery-saver flag or layer toggles change.
 *
 * (The PRD's ToggleReceiver role is covered by CatToggleWidgetProvider, which
 * is itself the broadcast receiver handling widget taps; this class is the
 * engine's listening end.)
 */
class CatStateReceiver(private val onStateChanged: () -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        onStateChanged()
    }
}
