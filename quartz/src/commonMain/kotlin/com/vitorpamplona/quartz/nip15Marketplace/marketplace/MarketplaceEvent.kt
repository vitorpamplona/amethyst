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
package com.vitorpamplona.quartz.nip15Marketplace.marketplace

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

@Immutable
class MarketplaceEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent,
    PubKeyHintProvider {
    override fun isContentEncoded() = true

    // linkedPubKeys() runs for every relay copy of every event and forEachIndexableField() on
    // every keystroke, so the body is decoded once per instance — a failure included, which
    // also keeps the warning to one per event. Events are immutable; a race only decodes twice.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    private var marketplaceDataCache: Result<MarketplaceData>? = null

    fun marketplaceData(): MarketplaceData? =
        (
            marketplaceDataCache ?: try {
                Result.success(JsonMapper.fromJson<MarketplaceData>(content))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("MarketplaceEvent") { "Content Parse Error: ${toNostrUri()} ${e.message}" }
                Result.failure(e)
            }.also { marketplaceDataCache = it }
        ).getOrNull()

    override fun indexableContent() = marketplaceData()?.let { listOfNotNull(it.name, it.about).joinToString("\n") } ?: ""

    // The read path. The parse happens once and its fields are handed over one by
    // one; a scan that stops on the first hit never pays for the rest of the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        val data = marketplaceData() ?: return
        if (!visitor.visit(data.name)) return
        if (!visitor.visit(data.about)) return
    }

    // The merchants are listed in the public content JSON without relay hints.
    override fun pubKeyHints(): List<PubKeyHint> = emptyList()

    override fun linkedPubKeys(): List<HexKey> = merchants()

    /** The marketplace's merchant pubkeys, keeping only well-formed 64-char hex keys. */
    fun merchants(): List<HexKey> = marketplaceData()?.merchants?.filter { it.length == 64 && Hex.isHex64(it) } ?: emptyList()

    companion object {
        const val KIND = 30019

        fun build(
            marketplace: MarketplaceData,
            dTag: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MarketplaceEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, JsonMapper.toJson(marketplace), createdAt) {
            dTag(dTag)
            initializer()
        }
    }
}
