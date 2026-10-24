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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponseRule
import com.vitorpamplona.amethyst.commons.model.nip88Polls.PollResponsesCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent

/**
 * Encrypted NIP-88 polls inside a Marmot group, in the bounded profile MDK
 * enforces (`crates/traits/src/polls.rs` and `POLLS.md` in marmot-protocol/mdk).
 *
 * A Marmot poll is not a public NIP-88 poll with a different transport: the
 * profile is stricter, and two clients that disagree on it show the same group
 * different results. So the rules here are MDK's, not NIP-88's looser ones —
 * a single-choice response naming two options is not "the first one counts",
 * it is no vote at all; an option id the poll never declared voids the whole
 * response instead of being dropped from it.
 *
 * Kind `1068` carries the question and options, kind `1018` one voter's
 * complete selection. Both are ordinary unsigned app events inside MLS, so
 * the authenticated sender is the voter and votes are never anonymous.
 */
object MarmotPolls {
    const val KIND_POLL = 1068
    const val KIND_POLL_RESPONSE = 1018

    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 10
    const val MAX_QUESTION_BYTES = 1_024
    const val MAX_OPTION_BYTES = 256
    const val MAX_OPTION_ID_BYTES = 64
    const val MAX_LIFETIME_SECONDS = 30L * 24 * 60 * 60
    const val MAX_TAGS = 32

    private const val TAG_OPTION = "option"
    private const val TAG_POLL_TYPE = "polltype"
    private const val TAG_ENDS_AT = "endsAt"
    private const val TAG_RESPONSE = "response"
    private const val TAG_EVENT = "e"

    enum class PollType(
        val nip88: String,
    ) {
        SINGLE_CHOICE("singlechoice"),
        MULTIPLE_CHOICE("multiplechoice"),
    }

    @Immutable
    data class Option(
        val id: String,
        val label: String,
    )

    @Immutable
    data class Definition(
        val question: String,
        val options: List<Option>,
        val type: PollType,
        val endsAt: Long?,
        val createdAt: Long,
    ) {
        fun isOpen(nowSeconds: Long): Boolean = endsAt == null || nowSeconds <= endsAt
    }

    /**
     * MDK's reading of the votes on one poll, handed to the shared tally ([PollResponsesCache]) by
     * the group the poll lives in, so this client and White Noise count the same group alike.
     *
     * A response counts only when it is well formed, names this poll, was sent while the poll was
     * open and fits its selection rules; anything else is no vote, and leaves the voter's earlier
     * valid vote standing. MDK also stops looking past a voter's newest 64 responses, so 64 newer
     * malformed ones would hide an older valid vote there and not here.
     */
    @Immutable
    data class ResponseRule(
        val pollId: HexKey,
        val poll: Definition,
    ) : PollResponseRule {
        override fun cast(response: PollResponseEvent): Set<String>? {
            // A member votes inside the group, as an unsigned rumor. A signed response came from a
            // relay outside it: knowing the poll's id must not be enough to vote in a group's poll.
            if (response.sig.isNotEmpty()) return null
            val (target, selections) = parseResponse(response) ?: return null
            if (target != pollId || !isValidResponse(poll, response.createdAt, selections)) return null
            return selections.toSet()
        }
    }

    /** The [ResponseRule] of [poll], or null when the poll itself breaks the profile. */
    fun responseRule(poll: Event): ResponseRule? = parsePoll(poll)?.let { ResponseRule(poll.id, it) }

    // ---- building ----------------------------------------------------------

    sealed class BuildError {
        data object InvalidQuestion : BuildError()

        data object InvalidOptionCount : BuildError()

        data object InvalidOptionLabel : BuildError()

        data object InvalidDeadline : BuildError()

        data object InvalidSelection : BuildError()
    }

