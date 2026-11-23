/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.marmot.features.accountSync.AccountSyncState
import com.vitorpamplona.marmot.features.accountSync.AccountSyncStore
import com.vitorpamplona.marmot.features.deviceGroup.DeviceGroupTrust
import com.vitorpamplona.marmot.features.deviceGroup.DeviceGroupTrustStore
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupConfig
import com.vitorpamplona.marmot.features.deviceGroup.PrivateKeyPackageStore
import com.vitorpamplona.marmot.features.deviceGroup.PrivateKeyPackages

/**
 * Where a front end keeps the device-group records as opaque blobs: the Android/desktop app
 * encrypts each one at rest (`EncryptedDeviceGroupBlobs`), `amy` writes owner-only files. The
 * bytes are marmot-kmp's versioned encodings and they hold secrets (the device tag's signing
 * seed, private KeyPackages), so an implementation must never write them in the clear to shared
 * storage.
 */
interface DeviceGroupBlobs {
    suspend fun load(name: String): ByteArray?

    suspend fun save(
        name: String,
        bytes: ByteArray,
    )
}

/** marmot-kmp's three device-group stores over [blobs], one blob each. */
class DeviceGroupBlobStores(
    val blobs: DeviceGroupBlobs,
) {
    val trust: DeviceGroupTrustStore =
        object : DeviceGroupTrustStore {
            override suspend fun load(): DeviceGroupTrust? = blobs.load(TRUST)?.let { DeviceGroupTrust.decode(it) }

            override suspend fun save(trust: DeviceGroupTrust) = blobs.save(TRUST, trust.encode())
        }

    val privateKeyPackages: PrivateKeyPackageStore =
        object : PrivateKeyPackageStore {
            override suspend fun load(): PrivateKeyPackages? = blobs.load(PRIVATE_KEY_PACKAGES)?.let { PrivateKeyPackages.decode(it) }

            override suspend fun save(keyPackages: PrivateKeyPackages) = blobs.save(PRIVATE_KEY_PACKAGES, keyPackages.encode())
        }

    val accountSync: AccountSyncStore =
        object : AccountSyncStore {
            override suspend fun load(): AccountSyncState? = blobs.load(ACCOUNT_SYNC)?.let { AccountSyncState.decode(it) }

            override suspend fun save(state: AccountSyncState) = blobs.save(ACCOUNT_SYNC, state.encode())
        }

    /** The config a `MarmotManager` takes; [relays] are where a device group this device creates lives. */
    fun config(relays: List<String>) = MarmotDeviceGroupConfig(trust, privateKeyPackages, relays, accountSync)

    companion object {
        const val TRUST = "device_group_trust"
        const val PRIVATE_KEY_PACKAGES = "private_key_packages"
        const val ACCOUNT_SYNC = "account_sync"
    }
}
