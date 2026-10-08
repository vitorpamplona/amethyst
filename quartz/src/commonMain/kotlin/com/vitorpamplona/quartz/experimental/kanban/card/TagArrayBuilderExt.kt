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

import com.vitorpamplona.quartz.experimental.kanban.card.tags.CardRankTag
import com.vitorpamplona.quartz.experimental.kanban.card.tags.CardStatusTag
import com.vitorpamplona.quartz.experimental.kanban.card.tags.TrackedBoardTag
import com.vitorpamplona.quartz.experimental.kanban.card.tags.TrackedCardTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.nip98HttpAuth.tags.UrlTag

fun TagArrayBuilder<KanbanCardEvent>.title(title: String) = addUnique(TitleTag.assemble(title))

fun TagArrayBuilder<KanbanCardEvent>.description(description: String) = addUnique(DescriptionTag.assemble(description))

fun TagArrayBuilder<KanbanCardEvent>.status(status: String) = addUnique(CardStatusTag.assemble(status))

fun TagArrayBuilder<KanbanCardEvent>.rank(rank: Long) = addUnique(CardRankTag.assemble(rank))

fun TagArrayBuilder<KanbanCardEvent>.board(board: ATag) = add(board.toATagArray())

fun TagArrayBuilder<KanbanCardEvent>.attachments(urls: List<String>) = addAll(urls.map { UrlTag.assemble(it) })

fun TagArrayBuilder<KanbanCardEvent>.assignees(assignees: List<HexKey>) = addAll(assignees.map { PTag.assemble(it, null) })

/** Makes the card a tracker card of [event] (`k` + `e`). */
fun TagArrayBuilder<KanbanCardEvent>.trackEvent(
    kind: Int,
    event: ETag,
) = addUnique(KindTag.assemble(kind)).add(event.toTagArray())

/** Makes the card a tracker card of an addressable event (`k` + a second `a`). */
fun TagArrayBuilder<KanbanCardEvent>.trackAddress(address: ATag) = addUnique(KindTag.assemble(address.kind)).add(address.toATagArray())

/** Makes the card a tracker card of another board's card (`k` 30302 + `refs/board` + `refs/card`). */
fun TagArrayBuilder<KanbanCardEvent>.trackCard(
    board: Address,
    cardDTag: String,
) = addUnique(KindTag.assemble(KanbanCardEvent.KIND)).addUnique(TrackedBoardTag.assemble(board)).addUnique(TrackedCardTag.assemble(cardDTag))
