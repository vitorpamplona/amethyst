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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.rememberExtendedNav
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.largeRelayIconModifier
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.ammolite.relays.RelayStats
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelayInformationDialog(
    onClose: () -> Unit,
    relayBriefInfo: RelayBriefInfoCache.RelayBriefInfo,
    relayInfo: Nip11RelayInformation,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)

    val messages =
        remember(relayBriefInfo) {
            RelayStats
                .get(url = relayBriefInfo.url)
                .messages
                .snapshot()
                .values
                .sortedByDescending { it.time }
                .toImmutableList()
        }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
    ) {
        SetDialogToEdgeToEdge()
        Surface {
            val color =
                remember {
                    mutableStateOf(Color.Transparent)
                }

            val context = LocalContext.current

            LazyColumn(
                modifier =
                    Modifier
                        .padding(10.dp)
                        .fillMaxSize(),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CloseButton(onPress = { onClose() })
                    }
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = StdPadding.fillMaxWidth(),
                    ) {
                        Column {
                            RenderRelayIcon(
                                displayUrl = relayBriefInfo.displayUrl,
                                iconUrl = relayInfo.icon ?: relayBriefInfo.favIcon,
                                loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                                loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
                                RelayStats.get(url = relayBriefInfo.url).pingInMs,
                                iconModifier = MaterialTheme.colorScheme.largeRelayIconModifier,
                            )
                        }

                        Spacer(modifier = DoubleHorzSpacer)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Title(relayInfo.name?.trim() ?: "")
                            SubtitleContent(relayInfo.description?.trim() ?: "")
                        }
                    }
                }
                item {
                    Section(stringRes(R.string.owner))

                    relayInfo.pubkey?.let {
                        DisplayOwnerInformation(it, accountViewModel, newNav)
                    }
                }
                item {
                    Section(stringRes(R.string.software))

                    DisplaySoftwareInformation(relayInfo)

                    Section(stringRes(R.string.version))

                    SectionContent(relayInfo.version ?: "")
                }
                item {
                    Section(stringRes(R.string.contact))

                    Box(modifier = Modifier.padding(start = 10.dp)) {
                        relayInfo.contact?.let {
                            if (it.startsWith("https:")) {
                                ClickableUrl(urlText = it, url = it)
                            } else if (it.startsWith("mailto:") || it.contains('@')) {
                                ClickableEmail(it)
                            } else {
                                SectionContent(it)
                            }
                        }
                    }
                }
                item {
                    Section(stringRes(R.string.supports))

                    DisplaySupportedNips(relayInfo)
                }
                item {
                    relayInfo.fees?.admission?.let {
                        if (it.isNotEmpty()) {
                            Section(stringRes(R.string.admission_fees))

                            it.forEach { item -> SectionContent("${item.amount?.div(1000) ?: 0} sats") }
                        }
                    }

                    relayInfo.payments_url?.let {
                        Section(stringRes(R.string.payments_url))

                        Box(modifier = Modifier.padding(start = 10.dp)) {
                            ClickableUrl(
                                urlText = it,
                                url = it,
                            )
                        }
                    }
                }
                item {
                    relayInfo.limitation?.let {
                        Section(stringRes(R.string.limitations))
                        val authRequiredText =
                            if (it.auth_required ?: false) stringRes(R.string.yes) else stringRes(R.string.no)

                        val paymentRequiredText =
                            if (it.payment_required ?: false) stringRes(R.string.yes) else stringRes(R.string.no)

                        val restrictedWritesText =
                            if (it.restricted_writes ?: false) stringRes(R.string.yes) else stringRes(R.string.no)

                        Column {
                            SectionContent(
                                "${stringRes(R.string.message_length)}: ${it.max_message_length ?: 0}",
                            )
                            SectionContent(
                                "${stringRes(R.string.subscriptions)}: ${it.max_subscriptions ?: 0}",
                            )
                            SectionContent("${stringRes(R.string.filters)}: ${it.max_filters ?: 0}")
                            SectionContent(
                                "${stringRes(R.string.subscription_id_length)}: ${it.max_subid_length ?: 0}",
                            )
                            SectionContent("${stringRes(R.string.minimum_prefix)}: ${it.min_prefix ?: 0}")
                            SectionContent(
                                "${stringRes(R.string.maximum_event_tags)}: ${it.max_event_tags ?: 0}",
                            )
                            SectionContent(
                                "${stringRes(R.string.content_length)}: ${it.max_content_length ?: 0}",
                            )
                            SectionContent(
                                "${stringRes(R.string.max_limit)}: ${it.max_limit ?: 0}",
                            )
                            SectionContent("${stringRes(R.string.minimum_pow)}: ${it.min_pow_difficulty ?: 0}")
                            SectionContent("${stringRes(R.string.auth)}: $authRequiredText")
                            SectionContent("${stringRes(R.string.payment)}: $paymentRequiredText")
                            SectionContent("${stringRes(R.string.restricted_writes)}: $restrictedWritesText")
                        }
                    }
                }
                item {
                    relayInfo.relay_countries?.let {
                        Section(stringRes(R.string.countries))

                        FlowRow { it.forEach { item -> SectionContent(item) } }
                    }
                }
                item {
                    relayInfo.language_tags?.let {
                        Section(stringRes(R.string.languages))

                        FlowRow { it.forEach { item -> SectionContent(item) } }
                    }
                }
                item {
                    relayInfo.tags?.let {
                        Section(stringRes(R.string.tags))

                        FlowRow { it.forEach { item -> SectionContent(item) } }
                    }
                }
                item {
                    relayInfo.posting_policy?.let {
                        Section(stringRes(R.string.posting_policy))

                        Box(Modifier.padding(10.dp)) {
                            ClickableUrl(
                                it,
                                it,
                            )
                        }
                    }
                }

                item {
                    Section(stringRes(R.string.relay_error_messages))
                }

                items(messages) { msg ->
                    Row {
                        SelectionContainer {
                            TranslatableRichTextViewer(
                                content =
                                    remember {
                                        "${timeAgo(msg.time, context)}, ${msg.type.name}: ${msg.message}"
                                    },
                                canPreview = false,
                                quotesLeft = 0,
                                modifier = Modifier.fillMaxWidth(),
                                tags = EmptyTagList,
                                backgroundColor = color,
                                id = msg.hashCode().toString(),
                                accountViewModel = accountViewModel,
                                nav = newNav,
                            )
                        }
                    }

                    Spacer(modifier = StdVertSpacer)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DisplaySupportedNips(relayInfo: Nip11RelayInformation) {
    FlowRow {
        relayInfo.supported_nips?.forEach { item ->
            val text = item.toString().padStart(2, '0')
            Box(Modifier.padding(10.dp)) {
                ClickableUrl(
                    urlText = text,
                    url = "https://github.com/nostr-protocol/nips/blob/master/$text.md",
                )
            }
        }

        relayInfo.supported_nip_extensions?.forEach { item ->
            val text = item.padStart(2, '0')
            Box(Modifier.padding(10.dp)) {
                ClickableUrl(
                    urlText = text,
                    url = "https://github.com/nostr-protocol/nips/blob/master/$text.md",
                )
            }
        }
    }
}

@Composable
private fun DisplaySoftwareInformation(relayInfo: Nip11RelayInformation) {
    val url = (relayInfo.software ?: "").replace("git+", "")
    Box(modifier = Modifier.padding(start = 10.dp)) {
        ClickableUrl(
            urlText = url,
            url = url,
        )
    }
}

@Composable
private fun DisplayOwnerInformation(
    userHex: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = userHex, accountViewModel) {
        CrossfadeIfEnabled(it, accountViewModel = accountViewModel) {
            if (it != null) {
                UserCompose(
                    baseUser = it,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun Title(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    )
}

@Composable
fun SubtitleContent(text: String) {
    Text(
        text = text,
    )
}

@Composable
fun Section(text: String) {
    Spacer(modifier = DoubleVertSpacer)
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
    Spacer(modifier = DoubleVertSpacer)
}

@Composable
fun SectionContent(text: String) {
    Text(
        modifier = Modifier.padding(start = 10.dp),
        text = text,
    )
}
