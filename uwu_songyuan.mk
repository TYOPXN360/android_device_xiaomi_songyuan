#
# Copyright (C) 2024 The Android Open Source Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Device capabilities must be declared before vendor/uwu/config/common.mk.
UWU_DEVICE_TYPE := phone
UWU_SUPPORTS_TELEPHONY := true

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit some common Lineage stuff.
$(call inherit-product, vendor/uwu/config/common.mk)

# Inherit from songyuan device.
$(call inherit-product, device/xiaomi/songyuan/device.mk)

# MIUI Camera. Activates once its blobs are extracted:
#   device/xiaomi/songyuan-miuicamera/extract-files.py <stock dump>
ifneq ($(wildcard vendor/xiaomi/songyuan-miuicamera/songyuan-miuicamera-vendor.mk),)
$(call inherit-product, device/xiaomi/songyuan-miuicamera/device.mk)
endif

## Device identifier
PRODUCT_DEVICE := songyuan
PRODUCT_NAME := uwu_songyuan
PRODUCT_BRAND := Redmi
PRODUCT_MODEL := Redmi K100 Pro Max
PRODUCT_MANUFACTURER := Xiaomi

# Songyuan uses the ST54L NFC controller. Remove NXP NFC packages and copy
# rules after all inherited products have been evaluated.
PRODUCT_PACKAGES := $(filter-out android.hardware.nfc2-service.nxp android.hardware.nqnfc-service.nxp nqnfcinfo nfc2-service-nxp.xml,$(PRODUCT_PACKAGES))
PRODUCT_COPY_FILES := $(filter-out vendor/xiaomi/sm8850-common/proprietary/odm/etc/init/nfc2-service-nxp.rc:odm/etc/init/nfc2-service-nxp.rc,$(PRODUCT_COPY_FILES))
