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
import com.vitorpamplona.amethyst.model.nip51Lists.originlessServers.OriginlessServersListState
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
 * Upload-picker contents. Combines the kind-10063 Blossom list with the
 * kind-10062 Originless node list. Originless never writes public `server`
 * tags; the encrypted event is the only source of those URLs.
 *
 * Configuring at least one Originless node adds [ORIGINLESS_UPLOAD_TARGET] to the
 * picker alongside Blossom/NIP-96 rather than replacing them, so a user who keeps
 * both picks per upload. Blossom stays the default: `ipfs://` is not resolvable by
 * clients without an Originless node list, so pinning there is an explicit choice.
 * `ipfs://` fetches use the node list either way.
 */
class UploadServerListState(
    val blossomServers: BlossomServerListState,
    val originlessServers: OriginlessServersListState,
    val settings: AccountSettings,
    val scope: CoroutineScope,
) {
    fun mergeServerList(
        blossom: List<String>?,
        originlessUrls: List<String> = originlessServers.flow.value,
    ): List<ServerName> = mergeUploadServerList(blossom, originlessUrls, blossomServers::host)

    val hostNameFlow: StateFlow<List<ServerName>> =
        combine(blossomServers.flow, originlessServers.flow) { blossoms, originlessUrls ->
            mergeServerList(blossoms, originlessUrls)
        }.onStart {
            emit(mergeServerList(blossomServers.flow.value, originlessServers.flow.value))
        }.onEach { servers ->
            resetTargetOrNull(blossomServers.flow.value, servers, settings.defaultFileServer)?.let {
                settings.changeDefaultFileServer(it)
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                mergeServerList(emptyList(), originlessServers.flow.value),
            )
}

/**
 * Upload picker contents: the user's Blossom/NIP-96 servers, plus a single
 * [ORIGINLESS_UPLOAD_TARGET] (fan-out to every node) once any Originless node is
 * configured. Neither hides the other; `ipfs://` fetches use the node list no matter
 * what is picked here.
 */
fun mergeUploadServerList(
    blossom: List<String>?,
    originlessUrls: List<String>,
    host: (String) -> String,
): List<ServerName> {
    val blossoms =
        blossom?.map { ServerName(host(it), it, ServerType.Blossom) }?.ifEmpty { DEFAULT_MEDIA_SERVERS }
            ?: DEFAULT_MEDIA_SERVERS
    return if (originlessUrls.isEmpty()) blossoms else blossoms + ORIGINLESS_UPLOAD_TARGET
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
 * An Originless pick is left alone here, for the same reason: kind 10062 loads on its own
 * schedule, so an empty node list is indistinguishable from "not loaded yet". Emptying the list is
 * a deliberate act, so the snap back to Blossom lives there instead --
 * [com.vitorpamplona.amethyst.model.Account.sendOriginlessServersList]. A legacy per-node pick is
 * still migrated onto the fan-out target once the list is in hand.
 *
 * A reset never lands on Originless: `ipfs://` uploads stay an explicit choice.
 */
fun resetTargetOrNull(
    rawList: List<String>,
    merged: List<ServerName>,
    current: ServerName,
): ServerName? {
    if (current.type == ServerType.Originless) {
        val target = merged.firstOrNull { it.type == ServerType.Originless }
        return if (target == null || target == current) null else target
    }
    return if (rawList.isNotEmpty() && merged.none { it == current }) {
        merged.firstOrNull { it.type != ServerType.Originless } ?: DEFAULT_MEDIA_SERVERS[0]
    } else {
        null
    }
}
