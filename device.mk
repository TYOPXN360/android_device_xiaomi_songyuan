
#
# Copyright (C) 2026 The Android Open Source Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Audio (above the common inherit: the first PRODUCT_COPY_FILES entry for a
# destination wins, and common installs the AOSP volume tables)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/audio/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml \
    $(LOCAL_PATH)/configs/audio/resourcemanager_canoe_mtp.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio/sku_canoe/resourcemanager_canoe_mtp.xml \
    $(LOCAL_PATH)/configs/audio/libdev_speaker.so:$(TARGET_COPY_OUT_VENDOR)/lib64/libdev_speaker.so

# Inherit from sm8650-common
$(call inherit-product, device/xiaomi/sm8850-common/common.mk)

# The common product's speaker module is the international blob; the device
# copy above is the matching OS4 implementation for this RM configuration.
PRODUCT_PACKAGES := $(filter-out libdev_speaker,$(PRODUCT_PACKAGES))

# Device-specific proprietary ODM blobs (audio firmware, sensors, and tuning).
$(call inherit-product, vendor/xiaomi/songyuan/songyuan-vendor.mk)

# Camera/sensor protobuf ABI: the China OS4 camera core requires
# sns_shkcam_config, which the common international blob does not export.
PRODUCT_PACKAGES := $(filter-out libsensinghubapi-prop,$(PRODUCT_PACKAGES))
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/camera/libsensinghubapi-prop.so:$(TARGET_COPY_OUT_VENDOR)/lib64/libsensinghubapi-prop.so

# The Xiaomi sensor service resolves its sub-HAL list from ODM on this device.
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/sensors/hals.conf:$(TARGET_COPY_OUT_ODM)/etc/sensors/hals.conf

# Camera
PRODUCT_PACKAGES += \
    android.hardware.graphics.allocator-V1-ndk.vendor \
    vendor.qti.hardware.camera.offlinecamera-V2-ndk.vendor

# Display
# songyuan's composer-service links composer3-V4-ndk; sm8850-common installs
# V1, which is what popsicle's build of the same service needs.
PRODUCT_PACKAGES += \
    vendor.qti.hardware.display.composer3-V4-ndk.vendor

# Euicc
# XiaomiEsimSwitcher provides the only path that powers the eSIM on: its toggle
# calls onHookUimPowerReqEx + onSetEsimStatus, which write the enable state to
# modem EFS and bring up the chip's PMIC rail. The eUICC is otherwise unpowered,
# which is why every slot reports mIsEuicc=false with an empty EID and no LPA
# can find it. See the local patch in hardware/xiaomi: the status *getter* hangs
# on this device and had to be kept off the settings screen's load path.
PRODUCT_PACKAGES += \
    XiaomiEuicc \
    XiaomiEsimSwitcher

# OpenEUICC, built from source (packages/apps/OpenEUICC). It ships its own
# privapp allowlist and liblpac-jni.
PRODUCT_PACKAGES += \
    OpenEUICC

# Declare the eUICC features so EuiccManager exists and the EuiccGoogle LPA can
# start (TelephonyFrameworkInitializer gates EUICC_SERVICE on this). The LPA
# then reports "Cannot find Euicc on device" because no slot is exposed as an
# eUICC -- a graceful error rather than a reboot.
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.telephony.euicc.mep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.telephony.euicc.mep.xml

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/permissions/privapp-permissions-euiccgoogle.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/permissions/privapp-permissions-euiccgoogle.xml


# Properties
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/properties/odm_CN.prop:$(TARGET_COPY_OUT_ODM)/etc/odm_CN.prop \
    $(LOCAL_PATH)/configs/properties/odm_GL.prop:$(TARGET_COPY_OUT_ODM)/etc/odm_GL.prop

# Updater: OTA feed (zips on SourceForge), see github.com/AlexZorzi/songyuan-ota
ifeq ($(wildcard vendor/extras/evolution.mk),)
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    lineage.updater.uri=https://raw.githubusercontent.com/AlexZorzi/songyuan-ota/main/{device}.json
else
PRODUCT_PACKAGES += \
    EvolutionUpdaterOverlaySongyuan
endif

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Init
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/init/init.songyuan.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.songyuan.rc \
    $(LOCAL_PATH)/configs/init/tee-supplicant.rc:$(TARGET_COPY_OUT_ODM)/etc/init/tee-supplicant.rc

# MiTEE: oemvm only (trustedvm's kernel lives on a partition we don't mount)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/qcvm/qcvm_config.json:$(TARGET_COPY_OUT_VENDOR)/etc/qcvm_config.json

