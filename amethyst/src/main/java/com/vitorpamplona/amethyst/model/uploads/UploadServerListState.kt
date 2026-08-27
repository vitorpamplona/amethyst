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
package com.vitorpamplona.amethyst.model.uploads

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ORIGINLESS_UPLOAD_TARGET
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Upload-picker contents. Combines the kind-10063 Blossom list with the local
 * Originless node list. Originless never writes a Nostr event; this state is the
 * only place the two lists meet.
 *
 * When Originless uploads are on, the picker is a single [ORIGINLESS_UPLOAD_TARGET]
 * (fan-out to every configured node). When off, the picker is Blossom/NIP-96 only.
 * `ipfs://` fetches still use the Originless node list regardless of this switch.
 */
class UploadServerListState(
    val blossomServers: BlossomServerListState,
    val settings: AccountSettings,
    val scope: CoroutineScope,
) {
    fun mergeServerList(
        blossom: List<String>?,
        originlessUrls: List<String> = settings.originlessServerUrls.value,
        originlessUploadsEnabled: Boolean = settings.originlessUploadsEnabled.value,
    ): List<ServerName> = mergeUploadServerList(blossom, originlessUrls, originlessUploadsEnabled, blossomServers::host)

    val hostNameFlow: StateFlow<List<ServerName>> =
        combine(blossomServers.flow, settings.originlessServerUrls, settings.originlessUploadsEnabled) { blossoms, originlessUrls, enabled ->
            mergeServerList(blossoms, originlessUrls, enabled)
        }.onStart {
            emit(mergeServerList(blossomServers.flow.value, settings.originlessServerUrls.value, settings.originlessUploadsEnabled.value))
        }.onEach { servers ->
            resetTargetOrNull(blossomServers.flow.value, servers, settings.defaultFileServer, settings.originlessUploadsEnabled.value)?.let {
                settings.changeDefaultFileServer(it)
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                mergeServerList(emptyList(), settings.originlessServerUrls.value, settings.originlessUploadsEnabled.value),
            )
}

/**
 * Upload picker contents. Originless uploads replace Blossom/NIP-96 entirely
 * so `ipfs://` pinning is optional; `ipfs://` fetches still use the Originless
 * node list regardless of this switch.
 */
fun mergeUploadServerList(
    blossom: List<String>?,
    originlessUrls: List<String>,
    originlessUploadsEnabled: Boolean,
    host: (String) -> String,
): List<ServerName> {
    if (originlessUploadsEnabled) {
        return if (originlessUrls.isEmpty()) emptyList() else listOf(ORIGINLESS_UPLOAD_TARGET)
    }
    return blossom?.map { ServerName(host(it), it, ServerType.Blossom) }?.ifEmpty { DEFAULT_MEDIA_SERVERS }
        ?: DEFAULT_MEDIA_SERVERS
}

/**
 * Decides whether the persisted default file server must be reset, and to what.
 *
 * Returns the new default server, or `null` when no change should happen.
 *
 * The guard on [rawList] being non-empty is what prevents the startup race: before the user's
 * [com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent] (kind 10063) loads from cache/relay,
 * [rawList] is empty and [merged] is the transient [DEFAULT_MEDIA_SERVERS] fallback. Resetting
 * against that fallback would clobber the locally-saved pick on every launch. Only reset once a
 * real, loaded list is in hand and it no longer contains the current pick (e.g. the user removed
 * it from their list).
 *
 * Originless is not a kind-10063 entry. When the Originless upload switch is on, the default
 * must be [ORIGINLESS_UPLOAD_TARGET]. When the switch is off, an Originless default is snapped
 * back to Blossom — Blossom state itself never sees [ServerType.Originless].
 */
fun resetTargetOrNull(
    rawList: List<String>,
    merged: List<ServerName>,
    current: ServerName,
    originlessUploadsEnabled: Boolean = false,
): ServerName? {
    if (originlessUploadsEnabled) {
        val target = merged.firstOrNull { it.type == ServerType.Originless }
        return if (current == target) null else target
    }
    if (current.type == ServerType.Originless) {
        return merged.firstOrNull { it.type != ServerType.Originless } ?: DEFAULT_MEDIA_SERVERS[0]
    }
    return if (rawList.isNotEmpty() && merged.none { it == current }) {
        merged.firstOrNull { it.type != ServerType.Originless } ?: merged.firstOrNull() ?: DEFAULT_MEDIA_SERVERS[0]
    } else {
        null
    }
}
