/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.musiclight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * XiaomiParts owns the settings UI, but the service must run in this app's
 * system_app domain. The rear-light page broadcasts a reload after changing
 * the state in Settings.Global.
 */
class MusicLightCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_APPLY -> MusicLight.apply(context)
            ACTION_RELOAD -> {
                // Stop first so the running service re-reads the app filter.
                context.stopService(Intent(context, MusicLightService::class.java))
                MusicLight.apply(context)
            }
        }
    }

    companion object {
        const val ACTION_APPLY = "org.lineageos.musiclight.action.APPLY"
        const val ACTION_RELOAD = "org.lineageos.musiclight.action.RELOAD"
    }
}
