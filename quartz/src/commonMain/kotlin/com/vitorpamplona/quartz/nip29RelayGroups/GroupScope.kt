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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/*
 * NIP-29 group scoping via the `h` tag.
 *
 * NIP-29 does not define new event kinds for content: a group chat message is a
 * regular kind-9 [com.vitorpamplona.quartz.nipC7Chats.ChatEvent], a thread is a
 * kind-11 [com.vitorpamplona.quartz.nip7DThreads.ThreadEvent], and reactions,
 * polls, comments and deletions keep their own kinds. What makes any of them
 * "belong" to a group is a single `["h", "<groupId>"]` tag that tells the
 * relay to route and authorize the write. These helpers add and read that tag on
 * ANY event, so the existing carrier classes can be reused unchanged:
 *
 * ```
 * val msg = ChatEvent.build("gm") { hTag(groupId) }          // kind 9 in a group
 * val react = ReactionEvent.build("🔥", target) { hTag(gid) } // reaction in a group
 * val gid = someEvent.groupId()                               // which group is this in?
 * ```
 */

/** Adds the `["h", groupId]` NIP-29 group tag (deduplicated). */
fun <T : Event> TagArrayBuilder<T>.hTag(groupId: String) = addUnique(GroupIdTag.assemble(groupId))

/** The group id (`h` tag value) that scopes this tag array to a NIP-29 group, or null. */
fun TagArray.hTag(): String? = firstTagValue(GroupIdTag.TAG_NAME)

/** The group id (`h` tag value) that scopes this event to a NIP-29 group, or null. */
fun Event.groupId(): String? = tags.hTag()

/** True when this event carries a NIP-29 group (`h`) tag. */
fun Event.isGroupScoped(): Boolean = groupId() != null

/**
 * True when this group-scoped event is actual room *content* — a message someone
 * posted — rather than metadata *about* content (a reaction, deletion, label,
 * moderation action, …), which carries the same `h` tag but isn't a "message".
 *
 * Only content represents a room's latest message in list views: a kind-7 reaction
 * to my group message is group-scoped too, so without this distinction it would be
 * picked as the group's "last message" and render as a bogus, unopenable row on the
 * Messages tab. Content kinds: 9 ([ChatEvent]), 1068 ([PollEvent]), 11
 * ([ThreadEvent]), 1111 ([CommentEvent]).
 */
fun Event.isGroupChatContent(): Boolean = this is ChatEvent || this is PollEvent || this is ThreadEvent || this is CommentEvent
