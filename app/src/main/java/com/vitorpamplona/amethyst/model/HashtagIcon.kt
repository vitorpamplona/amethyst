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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
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
            "Testing rendering of hashtags: #Bitcoin, #nostr, #lightning, #zap, #amethyst, #cashu, #plebs, #coffee, #skullofsatoshi, #grownostr, #footstr, #tunestr, #weed",
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
        "weed", "weedstr", "420", "cannabis", "marijuana" -> weed
        else -> null
    }
}

val bitcoin = HashtagIcon(R.drawable.ht_btc, "Bitcoin", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val nostr = HashtagIcon(R.drawable.ht_nostr, "Nostr", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val lightning = HashtagIcon(R.drawable.lightning, "Lightning", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val zap = HashtagIcon(R.drawable.zap, "Zap", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val amethyst = HashtagIcon(R.drawable.amethyst, "Amethyst", Modifier.padding(start = 2.dp, bottom = 1.dp, top = 1.dp))
val cashu = HashtagIcon(R.drawable.cashu, "Cashu", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val plebs = HashtagIcon(R.drawable.plebs, "Pleb", Modifier.padding(start = 2.dp, bottom = 1.dp, top = 1.dp))
val coffee = HashtagIcon(R.drawable.coffee, "Coffee", Modifier.padding(start = 3.dp, bottom = 1.dp, top = 1.dp))
val skull = HashtagIcon(R.drawable.skull, "SkullofSatoshi", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val growstr = HashtagIcon(R.drawable.grownostr, "GrowNostr", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val footstr = HashtagIcon(R.drawable.footstr, "Footstr", Modifier.padding(start = 2.dp, bottom = 1.dp, top = 1.dp))
val tunestr = HashtagIcon(R.drawable.tunestr, "Tunestr", Modifier.padding(start = 1.dp, bottom = 1.dp, top = 1.dp))
val weed = HashtagIcon(R.drawable.weed, "Weed", Modifier.padding(start = 1.dp, bottom = 0.dp, top = 0.dp))

@Immutable
class HashtagIcon(
    val icon: Int,
    val description: String,
    val modifier: Modifier = Modifier,
)
