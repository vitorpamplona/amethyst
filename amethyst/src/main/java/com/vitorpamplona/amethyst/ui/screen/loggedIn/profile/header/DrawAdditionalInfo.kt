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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUser
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.DisplayNip05ProfileStatus
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.DrawPlayName
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.apps.DisplayAppRecommendations
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.apps.UserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header.badges.DisplayBadges
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.ShowQRDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size25Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip39ExtIdentities.GitHubIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag
import com.vitorpamplona.quartz.nip39ExtIdentities.MastodonIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TelegramIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TwitterIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims

@Composable
fun DrawAdditionalInfo(
    baseUser: User,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by observeUser(baseUser, accountViewModel)
    val user = userState?.user ?: return
    val uri = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    user.toBestDisplayName().let {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 7.dp)) {
            CreateTextWithEmoji(
                text = it,
                tags = user.info?.tags ?: EmptyTagList,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
            )
            Spacer(StdHorzSpacer)
            user.info?.pronouns?.let {
                Text(
                    text = "($it)",
                )
                Spacer(StdHorzSpacer)
            }

            DrawPlayName(it)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = user.pubkeyDisplayHex(),
            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp),
            color = MaterialTheme.colorScheme.placeholderText,
        )

        IconButton(
            modifier =
                Modifier
                    .size(25.dp)
                    .padding(start = 5.dp),
            onClick = { clipboardManager.setText(AnnotatedString(user.pubkeyNpub())) },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringRes(id = R.string.copy_npub_to_clipboard),
                modifier = Size15Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        var dialogOpen by remember { mutableStateOf(false) }

        if (dialogOpen) {
            ShowQRDialog(
                user = user,
                accountViewModel = accountViewModel,
                onScan = {
                    dialogOpen = false
                    nav.nav(it)
                },
                onClose = { dialogOpen = false },
            )
        }

        IconButton(
            modifier = Size25Modifier,
            onClick = { dialogOpen = true },
        ) {
            Icon(
                painter = painterRes(R.drawable.ic_qrcode),
                contentDescription = stringRes(id = R.string.show_npub_as_a_qr_code),
                modifier = Size15Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }

    DisplayBadges(baseUser, accountViewModel, nav)

    DisplayNip05ProfileStatus(user, accountViewModel)

    val website = user.info?.website
    if (!website.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                tint = MaterialTheme.colorScheme.placeholderText,
                imageVector = Icons.Default.Link,
                contentDescription = stringRes(R.string.website),
                modifier = Modifier.size(16.dp),
            )

            ClickableTextPrimary(
                text = website.removePrefix("https://"),
                onClick = {
                    website.let {
                        runCatching {
                            if (it.contains("://")) {
                                uri.openUri(it)
                            } else {
                                uri.openUri("http://$it")
                            }
                        }
                    }
                },
                modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
            )
        }
    }

    val lud16 = remember(userState) { user.info?.lud16?.trim() ?: user.info?.lud06?.trim() }
    val pubkeyHex = remember { baseUser.pubkeyHex }
    DisplayLNAddress(lud16, pubkeyHex, accountViewModel, nav)
    DisplayMoneroTipping(user.info?.moneroAddress(), pubkeyHex, accountViewModel, nav)

    val identities = user.latestMetadata?.identityClaims()
    if (!identities.isNullOrEmpty()) {
        identities.forEach { identity: IdentityClaimTag ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    tint = Color.Unspecified,
                    painter = painterRes(id = getIdentityClaimIcon(identity)),
                    contentDescription = stringRes(getIdentityClaimDescription(identity)),
                    modifier = Modifier.size(16.dp),
                )

                ClickableTextPrimary(
                    text = identity.identity,
                    onClick = { runCatching { uri.openUri(identity.toProofUrl()) } },
                    modifier =
                        Modifier
                            .padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                            .weight(1f),
                )
            }
        }
    }

    user.info?.about?.let {
        Row(
            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
        ) {
            val defaultBackground = MaterialTheme.colorScheme.background
            val background = remember { mutableStateOf(defaultBackground) }

            TranslatableRichTextViewer(
                content = it,
                canPreview = false,
                quotesLeft = 1,
                tags = EmptyTagList,
                backgroundColor = background,
                id = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }

    DisplayAppRecommendations(appRecommendations, accountViewModel, nav)
}

fun getIdentityClaimIcon(identity: IdentityClaimTag): Int =
    when (identity) {
        is TwitterIdentity -> R.drawable.x
        is TelegramIdentity -> R.drawable.telegram
        is MastodonIdentity -> R.drawable.mastodon
        is GitHubIdentity -> R.drawable.github
        else -> R.drawable.github
    }

fun getIdentityClaimDescription(identity: IdentityClaimTag): Int =
    when (identity) {
        is TwitterIdentity -> R.string.twitter
        is TelegramIdentity -> R.string.telegram
        is MastodonIdentity -> R.string.mastodon
        is GitHubIdentity -> R.string.github
        else -> R.drawable.github
    }