    /**
     * The tags of a new poll, ids `"0".."9"` in display order. No `relay` tag:
     * authenticated group routing decides where a Marmot event goes, so a relay
     * hint inside one would only leak where the group lives.
     *
     * Returns null with the reason in [onError] when the input breaks the profile.
     */
    fun pollTags(
        createdAt: Long,
        question: String,
        labels: List<String>,
        type: PollType,
        endsAt: Long?,
        onError: (BuildError) -> Unit = {},
    ): Array<Array<String>>? {
        if (!isValidDisplayText(question, MAX_QUESTION_BYTES)) return null.also { onError(BuildError.InvalidQuestion) }
        if (labels.size !in MIN_OPTIONS..MAX_OPTIONS) return null.also { onError(BuildError.InvalidOptionCount) }
        if (labels.any { !isValidDisplayText(it, MAX_OPTION_BYTES) }) return null.also { onError(BuildError.InvalidOptionLabel) }
        if (!isValidDeadline(createdAt, endsAt)) return null.also { onError(BuildError.InvalidDeadline) }

        val tags = ArrayList<Array<String>>(labels.size + 2)
        labels.forEachIndexed { index, label -> tags.add(arrayOf(TAG_OPTION, index.toString(), label)) }
        tags.add(arrayOf(TAG_POLL_TYPE, type.nip88))
        if (endsAt != null) tags.add(arrayOf(TAG_ENDS_AT, endsAt.toString()))
        return tags.toTypedArray()
    }

    /**
     * The tags of a vote: exactly one two-element `e` tag naming the poll (no
     * relay hint — MDK rejects a third element) followed by one `response` tag
     * per chosen option. The content of a vote is always empty.
     *
     * A vote is a complete replacement of the voter's previous one, never a
     * delta, and an empty selection is not an unvote.
     */
    fun responseTags(
        pollEventId: HexKey,
        optionIds: List<String>,
    ): Array<Array<String>>? {
        if (!isValidEventId(pollEventId)) return null
        if (!isValidSelectionShape(optionIds, MAX_OPTIONS)) return null
        val tags = ArrayList<Array<String>>(optionIds.size + 1)
        tags.add(arrayOf(TAG_EVENT, pollEventId))
        optionIds.forEach { tags.add(arrayOf(TAG_RESPONSE, it)) }
        return tags.toTypedArray()
    }

    // ---- parsing -----------------------------------------------------------

    /**
     * The poll [event] defines, or null when it breaks the profile. A null for a
     * kind-1068 event is the "unsupported poll" state: the question must not be
     * shown as a usable poll.
     *
     * Unknown tags (NIP-88 relay hints, future extensions) are ignored so newer
     * senders degrade forward, while the known ones stay strict.
     */
    fun parsePoll(event: Event): Definition? {
        if (event.kind != KIND_POLL || event.tags.size > MAX_TAGS) return null
        val options = ArrayList<Option>()
        var type: PollType? = null
        var endsAt: Long? = null
        var sawEndsAt = false
        for (tag in event.tags) {
            when (tag.firstOrNull()) {
                TAG_OPTION -> {
                    if (tag.size != 3) return null
                    options.add(Option(tag[1], tag[2]))
                }

                TAG_POLL_TYPE -> {
                    if (tag.size != 2 || type != null) return null
                    type =
                        when (tag[1]) {
                            PollType.SINGLE_CHOICE.nip88 -> PollType.SINGLE_CHOICE
                            PollType.MULTIPLE_CHOICE.nip88 -> PollType.MULTIPLE_CHOICE
                            else -> return null
                        }
                }

                TAG_ENDS_AT -> {
                    if (tag.size != 2 || sawEndsAt) return null
                    sawEndsAt = true
                    endsAt = parseUnsigned(tag[1]) ?: return null
                }
            }
        }
        if (!isValidDisplayText(event.content, MAX_QUESTION_BYTES)) return null
        if (!isValidOptions(options)) return null
        if (!isValidDeadline(event.createdAt, endsAt)) return null
        return Definition(
            question = event.content,
            options = options,
            type = type ?: PollType.SINGLE_CHOICE,
            endsAt = endsAt,
            createdAt = event.createdAt,
        )
    }

