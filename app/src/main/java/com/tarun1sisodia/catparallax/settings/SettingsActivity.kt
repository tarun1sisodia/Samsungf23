package com.tarun1sisodia.catparallax.settings

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.tarun1sisodia.catparallax.CatWallpaperService
import com.tarun1sisodia.catparallax.R
import com.tarun1sisodia.catparallax.widget.CatToggleWidgetProvider

/**
 * Settings screen (PRD 4.3): master ON/OFF, sensitivity slider, battery
 * saver, per-layer toggles, live preview, and the soft/hard-stop note the
 * PRD asks to surface once.
 *
 * Deliberately a plain framework Activity (no appcompat) to keep the app
 * dependency-free.
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: PrefsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsRepository(this)
        setContentView(R.layout.activity_settings)

        val preview = findViewById<CatPreviewView>(R.id.preview)
        val swMaster = findViewById<Switch>(R.id.sw_master)
        val swBattery = findViewById<Switch>(R.id.sw_battery)
        val sbSens = findViewById<SeekBar>(R.id.sb_sensitivity)
        val tvSens = findViewById<TextView>(R.id.tv_sens_value)
        val btnApply = findViewById<Button>(R.id.btn_apply)
        val llLayers = findViewById<LinearLayout>(R.id.ll_layers)

        // ---- master toggle (kill switch #3, also reachable from widget/tile)
        swMaster.isChecked = prefs.enabled
        swMaster.setOnCheckedChangeListener { _, checked ->
            prefs.enabled = checked
            PrefsRepository.notifyStateChanged(this)
            CatToggleWidgetProvider.pushUpdate(this)
        }

        // ---- battery saver
        swBattery.isChecked = prefs.batterySaver
        swBattery.setOnCheckedChangeListener { _, checked ->
            prefs.batterySaver = checked
            PrefsRepository.notifyStateChanged(this)
        }

        // ---- sensitivity (progress 0..100 -> 0.2x .. 3.0x)
        sbSens.progress = ((prefs.sensitivity - 0.2f) / 2.8f * 100f).toInt()
        tvSens.text = sensLabel(prefs.sensitivity)
        sbSens.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.2f + progress / 100f * 2.8f
                prefs.sensitivity = value
                tvSens.text = sensLabel(value)
                preview.sensitivity = value
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                PrefsRepository.notifyStateChanged(this@SettingsActivity)
            }
        })

        // ---- layer visibility toggles
        for (id in PrefsRepository.TOGGLEABLE_LAYERS) {
            val sw = Switch(this)
            sw.text = PrefsRepository.LAYER_LABELS[id] ?: id
            sw.textSize = 15f
            sw.isChecked = prefs.isLayerEnabled(id)
            sw.setPadding(0, dp(10), 0, dp(10))
            sw.setOnCheckedChangeListener { _, checked ->
                prefs.setLayerEnabled(id, checked)
                preview.onLayersChanged()
                PrefsRepository.notifyStateChanged(this)
            }
            llLayers.addView(sw)
        }

        // ---- apply as wallpaper
        btnApply.setOnClickListener { applyWallpaper() }
    }

    override fun onResume() {
        super.onResume()
        // widget state sync (PRD 4.2): reflect the true state even if it was
        // changed elsewhere while this screen was hidden
        CatToggleWidgetProvider.pushUpdate(this)
    }

    private fun applyWallpaper() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, CatWallpaperService::class.java)
            )
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                Toast.makeText(this, R.string.apply_fallback_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sensLabel(v: Float): String = String.format("%.1f×", v)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
