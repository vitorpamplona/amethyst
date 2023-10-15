package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
                    color = MaterialTheme.colorScheme.placeholderText
                )

                repliesToDisplay.forEachIndexed { idx, user ->
                    ReplyInfoMention(user, prefix, onUserTagClick)

                    if (expanded) {
                        if (idx < repliesToDisplay.size - 2) {
                            Text(
                                ", ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.placeholderText
                            )
                        } else if (idx < repliesToDisplay.size - 1) {
                            Text(
                                stringResource(R.string.and),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.placeholderText
                            )
                        }
                    } else {
                        if (idx < repliesToDisplay.size - 1) {
                            Text(
                                ", ",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.placeholderText
                            )
                        } else if (idx < repliesToDisplay.size) {
                            Text(
                                stringResource(R.string.and),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.placeholderText
                            )

                            ClickableText(
                                AnnotatedString("${sortedMentions.size - 2}"),
                                style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.lessImportantLink, fontSize = 13.sp),
                                onClick = { expanded = true }
                            )

                            Text(
                                " ${stringResource(R.string.others)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.placeholderText
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
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var sortedMentions by remember { mutableStateOf<ImmutableList<User>>(persistentListOf()) }

    LaunchedEffect(Unit) {
        accountViewModel.loadMentions(mentions) { newSortedMentions ->
            if (newSortedMentions != sortedMentions) {
                sortedMentions = newSortedMentions
            }
        }
    }

    if (sortedMentions.isNotEmpty()) {
        ReplyInformationChannel(
            replyTo,
            sortedMentions,
            onUserTagClick = {
                nav("User/${it.pubkeyHex}")
            }
        )
        Spacer(modifier = StdVertSpacer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyInformationChannel(
    replyTo: ImmutableList<Note>?,
    mentions: ImmutableList<User>?,
    prefix: String = "",
    onUserTagClick: (User) -> Unit
) {
    FlowRow() {
        if (mentions != null && mentions.isNotEmpty()) {
            if (replyTo != null && replyTo.isNotEmpty()) {
                Text(
                    stringResource(id = R.string.replying_to),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.placeholderText
                )

                mentions.forEachIndexed { idx, user ->
                    ReplyInfoMention(user, prefix, onUserTagClick)

                    if (idx < mentions.size - 2) {
                        Text(
                            ", ",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.placeholderText
                        )
                    } else if (idx < mentions.size - 1) {
                        Text(
                            " ${stringResource(id = R.string.and)} ",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.placeholderText
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
            color = MaterialTheme.colorScheme.lessImportantLink,
            fontSize = 13.sp
        ),
        onClick = { onUserTagClick(user) }
    )
}
