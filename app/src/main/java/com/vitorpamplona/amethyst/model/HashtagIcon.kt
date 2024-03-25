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
package com.vitorpamplona.amethyst.model

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.Btc
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.Coffee
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Footstr
import com.vitorpamplona.amethyst.commons.hashtags.Grownostr
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.commons.hashtags.Mate
import com.vitorpamplona.amethyst.commons.hashtags.Nostr
import com.vitorpamplona.amethyst.commons.hashtags.Plebs
import com.vitorpamplona.amethyst.commons.hashtags.Skull
import com.vitorpamplona.amethyst.commons.hashtags.Tunestr
import com.vitorpamplona.amethyst.commons.hashtags.Weed
import com.vitorpamplona.amethyst.commons.hashtags.Zap
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.ui.components.HashTag
import com.vitorpamplona.amethyst.ui.components.RenderRegular
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.events.EmptyTagList

@Preview
@Composable
fun RenderHashTagIcons() {
    val nav: (String) -> Unit = {}

    ThemeComparisonColumn {
        RenderRegular(
            "Testing rendering of hashtags: #Bitcoin, #nostr, #lightning, #zap, #amethyst, #cashu, #plebs, #coffee, #skullofsatoshi, #grownostr, #footstr, #tunestr, #weed, #mate",
            EmptyTagList,
        ) { word, state ->
            when (word) {
                is HashTagSegment -> HashTag(word, nav)
                is RegularTextSegment -> Text(word.segmentText)
            }
        }
    }
}

fun checkForHashtagWithIcon(tag: String): HashtagIcon? {
    return when (tag.lowercase()) {
        "₿itcoin", "bitcoin", "btc", "timechain", "bitcoiner", "bitcoiners" -> bitcoin
        "nostr", "nostrich", "nostriches", "thenostr" -> nostr
        "lightning", "lightningnetwork" -> lightning
        "zap", "zaps", "zapper", "zappers", "zapping", "zapped", "zapathon", "zapraiser", "zaplife", "zapchain" -> zap
        "amethyst" -> amethyst
        "cashu", "ecash", "nut", "nuts", "deeznuts" -> cashu
        "plebs", "pleb", "plebchain" -> plebs
        "coffee", "coffeechain", "cafe" -> coffee
        "skullofsatoshi" -> skull
        "grownostr", "gardening", "garden" -> growstr
        "footstr" -> footstr
        "tunestr", "music", "nowplaying" -> tunestr
        "mate", "matechain", "matestr" -> matestr
        "weed", "weedstr", "420", "cannabis", "marijuana" -> weed
        else -> null
    }
}

val bitcoin = HashtagIcon(CustomHashTagIcons.Btc, "Bitcoin", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val nostr = HashtagIcon(CustomHashTagIcons.Nostr, "Nostr", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val lightning = HashtagIcon(CustomHashTagIcons.Lightning, "Lightning", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val zap = HashtagIcon(CustomHashTagIcons.Zap, "Zap", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val amethyst = HashtagIcon(CustomHashTagIcons.Amethyst, "Amethyst", Modifier.padding(start = 2.dp, bottom = 1.dp, top = 1.dp))
val cashu = HashtagIcon(CustomHashTagIcons.Cashu, "Cashu", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val plebs = HashtagIcon(CustomHashTagIcons.Plebs, "Pleb", Modifier.padding(start = 2.dp, bottom = 1.dp, top = 1.dp))
val coffee = HashtagIcon(CustomHashTagIcons.Coffee, "Coffee", Modifier.padding(start = 3.dp, bottom = 1.dp, top = 1.dp))
val skull = HashtagIcon(CustomHashTagIcons.Skull, "SkullofSatoshi", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val growstr = HashtagIcon(CustomHashTagIcons.Grownostr, "GrowNostr", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val footstr = HashtagIcon(CustomHashTagIcons.Footstr, "Footstr", Modifier.padding(start = 2.dp, bottom = 1.dp, top = 1.dp))
val tunestr = HashtagIcon(CustomHashTagIcons.Tunestr, "Tunestr", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val weed = HashtagIcon(CustomHashTagIcons.Weed, "Weed", Modifier.padding(start = 1.dp, bottom = 0.dp, top = 0.dp))
val matestr = HashtagIcon(CustomHashTagIcons.Mate, "Mate", Modifier.padding(start = 1.dp, bottom = 0.dp, top = 0.dp))

@Immutable
class HashtagIcon(
    val icon: ImageVector,
    val description: String,
    val modifier: Modifier = Modifier,
)
