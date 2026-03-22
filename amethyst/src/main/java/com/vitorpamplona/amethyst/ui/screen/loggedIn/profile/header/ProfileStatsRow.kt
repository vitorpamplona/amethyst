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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserContactCardsFollowerCount
import com.vitorpamplona.amethyst.ui.note.showAmountInteger
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.followers.dal.UserProfileFollowersUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.follows.dal.UserProfileFollowsUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.zaps.dal.UserProfileZapsViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun ProfileStatsRow(
    baseUser: User,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: UserProfileZapsViewModel,
    accountViewModel: AccountViewModel,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onZapsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FollowingStatItem(
            followsFeedViewModel = followsFeedViewModel,
            onClick = onFollowingClick,
        )

        FollowersStatItem(
            baseUser = baseUser,
            followersFeedViewModel = followersFeedViewModel,
            accountViewModel = accountViewModel,
            onClick = onFollowersClick,
        )

        ZapsStatItem(
            zapFeedViewModel = zapFeedViewModel,
            onClick = onZapsClick,
        )
    }
}

@Composable
private fun FollowingStatItem(
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    onClick: () -> Unit,
) {
    val followCount by followsFeedViewModel.followCount.collectAsStateWithLifecycle()

    val displayValue =
        if (followCount > 0) {
            followCount.toString()
        } else {
            "--"
        }

    StatItem(
        value = displayValue,
        label = stringRes(R.string.following).trim(),
        onClick = onClick,
    )
}

@Composable
private fun FollowersStatItem(
    baseUser: User,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val followerCountFromCards by observeUserContactCardsFollowerCount(baseUser, accountViewModel)
    val followerCountLocal by followersFeedViewModel.followerCount.collectAsStateWithLifecycle()

    val displayValue =
        if (followerCountFromCards != "--") {
            followerCountFromCards
        } else if (followerCountLocal > 0) {
            followerCountLocal.toString()
        } else {
            "--"
        }

    StatItem(
        value = displayValue,
        label = stringRes(R.string.followers).trim(),
        onClick = onClick,
    )
}

@Composable
private fun ZapsStatItem(
    zapFeedViewModel: UserProfileZapsViewModel,
    onClick: () -> Unit,
) {
    val zapAmount by zapFeedViewModel.totalReceivedZaps.collectAsStateWithLifecycle()

    val displayValue = showAmountInteger(zapAmount)

    StatItem(
        value = displayValue.ifBlank { "--" },
        label = stringRes(R.string.zaps),
        onClick = onClick,
    )
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "stat_value_$label",
        ) { targetValue ->
            Text(
                text = targetValue,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.placeholderText,
            textAlign = TextAlign.Center,
        )
    }
}
