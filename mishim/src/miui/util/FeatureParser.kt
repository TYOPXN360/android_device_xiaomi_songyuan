package miui.util

/**
 * Minimal shim for MIUI's FeatureParser.
 *
 * MiSound only calls getString(String); returning null makes the caller fall back
 * to its own defaults.
 */
object FeatureParser {
    @JvmStatic
    fun getString(key: String?): String? = null

    @JvmStatic
    fun getString(key: String?, defValue: String?): String? = defValue

    @JvmStatic
    fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    @JvmStatic
    fun getInt(key: String?, defValue: Int): Int = defValue

    @JvmStatic
    fun getLong(key: String?, defValue: Long): Long = defValue
}