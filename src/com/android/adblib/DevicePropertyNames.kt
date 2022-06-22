/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib

/** Names of commonly used device properties.  */
object DevicePropertyNames {
    const val RO_BUILD_VERSION_RELEASE = "ro.build.version.release"
    const val RO_BUILD_VERSION_SDK = "ro.build.version.sdk"
    const val RO_KERNEL_QEMU_AVD_NAME = "ro.kernel.qemu.avd_name"
    const val RO_PRODUCT_CPU_ABI = "ro.product.cpu.abi"
    const val RO_PRODUCT_MANUFACTURER = "ro.product.manufacturer"
    const val RO_PRODUCT_MODEL = "ro.product.model"
}
