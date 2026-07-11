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
package com.vitorpamplona.quartz.concord.cord04Roles.control.tags

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `["vac", <grantId>, <grantVersion>, <grantHash>]` versioned-authority-citation
 * tag: the exact Grant edition a delegated actor claims authority under (CORD-04).
 * Absent when the owner authors the edition. Carries three values, so it needs the
 * full tag (not just a value) to round-trip.
 */
class VacTag {
    companion object {
        const val TAG_NAME = "vac"

        fun isTag(tag: Array<String>) = tag.has(3) && tag[0] == TAG_NAME

        fun parse(tag: Array<String>): AuthorityCitation? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val grantId = tag[1].hexToByteArrayOrNull()?.takeIf { it.size == 32 } ?: return null
            val grantVersion = tag[2].toLongOrNull() ?: return null
            val grantHash = tag[3].hexToByteArrayOrNull()?.takeIf { it.size == 32 } ?: return null
            return AuthorityCitation(grantId, grantVersion, grantHash)
        }

        fun assemble(citation: AuthorityCitation) =
            arrayOf(
                TAG_NAME,
                citation.grantId.toHexKey(),
                citation.grantVersion.toString(),
                citation.grantHash.toHexKey(),
            )
    }
}
