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
package com.vitorpamplona.quartz.concord.cord04Roles.pins

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.EditionFold
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * Folds the Control Plane's Pin Lists (CORD-04 §7): one vsk-11 entity per Channel, at
 * `pins_locator(community_id, channel_id)`, gated like any edition by `PIN_MESSAGES` (the owner
 * always passes). An edition at any other coordinate — a list minted for another Community or a
 * Channel that isn't folded here — binds to nothing and is ignored.
 */
object ConcordPinLists {
    /** The Pin List coordinate (hex) of [channelIdHex] in [communityIdHex]. */
    fun coordinate(
        communityIdHex: HexKey,
        channelIdHex: HexKey,
    ): HexKey = ConcordKeyDerivation.pinsCoordinate(communityIdHex.hexToByteArray(), channelIdHex.hexToByteArray()).toHexKey()

    /** Per channel id, the authorized head edition of its Pin List. Channels with no list are absent. */
    fun heads(
        editions: Collection<ControlEdition>,
        authority: AuthorityResolver,
        communityIdHex: HexKey,
        channelIds: Collection<HexKey>,
        floors: Map<String, EntityFloor> = emptyMap(),
    ): Map<HexKey, ControlEdition> {
        if (channelIds.isEmpty()) return emptyMap()
        val channelByCoordinate = channelIds.associateBy { coordinate(communityIdHex, it) }
        val lists = editions.filter { it.entityKind == ControlEntityKind.PIN_LIST && it.entityIdHex in channelByCoordinate }
        if (lists.isEmpty()) return emptyMap()
        return EditionFold
            // Same gate every other entity folds under: well-formed, owner or a PIN_MESSAGES holder, vac satisfied.
            .foldGated(lists, floors, rank = authority::tieBreakRank) { authority.admits(it, ConcordPermissions.PIN_MESSAGES) }
            .mapNotNull { (coordinate, head) -> channelByCoordinate[coordinate]?.let { it to head } }
            .toMap()
    }
}
