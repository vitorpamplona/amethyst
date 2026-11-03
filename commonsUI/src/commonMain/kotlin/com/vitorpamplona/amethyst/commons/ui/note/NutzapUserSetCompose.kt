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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.notifications.NutzapUserSetCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nutzap
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.persistentListOf

/**
 * NIP-61 nutzaps that name no note, from one sender, as a [NotificationActivityCard]: the sender's
 * face with the cashu badge, the total, the newest comment, and "Your profile" as the target.
 * Plaintext over the wire, so nothing to decrypt.
 */
@Composable
fun NutzapUserSetCompose(
    nutzapSetCard: NutzapUserSetCard,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val backgroundColor =
        calculateBackgroundColor(
            createdAt = nutzapSetCard.createdAt,
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    val actors = remember(nutzapSetCard) { persistentListOf(ActivityActor(nutzapSetCard.user, ActivityKind.NUTZAP)) }
    val total = remember(nutzapSetCard) { zapTotal(emptyList(), nutzapSetCard.nutzapEvents) }
    val comment = remember(nutzapSetCard) { latestZapComment(emptyList(), nutzapSetCard.nutzapEvents) }

    NotificationActivityCard(
        actors = actors,
        createdAt = nutzapSetCard.createdAt,
        zapTotal = total,
        comment = comment,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
        modifier =
            Modifier
                .drawBehind { drawRect(backgroundColor.value) }
                .clickable { nav.nav(routeFor(nutzapSetCard.user)) },
        kindIcon = {
            Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = stringRes(Res.string.nutzap),
                modifier = it,
                tint = BitcoinOrange,
            )
        },
    ) {
        NotificationProfileLine()
    }
}
