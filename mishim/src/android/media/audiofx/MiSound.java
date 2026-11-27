package android.media.audiofx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * MIUI's android.media.audiofx.MiSound, implemented on top of AOSP AudioEffect.
 *
 * miui-framework.jar ships this as a thin wrapper over a native handle
 * (super(EFFECT_TYPE_NULL, EFFECT_TYPE_MISOUND, priority, session)). The methods
 * below are exactly what MiSound.apk grabs by reflection out of com.miui.misound's
 * MiSoundWrapper, so their signatures have to match verbatim.
 *
 * Wire format, reverse engineered from EffectMiSoundContext::setParams in
 * libmisoundfx_aosp_aidl_ext.so:
 *
 *   byte  0x0c   command id
 *   byte  0x10   parameter byte (band / enable / index)
 *   bytes 0x14.. NUL-terminated text, parsed with std::stof for float commands
 *
 * Written in Java on purpose: the APK ships an R8-stripped Kotlin runtime with no
 * kotlin.text classes, so Kotlin's String/StringBuilder helpers (and even
 * java.lang.StringBuilder, which Kotlin maps to kotlin.text.StringBuilder) are
 * unavailable at runtime. This class therefore touches java.* only.
 */
public class MiSound extends AudioEffect {

    public static final UUID EFFECT_TYPE_MISOUND =
            UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b");

    private static final int CMD_HEADSET_TYPE = 1;
    private static final int CMD_LEVEL = 2;
    private static final int CMD_MUSIC = 4;
    private static final int CMD_HIFI_MODE = 8;
    private static final int CMD_SCENARIO = 15;
    private static final int CMD_MOVIE_SURROUND = 16;
    private static final int CMD_MOVIE_VOCAL = 17;
    private static final int CMD_MOVIE_MODE_ENABLE = 18;
    private static final int CMD_3D_SURROUND = 20;
    private static final int CMD_SOUND_ID_GAIN = 23;
    private static final int CMD_SOUND_ID_ENABLE = 24;
    private static final int CMD_EAR_SCAN_ENABLE = 26;
    private static final int CMD_EAR_SCAN_VALUES = 27;

    private int lastHeadsetType = 0;
    private int lastMusic = 0;
    private int lastHifiMode = 0;
    private int lastScenario = 0;
    private int lastSoundIdEnable = 0;
    private int lastEarScanEnable = 0;
    private final float[] lastLevels = new float[10];
    private final float[] lastSoundIdGain = new float[6];
    private float[] lastEarScan = new float[0];

    public MiSound(int priority, int audioSession)
            throws IllegalStateException, IllegalArgumentException,
            UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_MISOUND, priority, audioSession);
    }

    /* ---------- transport ---------- */

    /**
     * The AIDL header that reaches the HAL carries `psize` at offset 0x0c and the
     * first payload byte at offset 0x10, so the real wire format is:
     *
     *   setParameter(cmd, data)  ->  HAL reads cmd from the psize slot
     *   data[0..3]  value (little endian int)
     *   data[4..]   NUL-terminated text parsed with std::stof
     *
     * Confirmed on device: setParameter(20, {0x30..}) logged
     * "Set 3d Surround to : 48", setParameter(24, ...) logged
     * "Set SoundID enable 48".
     */
    private int sendRaw(int cmd, int value, byte[] text) {
        int textLen = (text == null) ? 0 : text.length;
        byte[] data = new byte[4 + textLen + 1];
        data[0] = (byte) (value & 0xff);
        data[1] = (byte) ((value >> 8) & 0xff);
        data[2] = (byte) ((value >> 16) & 0xff);
        data[3] = (byte) ((value >> 24) & 0xff);
        if (text != null) {
            System.arraycopy(text, 0, data, 4, textLen);
        }
        try {
            int st = setParameter(cmd, data);
            android.util.Log.d("MiSoundShim",
                    "setParameter cmd=" + cmd + " value=" + value + " status=" + st + " len=" + data.length);
            return st;
        } catch (Throwable t) {
            android.util.Log.e("MiSoundShim", "setParameter cmd=" + cmd + " failed", t);
            return -1;
        }
    }

    private int send(int cmd, int value) {
        return sendRaw(cmd, value, null);
    }

    private int sendFloat(int cmd, int index, float f) {
        return sendRaw(cmd, index, Float.toString(f).getBytes(StandardCharsets.UTF_8));
    }

    private int sendFloats(int cmd, float[] values) {
        /* Same AIDL header rule: psize carries the command, payload starts at 0x10.
         * The HAL computes the element count as (vectorSize - 0x10) / 4. */
        byte[] data = new byte[values.length * 4];
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < values.length; i++) {
            bb.putFloat(i * 4, values[i]);
        }
        try {
            return setParameter(cmd, data);
        } catch (Throwable t) {
            return -1;
        }
    }

    /* ---------- surface used by MiSound.apk ---------- */

    public void setMusic(int value) {
        lastMusic = value;
        send(CMD_MUSIC, value);
    }

    public int getMusic() {
        return lastMusic;
    }

    public void setHeadsetType(int value) {
        lastHeadsetType = value;
        send(CMD_HEADSET_TYPE, value);
    }

    public int getHeadsetType() {
        return lastHeadsetType;
    }

    public int[] getHeadsetList() {
        int n = 16;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i;
        }
        return out;
    }

    public void setLevel(int index, float gain) {
        if (index >= 0 && index < lastLevels.length) {
            lastLevels[index] = gain;
        }
        sendFloat(CMD_LEVEL, index, gain);
    }

    public void setHifiMode(int value) {
        lastHifiMode = value;
        send(CMD_HIFI_MODE, value);
    }

    public void setScenario(int value) {
        lastScenario = value;
        send(CMD_SCENARIO, value);
    }

    public int getScenario() {
        return lastScenario;
    }

    public void setMovieSurroundLevel(int value) {
        send(CMD_MOVIE_SURROUND, value);
    }

    public void setMovieVocalLevel(int value) {
        send(CMD_MOVIE_VOCAL, value);
    }

    public void setMovieModeEnable(int value) {
        send(CMD_MOVIE_MODE_ENABLE, value);
    }

    public void set3dSurround(int value) {
        send(CMD_3D_SURROUND, value);
    }

    public void setSoundIdEnable(int value) {
        lastSoundIdEnable = value;
        send(CMD_SOUND_ID_ENABLE, value);
    }

    public int getSoundIdEnable() {
        return lastSoundIdEnable;
    }

    public void setSoundIdGain(float[] gains) {
        int n = Math.min(gains.length, lastSoundIdGain.length);
        for (int i = 0; i < n; i++) {
            lastSoundIdGain[i] = gains[i];
            sendFloat(CMD_SOUND_ID_GAIN, i, gains[i]);
        }
    }

    public void setEarScanEnable(int value) {
        lastEarScanEnable = value;
        send(CMD_EAR_SCAN_ENABLE, value);
    }

    public int getEarScanEnable() {
        return lastEarScanEnable;
    }

    public void setEarScanValues(float[] values) {
        lastEarScan = new float[values.length];
        System.arraycopy(values, 0, lastEarScan, 0, values.length);
        sendFloats(CMD_EAR_SCAN_VALUES, values);
    }

    public float[] getEarScanValues() {
        float[] out = new float[lastEarScan.length];
        System.arraycopy(lastEarScan, 0, out, 0, lastEarScan.length);
        return out;
    }

    /* getEnabled / setEnabled / hasControl / release are inherited from AudioEffect */
}
