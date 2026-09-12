package com.tarun1sisodia.catparallax.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Canvas
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.tarun1sisodia.catparallax.animation.AnimationController
import com.tarun1sisodia.catparallax.receiver.CatStateReceiver
import com.tarun1sisodia.catparallax.receiver.UnlockReceiver
import com.tarun1sisodia.catparallax.settings.PrefsRepository
import kotlin.math.abs

/**
 * The wallpaper engine: owns the draw loop, sensor wiring, animation
 * controller and receivers (PRD §5).
 *
 * Battery contract (PRD §8):
 *  - no fixed redraw loop — frames are drawn only on (a) sensor delta above a
 *    threshold, (b) an active animation, (c) state changes;
 *  - onVisibilityChanged(false) fully unregisters the sensor listener;
 *  - no foreground service, no persistent notification — Force Stop /
 *    swipe-away kills everything (the "hard stop").
 */
class CatEngine(private val service: WallpaperService) : WallpaperService.Engine() {

    private val appContext: Context = service.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = PrefsRepository(appContext)

    private var renderer: SceneRenderer? = null
    private var surfaceW = 0
    private var surfaceH = 0
    private var visible = false
    private var framePending = false

    // prefs snapshot, re-read on visibility and state broadcasts
    private var enabled = true
    private var sensitivity = 1f
    private var batterySaver = false

    private var parallax = ParallaxInput.ZERO
    private var lastPitch = 0f
    private var lastRoll = 0f
    private var haveLast = false

    private val sensorFusion = SensorFusion(appContext) { pitch, roll ->
        onParallaxChanged(pitch, roll)
    }

    private val animationController = AnimationController(mainHandler) { requestFrame() }

    private val unlockAnimator = com.tarun1sisodia.catparallax.animation.UnlockAnimator(
        controller = animationController,
        isActive = { visible && enabled && surfaceW > 0 }
    )

    private val unlockReceiver = UnlockReceiver { unlockAnimator.onUnlock() }
    private val stateReceiver = CatStateReceiver { onStateChanged() }

    init {
        // Registered for the engine's lifetime; unregistered in onDestroy.
        // Protected system broadcast + own-package custom broadcast.
        registerReceiverCompat(unlockReceiver, IntentFilter(android.content.Intent.ACTION_USER_PRESENT))
        registerReceiverCompat(stateReceiver, IntentFilter(PrefsRepository.ACTION_STATE_CHANGED))
    }

    // ----------------------------------------------------------- lifecycle --

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.onSurfaceChanged(holder, format, width, height)
        if (width != surfaceW || height != surfaceH) {
            surfaceW = width
            surfaceH = height
            renderer = SceneLoader.load(appContext, width, height).let { scene ->
                SceneRenderer(scene).also { it.setDisabledLayers(prefs.disabledLayerIds()) }
            }
        }
        requestFrame()
    }

    override fun onVisibilityChanged(visible: Boolean) {
        super.onVisibilityChanged(visible)
        this.visible = visible
        if (visible) {
            applyPrefs()
            if (enabled) {
                startEngine()
                unlockAnimator.deliverPending()
            } else {
                stopEngine()
            }
            requestFrame()
        } else {
            stopEngine()
        }
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
        super.onSurfaceDestroyed(holder)
        visible = false
        stopEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEngine()
        unregisterQuietly(unlockReceiver)
        unregisterQuietly(stateReceiver)
        mainHandler.removeCallbacksAndMessages(null)
    }

    // ---------------------------------------------------------- state flow --

    /** Widget / tile / settings changed something: re-read prefs and adapt. */
    private fun onStateChanged() {
        applyPrefs()
        if (visible) {
            if (enabled) {
                startEngine()
                unlockAnimator.deliverPending()
            } else {
                stopEngine()   // soft stop: sensors off, animations cancelled,
                               // last-rendered frame stays as a static image
            }
            requestFrame()
        }
    }

    private fun applyPrefs() {
        enabled = prefs.enabled
        sensitivity = prefs.sensitivity
        batterySaver = prefs.batterySaver
        animationController.batterySaver = batterySaver
        renderer?.setDisabledLayers(prefs.disabledLayerIds())
    }

    private fun startEngine() {
        animationController.resume()
        sensorFusion.start(
            if (batterySaver) SensorManager.SENSOR_DELAY_NORMAL
            else SensorManager.SENSOR_DELAY_GAME
        )
        haveLast = false
    }

    private fun stopEngine() {
        sensorFusion.stop()
        animationController.pauseAll()
    }

    // ----------------------------------------------------------- rendering --

    private fun onParallaxChanged(pitch: Float, roll: Float) {
        parallax = ParallaxInput(pitch, roll)
        if (!enabled || !visible) return
        val threshold = if (batterySaver) 0.006f else 0.0025f
        val moved = !haveLast ||
            abs(pitch - lastPitch) > threshold ||
            abs(roll - lastRoll) > threshold
        if (moved) {
            lastPitch = pitch
            lastRoll = roll
            haveLast = true
            requestFrame()
        }
    }

    /** Coalesced frame request: at most one draw posted per main-loop pass. */
    private fun requestFrame() {
        if (!visible || surfaceW == 0) return
        if (framePending) return
        framePending = true
        mainHandler.post {
            framePending = false
            drawFrame()
        }
    }

    private fun drawFrame() {
        val r = renderer ?: return
        val holder = surfaceHolder
        if (!holder.surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = lockCanvas(holder)
            if (canvas != null) {
                r.render(
                    canvas, surfaceW, surfaceH,
                    parallax,
                    animationController.currentFrame(),
                    sensitivity
                )
            }
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun lockCanvas(holder: SurfaceHolder): Canvas? = try {
        holder.lockHardwareCanvas()
    } catch (t: Throwable) {
        try {
            holder.lockCanvas()
        } catch (t2: Throwable) {
            null
        }
    }

    // ------------------------------------------------------------- helpers --

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterQuietly(receiver: BroadcastReceiver) {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (ignored: Exception) {
        }
    }
}
