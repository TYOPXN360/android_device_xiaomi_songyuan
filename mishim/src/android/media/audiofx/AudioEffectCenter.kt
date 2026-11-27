package android.media.audiofx

import android.content.Context

/**
 * Minimal stand-in for MIUI's android.media.audiofx.AudioEffectCenter.
 *
 * MiSound reaches this class purely through reflection (Class.forName + getField
 * + getMethod), so the names and shapes have to line up with what it looks for:
 *
 *   static String EFFECT_DOLBY / EFFECT_MISOUND / EFFECT_NONE
 *                 EFFECT_SPATIAL_AUDIO / EFFECT_SURROUND
 *   static AudioEffectCenter getInstance(Context)
 *   isEffectSupported / isEffectAvailable / isEffectActive (String)
 *   setEffectActive(String, boolean), release()
 *   setDsoActive(...), setGeqBandGainsForDolbySpatializer(int[])
 *
 * Under AOSP there is no MIUI audio-effect broker behind this, so every query
 * answers "unsupported": the settings UI renders and stays alive, it just cannot
 * report or switch Dolby / Misound / spatial effects. Method lookups that do not
 * match are caught by the caller and merely logged.
 */
object AudioEffectCenter {
    @JvmField
    val EFFECT_DOLBY: String = "dolby"

    @JvmField
    val EFFECT_MISOUND: String = "misound"

    @JvmField
    val EFFECT_NONE: String = "none"

    @JvmField
    val EFFECT_SPATIAL_AUDIO: String = "spatial_audio"

    @JvmField
    val EFFECT_SURROUND: String = "surround"

    @JvmStatic
    fun getInstance(context: Context?): AudioEffectCenter = this

    @JvmStatic
    fun isEffectSupported(effect: String?): Boolean = false

    @JvmStatic
    fun isEffectAvailable(effect: String?): Boolean = false

    @JvmStatic
    fun isEffectActive(effect: String?): Boolean = false

    @JvmStatic
    fun setEffectActive(effect: String?, active: Boolean): Boolean = false

    @JvmStatic
    fun setDsoActive(active: Boolean): Boolean = false

    @JvmStatic
    fun setGeqBandGainsForDolbySpatializer(gains: IntArray?): Boolean = false

    fun release() {
    }
}