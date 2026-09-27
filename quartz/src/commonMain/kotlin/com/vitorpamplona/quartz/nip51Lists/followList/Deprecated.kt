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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.quartz.nip51Lists.followList

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.description as movedDescription
import com.vitorpamplona.quartz.nip51Lists.starterPack.followIdSet as movedFollowIdSet
import com.vitorpamplona.quartz.nip51Lists.starterPack.followIds as movedFollowIds
import com.vitorpamplona.quartz.nip51Lists.starterPack.follows as movedFollows
import com.vitorpamplona.quartz.nip51Lists.starterPack.image as movedImage
import com.vitorpamplona.quartz.nip51Lists.starterPack.people as movedPeople
import com.vitorpamplona.quartz.nip51Lists.starterPack.person as movedPerson
import com.vitorpamplona.quartz.nip51Lists.starterPack.personFirst as movedPersonFirst
import com.vitorpamplona.quartz.nip51Lists.starterPack.removePerson as movedRemovePerson
import com.vitorpamplona.quartz.nip51Lists.starterPack.title as movedTitle

// This package was renamed to com.vitorpamplona.quartz.nip51Lists.starterPack (NIP-51 kind 39089 is a starter pack).
// Everything below forwards to it and will be removed in a future release.

private const val MOVED = "Moved to com.vitorpamplona.quartz.nip51Lists.starterPack"

@Deprecated(
    "Renamed to StarterPackEvent and moved to com.vitorpamplona.quartz.nip51Lists.starterPack. NIP-51 kind 39089 is a starter pack.",
    ReplaceWith("StarterPackEvent", "com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent"),
)
typealias FollowListEvent = StarterPackEvent

@Deprecated(MOVED, ReplaceWith("title(title)", "com.vitorpamplona.quartz.nip51Lists.starterPack.title"))
fun TagArrayBuilder<StarterPackEvent>.title(title: String) = movedTitle(title)

@Deprecated(MOVED, ReplaceWith("description(desc)", "com.vitorpamplona.quartz.nip51Lists.starterPack.description"))
fun TagArrayBuilder<StarterPackEvent>.description(desc: String) = movedDescription(desc)

@Deprecated(MOVED, ReplaceWith("image(imageUrl)", "com.vitorpamplona.quartz.nip51Lists.starterPack.image"))
fun TagArrayBuilder<StarterPackEvent>.image(imageUrl: String) = movedImage(imageUrl)

@Deprecated(MOVED, ReplaceWith("people(peoples)", "com.vitorpamplona.quartz.nip51Lists.starterPack.people"))
fun TagArrayBuilder<StarterPackEvent>.people(peoples: List<UserTag>) = movedPeople(peoples)

@Deprecated(MOVED, ReplaceWith("person(person)", "com.vitorpamplona.quartz.nip51Lists.starterPack.person"))
fun TagArrayBuilder<StarterPackEvent>.person(person: UserTag) = movedPerson(person)

@Deprecated(MOVED, ReplaceWith("person(pubkey, relayHint)", "com.vitorpamplona.quartz.nip51Lists.starterPack.person"))
fun TagArrayBuilder<StarterPackEvent>.person(
    pubkey: HexKey,
    relayHint: NormalizedRelayUrl?,
) = movedPerson(pubkey, relayHint)

@Deprecated(MOVED, ReplaceWith("personFirst(pubkey, relayHint)", "com.vitorpamplona.quartz.nip51Lists.starterPack.personFirst"))
fun TagArrayBuilder<StarterPackEvent>.personFirst(
    pubkey: HexKey,
    relayHint: NormalizedRelayUrl?,
) = movedPersonFirst(pubkey, relayHint)

@Deprecated(MOVED, ReplaceWith("removePerson(pubkey)", "com.vitorpamplona.quartz.nip51Lists.starterPack.removePerson"))
fun TagArrayBuilder<StarterPackEvent>.removePerson(pubkey: HexKey) = movedRemovePerson(pubkey)

@Deprecated(MOVED, ReplaceWith("follows()", "com.vitorpamplona.quartz.nip51Lists.starterPack.follows"))
fun TagArray.follows() = movedFollows()

@Deprecated(MOVED, ReplaceWith("followIds()", "com.vitorpamplona.quartz.nip51Lists.starterPack.followIds"))
fun TagArray.followIds() = movedFollowIds()

@Deprecated(MOVED, ReplaceWith("followIdSet()", "com.vitorpamplona.quartz.nip51Lists.starterPack.followIdSet"))
fun TagArray.followIdSet() = movedFollowIdSet()
