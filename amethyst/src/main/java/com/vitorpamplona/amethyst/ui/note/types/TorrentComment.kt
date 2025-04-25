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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.countToHumanReadableBytes
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Preview
@Composable
fun TorrentCommentPreview() {
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav

    val comment =
        runBlocking {
            withContext(Dispatchers.IO) {
                val torrent =
                    TorrentEvent(
                        id = "e1ab66dd66e6ac4f32119deacf80d7c50787705d343d3bb896c099cf93821757",
                        pubKey = "0aa39e5aef99a000a7bdb0b499158c92bc4aa20fb65931a52d055b5eb6dff738",
                        createdAt = 1724177932,
                        tags =
                            arrayOf(
                                arrayOf("title", "bitcoin-core-27.0"),
                                arrayOf("btih", "2b2d123e5e831b245fb1dc5b8b71f89de4a90d00"),
                                arrayOf("t", "application"),
                                arrayOf("file", "SHA256SUMS", "2842"),
                                arrayOf("file", "SHA256SUMS.asc", "7590"),
                                arrayOf("file", "SHA256SUMS.ots", "573"),
                                arrayOf("file", "bitcoin-27.0-aarch64-linux-gnu-debug.tar.gz", "456216269"),
                                arrayOf("file", "bitcoin-27.0-aarch64-linux-gnu.tar.gz", "46953175"),
                                arrayOf("file", "bitcoin-27.0-arm-linux-gnueabihf-debug.tar.gz", "449092874"),
                                arrayOf("file", "bitcoin-27.0-arm-linux-gnueabihf.tar.gz", "42887090"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin-unsigned.tar.gz", "16417606"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin-unsigned.zip", "16451447"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin.tar.gz", "36433324"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin.zip", "16217968"),
                                arrayOf("file", "bitcoin-27.0-codesignatures-27.0.tar.gz", "335808"),
                                arrayOf("file", "bitcoin-27.0-powerpc64-linux-gnu-debug.tar.gz", "467821947"),
                                arrayOf("file", "bitcoin-27.0-powerpc64-linux-gnu.tar.gz", "53252604"),
                                arrayOf("file", "bitcoin-27.0-powerpc64le-linux-gnu-debug.tar.gz", "460024501"),
                                arrayOf("file", "bitcoin-27.0-powerpc64le-linux-gnu.tar.gz", "52020438"),
                                arrayOf("file", "bitcoin-27.0-riscv64-linux-gnu-debug.tar.gz", "355544520"),
                                arrayOf("file", "bitcoin-27.0-riscv64-linux-gnu.tar.gz", "47063263"),
                                arrayOf("file", "bitcoin-27.0-win64-debug.zip", "522357199"),
                                arrayOf("file", "bitcoin-27.0-win64-setup-unsigned.exe", "32348633"),
                                arrayOf("file", "bitcoin-27.0-win64-setup.exe", "32359120"),
                                arrayOf("file", "bitcoin-27.0-win64-unsigned.tar.gz", "32296875"),
                                arrayOf("file", "bitcoin-27.0-win64.zip", "46821837"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin-unsigned.tar.gz", "17160472"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin-unsigned.zip", "17213924"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin.tar.gz", "38428165"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin.zip", "17474503"),
                                arrayOf("file", "bitcoin-27.0-x86_64-linux-gnu-debug.tar.gz", "471529844"),
                                arrayOf("file", "bitcoin-27.0-x86_64-linux-gnu.tar.gz", "48849225"),
                                arrayOf("file", "bitcoin-27.0.tar.gz", "13082621"),
                            ),
                        content = "bitcoin-core-27.0",
                        sig = "40e1ccfdc38a32e6c164bb66e50df0cd3769e0431137a07709534a72b462dfcbc40106560d0dd66841fef4cbb7aece7db64e83a0fbe414759d4d9a799e522c57",
                    )

                val comment =
                    TorrentCommentEvent(
                        id = "040aba32010b5adf7cb917054e894e86c8ea7a2bcee448b2266c493f3140e9a0",
                        pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                        createdAt = 1724276875,
                        tags = arrayOf(arrayOf("e", "e1ab66dd66e6ac4f32119deacf80d7c50787705d343d3bb896c099cf93821757", "wss://relay.damus.io/", "", "0aa39e5aef99a000a7bdb0b499158c92bc4aa20fb65931a52d055b5eb6dff738", "root")),
                        content = "nice!",
                        sig = "014391c310b1eebb807da4c9b11563126f2b795c9372a9432cae4dd2c0695b88584bb1c68814554c9b1a47626e3d60983e3653c29d0fdbc3a474277c140b95c3",
                    )

                LocalCache.justConsume(torrent, null)
                LocalCache.justConsume(comment, null)
                LocalCache.getOrCreateNote("040aba32010b5adf7cb917054e894e86c8ea7a2bcee448b2266c493f3140e9a0")
            }
        }

    ThemeComparisonColumn(
        toPreview = {
            RenderTorrentComment(
                comment,
                false,
                true,
                3,
                true,
                remember { mutableStateOf(Color.Transparent) },
                EmptyState,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    )
}

@Composable
fun RenderTorrentComment(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: Boolean,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column {
        val noteEvent = note.event

        if (unPackReply) {
            val torrentInfo =
                remember(noteEvent) {
                    if (noteEvent is TorrentCommentEvent) {
                        noteEvent.torrent()
                    } else {
                        null
                    }
                }

            torrentInfo?.let {
                TorrentHeader(
                    torrentHex = it.eventId,
                    modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                Spacer(modifier = StdVertSpacer)
            }
        }

        RenderTextEvent(
            note,
            makeItShort,
            canPreview,
            quotesLeft,
            unPackReply = false,
            backgroundColor,
            editState,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun TorrentHeader(
    torrentHex: String,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = torrentHex, accountViewModel = accountViewModel) {
        if (it != null) {
            ShortTorrentHeader(it, modifier, accountViewModel, nav)
        }
    }
}

@Composable
fun ShortTorrentHeader(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent by observeNoteEvent<TorrentEvent>(baseNote)

    ShortTorrentHeader(
        title = noteEvent?.title() ?: TorrentEvent.ALT_DESCRIPTION,
        size = noteEvent?.totalSizeBytes()?.let { countToHumanReadableBytes(it) } ?: "--",
        modifier.clickable { routeFor(baseNote, accountViewModel.userProfile())?.let { nav.nav(it) } },
        accountViewModel,
        nav,
    )
}

@Composable
fun ShortTorrentHeader(
    title: String,
    size: String,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Outlined.FileOpen,
            contentDescription = stringRes(id = R.string.torrent_file),
            modifier = Modifier.size(20.dp),
        )

        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )

        Text(
            text = size,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 5.dp),
        )
    }
}
