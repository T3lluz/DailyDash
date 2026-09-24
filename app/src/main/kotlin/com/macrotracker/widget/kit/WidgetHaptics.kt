package com.macrotracker.widget.kit

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * A tick under the finger for widget taps that change the widget in place (a tab, a
 * day, the server shown, refresh). Taps that open the app or a link need none: the
 * screen changing is the feedback.
 *
 * A widget tap runs in a broadcast while DailyDash is in the background, and Android
 * drops touch vibrations from background apps. Hardware feedback is the usage the
 * system lets through there (Android 13+), so the tick plays under it, and only while
 * the person has touch feedback on. Older versions try as touch and may stay silent.
 */
object WidgetHaptics {
    /** A tab, a chip, a day: something picked. */
    fun tick(context: Context) = play(context, VibrationEffect.EFFECT_TICK)

    /** A button pressed: refresh, retry. */
    fun click(context: Context) = play(context, VibrationEffect.EFFECT_CLICK)

    private fun play(context: Context, effectId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            if (!touchFeedbackOn(context)) return
            val vibrator = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
            val effect = VibrationEffect.createPredefined(effectId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build(),
                )
            }
        }
    }

    /**
     * The "Touch feedback" switch. Deprecated for apps that play touch usages (the system
     * applies it to those), but hardware feedback ignores it, so the widget asks itself.
     */
    @Suppress("DEPRECATION")
    private fun touchFeedbackOn(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
}
