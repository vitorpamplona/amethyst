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
package com.vitorpamplona.quartz.buzz.huddles.reaction

import com.vitorpamplona.quartz.buzz.huddles.HuddleReactionEvent
import com.vitorpamplona.quartz.buzz.huddles.reaction.tags.ReactionTag
import com.vitorpamplona.quartz.buzz.huddles.reaction.tags.SenderNameTag
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

fun TagArrayBuilder<HuddleReactionEvent>.reactionChannel(channelId: String) = addUnique(GroupIdTag.assemble(channelId))

fun TagArrayBuilder<HuddleReactionEvent>.reaction(emoji: String) = addUnique(ReactionTag.assemble(emoji))

fun TagArrayBuilder<HuddleReactionEvent>.senderName(senderName: String) = addUnique(SenderNameTag.assemble(senderName))

fun TagArrayBuilder<HuddleReactionEvent>.customEmoji(
    shortcode: String,
    url: String,
) = add(EmojiUrlTag(shortcode, url).toTagArray())
