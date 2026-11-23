/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.TwoStatePreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R

class DisplayEnhanceSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "DisplayEnhanceFragment"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        /**
         * Feature switches. The stock page exposes MEMC, AI HDR and super
         * resolution separately; the AI scene switch has no NVTIC entry point,
         * so it is not offered here.
         */
        private val FEATURE_KEYS = listOf(
            DisplayEnhanceController.KEY_MEMC,
            DisplayEnhanceController.KEY_S2H,
            DisplayEnhanceController.KEY_SR,
        )
    }

    private val switchBar by lazy {
        findPreference<MainSwitchPreference>(DisplayEnhanceController.SETTING_KEY)!!
    }

    private val features by lazy {
        FEATURE_KEYS.mapNotNull { findPreference<SwitchPreferenceCompat>(it) }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.d(TAG, "onCreatePreferences")
        setPreferencesFromResource(R.xml.settings_display_enhance, rootKey)

        val context = requireContext()

        // The master switch is a MainSwitchPreference and the per-feature rows
        // are SwitchPreferenceCompat; both are TwoStatePreference underneath.
        switchBar.isChecked = DisplayEnhanceController.isEnabled(context)
        switchBar.onPreferenceChangeListener = this

        for (pref in features) {
            pref.isChecked = DisplayEnhanceController.isFeatureEnabled(context, pref.key)
            pref.onPreferenceChangeListener = this
        }

        setFeaturesEnabled(switchBar.isChecked)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val checked = newValue as Boolean
        val context = requireContext()

        when {
            preference === switchBar -> {
                if (DEBUG) Log.d(TAG, "master: $checked")
                DisplayEnhanceController.setEnabled(context, checked)
                setFeaturesEnabled(checked)
            }
            preference is TwoStatePreference -> {
                if (DEBUG) Log.d(TAG, "${preference.key}: $checked")
                DisplayEnhanceController.setFeature(context, preference.key!!, checked)
            }
        }
        return true
    }

    private fun setFeaturesEnabled(enabled: Boolean) {
        features.forEach { it.isEnabled = enabled }
    }
}
