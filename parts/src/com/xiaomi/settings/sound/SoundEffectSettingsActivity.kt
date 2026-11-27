/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.sound

import android.os.Bundle
import com.android.settingslib.collapsingtoolbar.R
import com.xiaomi.settings.PartsActivity

class SoundEffectSettingsActivity : PartsActivity() {

    companion object {
        private const val TAG = "SoundEffectActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content_frame, SoundEffectSettingsFragment(), TAG)
            .commit()
    }
}
