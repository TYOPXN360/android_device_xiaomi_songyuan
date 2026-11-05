#
# Copyright (C) 2026 The Android Open Source Project
#
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/xiaomi/songyuan
KERNEL_PATH := $(DEVICE_PATH)-kernel
KERNEL_DIAGNOSTIC_FINGERPRINT := songyuan:$(PLATFORM_VERSION)/$(BUILD_ID):$(TARGET_BUILD_VARIANT)

# Inherit from sm8850-common
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

# Prebuilt Kernel
TARGET_NO_KERNEL_OVERRIDE := true
TARGET_PREBUILT_KERNEL := $(KERNEL_PATH)/kernel

# Kernel headers
TARGET_BOARD_KERNEL_HEADERS := device/xiaomi/songyuan-kernel/kernel-headers
# Kernel modules for vendor_boot ramdisk (开机与进入 Recovery 的核心驱动)
BOARD_VENDOR_RAMDISK_KERNEL_MODULES := $(wildcard $(KERNEL_PATH)/vendor_ramdisk/*.ko)
BOARD_VENDOR_RAMDISK_KERNEL_MODULES_LOAD := $(strip $(shell cat $(KERNEL_PATH)/vendor_ramdisk/modules.load 2>/dev/null))
BOARD_VENDOR_RAMDISK_RECOVERY_KERNEL_MODULES_LOAD := $(strip $(shell cat $(KERNEL_PATH)/vendor_ramdisk/modules.load.recovery 2>/dev/null))

TARGET_ODM_PROP += $(DEVICE_PATH)/configs/properties/odm.prop
TARGET_VENDOR_PROP += $(DEVICE_PATH)/configs/properties/vendor.prop

# Partitions
BOARD_SUPER_PARTITION_SIZE := 16642998272
BOARD_QTI_DYNAMIC_PARTITIONS_SIZE := 16632512512

# Security
BOOT_SECURITY_PATCH := 2026-08-01
VENDOR_SECURITY_PATCH := $(BOOT_SECURITY_PATCH)

# Inherit from the proprietary version
include vendor/xiaomi/songyuan/BoardConfigVendor.mk

# MIUI Camera
ifneq ($(wildcard vendor/xiaomi/songyuan-miuicamera/BoardConfigVendor.mk),)
include device/xiaomi/songyuan-miuicamera/BoardConfig.mk
endif