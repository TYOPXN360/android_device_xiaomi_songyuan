#!/bin/bash
#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#
# Rebuilds the MiSound shim and produces a MiSound.apk that carries it.
#
# MiSound (com.miui.misound) is a stock MIUI app that expects three classes the
# AOSP boot classpath does not provide:
#
#   miui.os.Build                      read by MiSoundApplication
#   miui.util.FeatureParser            feature flags
#   miui.util.HapticFeedbackUtil       linear-motor haptics
#   android.media.audiofx.AudioEffectCenter   反射访问的 MIUI 音效调度器
#   android.media.audiofx.MiSound      MiSoundWrapper 的反射目标
#
# The shim compiles those classes into a secondary dex and merges it into the
# APK's main dex, because the APK ships an R8-stripped Kotlin runtime that does
# not contain kotlin.text and cannot load a separate multidex cleanly.
#
# Usage (from the top of the tree, after `source build/envsetup.sh` and `lunch`):
#
#   device/xiaomi/songyuan/mishim/scripts/apply-shim.sh
#
# Result: vendor/xiaomi/songyuan/proprietary/product/app/MiSound/MiSound.apk is
# replaced with the shimmed, platform-signed build.

set -e

TOP=${TOP:-$(pwd)}
DEVICE_DIR="$TOP/device/xiaomi/songyuan/mishim"
STOCK_APK="$TOP/vendor/xiaomi/songyuan/proprietary/product/app/MiSound/MiSound.apk"

OUT="$DEVICE_DIR/out"
WORK="$OUT/work"

JAVA_HOME=${JAVA_HOME:-$TOP/prebuilts/jdk/jdk21/linux-x86}
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_JAR="$TOP/prebuilts/sdk/current/public/android.jar"
KOTLINC="$TOP/external/kotlinc/bin/kotlinc"
D8="$TOP/out/host/linux-x86/bin/d8"
ZIPALIGN="$TOP/out/host/linux-x86/bin/zipalign"
APKSIGNER="$TOP/out/host/linux-x86/bin/apksigner"
PLATFORM_PK8="$TOP/vendor/uwu-priv/keys/platform.pk8"
PLATFORM_PEM="$TOP/vendor/uwu-priv/keys/platform.x509.pem"

if [ ! -f "$STOCK_APK" ]; then
    echo "error: $STOCK_APK missing" >&2
    exit 1
fi

rm -rf "$WORK"
mkdir -p "$WORK/stub" "$WORK/classes" "$WORK/dex" "$WORK/apk"

echo "==> compiling compile-time stub"
"$JAVA_HOME/bin/javac" -source 17 -target 17 -nowarn \
    -d "$WORK/stub" "$DEVICE_DIR"/stub/android/media/audiofx/*.java

echo "==> compiling shim classes (Kotlin)"
"$KOTLINC" -cp "$WORK/stub:$ANDROID_JAR" -d "$WORK/classes" \
    "$DEVICE_DIR"/src/android/media/audiofx/*.kt \
    "$DEVICE_DIR"/src/miui/os/*.kt \
    "$DEVICE_DIR"/src/miui/util/*.kt

echo "==> compiling shim classes (Java)"
"$JAVA_HOME/bin/javac" -source 17 -target 17 -nowarn \
    -cp "$WORK/stub:$ANDROID_JAR:$WORK/classes" -d "$WORK/classes" \
    "$DEVICE_DIR"/src/android/media/audiofx/*.java \
    "$DEVICE_DIR"/src/kotlin/jvm/internal/*.java

echo "==> merging shim into the app dex"
unzip -o -q "$STOCK_APK" classes.dex -d "$WORK/apk"
"$D8" --lib "$ANDROID_JAR" --lib "$WORK/stub" --min-api 30 \
    --output "$WORK/dex" \
    "$WORK/apk/classes.dex" \
    $(find "$WORK/classes" -name '*.class')

echo "==> repacking MiSound.apk"
python3 - "$STOCK_APK" "$OUT/MiSound-unsigned.apk" "$WORK/dex/classes.dex" <<'PY'
import sys, zipfile
src, dst, merged = sys.argv[1], sys.argv[2], sys.argv[3]
merged_data = open(merged, "rb").read()
zin = zipfile.ZipFile(src)
with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        buf = zin.read(item.filename)
        if item.filename == "classes.dex":
            buf = merged_data
        zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        zi.compress_type = item.compress_type
        zi.external_attr = item.external_attr
        zi.create_system = item.create_system
        zout.writestr(zi, buf)
print("repacked", dst)
PY

echo "==> signing"
"$ZIPALIGN" -p -f 4 "$OUT/MiSound-unsigned.apk" "$OUT/MiSound-aligned.apk"
"$APKSIGNER" sign --key "$PLATFORM_PK8" --cert "$PLATFORM_PEM" \
    --out "$OUT/MiSound.apk" "$OUT/MiSound-aligned.apk"

cp "$OUT/MiSound.apk" "$STOCK_APK"
echo "==> installed shimmed MiSound.apk into the vendor tree"
sha256sum "$STOCK_APK"
