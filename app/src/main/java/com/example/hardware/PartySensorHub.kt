package com.example.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class TiltTelemetry(
    val rollX: Float = 0f,    // Left (-1) .. Right (+1)
    val pitchY: Float = 0f,   // Upright (0), Tilted Down (> +0.55), Tilted Up (< -0.55)
    val faceDown: Boolean = false
)

/**
 * Hardware accelerometer & shake detector powering offline physical party interactions:
 * - Shake-to-Bump auto-pairing
 * - Hot Potato shake-to-pass
 * - 2v2 Tilt Balance co-op steering
 * - Undercover tilt-to-hide peek protection
 * - Team Heads-Up tilt-down (Correct) / tilt-up (Pass)
 */
class PartySensorHub(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _tilt = MutableStateFlow(TiltTelemetry())
    val tilt: StateFlow<TiltTelemetry> = _tilt.asStateFlow()

    private val _shakeEvents = MutableSharedFlow<Float>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val shakeEvents: SharedFlow<Float> = _shakeEvents.asSharedFlow()

    private var lastShakeTimeMs = 0L
    private var isRegistered = false

    val hasAccelerometer: Boolean get() = accelerometer != null

    fun startListening() {
        if (isRegistered || accelerometer == null) return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isRegistered = true
    }

    fun stopListening() {
        if (!isRegistered) return
        sensorManager?.unregisterListener(this)
        isRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return
        val ax = values[0]
        val ay = values[1]
        val az = values[2]

        // Normalized roll (-1..+1) and pitch (-1..+1)
        val roll = (-ax / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val pitch = (az / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
        val isFaceDown = az < -6.5f

        _tilt.value = TiltTelemetry(
            rollX = roll,
            pitchY = pitch,
            faceDown = isFaceDown
        )

        // Shake impulse detection (g-force delta above 1.95g)
        val gForce = sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (gForce > 1.95f && now - lastShakeTimeMs > 550L) {
            lastShakeTimeMs = now
            _shakeEvents.tryEmit(gForce)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Tactile haptic patterns for offline party immersion.
 */
class PartyHaptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun bumpMatch() = vibratePattern(longArrayOf(0, 45, 55, 70), intArrayOf(0, 210, 0, 255))
    fun lightTap() = vibrateOneShot(22L, 120)
    fun paddleHit() = vibrateOneShot(30L, 180)
    fun bombHeartbeat(urgency: Float) {
        val amp = (110 + (urgency.coerceIn(0f, 1f) * 145)).toInt()
        vibrateOneShot(38L, amp)
    }

    fun bombExplode() = vibratePattern(longArrayOf(0, 120, 40, 220), intArrayOf(0, 255, 0, 255))
    fun victory() = vibratePattern(longArrayOf(0, 50, 40, 50, 40, 140), intArrayOf(0, 180, 0, 200, 0, 255))

    private fun vibrateOneShot(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        }
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        }
    }
}
