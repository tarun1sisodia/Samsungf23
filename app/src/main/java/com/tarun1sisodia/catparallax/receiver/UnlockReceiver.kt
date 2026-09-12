package com.tarun1sisodia.catparallax.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ACTION_USER_PRESENT → wake animation (PRD M4).
 *
 * Context-registered inside CatEngine's lifecycle (implicit broadcasts can't
 * be manifest-registered since API 26; USER_PRESENT is delivered fine to
 * context-registered receivers).
 */
class UnlockReceiver(private val onUnlock: () -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_USER_PRESENT == intent.action) {
            onUnlock()
        }
    }
}
