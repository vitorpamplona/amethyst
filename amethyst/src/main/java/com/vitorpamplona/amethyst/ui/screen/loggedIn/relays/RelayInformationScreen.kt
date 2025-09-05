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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.loadRelayInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.ui.theme.LargeRelayIconModifier
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RelayInformationScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayUrlNormalizer.normalizeOrNull(relayUrl)?.let {
        RelayInformationScreen(
            relay = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayInformationScreen(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                actions = {},
                title = {
                    Text(
                        relay.displayUrl(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Row {
                        Spacer(modifier = StdHorzSpacer)
                        BackButton(
                            onPress = nav::popBack,
                        )
                    }
                },
            )
        },
    ) { pad ->
        val relayInfo by loadRelayInfo(relay, accountViewModel)

        val messages =
            remember(relay) {
                RelayStats
                    .get(url = relay)
                    .messages
                    .snapshot()
                    .values
                    .sortedByDescending { it.time }
                    .toImmutableList()
            }

        LazyColumn(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .padding(bottom = Size10dp, start = Size10dp, end = Size10dp)
                    .fillMaxSize(),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = StdPadding.fillMaxWidth(),
                ) {
                    Column {
                        RenderRelayIcon(
                            displayUrl = relay.displayUrl(),
                            iconUrl = relayInfo.icon,
                            loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                            loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
                            RelayStats.get(relay).pingInMs,
                            iconModifier = LargeRelayIconModifier,
                        )
                    }

                    Spacer(modifier = DoubleHorzSpacer)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Title(relayInfo.name?.trim() ?: "")
                        Spacer(modifier = HalfVertSpacer)
                        SubtitleContent(relay.url)
                    }
                }
            }
            item {
                Section(stringRes(R.string.description))

                SectionContent(relayInfo.description?.trim() ?: stringRes(R.string.no_description))
            }
            relayInfo.pubkey?.let {
                item {
                    Section(stringRes(R.string.owner))
                    DisplayOwnerInformation(it, accountViewModel, nav)
                }
            }
            relayInfo.contact?.let {
                item {
                    Section(stringRes(R.string.contact))

                    Box(modifier = Modifier.padding(start = 10.dp)) {
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
            relayInfo.software?.let {
                item {
                    Section(stringRes(R.string.software))

                    DisplaySoftwareInformation(it)

                    Section(stringRes(R.string.version))

                    SectionContent(relayInfo.version ?: "")
                }
            }
            relayInfo.supported_nips?.let {
                if (it.isNotEmpty()) {
                    item {
                        Section(stringRes(R.string.supports))

                        DisplaySupportedNips(it, relayInfo.supported_nip_extensions)
                    }
                }
            }
            relayInfo.fees?.admission?.let {
                item {
                    if (it.isNotEmpty()) {
                        Section(stringRes(R.string.admission_fees))

                        it.forEach { item -> SectionContent("${item.amount?.div(1000) ?: 0} sats") }
                    }
                }
            }

            relayInfo.payments_url?.let {
                item {
                    Section(stringRes(R.string.payments_url))

                    Box(modifier = Modifier.padding(start = 10.dp)) {
                        ClickableUrl(
                            urlText = it,
                            url = it,
                        )
                    }
                }
            }

            relayInfo.limitation?.let {
                item {
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
            relayInfo.relay_countries?.let {
                item {
                    Section(stringRes(R.string.countries))

                    FlowRow { it.forEach { item -> SectionContent(item) } }
                }
            }
            relayInfo.language_tags?.let {
                item {
                    Section(stringRes(R.string.languages))

                    FlowRow { it.forEach { item -> SectionContent(item) } }
                }
            }
            relayInfo.tags?.let {
                item {
                    Section(stringRes(R.string.tags))

                    FlowRow { it.forEach { item -> SectionContent(item) } }
                }
            }
            relayInfo.posting_policy?.let {
                item {
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
                    RenderDebugMessage(msg, accountViewModel, nav)
                }

                Spacer(modifier = StdVertSpacer)
            }
        }
    }
}

@Composable
private fun RenderDebugMessage(
    msg: RelayDebugMessage,
    accountViewModel: AccountViewModel,
    newNav: INav,
) {
    SelectionContainer {
        val context = LocalContext.current
        val color =
            remember {
                mutableStateOf(Color.Transparent)
            }
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

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DisplaySupportedNips(
    supportedNips: List<Int>,
    supportedNipExtensions: List<String>?,
) {
    FlowRow {
        supportedNips.forEach { item ->
            val text = item.toString().padStart(2, '0')
            Box(Modifier.padding(10.dp)) {
                ClickableUrl(
                    urlText = text,
                    url = "https://github.com/nostr-protocol/nips/blob/master/$text.md",
                )
            }
        }

        supportedNipExtensions?.forEach { item ->
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
private fun DisplaySoftwareInformation(software: String) {
    val url = software.replace("git+", "")
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
