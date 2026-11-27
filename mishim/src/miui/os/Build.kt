package miui.os

/**
 * Minimal shim for MIUI's framework Build class.
 *
 * MiSound only reads DEVICE / IS_TABLET / PRODUCT from it. Values are non-const
 * @JvmField properties so the Kotlin compiler emits real static fields and does
 * not inline the literals at the call sites: the class must exist at runtime,
 * which is exactly what this provides.
 */
object Build {
    @JvmField
    val DEVICE: String = android.os.Build.DEVICE

    @JvmField
    val PRODUCT: String = android.os.Build.PRODUCT

    @JvmField
    val IS_TABLET: Boolean = false

    /* A few extra members real MIUI code commonly touches; harmless to keep. */
    @JvmField
    val MODEL: String = android.os.Build.MODEL

    @JvmField
    val BRAND: String = android.os.Build.BRAND

    @JvmField
    val MANUFACTURER: String = android.os.Build.MANUFACTURER

    @JvmField
    val IS_MIUI: Boolean = false

    @JvmField
    val IS_STABLE: Boolean = true

    @JvmField
    val IS_DEVELOPMENT_VERSION: Boolean = false

    @JvmField
    val VERSION: String = android.os.Build.VERSION.INCREMENTAL
}