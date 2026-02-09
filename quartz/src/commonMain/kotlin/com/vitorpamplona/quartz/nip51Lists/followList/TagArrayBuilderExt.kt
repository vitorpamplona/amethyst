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
package com.vitorpamplona.quartz.nip51Lists.followList

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag

fun TagArrayBuilder<FollowListEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<FollowListEvent>.description(desc: String) = addUnique(DescriptionTag.assemble(desc))

fun TagArrayBuilder<FollowListEvent>.image(imageUrl: String) = addUnique(ImageTag.assemble(imageUrl))

fun TagArrayBuilder<FollowListEvent>.people(peoples: List<UserTag>) = addAll(peoples.map { it.toTagArray() })

fun TagArrayBuilder<FollowListEvent>.person(person: UserTag) = add(person.toTagArray())

fun TagArrayBuilder<FollowListEvent>.person(
    pubkey: HexKey,
    relayHint: NormalizedRelayUrl?,
) = add(UserTag.assemble(pubkey, relayHint))

fun TagArrayBuilder<FollowListEvent>.personFirst(
    pubkey: HexKey,
    relayHint: NormalizedRelayUrl?,
) = addFirst(UserTag.assemble(pubkey, relayHint))

fun TagArrayBuilder<FollowListEvent>.removePerson(pubkey: HexKey) = remove(UserTag.TAG_NAME, pubkey)
