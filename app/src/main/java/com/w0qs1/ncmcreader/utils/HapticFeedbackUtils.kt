package com.w0qs1.ncmcreader.utils

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

object HapticFeedbackUtils {

    /**
     * Triggered as soon as any card tag is detected on the main screen.
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun performScanHaptic(context: Context) {
        vibrateOneShot(context, milliseconds = 100L)
    }

    /**
     * Triggered when an NCMC card has been read successfully.
     * Double pulse in a positive tone.
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun performSuccessHaptic(context: Context) {
        vibratePattern(context, pattern = longArrayOf(0, 80, 60, 120))
    }

    /**
     * Triggered when wrong card or read error occurs.
     * Single long negative tone.
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun performErrorHaptic(context: Context) {
        vibrateOneShot(context, milliseconds = 450L)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrateOneShot(context: Context, milliseconds: Long) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                val effect = VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {
            // Ignore if hardware or vibration unavailable
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibratePattern(context: Context, pattern: LongArray) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator != null && vibrator.hasVibrator()) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {
            // Ignore if hardware or vibration unavailable
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
