package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ReplyInformation(
    replyTo: ImmutableList<Note>?,
    mentions: ImmutableList<String>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var sortedMentions by remember { mutableStateOf<ImmutableList<User>?>(null) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            sortedMentions = mentions.mapNotNull { LocalCache.checkGetOrCreateUser(it) }
                .toSet()
                .sortedBy { !accountViewModel.account.userProfile().isFollowingCached(it) }
                .toImmutableList()
        }
    }

    if (sortedMentions != null) {
        ReplyInformation(replyTo, sortedMentions) {
            nav("User/${it.pubkeyHex}")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyInformation(
    replyTo: ImmutableList<Note>?,
    sortedMentions: ImmutableList<User>?,
    prefix: String = "",
    onUserTagClick: (User) -> Unit
) {
    var expanded by remember { mutableStateOf((sortedMentions?.size ?: 0) <= 2) }

    FlowRow() {
        if (sortedMentions != null && sortedMentions.isNotEmpty()) {
            if (replyTo != null && replyTo.isNotEmpty()) {
                val repliesToDisplay = if (expanded) sortedMentions else sortedMentions.take(2)

                Text(
                    stringResource(R.string.replying_to),
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )

                repliesToDisplay.forEachIndexed { idx, user ->
                    ReplyInfoMention(user, prefix, onUserTagClick)

                    if (expanded) {
                        if (idx < repliesToDisplay.size - 2) {
                            Text(
                                ", ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        } else if (idx < repliesToDisplay.size - 1) {
                            Text(
                                stringResource(R.string.and),
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    } else {
                        if (idx < repliesToDisplay.size - 1) {
                            Text(
                                ", ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        } else if (idx < repliesToDisplay.size) {
                            Text(
                                stringResource(R.string.and),
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            ClickableText(
                                AnnotatedString("${sortedMentions.size - 2}"),
                                style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
                                onClick = { expanded = true }
                            )

                            Text(
                                " ${stringResource(R.string.others)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyInformationChannel(
    replyTo: ImmutableList<Note>?,
    mentions: ImmutableList<String>,
    channelHex: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var sortedMentions by remember { mutableStateOf<ImmutableList<User>?>(null) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            sortedMentions = mentions
                .mapNotNull { LocalCache.checkGetOrCreateUser(it) }
                .toSet()
                .sortedBy { accountViewModel.account.isFollowing(it) }
                .toImmutableList()
        }
    }

    if (sortedMentions != null) {
        LoadChannel(channelHex) { channel ->
            ReplyInformationChannel(
                replyTo,
                sortedMentions,
                channel,
                onUserTagClick = {
                    nav("User/${it.pubkeyHex}")
                },
                onChannelTagClick = {
                    nav("Channel/${it.idHex}")
                }
            )
        }
    }
}

@Composable
fun ReplyInformationChannel(replyTo: ImmutableList<Note>?, mentions: ImmutableList<User>?, channel: Channel, nav: (String) -> Unit) {
    ReplyInformationChannel(
        replyTo,
        mentions,
        channel,
        onUserTagClick = {
            nav("User/${it.pubkeyHex}")
        },
        onChannelTagClick = {
            nav("Channel/${it.idHex}")
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyInformationChannel(
    replyTo: ImmutableList<Note>?,
    mentions: ImmutableList<User>?,
    baseChannel: Channel,
    prefix: String = "",
    onUserTagClick: (User) -> Unit,
    onChannelTagClick: (Channel) -> Unit
) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel ?: return

    val channelName = remember(channelState) {
        AnnotatedString("${channel.info.name} ")
    }

    FlowRow() {
        Text(
            stringResource(R.string.in_channel),
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )

        ClickableText(
            text = channelName,
            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
            onClick = { onChannelTagClick(channel) }
        )

        if (mentions != null && mentions.isNotEmpty()) {
            if (replyTo != null && replyTo.isNotEmpty()) {
                Text(
                    stringResource(id = R.string.replying_to),
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )

                mentions.forEachIndexed { idx, user ->
                    ReplyInfoMention(user, prefix, onUserTagClick)

                    if (idx < mentions.size - 2) {
                        Text(
                            ", ",
                            fontSize = 13.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    } else if (idx < mentions.size - 1) {
                        Text(
                            " ${stringResource(id = R.string.add)} ",
                            fontSize = 13.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyInfoMention(
    user: User,
    prefix: String,
    onUserTagClick: (User) -> Unit
) {
    val innerUserState by user.live().metadata.observeAsState()

    CreateClickableTextWithEmoji(
        clickablePart = remember(innerUserState) { "$prefix${innerUserState?.user?.toBestDisplayName()}" },
        tags = remember(innerUserState) { innerUserState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() },
        style = LocalTextStyle.current.copy(
            color = MaterialTheme.colors.primary.copy(alpha = 0.52f),
            fontSize = 13.sp
        ),
        onClick = { onUserTagClick(user) }
    )
}
