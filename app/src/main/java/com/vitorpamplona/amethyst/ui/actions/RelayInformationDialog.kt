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
import com.vitorpamplona.amethyst.model.RelayBriefInfo
import com.vitorpamplona.amethyst.model.RelayInformation
import com.vitorpamplona.amethyst.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelayInformationDialog(
    onClose: () -> Unit,
    relayBriefInfo: RelayBriefInfo,
    relayInfo: RelayInformation,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val automaticallyShowProfilePicture = remember {
        accountViewModel.settings.showProfilePictures.value
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onPress = {
                        onClose()
                    })
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = StdPadding.fillMaxWidth()) {
                    Column() {
                        RenderRelayIcon(
                            relayBriefInfo.favIcon,
                            automaticallyShowProfilePicture,
                            Size55dp
                        )
                    }

                    Spacer(modifier = DoubleHorzSpacer)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row() {
                            Title(relayInfo.name?.trim() ?: "")
                        }

                        Row() {
                            SubtitleContent(relayInfo.description?.trim() ?: "")
                        }
                    }
                }

                Section(stringResource(R.string.owner))

                relayInfo.pubkey?.let {
                    DisplayOwnerInformation(it, accountViewModel, nav)
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

                        it.forEach { item ->
                            SectionContent("${item.amount?.div(1000) ?: 0} sats")
                        }
                    }
                }

                relayInfo.payments_url?.let {
                    Section(stringResource(R.string.payments_url))

                    Box(modifier = Modifier.padding(start = 10.dp)) {
                        ClickableUrl(
                            urlText = it,
                            url = it
                        )
                    }
                }

                relayInfo.limitation?.let {
                    Section(stringResource(R.string.limitations))
                    val authRequired = it.auth_required ?: false
                    val authRequiredText = if (authRequired) stringResource(R.string.yes) else stringResource(R.string.no)
                    val paymentRequired = it.payment_required ?: false
                    val paymentRequiredText = if (paymentRequired) stringResource(R.string.yes) else stringResource(R.string.no)

                    Column {
                        SectionContent("${stringResource(R.string.message_length)}: ${it.max_message_length ?: 0}")
                        SectionContent("${stringResource(R.string.subscriptions)}: ${it.max_subscriptions ?: 0}")
                        SectionContent("${stringResource(R.string.filters)}: ${it.max_subscriptions ?: 0}")
                        SectionContent("${stringResource(R.string.subscription_id_length)}: ${it.max_subid_length ?: 0}")
                        SectionContent("${stringResource(R.string.minimum_prefix)}: ${it.min_prefix ?: 0}")
                        SectionContent("${stringResource(R.string.maximum_event_tags)}: ${it.max_event_tags ?: 0}")
                        SectionContent("${stringResource(R.string.content_length)}: ${it.max_content_length ?: 0}")
                        SectionContent("${stringResource(R.string.minimum_pow)}: ${it.min_pow_difficulty ?: 0}")
                        SectionContent("${stringResource(R.string.auth)}: $authRequiredText")
                        SectionContent("${stringResource(R.string.payment)}: $paymentRequiredText")
                    }
                }

                relayInfo.relay_countries?.let {
                    Section(stringResource(R.string.countries))

                    FlowRow {
                        it.forEach { item ->
                            SectionContent(item)
                        }
                    }
                }

                relayInfo.language_tags?.let {
                    Section(stringResource(R.string.languages))

                    FlowRow {
                        it.forEach { item ->
                            SectionContent(item)
                        }
                    }
                }

                relayInfo.tags?.let {
                    Section(stringResource(R.string.tags))

                    FlowRow {
                        it.forEach { item ->
                            SectionContent(item)
                        }
                    }
                }

                relayInfo.posting_policy?.let {
                    Section(stringResource(R.string.posting_policy))

                    Box(Modifier.padding(10.dp)) {
                        ClickableUrl(
                            it,
                            it
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DisplaySupportedNips(relayInfo: RelayInformation) {
    FlowRow {
        relayInfo.supported_nips?.forEach { item ->
            val text = item.toString().padStart(2, '0')
            Box(Modifier.padding(10.dp)) {
                ClickableUrl(
                    urlText = text,
                    url = "https://github.com/nostr-protocol/nips/blob/master/$text.md"
                )
            }
        }

        relayInfo.supported_nip_extensions?.forEach { item ->
            val text = item.padStart(2, '0')
            Box(Modifier.padding(10.dp)) {
                ClickableUrl(
                    urlText = text,
                    url = "https://github.com/nostr-protocol/nips/blob/master/$text.md"
                )
            }
        }
    }
}

@Composable
private fun DisplaySoftwareInformation(relayInfo: RelayInformation) {
    val url = (relayInfo.software ?: "").replace("git+", "")
    Box(modifier = Modifier.padding(start = 10.dp)) {
        ClickableUrl(
            urlText = url,
            url = url
        )
    }
}

@Composable
private fun DisplayOwnerInformation(
    userHex: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    LoadUser(baseUserHex = userHex, accountViewModel) {
        Crossfade(it) {
            if (it != null) {
                UserCompose(baseUser = it, accountViewModel = accountViewModel, showDiviser = false, nav = nav)
            }
        }
    }
}

@Composable
fun Title(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    )
}

@Composable
fun SubtitleContent(text: String) {
    Text(
        text = text
    )
}

@Composable
fun Section(text: String) {
    Spacer(modifier = DoubleVertSpacer)
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    )
    Spacer(modifier = DoubleVertSpacer)
}

@Composable
fun SectionContent(text: String) {
    Text(
        modifier = Modifier.padding(start = 10.dp),
        text = text
    )
}
