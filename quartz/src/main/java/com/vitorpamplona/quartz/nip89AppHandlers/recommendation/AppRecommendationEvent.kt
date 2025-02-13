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
package com.vitorpamplona.quartz.nip89AppHandlers.recommendation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip89AppHandlers.PlatformType
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.tags.RecommendationTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class AppRecommendationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun recommendations() = tags.mapNotNull(RecommendationTag::parse)

    fun recommendationAddresses() = tags.mapNotNull(RecommendationTag::parseAddress)

    companion object {
        const val KIND = 31989
        const val ALT_DESCRIPTION = "App recommendations by the author"

        class AppRecommendationItem(
            val appDefinitionEvent: AppDefinitionEvent,
            val relayHint: String?,
            val platform: PlatformType,
        )

        fun build(
            supportedKind: String,
            appReferences: List<AppRecommendationItem>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<AppRecommendationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(supportedKind)
            appReferences.forEach {
                recommend(it.appDefinitionEvent.addressTag(), it.relayHint, it.platform.code)
            }
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
