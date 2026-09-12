package com.tarun1sisodia.catparallax

import android.service.wallpaper.WallpaperService
import com.tarun1sisodia.catparallax.engine.CatEngine

/**
 * Home-screen live wallpaper entry point (PRD M1).
 *
 * Everything the engine needs lives inside WallpaperService.Engine — there is
 * deliberately no foreground service and no persistent notification, so
 * Force Stop / swipe-away fully terminates the app (the "hard stop").
 */
class CatWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = CatEngine(this)
}
