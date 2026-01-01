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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.donations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.ReusableZapButton
import com.vitorpamplona.amethyst.ui.components.ZapButtonConfig
import com.vitorpamplona.amethyst.ui.components.appendLink
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.DisplayZapSplits
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Preview
@Composable
fun ZapTheDevsCardPreview() {
    runBlocking(Dispatchers.IO) {
        val releaseNotes =
            TextNoteEvent(
                id = "0465b20da0adf45dd612024d124e1ed384f7ecd2cd7358e77998828e7bf35fa2",
                pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                createdAt = 1708014675,
                tags =
                    arrayOf(
                        arrayOf("p", "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b", "", "mention"),
                        arrayOf("p", "7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf", "", "mention"),
                        arrayOf("t", "Amethyst"),
                        arrayOf("t", "amethyst"),
                        arrayOf("zap", "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", "wss://vitor.nostr1.com", "0.6499999761581421"),
                        arrayOf("zap", "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b", "wss://nos.lol", "0.25"),
                        arrayOf("zap", "7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf", "wss://vitor.nostr1.com", "0.10000000149011612"),
                        arrayOf("r", "https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-googleplay-universal-v0.84.2.apk"),
                        arrayOf("r", "https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-fdroid-universal-v0.84.2.apk"),
                    ),
                content = "#Amethyst v0.84.2: Text alignment fix\n\nBugfixes:\n- Fixes link misalignment in posts\n\nUpdated translations: \n- Czech, German, Swedish, and Portuguese by nostr:npub1e2yuky03caw4ke3zy68lg0fz3r4gkt94hx4fjmlelacyljgyk79svn3eef\n- French by nostr:npub106efcyntxc5qwl3w8krrhyt626m59ya2nk9f40px5s968u5xdwhsjsr8fz\n\nDownload:\n- [Play Edition](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-googleplay-universal-v0.84.2.apk )\n- [FOSS Edition - No translations](https://github.com/vitorpamplona/amethyst/releases/download/v0.84.2/amethyst-fdroid-universal-v0.84.2.apk )",
                sig = "e036ecce534e22efd47634c56328af62576ab3a36c565f7c8c5fbea67f48cd46d4041ecfc0ca01dafa0ebe8a0b119d125527a28f88aa30356b80c26dd0953aed",
            )

        LocalCache.consume(releaseNotes, null, true)
    }

    val accountViewModel = mockAccountViewModel()

    LoadNote(
        baseNoteHex = "0465b20da0adf45dd612024d124e1ed384f7ecd2cd7358e77998828e7bf35fa2",
        accountViewModel,
    ) { releaseNote ->
        if (releaseNote != null) {
            ThemeComparisonColumn {
                ZapTheDevsCard(
                    releaseNote,
                    accountViewModel,
                    nav = EmptyNav(),
                )
            }
        }
    }
}

@Composable
fun ZapTheDevsCard(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val releaseNoteState by observeNote(baseNote, accountViewModel)
    val releaseNote = releaseNoteState.note

    Row(modifier = Modifier.padding(start = Size10dp, end = Size10dp, bottom = Size10dp)) {
        Card(
            modifier = MaterialTheme.colorScheme.imageModifier,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringRes(id = R.string.zap_the_devs_title),
                        style =
                            TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                    )

                    IconButton(
                        modifier = Size20Modifier,
                        onClick = { accountViewModel.markDonatedInThisVersion() },
                    ) {
                        CloseIcon()
                    }
                }

                Spacer(modifier = StdVertSpacer)

                Text(
                    buildAnnotatedString {
                        append(stringRes(id = R.string.zap_the_devs_description, BuildConfig.VERSION_NAME))
                        append(" ")
                        appendLink("#value4value", MaterialTheme.colorScheme.primary) { nav.nav(Route.Hashtag("value4value")) }
                    },
                )

                Spacer(modifier = StdVertSpacer)

                val noteEvent = releaseNote.event
                if (noteEvent != null) {
                    val route =
                        remember(releaseNote) {
                            routeFor(releaseNote, accountViewModel.account)
                        }

                    if (route != null) {
                        Text(
                            text =
                                buildAnnotatedString {
                                    withLink(
                                        LinkAnnotation.Clickable("clickable") { nav.nav(route) },
                                    ) {
                                        append(stringRes(id = R.string.version_name, BuildConfig.VERSION_NAME.substringBefore("-")))
                                    }
                                    append(" " + stringRes(id = R.string.brought_to_you_by))
                                },
                        )
                    } else {
                        Text(stringRes(id = R.string.this_version_brought_to_you_by))
                    }

                    Spacer(modifier = StdVertSpacer)

                    DisplayZapSplits(
                        noteEvent = noteEvent,
                        useAuthorIfEmpty = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )

                    Spacer(modifier = StdVertSpacer)
                }

                ZapDonationButton(
                    baseNote = releaseNote,
                    grayTint = MaterialTheme.colorScheme.onPrimary,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun ZapDonationButton(
    baseNote: Note,
    grayTint: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val config =
        ZapButtonConfig(
            grayTint = grayTint,
        )

    ReusableZapButton(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
        config = config,
    )
}
