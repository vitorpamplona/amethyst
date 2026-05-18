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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier

// Maps the first path segment of a nowhere URL to a localized label. The nowhere project ships
// eight tools (event, fundraiser, store, petition, message, drop, art, forum) and the URL path
// uses the first letter as a discriminator (e.g. nowhr.xyz/s#... is a store). Unknown codes
// fall through to the generic "Nowhere site" label.
private val nowhereToolLabels =
    mapOf(
        "e" to R.string.nowhere_link_card_event,
        "f" to R.string.nowhere_link_card_fundraiser,
        "s" to R.string.nowhere_link_card_store,
        "p" to R.string.nowhere_link_card_petition,
        "m" to R.string.nowhere_link_card_message,
        "d" to R.string.nowhere_link_card_drop,
        "a" to R.string.nowhere_link_card_art,
        "fo" to R.string.nowhere_link_card_forum,
    )

@Composable
fun NowhereLinkCard(segment: NowhereLinkSegment) {
    val uri = LocalUriHandler.current
    val titleRes = segment.tool?.lowercase()?.let { nowhereToolLabels[it] } ?: R.string.nowhere_link_card_generic

    Column(
        modifier =
            MaterialTheme.colorScheme.innerPostModifier
                .clickable {
                    runCatching {
                        val target = if (segment.segmentText.contains("://")) segment.segmentText else "https://${segment.segmentText}"
                        uri.openUri(target)
                    }
                }.padding(12.dp),
    ) {
        Text(
            text = stringRes(titleRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.padding(top = 2.dp))

        Text(
            text = segment.host,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
