/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.rearlight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.TwoStatePreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R
import java.text.Collator
import java.util.HashSet

class RearLightSettingsFragment :
    SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    companion object {
        private const val TAG = "RearLightFragment"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    private lateinit var musicApps: PreferenceCategory
    private lateinit var musicMode: ListPreference
    private val selectedMusicApps = HashSet<String>()
    private var musicSelectedOnly = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (DEBUG) Log.d(TAG, "onCreatePreferences")
        setPreferencesFromResource(R.xml.settings_rear_light, rootKey)

        // The master switch is a MainSwitchPreference, the behaviour switches
        // are SwitchPreferenceCompat; both are TwoStatePreference underneath,
        // so one loop covers them.
        for (key in RearLightController.SWITCH_KEYS) {
            val pref = findPreference<TwoStatePreference>(key) ?: continue
            pref.isChecked = RearLightController.isEnabled(requireContext(), key)
            pref.onPreferenceChangeListener = this
        }

        setupMusicLightSettings()
    }

    private fun setupMusicLightSettings() {
        val context = requireContext()
        musicSelectedOnly = MusicLightSettings.isSelectedOnly(context)
        selectedMusicApps.clear()
        selectedMusicApps.addAll(MusicLightSettings.getApps(context))

        val enabled = findPreference<SwitchPreferenceCompat>(MusicLightSettings.KEY_ENABLED) ?: return
        enabled.isChecked = MusicLightSettings.isEnabled(context)
        enabled.onPreferenceChangeListener = this

        musicApps = findPreference<PreferenceCategory>(MusicLightSettings.KEY_APPS) ?: return
        musicApps.isVisible = musicSelectedOnly
        addMusicAppSwitches(context, musicApps)

        musicMode = findPreference<ListPreference>(MusicLightSettings.KEY_MODE) ?: return
        musicMode.entries = arrayOf<CharSequence>(
            getString(R.string.music_light_apps_mode_all),
            getString(R.string.music_light_apps_mode_selected),
        )
        musicMode.entryValues = arrayOf<CharSequence>(
            MusicLightSettings.MODE_ALL,
            MusicLightSettings.MODE_SELECTED,
        )
        musicMode.value = if (musicSelectedOnly) {
            MusicLightSettings.MODE_SELECTED
        } else {
            MusicLightSettings.MODE_ALL
        }
        musicMode.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            musicSelectedOnly = MusicLightSettings.MODE_SELECTED == value
            musicApps.isVisible = musicSelectedOnly
            saveMusicFilter(context)
            true
        }
    }

    /* One switch per launcher app, sorted by name; each change is saved right away. */
    private fun addMusicAppSwitches(context: Context, apps: PreferenceCategory) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcher, 0)
        val collator = Collator.getInstance()
        activities.sortWith { a, b ->
            collator.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString())
        }
        val added = HashSet<String>()
        for (info in activities) {
            val pkg = info.activityInfo.packageName
            if (!added.add(pkg)) continue

            val app = SwitchPreferenceCompat(context)
            app.key = "${MusicLightSettings.KEY_APPS}:$pkg"
            app.isPersistent = false
            app.title = info.loadLabel(pm)
            app.icon = info.loadIcon(pm)
            app.isChecked = selectedMusicApps.contains(pkg)
            app.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                if (value == true) {
                    selectedMusicApps.add(pkg)
                } else {
                    selectedMusicApps.remove(pkg)
                }
                saveMusicFilter(context)
                true
            }
            apps.addPreference(app)
        }
    }

    private fun saveMusicFilter(context: Context) {
        MusicLightSettings.setFilter(context, musicSelectedOnly, HashSet(selectedMusicApps))
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val key = preference.key ?: return true
        if (key == MusicLightSettings.KEY_ENABLED) {
            MusicLightSettings.setEnabled(requireContext(), newValue == true)
            return true
        }
        if (key in RearLightController.SWITCH_KEYS) {
            val isChecked = newValue as Boolean
            if (DEBUG) Log.d(TAG, "onPreferenceChange: $key=$isChecked")
            RearLightController.setEnabled(requireContext(), key, isChecked)
            return true
        }
        return true
    }
}
