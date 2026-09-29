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
package com.vitorpamplona.quartz.nip53LiveActivities.chat.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.AddressReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull

/**
 * The `a` tag a NIP-53 chat message names its activity (a 30311 stream, a 30312 space) with.
 * The spec's example marks it `root`, which tells it apart from an activity the message only
 * mentions:
 *
 *     ["a", "<kind>:<pubkey>:<d>", "<optional relay>", "root"]
 */
@Immutable
data class ActivityTag(
    val activity: ATag,
    val marker: String? = null,
) : AddressReferenceTag {
    override fun toAddressId() = activity.toAddressId()

    override val relayHint get() = activity.relayHint

    fun isRoot() = marker == ROOT_MARKER

    fun toTagArray() = assemble(activity, marker)

    companion object {
        const val TAG_NAME = ATag.TAG_NAME
        const val ROOT_MARKER = "root"

        fun parse(tag: Array<String>): ActivityTag? {
            val activity = ATag.parse(tag) ?: return null
            return ActivityTag(activity, tag.getOrNull(3)?.ifEmpty { null })
        }

        fun assemble(
            activity: ATag,
            marker: String?,
        ) = arrayOfNotNull(TAG_NAME, activity.toTag(), activity.relay?.url ?: if (marker != null) "" else null, marker)
    }
}
