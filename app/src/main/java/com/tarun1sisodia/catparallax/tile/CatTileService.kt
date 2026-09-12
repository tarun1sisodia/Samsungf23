package com.tarun1sisodia.catparallax.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tarun1sisodia.catparallax.R
import com.tarun1sisodia.catparallax.settings.PrefsRepository
import com.tarun1sisodia.catparallax.widget.CatToggleWidgetProvider

/**
 * Quick Settings tile (PRD M8 stretch — included): a second, faster kill
 * switch in the notification shade. Active = cat running, inactive = frozen.
 */
class CatTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = PrefsRepository(this)
        prefs.enabled = !prefs.enabled
        updateTile()
        PrefsRepository.notifyStateChanged(this)
        CatToggleWidgetProvider.pushUpdate(this)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val prefs = PrefsRepository(this)
        tile.state = if (prefs.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }
}
