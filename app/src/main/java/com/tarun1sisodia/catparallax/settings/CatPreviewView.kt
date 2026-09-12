package com.tarun1sisodia.catparallax.settings

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.tarun1sisodia.catparallax.animation.AnimationController
import com.tarun1sisodia.catparallax.engine.ParallaxInput
import com.tarun1sisodia.catparallax.engine.SceneLoader
import com.tarun1sisodia.catparallax.engine.SceneRenderer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live preview inside Settings (PRD 4.3): renders through the exact same
 * SceneLoader/SceneRenderer pipeline as the wallpaper engine. A slow sine
 * sweep simulates tilt; tapping the preview plays the wake animation.
 */
class CatPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var renderer: SceneRenderer? = null
    private val prefs = PrefsRepository(context)

    var sensitivity: Float = prefs.sensitivity

    private var parallax = ParallaxInput.ZERO
    private val controller = AnimationController(Handler(Looper.getMainLooper())) {
        invalidate()
    }
    private var sweep: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        renderer = SceneLoader.load(context, w, h).let { scene ->
            SceneRenderer(scene).also { it.setDisabledLayers(prefs.disabledLayerIds()) }
        }
        controller.resume()

        if (sweep == null) {
            sweep = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 8000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { anim ->
                    val t = anim.animatedFraction
                    parallax = ParallaxInput(
                        pitch = (sin(t * 2.0 * Math.PI) * 0.55).toFloat(),
                        roll = (cos(t * 2.0 * Math.PI) * 0.55).toFloat()
                    )
                    invalidate()
                }
                start()
            }
        }
    }

    fun onLayersChanged() {
        renderer?.setDisabledLayers(prefs.disabledLayerIds())
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            controller.playWake()
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val r = renderer ?: return
        r.render(canvas, width, height, parallax, controller.currentFrame(), sensitivity)
    }

    override fun onDetachedFromWindow() {
        sweep?.cancel()
        controller.pauseAll()
        super.onDetachedFromWindow()
    }
}
