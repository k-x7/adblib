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
package com.android.adblib.testing

import com.android.adblib.AdbHostServices
import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceList
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.ForwardSocketList
import com.android.adblib.MdnsCheckResult
import com.android.adblib.MdnsServiceList
import com.android.adblib.PairResult
import com.android.adblib.SocketSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * A fake implementation of [AdbHostServices] for tests.
 */
class FakeAdbHostServices(override val session: AdbLibSession) : AdbHostServices {

    var devicesList = DeviceList(emptyList(), emptyList())
    var devicesTrackingData = listOf<DeviceList>()

    override suspend fun version(): Int = 0

    override suspend fun devices(format: AdbHostServices.DeviceInfoFormat): DeviceList = devicesList

    override fun trackDevices(format: AdbHostServices.DeviceInfoFormat): Flow<DeviceList> =
        devicesTrackingData.asFlow()

    override suspend fun hostFeatures(): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun kill() {
        TODO("Not yet implemented")
    }

    override suspend fun mdnsCheck(): MdnsCheckResult {
        TODO("Not yet implemented")
    }

    override suspend fun mdnsServices(): MdnsServiceList {
        TODO("Not yet implemented")
    }

    override suspend fun pair(deviceAddress: DeviceAddress, pairingCode: String): PairResult {
        TODO("Not yet implemented")
    }

    override suspend fun getState(device: DeviceSelector): DeviceState {
        TODO("Not yet implemented")
    }

    override suspend fun getSerialNo(device: DeviceSelector): String {
        TODO("Not yet implemented")
    }

    override suspend fun getDevPath(device: DeviceSelector): String {
        TODO("Not yet implemented")
    }

    override suspend fun features(device: DeviceSelector): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun listForward(): ForwardSocketList {
        TODO("Not yet implemented")
    }

    override suspend fun forward(
        device: DeviceSelector,
        local: SocketSpec,
        remote: SocketSpec,
        rebind: Boolean
    ): String? {
        TODO("Not yet implemented")
    }

    override suspend fun killForward(device: DeviceSelector, local: SocketSpec) {
        TODO("Not yet implemented")
    }

    override suspend fun killForwardAll(device: DeviceSelector) {
        TODO("Not yet implemented")
    }

}
