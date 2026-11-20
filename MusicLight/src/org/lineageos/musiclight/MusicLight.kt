/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.musiclight

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Persistent state for the music-reactive rear light.
 *
 * The settings UI lives in XiaomiParts, next to the rear-light controls. Both
 * apps are system apps, so Settings.Global is the shared state: the UI writes
 * it and this service reads it. The service stays in its own app to keep the
 * system_app SELinux domain that is allowed to write the LED sysfs.
 */
object MusicLight {
    const val KEY_ENABLED = "music_light_enabled"
    const val KEY_MODE = "music_light_apps_mode"
    const val KEY_APPS = "music_light_apps"
    const val MODE_ALL = "all"
    const val MODE_SELECTED = "selected"

    private const val KEY_LIT = "music_light_lit"

    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, KEY_ENABLED, 0) != 0

    /* Only light up for the apps picked in settings instead of any media. */
    fun isSelectedOnly(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, KEY_MODE, 0) != 0

    fun getApps(context: Context): Set<String> {
        val value = Settings.Global.getString(context.contentResolver, KEY_APPS) ?: return emptySet()
        return value.split('\n').filter { it.isNotEmpty() }.toSet()
    }

    fun setFilter(context: Context, selectedOnly: Boolean, apps: Set<String>) {
        Settings.Global.putInt(context.contentResolver, KEY_MODE, if (selectedOnly) 1 else 0)
        Settings.Global.putString(context.contentResolver, KEY_APPS, apps.joinToString("\n"))
        // Restart so the running service picks up the new filter.
        if (isEnabled(context)) {
            context.stopService(Intent(context, MusicLightService::class.java))
            apply(context)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.Global.putInt(context.contentResolver, KEY_ENABLED, if (enabled) 1 else 0)
        apply(context)
    }

    fun apply(context: Context) {
        val service = Intent(context, MusicLightService::class.java)
        if (isEnabled(context)) {
            context.startService(service)
        } else {
            context.stopService(service)
            releaseIfLeftLit(context)
        }
    }

    /* Remembered across process deaths: a killed service can't switch the LED off. */
    fun setLit(context: Context, lit: Boolean) {
        Settings.Global.putInt(context.contentResolver, KEY_LIT, if (lit) 1 else 0)
    }

    fun releaseIfLeftLit(context: Context) {
        if (Settings.Global.getInt(context.contentResolver, KEY_LIT, 0) != 0) {
            MusicLightService.release()
            setLit(context, false)
        }
    }
}
