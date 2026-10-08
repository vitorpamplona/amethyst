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
package com.vitorpamplona.quartz.experimental.kanban.board

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.kanban.board.tags.ColumnTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Kanban board (kind 30301), per NIP PR #1665 ("NIP-100", draft, unmerged): a titled board with
 * ordered columns, whose cards are kind 30302 events that `a`-tag it ([KanbanCardEvent]).
 *
 * - `d`, `title` (required), `description` (may be markdown), `alt` (NIP-31);
 * - `col` = `[column id, name, order]` per column ([ColumnTag]);
 * - `p` = maintainers. Only the board's author edits the board; the author and the maintainers may
 *   add and edit cards ([canEditCards]). Anyone may react, comment (NIP-22) or zap.
 *
 * **The kind is shared**, and this is its primary class. WalletScrutiny publishes reproducible-
 * build verifications on 30301 (`BuildVerificationEvent`), and an encrypted planner app stores
 * NIP-44-encrypted tasks on it (`b` board hash, `col`, `status`). `EventFactory` tells them apart
 * by tags ([isKanbanBoard], `BuildVerificationEvent.isBuildVerification`) and builds
 * [UnrecognizedKind30301Event] for the rest. The board is the primary class — the one a
 * kind-level `EventFactory.probe` answers with — because it is the use a NIP proposal assigns to
 * the number and the one other events address (`30301:<pubkey>:<d>` in every 30302 card); the
 * other two are app-private formats documented only by their apps. In the 2026-10 census the
 * planner was the most frequent shape (135 of 300 fetched, 14 authors), then the boards (95, one
 * author) and the verifications (70, 3 authors).
 *
 * Searchable by title and description (human text). Column names are not indexed: they are
 * mostly the same few workflow words ("To Do", "Done") on every board.
 */
@Immutable
class KanbanBoardEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    PubKeyHintProvider,
    SearchableEvent {
    fun title() = tags.boardTitle()

    fun description() = tags.boardDescription()

    /** The columns, in tag order. */
    fun columns() = tags.columns()

    /** The columns by their `order`, columns without one last, ties in tag order. */
    fun sortedColumns(): List<ColumnTag> = columns().sortedBy { it.order ?: Int.MAX_VALUE }

    fun maintainers() = tags.maintainers()

    fun maintainerKeys(): List<HexKey> = tags.maintainerKeys().filter { Hex.isHex64(it) }

    /**
     * True when [user] may add or edit cards: the board's author, or a maintainer. Without `p`
     * tags the author is the only one.
     */
    fun canEditCards(user: HexKey): Boolean = user == pubKey || user in maintainerKeys()

    override fun indexableContent() = listOfNotNull(title(), description()).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(description())
    }

    override fun pubKeyHints(): List<PubKeyHint> = tags.mapNotNull(PTag::parseAsHint)

    /** `MAINTAINER`: the board's maintainers (`p`), who may add and edit its cards. */
    override fun linkedPubKeys(): List<HexKey> = maintainerKeys()

    companion object {
        const val KIND = 30301

        /**
         * True for a Kanban board shape: a `title`, which the PR requires, or a column with a name.
         * The planner app's events (`b`, `["col", "day"]`, `status`) and WalletScrutiny's
         * verifications have neither.
         */
        fun isKanbanBoard(tags: TagArray): Boolean = tags.fastAny { it.size > 1 && it[0] == TitleTag.TAG_NAME && it[1].isNotEmpty() } || tags.fastAny(ColumnTag::isTag)

        fun build(
            dTag: String,
            title: String,
            columns: List<ColumnTag>,
            description: String? = null,
            maintainers: List<HexKey> = emptyList(),
            alt: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<KanbanBoardEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(dTag)
            title(title)
            description?.let { description(it) }
            alt?.let { alt(it) }
            columns(columns)
            maintainers(maintainers)
            initializer()
        }
    }
}
