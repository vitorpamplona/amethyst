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
package com.vitorpamplona.amethyst.commons.account

import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupBlobStores
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupBlobs
import com.vitorpamplona.amethyst.commons.model.marmot.devices.DeviceGroupSwitch
import com.vitorpamplona.marmot.MarmotIngestDedupStore
import com.vitorpamplona.marmot.client.InMemoryMlsGroupStateStore
import com.vitorpamplona.marmot.groups.MarmotMessageStore
import com.vitorpamplona.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.marmot.mip05PushNotifications.MarmotPushStateStore
import com.vitorpamplona.marmot.protocolCore.MarmotPublishObligationStore
import okio.Path

/**
 * The stores one account keeps at rest in its own directory: today the Marmot (MLS) group state,
 * messages, KeyPackages and the rest of the Marmot bookkeeping. A null store means the account
 * keeps that state in memory only, as [Account][com.vitorpamplona.amethyst.commons.model.Account]
 * already handles.
 */
class AccountStores(
    val mlsGroupStateStore: MlsGroupStateStore?,
    val marmotMessageStore: MarmotMessageStore? = null,
    val marmotKeyPackageStore: KeyPackageBundleStore? = null,
    val marmotPublishObligationStore: MarmotPublishObligationStore? = null,
    val marmotIngestDedupStore: MarmotIngestDedupStore? = null,
    val marmotPushStateStore: MarmotPushStateStore? = null,
    /** Whether Marmot device groups are on for this account; their records go in [marmotLocalBlobs]. */
    val marmotDeviceGroupSwitch: DeviceGroupSwitch? = null,
    val marmotLocalBlobs: DeviceGroupBlobs? = null,
    /** The platform name this device writes in its device-group roster entry (`Android`, `macOS`, `iOS`...). */
    val marmotDevicePlatform: String = "",
) {
    /** The device-group stores, only while this account's switch is on. */
    fun marmotDeviceGroupStores(): DeviceGroupBlobStores? {
        val blobs = marmotLocalBlobs ?: return null
        return if (marmotDeviceGroupSwitch?.isEnabled() == true) DeviceGroupBlobStores(blobs) else null
    }
}

/**
 * Opens an account's [AccountStores] in its directory ([accountDir], created before this runs).
 * [newAccount] is true when this install never held the account: device groups start on for it.
 *
 * Each platform passes its own: Android, the desktop and iOS keep them encrypted in files
 * (`EncryptedFileAccountStores`). [InMemory] keeps the account working while its Marmot groups
 * last only until the app closes.
 */
fun interface AccountStoreFactory {
    fun open(
        accountDir: Path,
        newAccount: Boolean,
    ): AccountStores

    /** Every store in memory: Marmot groups work but do not survive a restart. */
    class InMemory(
        private val devicePlatform: String,
    ) : AccountStoreFactory {
        override fun open(
            accountDir: Path,
            newAccount: Boolean,
        ) = AccountStores(
            mlsGroupStateStore = InMemoryMlsGroupStateStore(),
            marmotDevicePlatform = devicePlatform,
        )
    }
}
