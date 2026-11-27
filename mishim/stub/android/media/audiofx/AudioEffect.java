package android.media.audiofx;

import java.util.UUID;

/**
 * Compile-time stub for the @hide parts of android.media.audiofx.AudioEffect
 * that MiSound needs. Only used on the Kotlin compile classpath; at runtime the
 * real class comes from the boot classpath, which exposes exactly these members
 * (verified against the built framework.jar: setParameter(I[B)I etc., there is
 * no single-argument setParameter).
 */
public class AudioEffect {
    public static final UUID EFFECT_TYPE_NULL = new UUID(0L, 0L);

    protected AudioEffect(UUID type, UUID uuid, int priority, int audioSession) {
    }

    public int setParameter(int psize, byte[] param) {
        return 0;
    }

    public int setParameter(int[] psize, byte[] param) {
        return 0;
    }

    public int getParameter(int psize, byte[] param) {
        return 0;
    }

    public int getParameter(int[] psize, byte[] param) {
        return 0;
    }

    public boolean getEnabled() {
        return false;
    }

    public int setEnabled(boolean enabled) {
        return 0;
    }

    public boolean hasControl() {
        return false;
    }

    public void release() {
    }
}
