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

import com.vitorpamplona.amethyst.commons.marmot.EncryptedDeviceGroupBlobs
import com.vitorpamplona.amethyst.commons.marmot.EncryptedKeyPackageBundleStore
import com.vitorpamplona.amethyst.commons.marmot.EncryptedMarmotMessageStore
import com.vitorpamplona.amethyst.commons.marmot.EncryptedMlsGroupStateStore
import com.vitorpamplona.amethyst.commons.marmot.EncryptedPublishObligationStore
import com.vitorpamplona.amethyst.commons.marmot.MarmotDeviceGroupSwitch
import com.vitorpamplona.amethyst.commons.marmot.MarmotDevicePlatform
import com.vitorpamplona.amethyst.commons.model.marmot.AndroidIngestDedupStore
import com.vitorpamplona.amethyst.commons.model.marmot.AndroidPushStateStore
import com.vitorpamplona.marmot.client.InMemoryMlsGroupStateStore
import com.vitorpamplona.quartz.utils.Log
import okio.Path

/**
 * The JVM and Android [AccountStoreFactory]: every store encrypted in files in the account's
 * directory. A store that cannot open falls back to memory (or to none), and says so in the log.
 */
object EncryptedFileAccountStores : AccountStoreFactory {
    private const val TAG = "AccountCacheState"

    override fun open(
        accountDir: Path,
        newAccount: Boolean,
    ): AccountStores {
        val dir = accountDir.toFile()

        val mlsStore =
            try {
                Log.d(TAG) { "Initializing EncryptedMlsGroupStateStore at ${dir.absolutePath}" }
                EncryptedMlsGroupStateStore(dir)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to initialize EncryptedMlsGroupStateStore, falling back to in-memory store (Marmot groups will NOT persist across restarts)",
                    e,
                )
                InMemoryMlsGroupStateStore()
            }
        Log.d(TAG) { "Using Marmot store: ${mlsStore::class.simpleName}" }

        val marmotMessageStore =
            try {
                EncryptedMarmotMessageStore(dir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EncryptedMarmotMessageStore (Marmot messages will NOT persist across restarts)", e)
                null
            }

        val marmotKeyPackageStore =
            try {
                EncryptedKeyPackageBundleStore(dir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EncryptedKeyPackageBundleStore (Marmot KeyPackages will NOT persist across restarts)", e)
                null
            }

        val marmotPublishObligationStore =
            try {
                EncryptedPublishObligationStore(dir)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to initialize EncryptedPublishObligationStore " +
                        "(a Marmot commit interrupted mid-publish will NOT be retried after a restart)",
                    e,
                )
                null
            }

        val marmotIngestDedupStore =
            try {
                AndroidIngestDedupStore(dir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AndroidIngestDedupStore (every backdated gift wrap will be re-decided on each sync)", e)
                null
            }

        val marmotPushStateStore =
            try {
                AndroidPushStateStore(dir)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to initialize AndroidPushStateStore " +
                        "(a revoked push token could be resurrected by a relayed token list after a restart)",
                    e,
                )
                null
            }

        return AccountStores(
            mlsGroupStateStore = mlsStore,
            marmotMessageStore = marmotMessageStore,
            marmotKeyPackageStore = marmotKeyPackageStore,
            marmotPublishObligationStore = marmotPublishObligationStore,
            marmotIngestDedupStore = marmotIngestDedupStore,
            marmotPushStateStore = marmotPushStateStore,
            // Marmot device groups stay off unless this account's switch file exists; their records
            // hold the device tag's signing seed and private KeyPackages, so they are encrypted at rest.
            marmotDeviceGroupSwitch = MarmotDeviceGroupSwitch(dir, newAccount),
            marmotLocalBlobs = EncryptedDeviceGroupBlobs(dir),
            marmotDevicePlatform = MarmotDevicePlatform.name(),
        )
    }
}
