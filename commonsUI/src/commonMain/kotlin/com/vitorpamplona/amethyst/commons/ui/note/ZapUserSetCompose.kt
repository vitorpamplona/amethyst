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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.notifications.ZapUserSetCard
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.toImmutableList

/**
 * Zaps on the user's profile rather than on a note, as a [NotificationActivityCard] whose second line
 * says so. [ZapUserSetCard.user] is the zapped profile (the user), not a zapper.
 */
@Composable
fun ZapUserSetCompose(
    zapSetCard: ZapUserSetCard,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val backgroundColor =
        calculateBackgroundColor(
            createdAt = zapSetCard.createdAt,
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    MapZaps(zapSetCard.zapEvents, accountViewModel) { zaps ->
        val actors =
            remember(zaps) {
                val seen = HashSet<String>()
                zaps
                    .filter { zap -> zap.user?.let { seen.add(it.pubkeyHex) } ?: true }
                    .map { ActivityActor(it.user, ActivityKind.ZAP) }
                    .toImmutableList()
            }
        val total = remember(zapSetCard) { zapTotal(zapSetCard.zapEvents, emptyList()) }
        val comment = remember(zaps) { latestZapComment(zaps, emptyList()) }

        NotificationActivityCard(
            actors = actors,
            createdAt = zapSetCard.createdAt,
            zapTotal = total,
            comment = comment,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
            modifier =
                Modifier
                    .drawBehind { drawRect(backgroundColor.value) }
                    .clickable { nav.nav(routeFor(zapSetCard.user)) },
            kindIcon = { ZappedIcon(it) },
        ) {
            NotificationProfileLine()
        }
    }
}
