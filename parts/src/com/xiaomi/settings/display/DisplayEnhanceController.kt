/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.Display

/**
 * Drives "双芯视效" (the AI image engine) the same way the stock HyperOS
 * framework does.
 *
 * Stock never writes to the Novatek HAL directly for this switch.  The whole
 * path is FrameMaster:
 *
 *   ServiceManagerUtil.notifyFrameMaster(featureId, status, value, cmd, reserve)
 *     -> vendor.xiaomi.hardware.framemaster.IFrameMaster/default
 *     -> transact(1): writeInt(featureId), writeInt(status), writeInt(value),
 *                     writeString(cmd), writeString(reserve)
 *     -> libframemaster.so: FrameMaster::setParameter -> FrameHandlerBase
 *
 * FrameHandlerBase::setParameter dispatches on featureId to a handler slot,
 * and slot 2 is DualDpuHandler::handleVideoMemc, which is the one
 * DisplayDualDpuHandler.DualDpuSwitchFM uses:
 *
 *   on : status = 1, value = target fps,
 *        cmd = "\"Category:1,MEMC:1,MEMCMode:2,TargetFPS:60,LDSR:1,LDSRMode:3,
 *                LDSRV2Mode:0x22,SDR2HDR:1,SDR2HDRMode:0x636,3DLUT:1,3DLUTMode:6\""
 *   off: status = 0, value = target fps, cmd = "\"SceneID:0000\""
 *
 * The parameter names, the mode values and the ordering below are copied
 * verbatim from the stock /odm/etc/display/ui_service_cloud_config.xml, so the
 * HAL receives exactly the string it receives on HyperOS.
 *
 * The individual switches compose that string: each one emits its own key when
 * enabled and its "off" key when disabled, in the stock key order. 3DLUT is
 * carried along with the master switch, as in every stock entry.
 */
object DisplayEnhanceController {

    private const val TAG = "DisplayEnhance"

    // --- settings keys -----------------------------------------------------
    const val SETTING_KEY = "display_enhance_ai_enable"
    const val KEY_MEMC = "display_enhance_memc"
    const val KEY_S2H = "display_enhance_s2h"
    const val KEY_SR = "display_enhance_sr"

    private const val PROP = "persist.sys.disp_enhance"

    // --- FrameMaster ------------------------------------------------------
    private const val FRAME_MASTER_SERVICE =
        "vendor.xiaomi.hardware.framemaster.IFrameMaster/default"
    private const val FRAME_MASTER_INTERFACE =
        "vendor.xiaomi.hardware.framemaster.IFrameMaster"
    private const val TRANSACTION_SET_PARAMETER = 1

    /** FrameHandlerBase slot for DualDpuHandler::handleVideoMemc. */
    private const val FEATURE_VIDEO_MEMC = 2

    /** DisplayDualDpuHandler.byPass, the stock "all off" command. */
    private const val BYPASS_CMD = "\"SceneID:0000\""

    /** Stock mode constants, straight from the cloud config. */
    private const val MEMC_MODE = 2
    private const val LDSR_MODE = 3
    private const val LDSRV2_MODE = "0x22"
    private const val SDR2HDR_MODE = "0x636"
    private const val LUT3D_MODE = 6

    fun isEnabled(context: Context): Boolean =
        Settings.System.getIntForUser(
            context.contentResolver, SETTING_KEY, 1, UserHandle.USER_CURRENT,
        ) == 1

    fun isMemcEnabled(context: Context): Boolean = feature(context, KEY_MEMC, true)
    fun isS2hEnabled(context: Context): Boolean = feature(context, KEY_S2H, true)
    fun isSrEnabled(context: Context): Boolean = feature(context, KEY_SR, true)

    private fun feature(context: Context, key: String, default: Boolean): Boolean =
        Settings.System.getIntForUser(
            context.contentResolver, key, if (default) 1 else 0, UserHandle.USER_CURRENT,
        ) == 1

    fun setEnabled(context: Context, enabled: Boolean) {
        Settings.System.putIntForUser(
            context.contentResolver, SETTING_KEY, if (enabled) 1 else 0,
            UserHandle.USER_CURRENT,
        )
        Thread { push(context, enabled) }.start()
    }

    fun setFeature(context: Context, key: String, enabled: Boolean) {
        Settings.System.putIntForUser(
            context.contentResolver, key, if (enabled) 1 else 0, UserHandle.USER_CURRENT,
        )
        if (isEnabled(context)) {
            Thread { push(context, true) }.start()
        }
    }

    /** Re-apply the persisted state after boot. */
    fun restore(context: Context) {
        val enabled = isEnabled(context)
        if (!enabled) {
            apply(false)
            notifyFrameMaster(FEATURE_VIDEO_MEMC, 0, 120, BYPASS_CMD)
            return
        }
        Thread { push(context, true) }.start()
    }

    private fun push(context: Context, enabled: Boolean) {
        try {
            if (!enabled) {
                notifyFrameMaster(FEATURE_VIDEO_MEMC, 0, targetFps(context), BYPASS_CMD)
                apply(false)
                return
            }

            // Start the Novatek HAL first (init reacts to the persist property),
            // then hand it the dual-DPU request.
            apply(true)
            Thread.sleep(350)

            val fps = targetFps(context)
            val cmd = buildString {
                append("\"")
                append("Category:1")
                append(",MEMC:").append(if (isMemcEnabled(context)) 1 else 0)
                if (isMemcEnabled(context)) {
                    append(",MEMCMode:").append(MEMC_MODE)
                    append(",TargetFPS:").append(fps)
                }
                append(",LDSR:").append(if (isSrEnabled(context)) 1 else 0)
                if (isSrEnabled(context)) {
                    append(",LDSRMode:").append(LDSR_MODE)
                    append(",LDSRV2Mode:").append(LDSRV2_MODE)
                }
                append(",SDR2HDR:").append(if (isS2hEnabled(context)) 1 else 0)
                if (isS2hEnabled(context)) {
                    append(",SDR2HDRMode:").append(SDR2HDR_MODE)
                }
                append(",3DLUT:1")
                append(",3DLUTMode:").append(LUT3D_MODE)
                append("\"")
            }
            notifyFrameMaster(FEATURE_VIDEO_MEMC, 1, fps, cmd)
        } catch (t: Throwable) {
            Log.e(TAG, "failed to apply display enhancement", t)
        }
    }

    private fun targetFps(context: Context): Int {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return 120
        val rate = dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
        return if (rate > 60f) 120 else 60
    }

    private fun notifyFrameMaster(featureId: Int, status: Int, value: Int, cmd: String) {
        val binder: IBinder = ServiceManager.getService(FRAME_MASTER_SERVICE) ?: run {
            Log.w(TAG, "FrameMaster service is not available")
            return
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(FRAME_MASTER_INTERFACE)
            data.writeInt(featureId)
            data.writeInt(status)
            data.writeInt(value)
            data.writeString(cmd)
            data.writeString("null")
            val ok = binder.transact(TRANSACTION_SET_PARAMETER, data, reply, 0)
            Log.i(
                TAG,
                "FrameMaster feature=$featureId status=$status value=$value " +
                    "cmd=$cmd ok=$ok",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "FrameMaster transaction failed", t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun apply(enabled: Boolean) {
        val value = if (enabled) "1" else "0"
        try {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("set", String::class.java, String::class.java)
                .invoke(null, PROP, value)
            Log.i(TAG, "applied $PROP=$value")
        } catch (t: Throwable) {
            Log.e(TAG, "failed to set $PROP=$value", t)
        }
    }
}
