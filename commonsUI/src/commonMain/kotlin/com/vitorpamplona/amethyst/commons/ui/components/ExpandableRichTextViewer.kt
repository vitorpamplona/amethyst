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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.collection.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onPlaced
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.show_less
import com.vitorpamplona.amethyst.commons.resources.show_more
import com.vitorpamplona.amethyst.commons.richtext.ExpandableTextCutOffCalculator
import com.vitorpamplona.amethyst.commons.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.getGradient
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdTopPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

object ShowFullTextCache {
    val cache = LruCache<String, Boolean>(10)
}

@Composable
fun ExpandableRichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    id: String,
    callbackUri: String? = null,
    authorPubKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showFullText by
        rememberSaveable {
            val cached = ShowFullTextCache.cache[id]
            if (cached == null) {
                ShowFullTextCache.cache.put(id, false)
                mutableStateOf(false)
            } else {
                mutableStateOf(cached)
            }
        }

    val whereToCut = remember(content) { ExpandableTextCutOffCalculator.indexToCutOff(content) }

    val text by
        remember(content) {
            derivedStateOf {
                if (showFullText) {
                    content
                } else {
                    content.take(whereToCut)
                }
            }
        }

    val canCollapse = showFullText && content.length > whereToCut
    val gutter = LocalExpandedTextGutter.current

    if (canCollapse && gutter != null) {
        // The note shows "Show Less" in its author column, pinned while the text scrolls by.
        DisposableEffect(gutter, id) {
            val collapse: () -> Unit = {
                showFullText = false
                ShowFullTextCache.cache.put(id, false)
            }
            gutter.register(collapse)
            onDispose { gutter.unregister(collapse) }
        }
    }

    if (canCollapse && gutter == null) {
        // No author column here (chats, profiles, quotes): "Show Less" goes under the text.
        val viewport = LocalZoomViewport.current
        val dispatcher = remember { NestedScrollDispatcher() }
        val placed = remember { PlacedCoordinates() }

        Column(
            Modifier
                .nestedScroll(NoOpNestedScrollConnection, dispatcher)
                .onPlaced { placed.coordinates = it },
        ) {
            RichTextViewer(text, canPreview, quotesLeft, modifier, tags, backgroundColor, callbackUri, authorPubKey, accountViewModel, nav)

            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                ShowLessButton {
                    placed.coordinates?.let { dispatcher.revealTopOf(it, viewport) }
                    showFullText = false
                    ShowFullTextCache.cache.put(id, false)
                }
            }
        }
        return
    }

    Box {
        RichTextViewer(
            text,
            canPreview,
            quotesLeft,
            modifier.align(Alignment.TopStart),
            tags,
            backgroundColor,
            callbackUri,
            authorPubKey,
            accountViewModel,
            nav,
        )

        if (content.length > whereToCut && !showFullText) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(getGradient(backgroundColor)),
            ) {
                ShowMoreButton {
                    showFullText = !showFullText
                    ShowFullTextCache.cache.put(id, showFullText)
                }
            }
        }
    }
}

@Composable
fun ShowMoreButton(onClick: () -> Unit) {
    FilledTonalButton(
        modifier = StdTopPadding,
        onClick = onClick,
        shape = ButtonBorder,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(Res.string.show_more))
    }
}

@Composable
fun ShowLessButton(onClick: () -> Unit) {
    FilledTonalButton(
        modifier = StdTopPadding,
        onClick = onClick,
        shape = ButtonBorder,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(Res.string.show_less))
    }
}
