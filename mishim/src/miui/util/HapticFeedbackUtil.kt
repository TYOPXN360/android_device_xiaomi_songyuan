package miui.util

import android.content.Context

/**
 * Minimal shim for MIUI's HapticFeedbackUtil.
 *
 * MiSound constructs it in onCreate and drives the equaliser slider with it.
 * There is no MIUI linear-motor haptics backend under AOSP, so every query
 * answers "not supported" and the feedback calls are no-ops: the UI still works,
 * it just does not tick while dragging.
 */
class HapticFeedbackUtil {
    constructor()

    constructor(context: Context?, maybe: Boolean)

    fun isSupportLinearMotorVibrate(): Boolean = false

    fun isSupportLinearMotorVibrate(type: Int): Boolean = false

    fun performExtHapticFeedback(type: Int): Boolean = false

    fun performExtHapticFeedback(type: Int, strength: Int): Boolean = false
}