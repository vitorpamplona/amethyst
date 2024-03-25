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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Tunestr
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.LoadStatuses
import com.vitorpamplona.amethyst.ui.note.NIP05CheckingIcon
import com.vitorpamplona.amethyst.ui.note.NIP05FailedVerification
import com.vitorpamplona.amethyst.ui.note.NIP05VerifiedIcon
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.NIP05IconSize
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.UserMetadata
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun nip05VerificationAsAState(
    userMetadata: UserMetadata,
    pubkeyHex: String,
    accountViewModel: AccountViewModel,
): MutableState<Boolean?> {
    val nip05Verified =
        remember(userMetadata.nip05) {
            // starts with null if must verify or already filled in if verified in the last hour
            val default =
                if ((userMetadata.nip05LastVerificationTime ?: 0) > TimeUtils.oneHourAgo()) {
                    userMetadata.nip05Verified
                } else {
                    null
                }

            mutableStateOf(default)
        }

    if (nip05Verified.value == null) {
        LaunchedEffect(key1 = userMetadata.nip05) {
            accountViewModel.verifyNip05(userMetadata, pubkeyHex) { newVerificationStatus ->
                if (nip05Verified.value != newVerificationStatus) {
                    nip05Verified.value = newVerificationStatus
                }
            }
        }
    }

    return nip05Verified
}

@Composable
fun ObserveDisplayNip05Status(
    baseNote: Note,
    columnModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchAuthor(baseNote = baseNote) {
        ObserveDisplayNip05Status(it, columnModifier, accountViewModel, nav)
    }
}

@Composable
fun ObserveDisplayNip05Status(
    baseUser: User,
    columnModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val nip05 by baseUser.live().nip05Changes.observeAsState(baseUser.nip05())

    LoadStatuses(baseUser, accountViewModel) { statuses ->
        Crossfade(
            targetState = nip05,
            modifier = columnModifier,
            label = "ObserveDisplayNip05StatusCrossfade",
        ) {
            VerifyAndDisplayNIP05OrStatusLine(
                it,
                statuses,
                baseUser,
                columnModifier,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun VerifyAndDisplayNIP05OrStatusLine(
    nip05: String?,
    statuses: ImmutableList<AddressableNote>,
    baseUser: User,
    columnModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(modifier = columnModifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (nip05 != null) {
                val nip05Verified =
                    nip05VerificationAsAState(baseUser.info!!, baseUser.pubkeyHex, accountViewModel)

                if (nip05Verified.value != true) {
                    DisplayNIP05(nip05, nip05Verified)
                } else if (!statuses.isEmpty()) {
                    RotateStatuses(statuses, accountViewModel, nav)
                } else {
                    DisplayNIP05(nip05, nip05Verified)
                }
            } else {
                if (!statuses.isEmpty()) {
                    RotateStatuses(statuses, accountViewModel, nav)
                } else {
                    DisplayUsersNpub(baseUser.pubkeyDisplayHex())
                }
            }
        }
    }
}

@Composable
fun RotateStatuses(
    statuses: ImmutableList<AddressableNote>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var indexToDisplay by remember(statuses) { mutableIntStateOf(0) }

    DisplayStatus(statuses[indexToDisplay], accountViewModel, nav)

    if (statuses.size > 1) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(10.seconds)
                indexToDisplay = (indexToDisplay + 1) % statuses.size
            }
        }
    }
}

