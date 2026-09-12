package com.tarun1sisodia.catparallax.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tarun1sisodia.catparallax.R
import com.tarun1sisodia.catparallax.settings.PrefsRepository

/**
 * 2x1 home-screen kill switch (PRD 4.2): "Cat: ON / Cat: OFF".
 *
 * Tap -> flips PrefsRepository.enabled -> pushes the new label/icon to every
 * widget instance -> broadcasts ACTION_STATE_CHANGED so a live engine freezes
 * (soft stop: sensors off, animations cancelled, last frame stays).
 *
 * The widget always renders from the current prefs in onUpdate, so its state
 * stays truthful across reboots and changes made from the tile or Settings.
 */
class CatToggleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        pushUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val prefs = PrefsRepository(context)
            prefs.enabled = !prefs.enabled
            pushUpdate(context)
            PrefsRepository.notifyStateChanged(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.tarun1sisodia.catparallax.ACTION_TOGGLE"

        /** Refresh every widget instance from current prefs. */
        fun pushUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, CatToggleWidgetProvider::class.java)
            )
            pushUpdate(context, mgr, ids)
        }

        private fun pushUpdate(
            context: Context,
            mgr: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return
            val enabled = PrefsRepository(context).enabled
            val rv = RemoteViews(context.packageName, R.layout.widget_cat_toggle).apply {
                setTextViewText(
                    R.id.tv_state,
                    context.getString(if (enabled) R.string.cat_on else R.string.cat_off)
                )
                setImageViewResource(
                    R.id.iv_icon,
                    if (enabled) R.drawable.ic_widget_cat_on else R.drawable.ic_widget_cat_off
                )
                setInt(
                    R.id.widget_root, "setBackgroundResource",
                    if (enabled) R.drawable.widget_bg_on else R.drawable.widget_bg_off
                )
                setOnClickPendingIntent(R.id.widget_root, toggleIntent(context))
            }
            mgr.updateAppWidget(appWidgetIds, rv)
        }

        private fun toggleIntent(context: Context): PendingIntent {
            val intent = Intent(context, CatToggleWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
