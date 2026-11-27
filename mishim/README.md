# MiSound (com.miui.misound) on AOSP — bring-up notes

Stock "音质音效" app from HyperOS. It is not part of this ROM and its Java side
expects five classes that only MIUI's framework provides. This directory holds
the replacement classes and a script that bakes them into the APK.

## Why the classes cannot simply be installed

The app ships an R8-stripped Kotlin runtime: `kotlin.text` is gone, and even
`java.lang.StringBuilder` is unavailable from Kotlin because Kotlin maps it to
`kotlin.text.StringBuilder`. Every shim class therefore has to touch `java.*`
only, which is why `MiSound` and `Intrinsics` are written in Java while the rest
are Kotlin (they use no string or collection API).

## The classes

| Class | Role |
|---|---|
| `miui.os.Build` | `DEVICE` / `PRODUCT` / `IS_TABLET` (+ a few harmless extras), read by `MiSoundApplication` |
| `miui.util.FeatureParser` | feature flags; `getString` returns null so callers fall back to defaults |
| `miui.util.HapticFeedbackUtil` | linear-motor haptics; every query answers "not supported", UI still works |
| `android.media.audiofx.AudioEffectCenter` | MIUI effect broker reached via `Class.forName`; constants + `getInstance(Context)` + query/set stubs |
| `android.media.audiofx.MiSound` | the real shim: talks to the misoundfx HAL |
| `kotlin.jvm.internal.Intrinsics` | Java stand-in; a Kotlin one would reference itself |

## The wire format (reverse engineered)

`EffectMiSoundContext::setParams` in `libmisoundfx_aosp_aidl_ext.so` reads:

```
vector<uint8_t> data
  data[0x0c]  command id
  data[0x10]  parameter byte (band / enable / index)
  data[0x14..] NUL terminated text, parsed with std::stof for float commands
```

But AOSP's AIDL path puts the Java `setParameter(int psize, byte[] param)`
`psize` at offset 0x0c and the payload starts at 0x10, so the practical call is:

```java
setParameter(cmd, data);   // psize carries the command
// data[0..3] value (little endian int)
// data[4..]  float literal text
```

Confirmed on device: `setParameter(20, {0x30..})` logged
`Set 3d Surround to : 48`, and `setParameter(24, ...)` logged
`Set SoundID enable 48`.

Command ids, taken from `miui-framework.jar`'s `MiSound`:

| cmd | meaning | HAL support |
|---|---|---|
| 1 | headset type | no |
| 2 | EQ band level (text = gain) | yes |
| 3 | mode | no |
| 4 | music | no |
| 5 | movie | no |
| 7 | movie mode | no |
| 8 | hifi mode | no |
| 9 | ears compensation on | yes |
| 10 / 11 | compensation EQ left / right | yes |
| 14 | compensation example | yes |
| 15 | scenario | yes (logged as unused) |
| 16 / 17 | movie surround / vocal | no |
| 18 | movie mode enable | no |
| 19 | headset list | no |
| 20 | 3D surround | yes |
| 23 | SoundID gain | yes |
| 24 | SoundID enable | yes |
| 25 | effect enable | yes (also via AudioEffect.setEnabled) |
| 26 / 27 | ear canal scan enable / values | yes |
| 29 | no spatial flag | yes |
| 30 | volume index | yes |
| 31 | super effect profile | yes |

Commands the HAL does not handle are silently rejected; the shim keeps their
state locally so the UI stays consistent. `EffectMiSound::commandImpl` was also
checked — it only implements the standard AIDL commands, not these.

## Effect scope

`MiSound(priority, audioSession)` is constructed with session 0 (global), so the
equalizer and friends apply to every output — loudspeaker, headset and Bluetooth,
not only the headset. That is why the equalizer page works without headphones.

## SELinux

`EffectMiSoundContext::saveParamToProperty` writes `persist.vendor.audio.*`. The
generic vendor policy labels those `vendor_audio_prop` but never grants the
effect HAL write access, so every save was rejected:

```
avc: denied { set } for property=persist.vendor.audio.3dsurround.enable
     scontext=u:r:hal_audio_default:s0 tcontext=u:object_r:vendor_audio_prop:s0
```

`sepolicy/vendor/hal_audio_default.te` adds
`set_prop(hal_audio_default, vendor_audio_prop)`; without it the DSP gets the new
value immediately but the setting is lost on reboot.

## Rebuilding the APK

```bash
source build/envsetup.sh
lunch uwu_songyuan-cp2a-user
device/xiaomi/songyuan/mishim/scripts/apply-shim.sh
```

The script compiles the stub, the Kotlin and Java shims, merges the shim classes
into the APK's main dex with d8 (a secondary dex is rejected by ART: the merged
archive trips `Out-of-order string_ids` if you try to rename packages after the
fact), then zipaligns, signs and writes the result back into
`vendor/xiaomi/songyuan/proprietary/product/app/MiSound/MiSound.apk`.

## Verification on device

```bash
adb shell pm path com.miui.misound                 # expect /product/app/MiSound/
adb shell am start -n com.miui.misound/.speffect.CustomEqActivity
adb logcat -d | grep -E "Set EQ level|saveParamToProperty"
```

A working drag on the ten band equalizer produces `Set EQ level: EQ band = N` /
`EQ value = X` and no `avc: denied` on the save.
