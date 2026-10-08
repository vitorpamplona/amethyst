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
package com.vitorpamplona.quartz.experimental.kanban.card

import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.tags.CardRankTag
import com.vitorpamplona.quartz.experimental.kanban.card.tags.CardStatusTag
import com.vitorpamplona.quartz.experimental.kanban.card.tags.TrackedBoardTag
import com.vitorpamplona.quartz.experimental.kanban.card.tags.TrackedCardTag
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.nip98HttpAuth.tags.UrlTag
import com.vitorpamplona.quartz.utils.Hex

fun TagArray.cardTitle() = fastFirstNotNullOfOrNull(TitleTag::parse)

fun TagArray.cardDescription() = fastFirstNotNullOfOrNull(DescriptionTag::parse)

fun TagArray.cardStatus() = fastFirstNotNullOfOrNull(CardStatusTag::parse)

fun TagArray.cardRank() = fastFirstNotNullOfOrNull(CardRankTag::parse)

fun TagArray.cardAttachments() = mapNotNull(UrlTag::parse)

fun TagArray.assignees() = mapNotNull(PTag::parse)

fun TagArray.assigneeKeys() = mapNotNull(PTag::parseKey).filter { Hex.isHex64(it) }

/** The board the card belongs to: the first `a` naming a kind-30301 address. */
fun TagArray.board(): ATag? = fastFirstNotNullOfOrNull { tag -> ATag.parse(tag)?.takeIf { it.kind == KanbanBoardEvent.KIND } }

/** The kind of the tracked event (`k`); its presence makes the card a tracker card. */
fun TagArray.trackedKind() = fastFirstNotNullOfOrNull(KindTag::parse)

/** The tracked event (`e`), a 64-hex id. */
fun TagArray.trackedEvent(): ETag? = fastFirstNotNullOfOrNull { tag -> ETag.parse(tag)?.takeIf { Hex.isHex64(it.eventId) } }

/** The tracked addressable event: the first `a` that is not a board address. */
fun TagArray.trackedAddress(): ATag? = fastFirstNotNullOfOrNull { tag -> ATag.parse(tag)?.takeIf { it.kind != KanbanBoardEvent.KIND } }

fun TagArray.trackedCardBoard() = fastFirstNotNullOfOrNull(TrackedBoardTag::parse)

fun TagArray.trackedCardDTag() = fastFirstNotNullOfOrNull(TrackedCardTag::parse)