    /** The poll id and selection a kind-1018 [event] carries, or null when malformed. */
    fun parseResponse(event: Event): Pair<HexKey, List<String>>? {
        if (event.kind != KIND_POLL_RESPONSE || event.content.isNotEmpty() || event.tags.size > MAX_TAGS) return null
        var target: String? = null
        val selections = ArrayList<String>()
        for (tag in event.tags) {
            when (tag.firstOrNull()) {
                TAG_EVENT -> {
                    if (tag.size != 2 || target != null) return null
                    if (!isValidEventId(tag[1])) return null
                    target = tag[1]
                }

                TAG_RESPONSE -> {
                    if (tag.size != 2) return null
                    selections.add(tag[1])
                }
            }
        }
        if (target == null) return null
        if (!isValidSelectionShape(selections, MAX_OPTIONS)) return null
        return target to selections
    }

    /** True when a response stamped [respondedAt] choosing [selections] counts for [poll]. */
    fun isValidResponse(
        poll: Definition,
        respondedAt: Long,
        selections: List<String>,
    ): Boolean {
        if (respondedAt < poll.createdAt) return false
        if (poll.endsAt != null && respondedAt > poll.endsAt) return false
        return isValidSelection(poll.type, poll.options.map { it.id }, selections)
    }

    fun isValidSelection(
        type: PollType,
        optionIds: List<String>,
        selections: List<String>,
    ): Boolean {
        if (!isValidSelectionShape(selections, optionIds.size)) return false
        if (type == PollType.SINGLE_CHOICE && selections.size != 1) return false
        val known = optionIds.toHashSet()
        return selections.all { it in known }
    }

    // ---- validation helpers ------------------------------------------------

    private fun isValidOptions(options: List<Option>): Boolean {
        if (options.size !in MIN_OPTIONS..MAX_OPTIONS) return false
        val ids = HashSet<String>()
        for (option in options) {
            if (!isValidOptionId(option.id) || !ids.add(option.id)) return false
            if (!isValidDisplayText(option.label, MAX_OPTION_BYTES)) return false
        }
        return true
    }

    private fun isValidOptionId(id: String): Boolean =
        id.isNotEmpty() &&
            id.length <= MAX_OPTION_ID_BYTES &&
            id.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }

    private fun isValidSelectionShape(
        ids: List<String>,
        max: Int,
    ): Boolean {
        if (ids.isEmpty() || ids.size > max) return false
        val unique = HashSet<String>()
        return ids.all { isValidOptionId(it) && unique.add(it) }
    }

    private fun isValidEventId(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun isValidDeadline(
        createdAt: Long,
        endsAt: Long?,
    ): Boolean {
        if (endsAt == null) return true
        val lifetime = endsAt - createdAt
        return lifetime in 1..MAX_LIFETIME_SECONDS
    }

    /** MDK parses `endsAt` as a u64: digits only, no sign, no whitespace. */
    private fun parseUnsigned(value: String): Long? {
        if (value.isEmpty() || !value.all { it in '0'..'9' }) return null
        return value.toLongOrNull()
    }

    /**
     * Rust's `validate_display_text`: non-blank, not padded, within [maxBytes]
     * UTF-8 bytes, and free of control and bidirectional-override characters.
     */
    fun isValidDisplayText(
        value: String,
        maxBytes: Int,
    ): Boolean {
        if (value.isBlank()) return false
        if (value.trim() != value) return false
        if (value.encodeToByteArray().size > maxBytes) return false
        return value.none { isControl(it) || it in BIDI_CONTROLS }
    }

    private fun isControl(c: Char): Boolean = c.code in 0x00..0x1F || c.code in 0x7F..0x9F

    private val BIDI_CONTROLS =
        setOf(
            '\u061C',
            '\u200E',
            '\u200F',
            '\u202A',
            '\u202B',
            '\u202C',
            '\u202D',
            '\u202E',
            '\u2066',
            '\u2067',
            '\u2068',
            '\u2069',
        )
}
