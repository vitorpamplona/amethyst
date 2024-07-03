/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.RepostEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LevelSignature(
    val signature: String,
    val createdAt: Long?,
    val author: User?,
)

object ThreadLevelCalculator {
    val levelFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd-HH:mm:ss")

    private fun formattedDateTime(timestamp: Long): String =
        Instant
            .ofEpochSecond(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(levelFormatter)

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
        if (
            note.event is RepostEvent || note.event is GenericRepostEvent || replyTo == null || replyTo.isEmpty()
        ) {
            return LevelSignature(
                signature = "/" + formattedDateTime(note.createdAt() ?: 0) + note.idHex.substring(0, 8) + ";",
                createdAt = note.createdAt(),
                author = note.author,
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
            if (parent?.author == note.author && note.createdAt() != null) {
                // author of the thread first, in **ascending** order
                "9" +
                    formattedDateTime((parent?.createdAt ?: 0) + (now - (note.createdAt() ?: 0))) +
                    note.idHex.substring(0, 8)
            } else if (note.author?.pubkeyHex == account.pubkeyHex) {
                "8" + formattedDateTime(note.createdAt() ?: 0) + note.idHex.substring(0, 8) // my replies
            } else if (note.author?.pubkeyHex in accountFollowingSet) {
                "7" + formattedDateTime(note.createdAt() ?: 0) + note.idHex.substring(0, 8) // my follows replies.
            } else {
                "0" + formattedDateTime(note.createdAt() ?: 0) + note.idHex.substring(0, 8) // everyone else.
            }

        val mySignature =
            LevelSignature(
                signature = parentSignature + "/" + threadOrder + ";",
                createdAt = note.createdAt(),
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
            return 0
        }

        return replyTo.maxOf {
            cachedLevels[it] ?: replyLevel(it, cachedLevels).apply { cachedLevels.put(it, this) }
        } + 1
    }
}
