/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.musiclight

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

/*
 * Pulses the rear LED with whatever is playing, like stock HyperOS. The
 * analysis runs in the audio HAL (libmusiclightaidl, the stock algorithm),
 * which sets the LED level; this service powers the LED up while media plays,
 * keeps the effect on the player's session and switches the LED off after.
 *
 * The effect goes on the player's session rather than the output mix:
 * AudioFlinger suspends output mix effects whenever any session effect (like
 * MiSound) is enabled, and the stock V3 effect HAL can't opt out of that.
 */
class MusicLightService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var audioManager: AudioManager
    private var effect: AudioEffect? = null
    private var session: Int = 0
    private var lit: Boolean = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            update(configs.orEmpty())
        }
    }

    override fun onCreate() {
        thread = HandlerThread(TAG).apply { start() }
        handler = Handler(thread.looper)
        MusicLight.releaseIfLeftLit(this)
        audioManager = getSystemService(AudioManager::class.java)
        audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
        handler.post { update(audioManager.activePlaybackConfigurations) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        handler.post {
            stop()
            thread.quitSafely()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update(configs: List<AudioPlaybackConfiguration>) {
        val selectedOnly = MusicLight.isSelectedOnly(this)
        val apps = MusicLight.getApps(this)
        val activeSession = configs.firstOrNull {
            it.isActive && isWanted(it, selectedOnly, apps)
        }?.sessionId ?: AudioManager.AUDIO_SESSION_ID_GENERATE

        if (activeSession == AudioManager.AUDIO_SESSION_ID_GENERATE) {
            stop()
        } else if (!lit) {
            start(activeSession)
        } else if (effect == null || activeSession != session) {
            effect?.release()
            effect = null
            attach(activeSession)
        }
    }

    /*
     * Any media playback, or with an app selection, any playback of the
     * selected apps that isn't a call or a system sound (so WhatsApp voice
     * messages count).
     */
    private fun isWanted(
        config: AudioPlaybackConfiguration,
        selectedOnly: Boolean,
        apps: Set<String>,
    ): Boolean {
        val usage = config.audioAttributes.usage
        if (!selectedOnly) {
            return usage == AudioAttributes.USAGE_MEDIA
        }
        if (usage != AudioAttributes.USAGE_MEDIA &&
            usage != AudioAttributes.USAGE_GAME &&
            usage != AudioAttributes.USAGE_UNKNOWN
        ) {
            return false
        }
        val packages = packageManager.getPackagesForUid(config.clientUid) ?: return false
        return packages.any { apps.contains(it) }
    }

    private fun start(activeSession: Int) {
        MusicLight.setLit(this, true)
        lit = true
        write("hwen", "1")
        write("color", "FFFFFF")
        for (segment in 0 until SEGMENTS) {
            write("rgbcolor", "$segment FFFFFF")
        }
        write("brightness", "255")
        write("run", "1")
        write("reg", "$GLOBAL_CURRENT_REG 0")
        attach(activeSession)
        if (effect == null) {
            stop()
        }
    }

    private fun attach(activeSession: Int) {
        try {
            val newEffect = AudioEffect(EFFECT_TYPE, EFFECT_IMPL, 0, activeSession)
            newEffect.setEnabled(true)
            effect = newEffect
            session = activeSession
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to attach the music light effect to session $activeSession", e)
            effect?.release()
            effect = null
        }
    }

    private fun stop() {
        effect?.release()
        effect = null
        if (lit) {
            release()
            MusicLight.setLit(this, false)
            lit = false
        }
    }

    companion object {
        private const val TAG = "MusicLight"
        private const val LED = "/sys/class/leds/aw21024_led/"
        private const val SEGMENTS = 8

        // The driver only applies "brightness" on a "run" write, which reloads
        // the whole chip. The effect dims every LED at once with the global
        // current.
        private const val GLOBAL_CURRENT_REG = "6e"

        private val EFFECT_TYPE = UUID.fromString("1a8feafc-486d-4fea-9e19-9a5f583f1c91")
        private val EFFECT_IMPL = UUID.fromString("7e1bef4c-45d6-4672-8070-25a8a4d230a7")

        fun release() {
            write("reg", "$GLOBAL_CURRENT_REG 0")
            write("run", "0")
            write("hwen", "0")
        }

        private fun write(node: String, value: String) {
            try {
                FileOutputStream(LED + node).use { out ->
                    out.write(value.toByteArray(StandardCharsets.US_ASCII))
                }
            } catch (e: IOException) {
                Log.w(TAG, "Failed to write $node", e)
            }
        }
    }
}