@Composable
fun DisplayUsersNpub(npub: String) {
    Text(
        text = npub,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun DisplayStatus(
    addressableNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by addressableNote.live().metadata.observeAsState()

    val content = remember(noteState) { addressableNote.event?.content() ?: "" }
    val type = remember(noteState) { (addressableNote.event as? AddressableEvent)?.dTag() ?: "" }
    val url = remember(noteState) { addressableNote.event?.firstTaggedUrl()?.ifBlank { null } }
    val nostrATag = remember(noteState) { addressableNote.event?.firstTaggedAddress() }
    val nostrHexID =
        remember(noteState) { addressableNote.event?.firstTaggedEvent()?.ifBlank { null } }

    when (type) {
        "music" ->
            Icon(
                imageVector = CustomHashTagIcons.Tunestr,
                null,
                modifier = Size15Modifier.padding(end = Size5dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        else -> {}
    }

    Text(
        text = content,
        fontSize = Font14SP,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    if (url != null) {
        val uri = LocalUriHandler.current
        Spacer(modifier = StdHorzSpacer)
        IconButton(
            modifier = Size15Modifier,
            onClick = { runCatching { uri.openUri(url.trim()) } },
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                null,
                modifier = Size15Modifier,
                tint = MaterialTheme.colorScheme.lessImportantLink,
            )
        }
    } else if (nostrATag != null) {
        LoadAddressableNote(nostrATag, accountViewModel) { note ->
            if (note != null) {
                Spacer(modifier = StdHorzSpacer)
                IconButton(
                    modifier = Size15Modifier,
                    onClick = {
                        routeFor(
                            note,
                            accountViewModel.userProfile(),
                        )
                            ?.let { nav(it) }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        null,
                        modifier = Size15Modifier,
                        tint = MaterialTheme.colorScheme.lessImportantLink,
                    )
                }
            }
        }
    } else if (nostrHexID != null) {
        LoadNote(baseNoteHex = nostrHexID, accountViewModel) {
            if (it != null) {
                Spacer(modifier = StdHorzSpacer)
                IconButton(
                    modifier = Size15Modifier,
                    onClick = {
                        routeFor(
                            it,
                            accountViewModel.userProfile(),
                        )
                            ?.let { nav(it) }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        null,
                        modifier = Size15Modifier,
                        tint = MaterialTheme.colorScheme.lessImportantLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayNIP05(
    nip05: String,
    nip05Verified: MutableState<Boolean?>,
) {
    val uri = LocalUriHandler.current
    val (user, domain) =
        remember(nip05) {
            val parts = nip05.split("@")
            if (parts.size == 1) {
                listOf("_", parts[0])
            } else {
                listOf(parts[0], parts[1])
            }
        }

    if (user != "_") {
        Text(
            text = remember(nip05) { AnnotatedString(user) },
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.nip05,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    NIP05VerifiedSymbol(nip05Verified, NIP05IconSize)

    ClickableText(
        text = remember(nip05) { AnnotatedString(domain) },
        onClick = { runCatching { uri.openUri("https://$domain") } },
        style =
            LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
        maxLines = 1,
        overflow = TextOverflow.Visible,
    )
}

@Composable
private fun NIP05VerifiedSymbol(
    nip05Verified: MutableState<Boolean?>,
    modifier: Modifier,
) {
    Crossfade(targetState = nip05Verified.value) {
        when (it) {
            null -> NIP05CheckingIcon(modifier = modifier)
            true -> NIP05VerifiedIcon(modifier = modifier)
            false -> NIP05FailedVerification(modifier = modifier)
        }
    }
}

@Composable
fun DisplayNip05ProfileStatus(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current

    user.nip05()?.let { nip05 ->
        if (nip05.split("@").size <= 2) {
            val nip05Verified = nip05VerificationAsAState(user.info!!, user.pubkeyHex, accountViewModel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NIP05VerifiedSymbol(nip05Verified, Size16Modifier)
                var domainPadStart = 5.dp

                val (user, domain) =
                    remember(nip05) {
                        val parts = nip05.split("@")
                        if (parts.size == 1) {
                            listOf("_", parts[0])
                        } else {
                            listOf(parts[0], parts[1])
                        }
                    }

                if (user != "_") {
                    Text(
                        text = remember { AnnotatedString(user + "@") },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    domainPadStart = 0.dp
                }

                ClickableText(
                    text = AnnotatedString(domain),
                    onClick = { nip05.let { runCatching { uri.openUri("https://${it.split("@")[1]}") } } },
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = domainPadStart),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
