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
package com.vitorpamplona.quartz.marmot.groups

import com.vitorpamplona.quartz.marmot.appComponents.MarmotGroupState
import com.vitorpamplona.quartz.marmot.appComponents.agentTextStream.AgentTextStreamCrypto
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.HexKey

// Marmot's reads of an MlsGroup's GroupContext.
//
// These were methods on MlsGroup itself, which put MIP-01 extension parsing and
// Nostr routing ids inside an RFC 9420 engine. None of them needs the group's
// internals — every one goes through the public extensions / exporterSecret /
// view() surface — so they live here as extensions, and the engine no longer
// knows Marmot exists.

/** Parsed Marmot Group Data Extension from the current GroupContext, or null. */
fun MlsGroup.currentMarmotData(): MarmotGroupData? = MarmotGroupData.fromExtensions(extensions)

/** The current profile's component view of this GroupContext. */
fun MlsGroup.currentGroupState(): MarmotGroupState = MarmotGroupState.fromExtensions(extensions)

/**
 * The `nostr_group_id` this group routes kind-445 traffic under, from
 * whichever profile the group is actually using.
 *
 * A current-profile group carries it in the `marmot.transport.nostr.routing.v1`
 * component (`0x8004`); a legacy group carries it inside the monolithic
 * `0xF2EE` extension. Reading only the legacy one leaves us unable to join
 * any group a current-profile client created — the routing id is required
 * to subscribe at all, so the failure is total rather than partial.
 */
fun MlsGroup.currentNostrGroupId(): HexKey? =
    currentGroupState().routing?.nostrGroupIdHex
        ?: currentMarmotData()?.nostrGroupId

/**
 * The group's configured admin account identities, as lowercase hex.
 *
 * Empty means the group names no admins at all, which happens during
 * bootstrap and in groups that carry neither carrier.
 */
fun MlsGroup.currentAdminIdentities(): Set<String> = MarmotGroupPolicy.adminIdentitiesIn(extensions)

/** True if the local member is an active admin. */
fun MlsGroup.isLocalAdmin(): Boolean = MarmotGroupPolicy.isLocalAdmin(view())

/**
 * `MLS-Exporter("marmot", "agent-text-stream-quic", 32)` — the secret every
 * member of this epoch derives per-stream record keys from. Per-stream and
 * per-record separation is entirely in the HKDF key context, so this one
 * secret covers every stream in the epoch.
 */
fun MlsGroup.agentTextStreamSecret(): ByteArray =
    exporterSecret(
        AgentTextStreamCrypto.EXPORTER_LABEL,
        AgentTextStreamCrypto.EXPORTER_CONTEXT,
        AgentTextStreamCrypto.SECRET_LENGTH,
    )
