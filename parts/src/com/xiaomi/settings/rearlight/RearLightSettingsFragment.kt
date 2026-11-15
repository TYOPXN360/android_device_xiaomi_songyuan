/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.rearlight

import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R

class RearLightSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "RearLightFragment"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.d(TAG, "onCreatePreferences")
        setPreferencesFromResource(R.xml.settings_rear_light, rootKey)

        // The master switch is a MainSwitchPreference, the two behaviours are
        // SwitchPreferenceCompat; both are TwoStatePreference underneath, so one
        // loop covers all three.
        for (key in RearLightController.SWITCH_KEYS) {
            val pref = findPreference<TwoStatePreference>(key) ?: continue
            pref.isChecked = RearLightController.isEnabled(requireContext(), key)
            pref.onPreferenceChangeListener = this
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val key = preference.key ?: return true
        if (key in RearLightController.SWITCH_KEYS) {
            val isChecked = newValue as Boolean
            if (DEBUG) Log.d(TAG, "onPreferenceChange: $key=$isChecked")
            RearLightController.setEnabled(requireContext(), key, isChecked)
            return true
        }
        return true
    }
}
