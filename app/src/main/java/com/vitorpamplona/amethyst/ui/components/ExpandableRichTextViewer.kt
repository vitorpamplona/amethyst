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
package com.vitorpamplona.amethyst.ui.components

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.ExpandableTextCutOffCalculator
import com.vitorpamplona.amethyst.ui.note.getGradient
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.secondaryButtonBackground
import com.vitorpamplona.quartz.events.ImmutableListOfLists

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
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var showFullText by
        remember {
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

    Box {
        RichTextViewer(
            text,
            canPreview,
            quotesLeft,
            modifier.align(Alignment.TopStart),
            tags,
            backgroundColor,
            accountViewModel,
            nav,
        )

        if (content.length > whereToCut && !showFullText) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
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
    Button(
        modifier = Modifier.padding(top = 10.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryButtonBackground,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.show_more), color = Color.White)
    }
}