# Face (mifaced, MiTEE face TA)
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/init/miface.rc:$(TARGET_COPY_OUT_ODM)/etc/init/miface.rc \
    $(LOCAL_PATH)/configs/permissions/signature-permissions-faceenroll.xml:$(TARGET_COPY_OUT_ODM)/etc/permissions/signature-permissions-faceenroll.xml \
    frameworks/native/data/etc/android.hardware.biometrics.face.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.biometrics.face.xml

PRODUCT_PACKAGES += \
    android.hardware.biometrics.face-V4-ndk.vendor \
    SongyuanFaceEnroll

# Lights (rear AW21024 LED)
PRODUCT_PACKAGES += \
    android.hardware.light-service.sm8850

# Overlays
PRODUCT_PACKAGES += \
    ApertureOverlaySongyuan \
    FrameworksResSongyuan \
    LineageSDKResSongyuan \
    NfcOverlaySongyuan \
    SongyuanEuiccOverlay \
    SettingsOverlaySongyuan \
    SystemUIResSongyuan


# Properties stock keeps in mi_ext/etc/build.prop. mi_ext folds into system_ext
# on this build, and that channel is the one that actually takes effect: a bare
# ro.product.* key in a vendor build.prop is silently ignored, verified on
# device.
#
# Deliberately left out: the MIUI version strings (ro.mi.os.version.*,
# ro.mi.xms.version.incremental, ro.ai.os.version.*, ro.build.version.incremental)
# which are stock ROM bookkeeping, ro.vendor.build.ab_ota_partitions which this
# build generates itself, and ro.product.mod_device which the MIUI camera tree
# owns.
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.miui.build.region=cn \
    ro.boot.monitor=true \
    ro.fling.optimize.sf.device=true \
    ro.mi.os.custfeatureresolve=true \
    ro.miui.support.system.app.uninstall.v2=true \
    ro.ai.transparent.sandbox.enabled=true \
    ro.config.WlanAntTunerPolicy=3 \
    ro.wifi.tvendor.disable1=true \
    ro.com.google.clientidbase=android-xiaomi \
    ro.com.google.clientidbase.ms=android-xiaomi \
    ro.logd.size.crash=256K \
    ro.logd.size.events=1M \
    ro.logd.size.kernel=64K \
    ro.logd.size.security=64K \
    persist.sys.power_mode_support=true \
    persist.sys.pre_startup=true \
    persist.sys.support_power_seg_bpf_track=true \
    persist.vendor.camera.gesture.emoji.support=1 \
    persist.audio.video.large_jpegopt_enable=1 \
    persist.audio.laggy.detect.enable=100 \
    bluetooth.device_id.product_id=0x1200 \
    bluetooth.device_id.vendor_id=0x038F

# HDR brightness needs a per-display configuration: DisplayDeviceConfig only
# takes its high brightness mode data from display_id_<id>.xml, and
# copyUninitializedValuesFromSecondaryConfig (the default.xml fallback) only
# carries the density mapping. Stock ships one identical reference file for
# seven other display ids; this is that file, for our panel, plus the quirk that
# is required because the SDM backlight path does not advertise
# DisplayCapability::BRIGHTNESS.
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/displayconfig/display_id_4630947002586003091.xml:$(TARGET_COPY_OUT_VENDOR)/etc/displayconfig/display_id_4630947002586003091.xml

# Region properties. Stock keeps these in /system_ext/etc/cust/cn/cust.prop and
# applies them through its customization service; without them MIUICamera decides
# it is running on a global build and loads the global camera configuration,
# while the vendor tuning data here is the China one. That mismatch is what makes
# the SAT multi camera reprocess path compute an output ROI outside the buffer and
# abort the camera provider when the aspect ratio is switched to full screen.
# Verified on stock: app_config_region=CN in the camera's own preferences.
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.miui.region=CN \
    ro.miui.mcc=9460 \
    ro.miui.mnc=9999 \
    ro.miui.cust_variant=cn \
    ro.miui.cust_hardware=V1 \
    ro.vendor.miui.region=CN \
    ro.vendor.miui.mcc=9460 \
    ro.vendor.miui.mnc=9999 \
    ro.vendor.miui.cust_variant=cn \
    ro.vendor.miui.cust_hardware=V1

# Ship the customization data itself as well, so anything that reads the
# directory keeps working.
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/cust/cn/cust.prop:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/cust/cn/cust.prop

# Stub Security Center. MIUICamera asks com.miui.securitycenter to declare its
# privacy permissions on first launch and treats result code 1 as accepted; with
# no such activity the request never completes, mIsNewCTAShowing stays true and
# setupCamera is skipped, leaving the viewfinder frozen.
PRODUCT_PACKAGES += \
    SecurityCenterStub


