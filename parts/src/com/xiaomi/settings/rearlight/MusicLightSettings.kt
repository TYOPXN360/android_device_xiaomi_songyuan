/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.rearlight

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * The rear-light page writes the music-light state to Settings.Global and
 * asks the small SongyuanMusicLight service app to apply it. The service stays
 * in its own app so it keeps its system_app SELinux domain, which already has
 * access to the vendor LED sysfs nodes.
 */
object MusicLightSettings {
    const val KEY_ENABLED = "music_light_enabled"
    const val KEY_MODE = "music_light_apps_mode"
    const val KEY_APPS = "music_light_apps"
    const val MODE_ALL = "all"
    const val MODE_SELECTED = "selected"

    private const val TARGET_PACKAGE = "org.lineageos.musiclight"
    private const val ACTION_APPLY = "org.lineageos.musiclight.action.APPLY"
    private const val ACTION_RELOAD = "org.lineageos.musiclight.action.RELOAD"

    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, KEY_ENABLED, 0) != 0

    fun isSelectedOnly(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, KEY_MODE, 0) != 0

    fun getApps(context: Context): Set<String> {
        val value = Settings.Global.getString(context.contentResolver, KEY_APPS) ?: return emptySet()
        return value.split('\n').filter { it.isNotEmpty() }.toSet()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.Global.putInt(context.contentResolver, KEY_ENABLED, if (enabled) 1 else 0)
        send(context, ACTION_APPLY)
    }

    fun setFilter(context: Context, selectedOnly: Boolean, apps: Set<String>) {
        Settings.Global.putInt(context.contentResolver, KEY_MODE, if (selectedOnly) 1 else 0)
        Settings.Global.putString(context.contentResolver, KEY_APPS, apps.joinToString("\n"))
        // Restart the running service so it picks up the new filter.
        send(context, ACTION_RELOAD)
    }

    private fun send(context: Context, action: String) {
        context.sendBroadcast(Intent(action).setPackage(TARGET_PACKAGE))
    }
}
