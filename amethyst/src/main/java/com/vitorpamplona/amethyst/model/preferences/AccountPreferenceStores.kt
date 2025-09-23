/**
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
package com.vitorpamplona.amethyst.model.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.quartz.utils.cache.LargeCache
import java.io.File

class AccountPreferenceStores(
    val rootFilesDir: () -> File,
) {
    companion object {
        val defaultHomeFollowList = stringPreferencesKey("defaultHomeFollowList")
        val defaultStoriesFollowList = stringPreferencesKey("defaultStoriesFollowList")
        val defaultNotificationFollowList = stringPreferencesKey("defaultNotificationFollowList")
        val defaultDiscoveryFollowList = stringPreferencesKey("defaultDiscoveryFollowList")

        val localRelayServers = stringPreferencesKey("localRelayServers")
        val defaultFileServer = stringPreferencesKey("defaultFileServer")

        val latestUserMetadata = stringPreferencesKey("latestUserMetadata")
        val latestContactList = stringPreferencesKey("latestContactList")
        val latestDMRelayList = stringPreferencesKey("latestDMRelayList")
        val latestNIP65RelayList = stringPreferencesKey("latestNIP65RelayList")
        val latestSearchRelayList = stringPreferencesKey("latestSearchRelayList")
        val latestBlockedRelayList = stringPreferencesKey("latestBlockedRelayList")
        val latestTrustedRelayList = stringPreferencesKey("latestTrustedRelayList")
        val latestMuteList = stringPreferencesKey("latestMuteList")
        val latestPrivateHomeRelayList = stringPreferencesKey("latestPrivateHomeRelayList")
        val latestAppSpecificData = stringPreferencesKey("latestAppSpecificData")
        val latestChannelList = stringPreferencesKey("latestChannelList")
        val latestCommunityList = stringPreferencesKey("latestCommunityList")
        val latestHashtagList = stringPreferencesKey("latestHashtagList")
        val latestGeohashList = stringPreferencesKey("latestGeohashList")
        val latestEphemeralChatList = stringPreferencesKey("latestEphemeralChatList")

        val hideDeleteRequestDialog = stringPreferencesKey("hideDeleteRequestDialog")
        val hideBlockAlertDialog = stringPreferencesKey("hideBlockAlertDialog")
        val hideNip17WarningDialog = stringPreferencesKey("hideNip17WarningDialog")

        val torSettings = stringPreferencesKey("tor_settings")

        val hasDonatedInVersion = stringPreferencesKey("hasDonatedInVersion")
    }

    private val storeCache = LargeCache<String, DataStore<Preferences>>()

    fun file(npub: String) = File(rootFilesDir(), "datastore/$npub.preferences")

    private fun getDataStore(npub: String): DataStore<Preferences> =
        storeCache.getOrCreate(npub) {
            PreferenceDataStoreFactory.create(
                produceFile = { file(npub) },
            )
        }

    fun removeAccount(npub: String): Boolean {
        val deleted = file(npub).delete()
        storeCache.remove(npub)
        return deleted
    }
}
