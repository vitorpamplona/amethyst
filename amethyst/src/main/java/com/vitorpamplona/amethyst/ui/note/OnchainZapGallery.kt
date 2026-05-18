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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.OnchainZapEntry
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteZaps
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifier
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.ui.theme.overPictureBackground
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.math.BigDecimal

private fun onOnchainZapEntryClick(
    entry: OnchainZapEntry,
    nav: INav,
) {
    entry.source.author?.let { nav.nav(routeFor(it)) }
}

@Composable
internal fun WatchOnchainZapsAndRenderGallery(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    // Reuse the same flow the lightning gallery subscribes to. Note.addOnchainZap
    // invalidates flowSet.zaps, so this composable refreshes when on-chain zaps
    // arrive or upgrade pending → confirmed.
    val zapsState by observeNoteZaps(baseNote, accountViewModel)
    val entries =
        zapsState
            ?.note
            ?.onchainZaps
            ?.values
            ?.toImmutableList() ?: persistentListOf()

    if (entries.isNotEmpty()) {
        RenderOnchainZapGallery(entries, nav, accountViewModel)
    }
}

@Composable
private fun RenderOnchainZapGallery(
    entries: ImmutableList<OnchainZapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(Modifier.fillMaxWidth()) {
        Box(modifier = WidthAuthorPictureModifier) {
            OnchainZappedIcon(
                modifier = Modifier.size(Size25dp).align(Alignment.TopEnd),
            )
        }

        OnchainZapAuthorGallery(entries, nav, accountViewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnchainZapAuthorGallery(
    entries: ImmutableList<OnchainZapEntry>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow {
            entries.forEach { entry ->
                OnchainZapEntryRow(entry, nav, accountViewModel)
            }
        }
    }
}

@Composable
private fun OnchainZapEntryRow(
    entry: OnchainZapEntry,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val user = entry.source.author
    val amountText =
        remember(entry.verifiedSats) {
            showAmount(BigDecimal.valueOf(entry.verifiedSats))
        }
    val avatarAlpha = if (entry.confirmed) 1f else 0.6f

    Row(
        modifier =
            Modifier.clickable { onOnchainZapEntryClick(entry, nav) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Size35Modifier,
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Only the avatar dims for pending entries. The amount overlay and clock
            // badge stay at full opacity so they remain readable.
            Box(modifier = Modifier.alpha(avatarAlpha)) {
                WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
                    user,
                    accountViewModel,
                )
            }

            // Amount overlay — same look as the lightning row's CrossfadeToDisplayAmount.
            Box(
                modifier =
                    Modifier
                        .size(Size35dp)
                        .clip(CircleShape),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val overlayBg = MaterialTheme.colorScheme.overPictureBackground
                Box(
                    modifier = remember(overlayBg) { Modifier.width(Size35dp).background(overlayBg) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = amountText,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.bitcoinColor,
                        fontSize = 12.sp,
                        modifier = bottomPadding1dp,
                    )
                }
            }

            if (!entry.confirmed) {
                // TopStart so the badge doesn't collide with the FollowingIcon
                // that WatchUserMetadataAndFollowsAndRenderUserProfilePicture
                // paints at TopEnd for followed users.
                Box(
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    PendingClockBadge(modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
