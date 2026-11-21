/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.os.Bundle
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R

class DisplayEnhanceSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "DisplayEnhanceFragment"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    private val switchBar by lazy {
        findPreference<MainSwitchPreference>(DisplayEnhanceController.SETTING_KEY)!!
    }

    private val memc by lazy {
        findPreference<CheckBoxPreference>(DisplayEnhanceController.KEY_MEMC)!!
    }

    private val s2h by lazy {
        findPreference<CheckBoxPreference>(DisplayEnhanceController.KEY_S2H)!!
    }

    private val sr by lazy {
        findPreference<CheckBoxPreference>(DisplayEnhanceController.KEY_SR)!!
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.d(TAG, "onCreatePreferences")
        setPreferencesFromResource(R.xml.settings_display_enhance, rootKey)

        val context = requireContext()
        switchBar.isChecked = DisplayEnhanceController.isEnabled(context)
        switchBar.onPreferenceChangeListener = this

        memc.isChecked = DisplayEnhanceController.isMemcEnabled(context)
        s2h.isChecked = DisplayEnhanceController.isS2hEnabled(context)
        sr.isChecked = DisplayEnhanceController.isSrEnabled(context)
        listOf(memc, s2h, sr).forEach { it.onPreferenceChangeListener = this }

        setFeaturesEnabled(switchBar.isChecked)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val checked = newValue as Boolean
        when (preference) {
            switchBar -> {
                if (DEBUG) Log.d(TAG, "master: $checked")
                DisplayEnhanceController.setEnabled(requireContext(), checked)
                setFeaturesEnabled(checked)
            }
            memc -> {
                if (DEBUG) Log.d(TAG, "memc: $checked")
                DisplayEnhanceController.setFeature(
                    requireContext(), DisplayEnhanceController.KEY_MEMC, checked,
                )
            }
            s2h -> {
                if (DEBUG) Log.d(TAG, "s2h: $checked")
                DisplayEnhanceController.setFeature(
                    requireContext(), DisplayEnhanceController.KEY_S2H, checked,
                )
            }
            sr -> {
                if (DEBUG) Log.d(TAG, "sr: $checked")
                DisplayEnhanceController.setFeature(
                    requireContext(), DisplayEnhanceController.KEY_SR, checked,
                )
            }
        }
        return true
    }

    private fun setFeaturesEnabled(enabled: Boolean) {
        listOf(memc, s2h, sr).forEach { it.isEnabled = enabled }
    }
}
