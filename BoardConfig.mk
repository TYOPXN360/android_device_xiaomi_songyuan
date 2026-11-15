#
# Copyright (C) 2026 The Android Open Source Project
#
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/xiaomi/songyuan
KERNEL_PATH := $(DEVICE_PATH)-kernel

KERNEL_RELEASE := 6.12.69-android16-6-g0a1dc0413ec7-ab16271006-4k

# Inherit from sm8650-common
include device/xiaomi/sm8850-common/BoardConfigCommon.mk

# NFC (ST54L). Our own manifest, not the stock odm one: that also declares
# ISecureElement/eSE1, which manifest_canoe.xml already declares and
# secure_element-service.qti already provides.
ODM_MANIFEST_FILES := \
    $(DEVICE_PATH)/configs/vintf/android.hardware.contexthub-service.qmi.xml \
    $(DEVICE_PATH)/configs/vintf/nfc-service-st.xml

# Display
TARGET_SCREEN_DENSITY := 420

# Dtb/o
BOARD_PREBUILT_DTBOIMAGE := $(KERNEL_PATH)/dtbo.img
BOARD_PREBUILT_DTBIMAGE_DIR := $(KERNEL_PATH)/dtb

TARGET_NO_KERNEL_OVERRIDE := true

# Kernel headers: no kernel source is published for songyuan, so the only copy
# of the QTI UAPI headers is the tarball stock shipped. In-tree code needs them
# (ipacm and libipanat include <linux/msm_ipa.h>, the display HALs include the
# sde and msm_drm_pp ones), so unpack them and point the native kernel-header
# path at the result.
#
# TARGET_BOARD_KERNEL_HEADERS is the makefile-side knob for the built-in
# device_kernel_headers module: config.mk validates the directory, soong_config.mk
# writes it into soong.variables as DeviceKernelHeaders, and cc/kernel_headers.go
# reexports it as a system include dir. Every module that already lists
# device_kernel_headers picks it up with no change of its own, which is exactly
# what ipacm needs.
#
# The directory layout has to mirror the kernel include/ tree, which is why it
# holds linux/ and display/ directly. config.mk also validates that the
# directory name ends in kernel-headers, so the name is not free-form.
TARGET_BOARD_KERNEL_HEADERS := $(KERNEL_PATH)/kernel-headers

# The prebuilt_kernel_includes genrule in vendor/uwu/build/soong still takes the
# tarball as its input, so this assignment has to stay or that genrule expands
# to "gzip -d < " with nothing after the redirect and the build dies on a shell
# syntax error. It is not what makes ipacm find msm_ipa.h - TARGET_BOARD_KERNEL_HEADERS
# above does that - but the genrule has no other source for the file.
#
# Note this one is only read at makefile level. cc.go reads the same name with
# Getenv to decide whether to swap device_kernel_headers for
# prebuilt_kernel_headers, and a makefile assignment cannot satisfy that; that
# is why the header lookup goes through TARGET_BOARD_KERNEL_HEADERS instead.
TARGET_PREBUILT_KERNEL_HEADERS := $(KERNEL_PATH)/kernel-headers.tar.gz

PRODUCT_COPY_FILES += \
	$(KERNEL_PATH)/kernel:kernel

# Kernel modules
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_LOAD := $(strip $(shell cat $(KERNEL_PATH)/vendor_ramdisk/modules.load))
BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD := $(strip $(shell cat $(KERNEL_PATH)/vendor_ramdisk/modules.load.recovery))
BOARD_VENDOR_KERNEL_MODULES_LOAD := $(strip $(shell cat $(KERNEL_PATH)/vendor_dlkm/modules.load))

PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(KERNEL_PATH)/vendor_dlkm/,$(TARGET_COPY_OUT_VENDOR_DLKM)/lib/modules) \
    $(call find-copy-subdir-files,*,$(KERNEL_PATH)/vendor_ramdisk/,$(TARGET_COPY_OUT_VENDOR_RAMDISK)/lib/modules) \
    $(call find-copy-subdir-files,*,$(KERNEL_PATH)/system_dlkm_flatten/,$(TARGET_COPY_OUT_SYSTEM_DLKM)/flatten/lib/modules) \
    $(call find-copy-subdir-files,*,$(KERNEL_PATH)/system_dlkm/,$(TARGET_COPY_OUT_SYSTEM_DLKM)/lib/modules/$(KERNEL_RELEASE))

TARGET_ODM_PROP += $(DEVICE_PATH)/configs/properties/odm.prop
TARGET_VENDOR_PROP += $(DEVICE_PATH)/configs/properties/vendor.prop

# Partitions
BOARD_SUPER_PARTITION_SIZE := 16642998272
BOARD_QTI_DYNAMIC_PARTITIONS_SIZE := 16632512512

# Sepolicy for the pieces that only exist on songyuan: the AW21024 rear light
# and the Novatek visual display processor.
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/public
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/private
BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/vendor

# Security
# Must be >= the bootloader's security-patch-level, or it refuses the boot chain# and the device bounces back to fastboot right after the MI logo (no vibration:
# init never runs). Check the floor with:
#   fastboot getvar security-patch-level   -> 2026-10-01 on this device
# This value is written into the boot image as
#   com.android.build.boot.security_patch
# and the stock OS4.0.5.0 boot.img carries exactly 2026-10-01.
BOOT_SECURITY_PATCH := 2026-10-01
VENDOR_SECURITY_PATCH := $(BOOT_SECURITY_PATCH)

# Inherit from the proprietary version
include vendor/xiaomi/songyuan/BoardConfigVendor.mk

# MIUI Camera
ifneq ($(wildcard vendor/xiaomi/songyuan-miuicamera/BoardConfigVendor.mk),)
include device/xiaomi/songyuan-miuicamera/BoardConfig.mk
endif
