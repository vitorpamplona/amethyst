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
package com.vitorpamplona.quartz.buzz.moderation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.moderation.tags.CategoryTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz product-feedback submission (`kind:42000`): a user-signed feedback report. The
 * `content` is the free-text feedback body (required, non-empty); an optional [CategoryTag]
 * (at most one of `bug`/`praise`/`needs-work`) classifies it, and optional `imeta` tags may
 * attach media. The relay validates and sidecars it to the deployment feedback table — it is
 * never stored or fanned out as an event. Ground truth:
 * `buzz-relay/src/handlers/product_feedback.rs`.
 */
@Immutable
class ProductFeedbackEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The feedback category (`bug`/`praise`/`needs-work`), if present. */
    fun category() = tags.feedbackCategory()

    companion object {
        const val KIND = 42000

        fun build(
            body: String,
            category: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProductFeedbackEvent>.() -> Unit = {},
        ) = eventTemplate<ProductFeedbackEvent>(KIND, body, createdAt) {
            category?.let { addUnique(CategoryTag.assemble(it)) }
            initializer()
        }
    }
}
