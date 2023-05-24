package com.vitorpamplona.amethyst.ui.note

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
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReplyInformation(replyTo: List<Note>?, mentions: List<String>, account: Account, nav: (String) -> Unit) {
    var dupMentions by remember { mutableStateOf<List<User>?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            dupMentions = mentions.mapNotNull { LocalCache.checkGetOrCreateUser(it) }
        }
    }

    if (dupMentions != null) {
        ReplyInformation(replyTo, dupMentions, account) {
            nav("User/${it.pubkeyHex}")
        }
    }
}

@Composable
fun ReplyInformation(replyTo: List<Note>?, dupMentions: List<User>?, account: Account, prefix: String = "", onUserTagClick: (User) -> Unit) {
    val mentions = dupMentions?.toSet()?.sortedBy { !account.userProfile().isFollowingCached(it) }
    var expanded by remember { mutableStateOf((mentions?.size ?: 0) <= 2) }

    FlowRow() {
        if (mentions != null && mentions.isNotEmpty()) {
            if (replyTo != null && replyTo.isNotEmpty()) {
                val repliesToDisplay = if (expanded) mentions else mentions.take(2)

                Text(
                    stringResource(R.string.replying_to),
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )

                repliesToDisplay.forEachIndexed { idx, user ->
                    val innerUserState by user.live().metadata.observeAsState()
                    val innerUser = innerUserState?.user

                    innerUser?.let { myUser ->
                        CreateClickableTextWithEmoji(
                            clickablePart = "$prefix@${myUser.toBestDisplayName()}",
                            tags = myUser.info?.latestMetadata?.tags,
                            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
                            onClick = { onUserTagClick(myUser) }
                        )

                        if (expanded) {
                            if (idx < repliesToDisplay.size - 2) {
                                Text(
                                    ", ",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                )
                            } else if (idx < repliesToDisplay.size - 1) {
                                Text(
                                    "${stringResource(R.string.and)}",
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
                                    "${stringResource(R.string.and)}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                )

                                ClickableText(
                                    AnnotatedString("${mentions.size - 2}"),
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
}

@Composable
fun ReplyInformationChannel(replyTo: List<Note>?, mentions: List<String>, channel: Channel, account: Account, nav: (String) -> Unit) {
    var sortedMentions by remember { mutableStateOf<List<User>?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            sortedMentions = mentions
                .mapNotNull { LocalCache.checkGetOrCreateUser(it) }
                .toSet()
                .sortedBy { account.isFollowing(it) }
        }
    }

    if (sortedMentions != null) {
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

@Composable
fun ReplyInformationChannel(replyTo: List<Note>?, mentions: List<User>?, channel: Channel, nav: (String) -> Unit) {
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

@Composable
fun ReplyInformationChannel(
    replyTo: List<Note>?,
    mentions: List<User>?,
    baseChannel: Channel,
    prefix: String = "",
    onUserTagClick: (User) -> Unit,
    onChannelTagClick: (Channel) -> Unit
) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel ?: return

    FlowRow() {
        Text(
            stringResource(R.string.in_channel),
            fontSize = 13.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )

        ClickableText(
            AnnotatedString("${channel.info.name} "),
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

                val mentionSet = mentions.toSet()

                mentionSet.forEachIndexed { idx, user ->
                    val innerUserState by user.live().metadata.observeAsState()
                    val innerUser = innerUserState?.user

                    innerUser?.let { myUser ->
                        CreateClickableTextWithEmoji(
                            clickablePart = "$prefix@${myUser.toBestDisplayName()}",
                            tags = myUser.info?.latestMetadata?.tags,
                            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary.copy(alpha = 0.52f), fontSize = 13.sp),
                            onClick = { onUserTagClick(myUser) }
                        )

                        if (idx < mentionSet.size - 2) {
                            Text(
                                ", ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        } else if (idx < mentionSet.size - 1) {
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
}
