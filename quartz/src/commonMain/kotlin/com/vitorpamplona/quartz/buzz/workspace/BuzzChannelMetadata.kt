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
package com.vitorpamplona.quartz.buzz.workspace

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/*
 * Buzz-specific readers over a NIP-29 group's relay-signed metadata (kind:39000).
 *
 * Buzz relays enrich the standard metadata so clients can classify a channel without a
 * separate fetch: a single `t` tag carries the channel type ("stream" / "forum" / "dm" — note
 * this collides with NIP-29's use of `t` for topic hashtags, so on a Buzz relay
 * GroupMetadataEvent.hashtags is really the channel type), and a DM's participants are inlined
 * as `p` tags on the 39000 itself. Ground truth: buzz-relay/src/handlers/side_effects.rs
 * (emit_group_discovery_events).
 */

/** The Buzz channel type from the relay's `t` tag ("stream" / "forum" / "dm"), or null. */
fun GroupMetadataEvent.buzzChannelType(): String? = tags.firstTagValue("t")

/** True when the relay marks this channel a DM (`t` = "dm"). */
fun GroupMetadataEvent.isBuzzDm(): Boolean = buzzChannelType() == BUZZ_CHANNEL_TYPE_DM

/** True when the relay marks this channel a forum (`t` = "forum"). */
fun GroupMetadataEvent.isBuzzForum(): Boolean = buzzChannelType() == BUZZ_CHANNEL_TYPE_FORUM

/** The DM participant pubkeys inlined as `p` tags on a Buzz DM's 39000 (empty for non-DMs). */
fun GroupMetadataEvent.buzzParticipants(): List<HexKey> = tags.mapNotNull(PTag::parseKey)

const val BUZZ_CHANNEL_TYPE_DM = "dm"
const val BUZZ_CHANNEL_TYPE_FORUM = "forum"
const val BUZZ_CHANNEL_TYPE_STREAM = "stream"
