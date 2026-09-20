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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_caches_here
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.GeocacheTab
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geocaches.dal.GeocacheListingKinds
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import org.jetbrains.compose.resources.stringResource

/**
 * "N caches here", shown on a geohash feed when there are any.
 *
 * A player scrolling a place's feed is exactly the person who wants to know there is something
 * hidden in it, and the geohash screen is where Amethyst already answers "what is going on
 * here". The chip is absent rather than zeroed when the cell holds nothing, because a permanent
 * "0 caches here" is noise on every other place in the world.
 *
 * Counting reads what is already in [LocalCache] rather than issuing a query. The geocaching
 * subscription is what fills that cache, so the number is "caches Amethyst knows about here" —
 * which is the honest claim, and it grows as the hub is used rather than pretending to be a
 * census of the relay network.
 */
@Composable
fun GeocachesHereChip(
    geohash: String,
    nav: INav,
) {
    val palette = rememberGeocachePalette()

    val count =
        remember(geohash) {
            LocalCache.addressables
                .filterIntoSet(GeocacheListingKinds) { _, note ->
                    val event = note.event
                    // A listing publishes its whole ladder from 3 to 9 characters, so a cell
                    // match is a prefix match against any tagged level rather than equality.
                    event is GeocacheListingEvent &&
                        !event.isArchived() &&
                        event.geohashes().any { it.startsWith(geohash) || geohash.startsWith(it) }
                }.size
        }

    if (count == 0) return

    Row(
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(palette.wash(palette.live))
                .clickable { nav.nav(Route.Geocaches(GeocacheTab.MAP)) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Explore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = palette.live,
        )
        Text(
            text = stringResource(Res.string.geocache_caches_here, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
