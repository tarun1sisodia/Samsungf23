package com.tarun1sisodia.catparallax.animation

import android.os.Handler
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sin

/** Per-frame animation parameters consumed by SceneRenderer (design units). */
class AnimFrame(
    val eyeVariant: String = "open",
    val tailRotation: Float = 0f,
    val earLRotation: Float = 0f,
    val earRRotation: Float = 0f,
    val bodyScaleX: Float = 1f,
    val bodyScaleY: Float = 1f,
    val bodyDy: Float = 0f
)

/**
 * State machine + idle-behavior timer (PRD §5: its own independent timer, so
 * idle behaviors layer on top of parallax regardless of tilt state).
 *
 * While an animation is active it self-schedules ~60 fps redraw requests via
 * [requestFrame]; while idle it schedules nothing except the next randomized
 * idle event (4–12 s), which is what keeps the battery footprint low.
 */
class AnimationController(
    private val handler: Handler,
    private val requestFrame: () -> Unit
) {

    var state: AnimationState = AnimationState.IDLE
        private set

    /** Battery saver: no idle behaviors (wake animation still plays). */
    var batterySaver = false

    private var animStart = 0L
    private var idleRunnable: Runnable? = null
    private var ticking = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (state == AnimationState.IDLE || state == AnimationState.PAUSED) {
                ticking = false
                return
            }
            val now = SystemClock.uptimeMillis()
            if (now - animStart >= durationOf(state)) {
                finishAnimation()
            } else {
                requestFrame()
                handler.postDelayed(this, FRAME_MS)
            }
        }
    }

    fun currentFrame(now: Long = SystemClock.uptimeMillis()): AnimFrame {
        if (state == AnimationState.IDLE || state == AnimationState.PAUSED) return IDLE_FRAME
        val dur = durationOf(state)
        val t = ((now - animStart).toFloat() / dur).coerceIn(0f, 1f)
        return when (state) {
            AnimationState.BLINK -> blinkFrame(t)
            AnimationState.TAIL_FLICK -> flickFrame(t)
            AnimationState.WAKING -> wakeFrame(t)
            // Phase 2 (PRD §9.5): angry bite uses its own frames once art exists.
            AnimationState.ANGRY_BITE -> wakeFrame(t)
            else -> IDLE_FRAME
        }
    }

    fun playWake() = start(AnimationState.WAKING)
    fun playBlink() = start(AnimationState.BLINK)
    fun playTailFlick() = start(AnimationState.TAIL_FLICK)

    /** Resume from PAUSED (kill switch back on). */
    fun resume() {
        state = AnimationState.IDLE
        scheduleIdle()
    }

    /** Soft stop: cancel timers and freezes the cat. */
    fun pauseAll() {
        cancelIdle()
        handler.removeCallbacks(tickRunnable)
        ticking = false
        state = AnimationState.PAUSED
    }

    // ------------------------------------------------------------ internals --

    private fun start(s: AnimationState) {
        if (state == AnimationState.PAUSED) return
        cancelIdle()
        state = s
        animStart = SystemClock.uptimeMillis()
        requestFrame()
        if (!ticking) {
            ticking = true
            handler.postDelayed(tickRunnable, FRAME_MS)
        }
    }

    private fun finishAnimation() {
        handler.removeCallbacks(tickRunnable)
        ticking = false
        state = AnimationState.IDLE
        requestFrame()
        scheduleIdle()
    }

    private fun scheduleIdle() {
        if (batterySaver) return
        cancelIdle()
        val r = Runnable {
            idleRunnable = null
            if (state == AnimationState.IDLE) {
                // mostly blinks, occasional tail flicks
                if (Math.random() < 0.62) playBlink() else playTailFlick()
            }
        }
        idleRunnable = r
        handler.postDelayed(r, IDLE_MIN_MS + (Math.random() * (IDLE_MAX_MS - IDLE_MIN_MS)).toLong())
    }

    private fun cancelIdle() {
        idleRunnable?.let { handler.removeCallbacks(it) }
        idleRunnable = null
    }

    // ----------------------------------------------------------- frame math --

    private fun blinkFrame(t: Float): AnimFrame {
        val eyes = if (t in 0.15f..0.85f) "closed" else "open"
        return AnimFrame(eyeVariant = eyes)
    }

    private fun flickFrame(t: Float): AnimFrame {
        val rot = (16.0 * sin(PI * t) * (1.0 - 0.35 * t)).toFloat()
        return AnimFrame(tailRotation = rot)
    }

    /**
     * Wake/greet: anticipation squash -> stretchy hop with ears perked and
     * eyes wide -> land with a settle, then a relaxed tail flick.
     */
    private fun wakeFrame(t: Float): AnimFrame {
        val eyes = if (t < 0.5f) "wide" else "open"

        var sx = 1f
        var sy = 1f
        var dy = 0f
        when {
            t < 0.15f -> {                       // anticipation squash
                val u = t / 0.15f
                sy = 1f - 0.08f * u * u
                sx = 1f + 0.07f * u * u
            }
            t < 0.75f -> {                       // hop
                val u = (t - 0.15f) / 0.60f
                val s = sin(PI * u)
                dy = -22f * s
                sy = 0.92f + 0.20f * s
                sx = 1.07f - 0.15f * s
            }
            else -> {                            // settle back to 1:1
                val v = smoothstep((t - 0.75f) / 0.25f)
                sy = 0.92f + 0.08f * v
                sx = 1.07f - 0.07f * v
            }
        }

        // ears perk outward during the first half, relax by 80%
        val env = when {
            t < 0.5f -> (t / 0.25f).coerceAtMost(1f)
            t < 0.8f -> 1f - (t - 0.5f) / 0.3f
            else -> 0f
        }
        val earL = -9f * env
        val earR = 9f * env

        // satisfied tail flick on the way down
        val tail = if (t in 0.55f..1f) {
            val w = (t - 0.55f) / 0.45f
            (12.0 * sin(PI * w)).toFloat()
        } else 0f

        return AnimFrame(
            eyeVariant = eyes,
            tailRotation = tail,
            earLRotation = earL,
            earRRotation = earR,
            bodyScaleX = sx,
            bodyScaleY = sy,
            bodyDy = dy
        )
    }

    private fun smoothstep(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    companion object {
        private const val FRAME_MS = 16L
        private const val IDLE_MIN_MS = 4000L
        private const val IDLE_MAX_MS = 12000L
        private val IDLE_FRAME = AnimFrame()

        private fun durationOf(s: AnimationState): Long = when (s) {
            AnimationState.BLINK -> 340L
            AnimationState.TAIL_FLICK -> 700L
            AnimationState.WAKING -> 1500L
            AnimationState.ANGRY_BITE -> 1500L
            else -> 1L
        }
    }
}
