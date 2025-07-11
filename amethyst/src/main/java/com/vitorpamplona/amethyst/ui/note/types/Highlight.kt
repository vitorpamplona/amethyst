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
package com.vitorpamplona.amethyst.ui.note.types

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.DisplayEvent
import com.vitorpamplona.amethyst.ui.components.RenderUserAsClickableText
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.measureSpaceWidth
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueFor
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun RenderHighlight(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? HighlightEvent ?: return

    DisplayHighlight(
        comment = noteEvent.comment(),
        highlight = noteEvent.quote(),
        context = noteEvent.context(),
        authorHex = noteEvent.pubKey,
        url = noteEvent.inUrl(),
        postAddress = noteEvent.inPostAddress(),
        postVersion = noteEvent.inPostVersion(),
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayHighlight(
    comment: String?,
    highlight: String,
    context: String?,
    authorHex: String?,
    url: String?,
    postAddress: Address?,
    postVersion: ETag?,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    comment?.let {
        TranslatableRichTextViewer(
            content = it,
            canPreview = canPreview && !makeItShort,
            quotesLeft = quotesLeft,
            modifier = Modifier.fillMaxWidth(),
            tags = EmptyTagList,
            backgroundColor = backgroundColor,
            id = it,
            callbackUri = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    val quote =
        remember {
            highlight.split("\n").joinToString("\n") { "> *${it.removeSuffix(" ")}*" }
        }

    TranslatableRichTextViewer(
        content = quote,
        canPreview = canPreview && !makeItShort,
        quotesLeft = quotesLeft,
        modifier = Modifier.fillMaxWidth(),
        tags = EmptyTagList,
        backgroundColor = backgroundColor,
        id = quote,
        callbackUri = null,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    val spaceWidth = measureSpaceWidth(textStyle = LocalTextStyle.current)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spaceWidth),
        verticalArrangement = Arrangement.Center,
    ) {
        DisplayQuoteAuthor(
            highlightQuote = highlight,
            authorHex = authorHex,
            baseUrl = url,
            postAddress = postAddress,
            postVersion = postVersion,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun DisplayQuoteAuthor(
    highlightQuote: String,
    authorHex: String?,
    baseUrl: String?,
    postAddress: Address?,
    postVersion: ETag?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var userBase by remember { mutableStateOf<User?>(authorHex?.let { accountViewModel.getUserIfExists(it) }) }

    if (userBase == null && authorHex != null) {
        LaunchedEffect(authorHex) {
            accountViewModel.checkGetOrCreateUser(authorHex) { newUserBase ->
                userBase = newUserBase
            }
        }
    }

    var addressable by remember {
        mutableStateOf<AddressableNote?>(postAddress?.let { accountViewModel.getAddressableNoteIfExists(it) })
    }

    if (addressable == null && postAddress != null) {
        LaunchedEffect(key1 = postAddress) {
            val newNote =
                withContext(Dispatchers.IO) {
                    accountViewModel.getOrCreateAddressableNote(postAddress)
                }
            if (addressable != newNote) {
                addressable = newNote
            }
        }
    }

    var version by remember {
        mutableStateOf<Note?>(postVersion?.let { accountViewModel.getNoteIfExists(it.eventId) })
    }

    if (version == null && postVersion != null) {
        LaunchedEffect(key1 = postVersion) {
            val newNote =
                withContext(Dispatchers.IO) {
                    accountViewModel.getOrCreateNote(postVersion.eventId)
                }
            if (version != newNote) {
                version = newNote
            }
        }
    }

    if (addressable != null) {
        addressable?.let {
            DisplayEntryForNote(it, userBase, accountViewModel, nav)
        }
    } else if (version != null) {
        version?.let {
            DisplayEntryForNote(it, userBase, accountViewModel, nav)
        }
    } else if (baseUrl != null) {
        val url = "$baseUrl${if (baseUrl.contains("#")) "&" else "#"}:~:text=${Uri.encode(highlightQuote)}"

        DisplayEntryForAUrl(url, userBase, accountViewModel, nav)
    } else if (userBase != null) {
        userBase?.let {
            DisplayEntryForUser(it, accountViewModel, nav)
        }
    }
}

@Composable
fun DisplayEntryForUser(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userMetadata by observeUserInfo(baseUser, accountViewModel)

    CreateClickableTextWithEmoji(
        clickablePart = userMetadata?.bestName() ?: baseUser.pubkeyDisplayHex(),
        maxLines = 1,
        route = remember(baseUser) { routeFor(baseUser) },
        nav = nav,
        tags = userMetadata?.tags,
    )
}

@Composable
fun DisplayEntryForNote(
    note: Note,
    userBase: User?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(note, accountViewModel)

    val author = userBase ?: noteState?.note?.author

    if (author != null) {
        RenderUserAsClickableText(author, null, accountViewModel, nav)
    }

    val noteEvent = noteState.note.event as? BaseThreadedEvent ?: return

    val description = remember(noteEvent) { noteEvent.tags.firstTagValueFor("title", "subject", "alt") }

    Text("-", maxLines = 1)

    if (description != null) {
        ClickableTextPrimary(
            text = description,
            onClick = { routeFor(note, accountViewModel.userProfile())?.let { nav.nav(it) } },
        )
    } else {
        DisplayEvent(noteEvent.id, note.toNostrUri(), null, accountViewModel, nav)
    }
}

@Composable
fun DisplayEntryForAUrl(
    url: String,
    userBase: User?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (userBase != null) {
        DisplayEntryForUser(userBase, accountViewModel, nav)
    }

    val validatedUrl =
        remember {
            try {
                URL(url)
            } catch (e: Exception) {
                Log.w("Note Compose", "Invalid URI: $url")
                null
            }
        }

    validatedUrl?.host?.let { host ->
        if (userBase != null) {
            Text("-", maxLines = 1)
        }
        ClickableUrl(urlText = host, url = url)
    }
}
