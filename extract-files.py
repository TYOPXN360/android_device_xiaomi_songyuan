#!/usr/bin/env -S PYTHONPATH=../../../tools/extract-utils python3
#
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

import extract_utils.tools
from extract_utils.fixups_blob import (
    BlobFixupCtx,
    File,
    blob_fixup,
    blob_fixups_user_type,
)
from extract_utils.fixups_lib import (
    lib_fixups,
    lib_fixups_user_type,
)
from extract_utils.main import (
    ExtractUtils,
    ExtractUtilsModule,
)
from extract_utils.tools import (
    llvm_objdump_path,
)
from extract_utils.utils import (
    run_cmd,
)

namespace_imports = [
    'device/xiaomi/sm8850-common',
    'hardware/qcom-caf/sm8850',
    'hardware/xiaomi',
    'vendor/qcom/opensource/commonsys-intf/display',
    'vendor/xiaomi/sm8850-common',
]

lib_fixups: lib_fixups_user_type = {
    **lib_fixups,
}

def blob_fixup_gme_ois_read(
    ctx: BlobFixupCtx,
    file: File,
    file_path: str,
    *args,
    **kwargs,
):
    with open(file_path, 'rb+') as f:
        content = f.read()
        target_bytes = b'\x5a\x78\x61\x0e\x7a\x02\x80\x3d\x03\x0c\x40\xfd'
        patched_bytes = b'\x5a\x78\x61\x0e\x7a\x02\x80\x3d\x03\x08\x40\xfd'
        if target_bytes in content:
            content = content.replace(target_bytes, patched_bytes)
            f.seek(0)
            f.write(content)
            f.truncate()

def blob_fixup_miface_weak_nonsecure(
    ctx: BlobFixupCtx,
    file: File,
    file_path: str,
    *args,
    **kwargs,
):
    # Use the non-secure camera and report WEAK strength
    patches = [
        (0x4C100, b'\x5a\x65\x41\x39', b'\xfa\x03\x1f\x2a'),  # mov w26, wzr
        (0x4C818, b'\x33\x65\x41\x39', b'\xf3\x03\x1f\x2a'),  # mov w19, wzr
        (0x75F44, b'\xea\x13\x40\x39', b'\x2a\x00\x80\x52'),  # mov w10, #1
    ]
    with open(file_path, 'rb+') as f:
        content = bytearray(f.read())
        if all(content[o:o + 4] == old for o, old, _ in patches):
            for o, _, new in patches:
                content[o:o + 4] = new
            f.seek(0)
            f.write(content)

blob_fixups: blob_fixups_user_type = {
    (
        'odm/etc/camera/enhance_motiontuning.xml',
        'odm/etc/camera/motiontuning.xml',
        'odm/etc/camera/snsc_bokeh_motiontuning.xml',
        'odm/etc/camera/snsc_enhance_motiontuning.xml',
        'odm/etc/camera/snsc_noface_motiontuning.xml',
        'odm/etc/camera/snsc_motiontuning.xml'
    ): blob_fixup()
        .regex_replace('xml=version', 'xml version'),
    (
        'vendor/lib64/libcameraopt.so',
    ): blob_fixup()
        .add_needed('libprocessgroup_shim.so'),
    (
        'odm/lib64/libMiEmojiEffect.so',
        'odm/lib64/libMiVideoFilter.so',
        'odm/lib64/libAncHumanPreviewBokeh.so',
        'odm/lib64/libarcsoft_beautyshot.so',
        'odm/lib64/libwa_widelens_undistort.so',
        'vendor/lib64/libMiPhotoFilter.so'
    ): blob_fixup()
        .clear_symbol_version('AHardwareBuffer_allocate')
        .clear_symbol_version('AHardwareBuffer_describe')
        .clear_symbol_version('AHardwareBuffer_lockPlanes')
        .clear_symbol_version('AHardwareBuffer_release')
        .clear_symbol_version('AHardwareBuffer_unlock')
        .clear_symbol_version('AHardwareBuffer_lock')
        .clear_symbol_version('AHardwareBuffer_isSupported'),
    (
       'odm/lib64/camera/components/com.qti.node.dewarp.so',
       'odm/lib64/hw/com.qti.chi.override.so',
       'odm/lib64/libcamximageformatutils.so',
       'odm/lib64/libchifeature2.so',
       'odm/lib64/vendor.qti.hardware.camera.offlinecamera-service-impl.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.graphics.allocator-V1-ndk.so',
            'android.hardware.graphics.allocator-V2-ndk.so'
        ),
    (
       'vendor/lib64/vendor.xiaomi.hardware.camera.injection-V1-ndk.so',
       'vendor/lib64/vendor.xiaomi.hardware.camera.injection-client.so',
       'vendor/lib64/vendor.xiaomi.hardware.camera.injection-service.so',
    ): blob_fixup()
        .replace_needed(
            'android.hardware.camera.device-V1-ndk.so',
            'android.hardware.camera.device-V2-ndk.so'
        ),
    (
        'odm/lib64/camera/plugins/com.xiaomi.plugin.losslessjpeg.so'
    ): blob_fixup()
        .replace_needed(
            'libdng_sdk.so',
            'libdng_sdk-songyuan.so'
        ),
    (
       'vendor/lib64/camera/components/com.qti.node.gme.so',
    ): blob_fixup()
        .call(blob_fixup_gme_ois_read),
    'odm/lib64/libmiface.so': blob_fixup()
        .call(blob_fixup_miface_weak_nonsecure),
}  # fmt: skip

module = ExtractUtilsModule(
    'songyuan',
    'xiaomi',
    blob_fixups=blob_fixups,
    lib_fixups=lib_fixups,
    namespace_imports=namespace_imports,
)

if __name__ == '__main__':
    utils = ExtractUtils.device_with_common(
        module, 'sm8850-common', module.vendor
    )
    utils.run()