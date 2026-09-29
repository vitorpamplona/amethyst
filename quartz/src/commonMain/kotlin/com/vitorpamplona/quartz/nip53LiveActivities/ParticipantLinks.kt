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
package com.vitorpamplona.quartz.nip53LiveActivities

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation

/**
 * NIP-53 participants: `["p", <pubkey>, <relay>, <role>, <proof>]`. The role (Host, Speaker,
 * Moderator…) and the proof of agreement to participate ride on the link as written.
 */
internal fun LinkBuilder.participantLinks(tags: TagArray) =
    tags.fastForEach {
        if (it.size < 2 || it[0] != "p") return@fastForEach
        val role = it.getOrNull(3)?.ifBlank { null }
        val proof = it.getOrNull(4)?.ifBlank { null }
        val props =
            when {
                role != null && proof != null -> mapOf("role" to role, "proof" to proof)
                role != null -> mapOf("role" to role)
                proof != null -> mapOf("proof" to proof)
                else -> null
            }
        user(Relation.PARTICIPANT, it[1], "p", props)
    }
