/**
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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import kotlin.math.min

data class LevelSignature(
    val signature: String,
    val createdAt: Long?,
    val author: User?,
)

/**
 * Platform-specific date-time formatter for thread signatures.
 * Returns formatted timestamp in pattern "uuuu-MM-dd-HH:mm:ss"
 */
expect fun formattedDateTime(timestamp: Long): String

object ThreadLevelCalculator {
    /**
     * This method caches signatures during each execution to avoid recalculation in longer threads
     */
    fun replyLevelSignature(
        note: Note,
        eventsToConsider: Set<HexKey>,
        cachedSignatures: MutableMap<Note, LevelSignature>,
        account: User,
        accountFollowingSet: Set<String>,
        now: Long,
    ): LevelSignature {
        val replyTo = note.replyTo

        // estimates the min date by replies if it doesn't exist.
        val createdAt =
            note.createdAt() ?: min(
                note.replies.minOfOrNull { it.createdAt() ?: now } ?: now,
                note.reactions.values.minOfOrNull { it.minOfOrNull { it.createdAt() ?: now } ?: now } ?: now,
            )

        val noteAuthor = note.author

        if (
            note.event is RepostEvent || note.event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()
        ) {
            return LevelSignature(
                signature = "/" + formattedDateTime(createdAt) + note.idHex.substring(0, 8) + ";",
                createdAt = createdAt,
                author = noteAuthor,
            )
        }

        val parent =
            (
                replyTo
                    .filter {
                        it.idHex in eventsToConsider
                    } // This forces the signature to be based on a branch, avoiding two roots
                    .map {
                        cachedSignatures[it]
                            ?: replyLevelSignature(
                                it,
                                eventsToConsider,
                                cachedSignatures,
                                account,
                                accountFollowingSet,
                                now,
                            ).apply { cachedSignatures.put(it, this) }
                    }.maxByOrNull { it.signature.length }
            )

        val parentSignature = parent?.signature?.removeSuffix(";") ?: ""

        val threadOrder =
            if (noteAuthor != null && parent?.author == noteAuthor) {
                // author of the thread first, in **ascending** order
                "9" + formattedDateTime((parent.createdAt ?: 0) + (now - createdAt)) + note.idHex.substring(0, 8)
            } else if (noteAuthor != null && noteAuthor.pubkeyHex == account.pubkeyHex) {
                "8" + formattedDateTime(createdAt) + note.idHex.substring(0, 8) // my replies
            } else if (noteAuthor != null && noteAuthor.pubkeyHex in accountFollowingSet) {
                "7" + formattedDateTime(createdAt) + note.idHex.substring(0, 8) // my follows replies.
            } else {
                "0" + formattedDateTime(createdAt) + note.idHex.substring(0, 8) // everyone else.
            }

        val mySignature =
            LevelSignature(
                signature = "$parentSignature/$threadOrder;",
                createdAt = createdAt,
                author = note.author,
            )

        cachedSignatures[note] = mySignature
        return mySignature
    }

    fun replyLevel(
        note: Note,
        cachedLevels: MutableMap<Note, Int> = mutableMapOf(),
    ): Int {
        val replyTo = note.replyTo
        if (
            note.event is RepostEvent || note.event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()
        ) {
            cachedLevels[note] = 0
            return 0
        }

        val thisLevel =
            replyTo.maxOf {
                cachedLevels[it] ?: replyLevel(it, cachedLevels)
            } + 1

        cachedLevels[note] = thisLevel

        return thisLevel
    }
}
