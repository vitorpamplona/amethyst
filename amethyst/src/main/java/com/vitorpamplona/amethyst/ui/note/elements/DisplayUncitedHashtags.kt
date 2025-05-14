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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.service.CachedRichTextParser
import com.vitorpamplona.amethyst.ui.components.ClickableTextColor
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun DisplayUncitedHashtags(
    event: Event,
    callbackUri: String? = null,
    nav: INav,
) {
    DisplayUncitedHashtags(event, event.content, callbackUri, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedHashtags(
    event: Event,
    content: String,
    callbackUri: String? = null,
    nav: INav,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val unusedHashtags by
        produceState(initialValue = emptyList<String>()) {
            val tagsInEvent = event.hashtags()
            if (tagsInEvent.isNotEmpty()) {
                launch(Dispatchers.Default) {
                    val state = CachedRichTextParser.parseText(content, event.tags.toImmutableListOfLists(), callbackUri)

                    val tagsInContent =
                        state
                            .paragraphs
                            .map {
                                it.words.mapNotNull {
                                    if (it is HashTagSegment) {
                                        it.hashtag
                                    } else {
                                        null
                                    }
                                }
                            }.flatten()

                    val unusedHashtags =
                        tagsInEvent.filterNot { eventTag ->
                            tagsInContent.any { contentTag ->
                                eventTag.equals(contentTag, true)
                            }
                        }

                    if (unusedHashtags.isNotEmpty()) {
                        value = unusedHashtags
                    }
                }
            }
        }

    if (unusedHashtags.isNotEmpty()) {
        FlowRow(
            modifier = HalfTopPadding,
        ) {
            unusedHashtags.forEach { hashtag ->
                ClickableTextColor(
                    text = "#$hashtag ",
                    onClick = { nav.nav(Route.Hashtag(hashtag)) },
                    linkColor = MaterialTheme.colorScheme.lessImportantLink,
                )
            }
        }
    }
}
