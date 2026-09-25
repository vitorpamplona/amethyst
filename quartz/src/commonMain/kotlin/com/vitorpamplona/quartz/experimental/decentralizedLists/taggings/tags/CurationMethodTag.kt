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
package com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure
import kotlinx.serialization.Serializable

/**
 * A pin's curation intent: whose point of view ranks the tag's members ([observer]), by which
 * method (e.g. `nip85:rank`), the rank [cutoff], and whether derived scores go into the
 * Trusted Lists published from it. Further method identifiers come by convention.
 */
@Immutable
@Serializable
data class CurationMethod(
    val observer: HexKey? = null,
    val method: String? = null,
    val cutoff: Int? = null,
    val includeScoreInTL: Boolean? = null,
) {
    companion object {
        const val NIP85_RANK = "nip85:rank"
    }
}

/** Tags & Taggings: `["curation-method", "<stringified CurationMethod JSON>"]` on a pin. */
class CurationMethodTag {
    companion object {
        const val TAG_NAME = "curation-method"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): CurationMethod? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return runCatching { JsonMapper.fromJson<CurationMethod>(tag[1]) }.getOrNull()
        }

        fun assemble(method: CurationMethod) = arrayOf(TAG_NAME, JsonMapper.toJson(method))
    }
}
