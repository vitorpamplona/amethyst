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
package com.vitorpamplona.quartz.nip15Marketplace.stall

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip15Marketplace.Nip15ParseFailed
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class StallEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent {
    override fun isContentEncoded() = true

    // isWellFormed() runs for every copy the cache consumes and forEachIndexableField() on every
    // search keystroke, so the body is decoded once per instance, a failure included. Events are
    // immutable; a race only decodes twice.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var stallDataCache: Any? = null // StallData, or Nip15ParseFailed

    /** The stall's JSON body, read leniently: null only when [content] is not a JSON object. */
    fun stallData(): StallData? {
        stallDataCache?.let { return it as? StallData }
        val parsed = StallData.parse(content, dTag())
        stallDataCache = parsed ?: Nip15ParseFailed
        return parsed
    }

    /**
     * A stall has a name. Other apps publish their own JSON on kind 30017 (a typing game's
     * scores, shop listings in other shapes); without a name there is nothing to show as a stall.
     */
    fun isWellFormed() = stallData()?.name != null

    override fun indexableContent() = stallData()?.let { listOfNotNull(it.name, it.description).joinToString("\n") } ?: ""

    // The read path. The parse happens once and its fields are handed over one by
    // one; a scan that stops on the first hit never pays for the rest of the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        val data = stallData() ?: return
        if (!visitor.visit(data.name)) return
        if (!visitor.visit(data.description)) return
    }

    companion object {
        const val KIND = 30017

        fun build(
            stall: StallData,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<StallEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, JsonMapper.toJson(stall), createdAt) {
            dTag(stall.id)
            initializer()
        }
    }
}
