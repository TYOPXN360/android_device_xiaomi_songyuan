#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from device makefile.
$(call inherit-product, device/xiaomi/songyuan/device.mk)

# Inherit the AOSP base telephony product (64-bit only, like lineage_songyuan.mk).
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit the uwuAOSP configuration. The "uwu_" product-name prefix is what
# makes build/make/core/config.mk pull in vendor/uwu/config/BoardConfigUwU.mk.
UWU_DEVICE_TYPE := phone
UWU_SUPPORTS_TELEPHONY := true
$(call inherit-product, vendor/uwu/config/common.mk)

PRODUCT_NAME := uwu_songyuan
PRODUCT_DEVICE := songyuan
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_BRAND := POCO
PRODUCT_MODEL := POCO F9 Ultra

PRODUCT_CHARACTERISTICS := nosdcard

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildFingerprint=POCO/songyuan_eea/songyuan:16/BQ2A.260225.001-BP2A.250705.008/OS3.0.305.0.WGNEUXM:user/release-keys \
    DeviceProduct=songyuan \
    SystemName=songyuan

# MIUI Camera (optional, activates once its blobs are extracted)
ifneq ($(wildcard vendor/xiaomi/songyuan-miuicamera/songyuan-miuicamera-vendor.mk),)
$(call inherit-product, device/xiaomi/songyuan-miuicamera/device.mk)
endif
