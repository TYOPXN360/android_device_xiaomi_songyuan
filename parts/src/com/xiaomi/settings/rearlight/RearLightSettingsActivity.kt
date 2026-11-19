/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.rearlight

import android.os.Bundle
import com.xiaomi.settings.PartsActivity
import com.android.settingslib.collapsingtoolbar.R

class RearLightSettingsActivity : PartsActivity() {

    companion object {
        private const val TAG = "RearLightActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content_frame, RearLightSettingsFragment(), TAG)
            .commit()
    }
}
