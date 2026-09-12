package com.tarun1sisodia.catparallax.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Tilt source for the parallax effect.
 *
 * Uses TYPE_GAME_ROTATION_VECTOR (gyro + accelerometer, no magnetometer) per
 * the PRD — no drift from compass interference, no runtime permission.
 *
 * Pipeline: rotation vector -> rotation matrix -> orientation (pitch/roll)
 * -> low-pass filter -> baseline-relative normalization in [-1, 1].
 *
 * The baseline (neutral grip pose) is captured over the first few events and
 * then re-centers very slowly, so the cat's "home" position follows how the
 * phone is actually being held rather than world-down.
 */
class SensorFusion(
    context: Context,
    private val onParallax: (Float, Float) -> Unit
) {

    private val sensorManager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var filteredPitch = 0f
    private var filteredRoll = 0f
    private var basePitch = 0f
    private var baseRoll = 0f
    private var baseCount = 0

    var running = false
        private set

    val hasSensor: Boolean
        get() = sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val pitch = orientation[1]
            val roll = orientation[2]

            // low-pass filter (jitter removal)
            filteredPitch += ALPHA * (pitch - filteredPitch)
            filteredRoll += ALPHA * (roll - filteredRoll)

            if (baseCount < BASE_SAMPLES) {
                basePitch += filteredPitch
                baseRoll += filteredRoll
                baseCount++
                if (baseCount == BASE_SAMPLES) {
                    basePitch /= BASE_SAMPLES
                    baseRoll /= BASE_SAMPLES
                }
            } else {
                // slow re-centering toward the current pose
                basePitch += (filteredPitch - basePitch) * RECENTER
                baseRoll += (filteredRoll - baseRoll) * RECENTER
            }

            val nPitch = ((filteredPitch - basePitch) / MAX_DELTA).coerceIn(-1f, 1f)
            val nRoll = ((filteredRoll - baseRoll) / MAX_DELTA).coerceIn(-1f, 1f)
            onParallax(nPitch, nRoll)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(delayUs: Int) {
        val sm = sensorManager ?: return
        val s = sensor ?: return
        if (running) return
        resetBaseline()
        running = sm.registerListener(listener, s, delayUs)
    }

    fun stop() {
        sensorManager?.unregisterListener(listener)
        running = false
    }

    private fun resetBaseline() {
        baseCount = 0
        basePitch = 0f
        baseRoll = 0f
        filteredPitch = 0f
        filteredRoll = 0f
    }

    companion object {
        private const val ALPHA = 0.12f        // low-pass strength per event
        private const val BASE_SAMPLES = 12    // events averaged for the neutral pose
        private const val MAX_DELTA = 0.30f    // radians from neutral == full parallax (~17°)
        private const val RECENTER = 0.0015f   // baseline drift toward current pose
    }
}
