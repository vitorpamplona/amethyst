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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Kanban card (kind 30302), per NIP PR #1665 ("NIP-100", draft, unmerged): a task on a
 * [KanbanBoardEvent], which it names with an `a` tag.
 *
 * - `d`, `title` (required), `description` (may be markdown), `alt` (NIP-31);
 * - `a` = the board (`30301:<board author>:<board d>`, required); `s` = status (the column);
 *   `rank` = order within the column; `u` = attachment URLs; `p` = assignees.
 * - A **tracker card** (any card with a `k`) mirrors another event instead of describing a task:
 *   `k` = the tracked kind, plus `e` (a regular event), a second `a` (an addressable one), or, for
 *   another board's card, `refs/board` + `refs/card`. Its status follows the tracked event's `s`.
 *
 * Cards are published by the board's author or its maintainers, who edit a card by republishing
 * its `d`; a client takes the newest version by any of them. That check needs the board, so it is
 * left to the caller (`KanbanBoardEvent.canEditCards`).
 *
 * **Kind 30302 is shared** too: Fieldbook publishes team-membership records on it (`d`, `client`,
 * `a` → a kind-30300 team, JSON `content`). `EventFactory` builds this class for
 * [isKanbanCard] tags and [UnrecognizedKind30302Event] for the rest.
 *
 * Searchable by title and description. Comments (NIP-22) on a card make sense, so it is a
 * [RootScope].
 */
@Immutable
class KanbanCardEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    PubKeyHintProvider,
    EventHintProvider,
    AddressHintProvider,
    SearchableEvent {
    fun title() = tags.cardTitle()

    fun description() = tags.cardDescription()

    fun status() = tags.cardStatus()

    fun rank() = tags.cardRank()

    fun attachments() = tags.cardAttachments()

    fun assignees() = tags.assignees()

    fun assigneeKeys() = tags.assigneeKeys()

    /** The board this card belongs to (`a` of kind 30301). */
    fun board() = tags.board()

    /** True for a tracker card: one with a `k`. */
    fun isTracker() = tags.fastAny(KindTag::match)

    fun trackedKind() = tags.trackedKind()

    fun trackedEvent() = tags.trackedEvent()

    fun trackedAddress() = tags.trackedAddress()

    /** The board holding the tracked card (`refs/board`), for a tracker of another board's card. */
    fun trackedCardBoard() = tags.trackedCardBoard()

    /** The tracked card's `d` (`refs/card`). */
    fun trackedCardDTag() = tags.trackedCardDTag()

    override fun indexableContent() = listOfNotNull(title(), description()).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(description())
    }

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint)

    /** `ASSIGNEE`: the people the card is assigned to (`p`). */
    override fun linkedPubKeys(): List<HexKey> = assigneeKeys()

    override fun eventHints(): List<EventIdHint> = tags.mapNotNull(ETag::parseAsHint)

    /** `TRACKED`: the event a tracker card mirrors (`e`). */
    override fun linkedEventIds(): List<HexKey> = listOfNotNull(trackedEvent()?.eventId)

    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    /**
     * `BOARD`: the board the card belongs to (`a` of kind 30301); `TRACKED`: the addressable event
     * a tracker card mirrors (any other `a`); `TRACKED_BOARD`: the board holding the card a tracker
     * mirrors (`refs/board`; the card itself is only a `d` there, so it is not an address).
     */
    override fun linkedAddressIds(): List<String> =
        listOfNotNull(
            board()?.toTag(),
            trackedAddress()?.toTag(),
            trackedCardBoard()?.toValue(),
        )

    companion object {
        const val KIND = 30302

        /**
         * True for a Kanban card shape: a `title`, which the PR requires, or an `a` naming a
         * kind-30301 board. Fieldbook's membership records (an `a` to a kind-30300 team, no title)
         * have neither.
         */
        fun isKanbanCard(tags: TagArray): Boolean =
            tags.fastAny { it.size > 1 && it[0] == TitleTag.TAG_NAME && it[1].isNotEmpty() } ||
                tags.fastAny { ATag.isTaggedWithKind(it, BOARD_KIND) }

        private val BOARD_KIND = KanbanBoardEvent.KIND.toString()

        fun build(
            dTag: String,
            title: String,
            board: ATag,
            status: String? = null,
            rank: Long? = null,
            description: String? = null,
            assignees: List<HexKey> = emptyList(),
            attachments: List<String> = emptyList(),
            alt: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<KanbanCardEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(dTag)
            title(title)
            description?.let { description(it) }
            alt?.let { alt(it) }
            board(board)
            status?.let { status(it) }
            rank?.let { rank(it) }
            attachments(attachments)
            assignees(assignees)
            initializer()
        }
    }
}
