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

import com.vitorpamplona.quartz.buzz.huddles.reaction.tags.ReactionTag
import com.vitorpamplona.quartz.buzz.huddles.reaction.tags.SenderNameTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

/** The ephemeral huddle channel the reaction targets — the `h` tag. */
fun TagArray.reactionChannel() = firstNotNullOfOrNull(GroupIdTag::parse)

/** The emoji from the `reaction` tag. */
fun TagArray.reaction() = firstNotNullOfOrNull(ReactionTag::parse)

/** The sender display name from the `sender_name` tag. */
fun TagArray.reactionSenderName() = firstNotNullOfOrNull(SenderNameTag::parse)

/** The NIP-30 custom emoji descriptors (`["emoji", shortcode, url]`). */
fun TagArray.reactionCustomEmojis() = mapNotNull(EmojiUrlTag::parse)
