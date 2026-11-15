/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.rearlight

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import android.util.Log

/**
 * The rear light is a single AW21024 driven LED shared by the battery, the
 * notification pulse and the camera/microphone indicators. The light HAL
 * decides which of them wins, and it cannot read Settings.System, so the
 * switches are carried over as properties.
 *
 * The notification switch is the exception: AOSP already owns that decision.
 * NotificationAttentionHelper reads Settings.System.NOTIFICATION_LIGHT_PULSE
 * and stops sending NOTIFICATIONS states to the HAL when it is off, so writing
 * the standard key is enough and no vendor property is involved.
 *
 * The charging switch has no AOSP equivalent - BatteryService drives
 * LIGHT_ID_BATTERY unconditionally - so it is enforced in the HAL, which is the
 * only place that can tell the light types apart.
 */
object RearLightController {

    private const val TAG = "RearLight"

    private const val PROP_MASTER = "persist.sys.rear_light.enable"
    private const val PROP_BATTERY = "persist.sys.rear_light.battery"

    /** Switches in the order they appear on the page. */
    val SWITCH_KEYS = listOf(
        "rear_light_enable",
        "rear_light_battery",
        "rear_light_notify",
    )

    fun isEnabled(context: Context, key: String): Boolean = when (key) {
        "rear_light_notify" -> Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.NOTIFICATION_LIGHT_PULSE,
            1,
            UserHandle.USER_CURRENT,
        ) != 0
        "rear_light_battery" -> getProp(PROP_BATTERY, "1") == "1"
        else -> getProp(PROP_MASTER, "1") == "1"
    }

    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        if (key == "rear_light_notify") {
            Settings.System.putIntForUser(
                context.contentResolver,
                Settings.System.NOTIFICATION_LIGHT_PULSE,
                if (enabled) 1 else 0,
                UserHandle.USER_CURRENT,
            )
            return
        }
        val prop = if (key == "rear_light_battery") PROP_BATTERY else PROP_MASTER
        setProp(prop, if (enabled) "1" else "0")
        Log.i(TAG, "applied $prop=$enabled")
    }

    private fun setProp(prop: String, value: String) {
        val cls = Class.forName("android.os.SystemProperties")
        cls.getMethod("set", String::class.java, String::class.java).invoke(null, prop, value)
    }

    private fun getProp(prop: String, default: String): String = try {
        val cls = Class.forName("android.os.SystemProperties")
        cls.getMethod("get", String::class.java, String::class.java)
            .invoke(null, prop, default) as String
    } catch (t: Throwable) {
        Log.e(TAG, "failed to read $prop", t)
        default
    }
}
