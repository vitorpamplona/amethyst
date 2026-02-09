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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Tunestr
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05VerifState
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserStatuses
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.NIP05IconSize
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.aTag.firstTaggedAddress
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.firstTaggedEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun ObserveDisplayNip05Status(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAuthor(baseNote = baseNote, accountViewModel) {
        ObserveDisplayNip05Status(it, accountViewModel, nav)
    }
}

@Composable
fun ObserveDisplayNip05Status(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val statuses by observeUserStatuses(baseUser, accountViewModel)
    val nip05State by baseUser.nip05State().flow.collectAsStateWithLifecycle()

    VerifyAndDisplayNIP05OrStatusLine(
        nip05State,
        statuses,
        baseUser,
        accountViewModel,
        nav,
    )
}

@Composable
private fun VerifyAndDisplayNIP05OrStatusLine(
    nip05State: Nip05State,
    statuses: ImmutableList<AddressableNote>,
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (nip05State is Nip05State.Exists) {
            val nip05VerifState by nip05State.verificationState.collectAsStateWithLifecycle()

            if (nip05VerifState.isExpired()) {
                LaunchedEffect(key1 = nip05VerifState) {
                    accountViewModel.runOnIO {
                        nip05State.checkAndUpdate(Amethyst.instance.nip05Client)
                    }
                }
            }

            if (nip05VerifState is Nip05VerifState.Verified && !statuses.isEmpty()) {
                ObserveRotateStatuses(statuses, accountViewModel, nav)
            } else {
                DisplayNIP05(nip05State, nip05VerifState, accountViewModel)
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

@Composable
fun ObserveRotateStatuses(
    statuses: ImmutableList<AddressableNote>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ObserveAllStatusesToAvoidSwitchigAllTheTime(statuses, accountViewModel)

    RotateStatuses(
        statuses,
        accountViewModel,
        nav,
    )
}

@Composable
fun ObserveAllStatusesToAvoidSwitchigAllTheTime(
    statuses: ImmutableList<AddressableNote>,
    accountViewModel: AccountViewModel,
) {
    statuses.map {
        EventFinderFilterAssemblerSubscription(it, accountViewModel)
    }
}

@Composable
fun RotateStatuses(
    statuses: ImmutableList<AddressableNote>,
    accountViewModel: AccountViewModel,
    nav: INav,
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
    nav: INav,
) {
    val noteState by observeNote(addressableNote, accountViewModel)
    val noteEvent = noteState.note.event as? StatusEvent ?: return

    DisplayStatus(noteEvent, accountViewModel, nav)
}

@Composable
fun DisplayStatus(
    event: StatusEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisplayStatusInner(
        event.content,
        event.dTag(),
        event.firstTaggedUrl()?.ifBlank { null },
        event.firstTaggedAddress(),
        event.firstTaggedEvent(),
        accountViewModel,
        nav,
    )
}

@Composable
fun DisplayStatusInner(
    content: String,
    type: String,
    url: String?,
    nostrATag: Address?,
    nostrETag: ETag?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
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
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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
                            accountViewModel.account,
                        )?.let { nav.nav(it) }
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        null,
                        modifier = Size15Modifier,
                        tint = MaterialTheme.colorScheme.lessImportantLink,
                    )
                }
            }
        }
    } else if (nostrETag != null) {
        LoadNote(baseNoteHex = nostrETag.eventId, accountViewModel) {
            if (it != null) {
                Spacer(modifier = StdHorzSpacer)
                IconButton(
                    modifier = Size15Modifier,
                    onClick = {
                        routeFor(
                            it,
                            accountViewModel.account,
                        )?.let { nav.nav(it) }
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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
fun ObserveAndDisplayNIP05(
    nip05State: Nip05State.Exists,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current

    if (nip05State.nip05.name != "_") {
        Text(
            text = remember(nip05State) { AnnotatedString(nip05State.nip05.name) },
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.nip05,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    ObserveAndRenderNIP05VerifiedSymbol(nip05State, 1, NIP05IconSize, accountViewModel)

    ClickableTextPrimary(
        text = nip05State.nip05.domain,
        onClick = { runCatching { uri.openUri("https://${nip05State.nip05.domain}") } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
        maxLines = 1,
        overflow = TextOverflow.Visible,
    )
}

@Composable
fun DisplayNIP05(
    nip05State: Nip05State.Exists,
    nip05VerifState: Nip05VerifState,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current

    if (nip05State.nip05.name != "_") {
        Text(
            text = remember(nip05State) { AnnotatedString(nip05State.nip05.name) },
            fontSize = Font14SP,
            color = MaterialTheme.colorScheme.nip05,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    RenderNIP05VerifiedSymbol(nip05VerifState, 1, NIP05IconSize, accountViewModel)

    ClickableTextPrimary(
        text = nip05State.nip05.domain,
        onClick = { runCatching { uri.openUri("https://${nip05State.nip05.domain}") } },
        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
        maxLines = 1,
        overflow = TextOverflow.Visible,
    )
}

@Composable
fun ObserveAndRenderNIP05VerifiedSymbol(
    nip05State: Nip05State.Exists,
    compositionSizeReference: Int,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val state by nip05State.verificationState.collectAsStateWithLifecycle()

    if (state.isExpired()) {
        LaunchedEffect(key1 = state) {
            accountViewModel.runOnIO {
                nip05State.checkAndUpdate(Amethyst.instance.nip05Client)
            }
        }
    }

    RenderNIP05VerifiedSymbol(state, compositionSizeReference, modifier, accountViewModel)
}

@Composable
fun RenderNIP05VerifiedSymbol(
    state: Nip05VerifState,
    compositionSizeReference: Int,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    CrossfadeIfEnabled(targetState = state, accountViewModel = accountViewModel) {
        when (it) {
            is Nip05VerifState.Verifying ->
                Icon(
                    imageVector = Icons.Default.Downloading,
                    contentDescription = stringRes(id = R.string.nip05_checking),
                    modifier = modifier,
                    tint = Color.Yellow,
                )
            is Nip05VerifState.NotStarted ->
                Icon(
                    imageVector = Icons.Default.Downloading,
                    contentDescription = stringRes(id = R.string.nip05_checking),
                    modifier = modifier,
                    tint = Color.Yellow,
                )
            is Nip05VerifState.Verified ->
                Icon(
                    painter = painterRes(R.drawable.nip_05, compositionSizeReference),
                    contentDescription = stringRes(id = R.string.nip05_verified),
                    modifier = modifier,
                    tint = Color.Unspecified,
                )
            is Nip05VerifState.Failed ->
                Icon(
                    imageVector = Icons.Default.Report,
                    contentDescription = stringRes(id = R.string.nip05_failed),
                    modifier = modifier,
                    tint = Color.Red,
                )
            is Nip05VerifState.Error ->
                Icon(
                    imageVector = Icons.Default.Report,
                    contentDescription = stringRes(id = R.string.nip05_failed),
                    modifier = modifier,
                    tint = Color.Red,
                )
        }
    }
}
