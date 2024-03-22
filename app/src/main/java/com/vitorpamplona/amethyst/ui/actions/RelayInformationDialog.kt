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
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.RelayBriefInfoCache
import com.vitorpamplona.amethyst.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.largeRelayIconModifier
import com.vitorpamplona.quartz.encoders.Nip11RelayInformation

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelayInformationDialog(
    onClose: () -> Unit,
    relayBriefInfo: RelayBriefInfoCache.RelayBriefInfo,
    relayInfo: Nip11RelayInformation,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val automaticallyShowProfilePicture =
        remember {
            accountViewModel.settings.showProfilePictures.value
        }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier.padding(10.dp).fillMaxSize().verticalScroll(scrollState),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onPress = { onClose() })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = StdPadding.fillMaxWidth(),
                ) {
                    Column {
                        RenderRelayIcon(
                            relayBriefInfo.displayUrl,
                            relayInfo.icon ?: relayBriefInfo.favIcon,
                            automaticallyShowProfilePicture,
                            MaterialTheme.colorScheme.largeRelayIconModifier,
                        )
                    }

                    Spacer(modifier = DoubleHorzSpacer)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row { Title(relayInfo.name?.trim() ?: "") }

                        Row { SubtitleContent(relayInfo.description?.trim() ?: "") }
                    }
                }

                Section(stringResource(R.string.owner))

                relayInfo.pubkey?.let {
                    DisplayOwnerInformation(it, accountViewModel) {
                        onClose()
                        nav(it)
                    }
                }

                Section(stringResource(R.string.software))

                DisplaySoftwareInformation(relayInfo)

                Section(stringResource(R.string.version))

                SectionContent(relayInfo.version ?: "")

                Section(stringResource(R.string.contact))

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

                Section(stringResource(R.string.supports))

                DisplaySupportedNips(relayInfo)

                relayInfo.fees?.admission?.let {
                    if (it.isNotEmpty()) {
                        Section(stringResource(R.string.admission_fees))

                        it.forEach { item -> SectionContent("${item.amount?.div(1000) ?: 0} sats") }
                    }
                }

                relayInfo.payments_url?.let {
                    Section(stringResource(R.string.payments_url))

                    Box(modifier = Modifier.padding(start = 10.dp)) {
                        ClickableUrl(
                            urlText = it,
                            url = it,
                        )
                    }
                }

                relayInfo.limitation?.let {
                    Section(stringResource(R.string.limitations))
                    val authRequiredText =
                        if (it.auth_required ?: false) stringResource(R.string.yes) else stringResource(R.string.no)

                    val paymentRequiredText =
                        if (it.payment_required ?: false) stringResource(R.string.yes) else stringResource(R.string.no)

                    val restrictedWritesText =
                        if (it.restricted_writes ?: false) stringResource(R.string.yes) else stringResource(R.string.no)

                    Column {
                        SectionContent(
                            "${stringResource(R.string.message_length)}: ${it.max_message_length ?: 0}",
                        )
                        SectionContent(
                            "${stringResource(R.string.subscriptions)}: ${it.max_subscriptions ?: 0}",
                        )
                        SectionContent("${stringResource(R.string.filters)}: ${it.max_filters ?: 0}")
                        SectionContent(
                            "${stringResource(R.string.subscription_id_length)}: ${it.max_subid_length ?: 0}",
                        )
                        SectionContent("${stringResource(R.string.minimum_prefix)}: ${it.min_prefix ?: 0}")
                        SectionContent(
                            "${stringResource(R.string.maximum_event_tags)}: ${it.max_event_tags ?: 0}",
                        )
                        SectionContent(
                            "${stringResource(R.string.content_length)}: ${it.max_content_length ?: 0}",
                        )
                        SectionContent(
                            "${stringResource(R.string.max_limit)}: ${it.max_limit ?: 0}",
                        )
                        SectionContent("${stringResource(R.string.minimum_pow)}: ${it.min_pow_difficulty ?: 0}")
                        SectionContent("${stringResource(R.string.auth)}: $authRequiredText")
                        SectionContent("${stringResource(R.string.payment)}: $paymentRequiredText")
                        SectionContent("${stringResource(R.string.restricted_writes)}: $restrictedWritesText")
                    }
                }

                relayInfo.relay_countries?.let {
                    Section(stringResource(R.string.countries))

                    FlowRow { it.forEach { item -> SectionContent(item) } }
                }

                relayInfo.language_tags?.let {
                    Section(stringResource(R.string.languages))

                    FlowRow { it.forEach { item -> SectionContent(item) } }
                }

                relayInfo.tags?.let {
                    Section(stringResource(R.string.tags))

                    FlowRow { it.forEach { item -> SectionContent(item) } }
                }

                relayInfo.posting_policy?.let {
                    Section(stringResource(R.string.posting_policy))

                    Box(Modifier.padding(10.dp)) {
                        ClickableUrl(
                            it,
                            it,
                        )
                    }
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
    nav: (String) -> Unit,
) {
    LoadUser(baseUserHex = userHex, accountViewModel) {
        Crossfade(it) {
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
