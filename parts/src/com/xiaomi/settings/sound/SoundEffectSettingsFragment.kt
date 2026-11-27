/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.sound

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.xiaomi.settings.R

/**
 * Entry point for MiSound (com.miui.misound), the stock "音质音效" app.
 *
 * The app is not built into this ROM and has no launcher activity of its own, so
 * XiaomiParts exposes the pages that work under AOSP. Each row simply launches
 * the corresponding MiSound activity by component name.
 *
 * The equalizer page is the one that also works with the loudspeaker: it drives
 * the misoundfx effect registered in audio_effects_config.xml, which we verified
 * end to end (drags reach EffectMiSoundContext and are applied to the DSP).
 */
class SoundEffectSettingsFragment : SettingsBasePreferenceFragment() {

    companion object {
        private const val TAG = "SoundEffectFragment"

        private const val MISOUND_PACKAGE = "com.miui.misound"

        private const val KEY_EQUALIZER = "sound_effect_equalizer"
        private const val KEY_DIRAC = "sound_effect_dirac"
        private const val KEY_HEADSET = "sound_effect_headset"

        private const val ACTIVITY_EQUALIZER = "com.miui.misound.speffect.CustomEqActivity"
        private const val ACTIVITY_DIRAC = "com.miui.misound.DiracEqualizer"
        private const val ACTIVITY_HEADSET = "com.miui.misound.HeadsetSettingsActivity"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_sound_effect, rootKey)

        bind(KEY_EQUALIZER, ACTIVITY_EQUALIZER)
        bind(KEY_DIRAC, ACTIVITY_DIRAC)
        bind(KEY_HEADSET, ACTIVITY_HEADSET)
    }

    private fun bind(key: String, activity: String) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener {
            launch(activity)
            true
        }
    }

    private fun launch(activity: String) {
        val intent = Intent().apply {
            component = ComponentName(MISOUND_PACKAGE, activity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // MiSound is optional: a build without it leaves the row inert rather
            // than crashing XiaomiParts.
            Log.w(TAG, "cannot launch $activity", e)
        }
    }
}
