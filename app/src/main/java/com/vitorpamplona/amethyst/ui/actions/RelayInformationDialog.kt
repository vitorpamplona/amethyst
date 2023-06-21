package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Report
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.RelayInformation
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.nip05VerificationAsAState
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DisplayLNAddress
import com.vitorpamplona.amethyst.ui.theme.Nip05
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun Section(text: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
fun SectionContent(text: String) {
    Text(
        modifier = Modifier.padding(start = 10.dp),
        text = text
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelayInformationDialog(onClose: () -> Unit, relayInfo: RelayInformation, baseUser: User, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = remember(userState) { userState?.user } ?: return
    val tags = remember(userState) { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }
    val lud16 = remember(userState) { user.info?.lud16?.trim() ?: user.info?.lud06?.trim() }
    val pubkeyHex = remember { baseUser.pubkeyHex }
    val uri = LocalUriHandler.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface {
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
                    CloseButton(onCancel = {
                        onClose()
                    })
                }

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Section(relayInfo.name ?: "")
                }

                SectionContent(relayInfo.description ?: "")

                Section(stringResource(R.string.owner))

                Row {
                    UserPicture(
                        baseUser = user,
                        accountViewModel = accountViewModel,
                        size = 100.dp,
                        modifier = Modifier.border(
                            3.dp,
                            MaterialTheme.colors.background,
                            CircleShape
                        ),
                        onClick = {
                            nav("User/${user.pubkeyHex}")
                        }
                    )

                    Column(Modifier.padding(start = 10.dp)) {
                        (user.bestDisplayName() ?: user.bestUsername())?.let {
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 7.dp)) {
                                CreateTextWithEmoji(
                                    text = it,
                                    tags = tags,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 25.sp
                                )
                            }
                        }

                        if (user.bestDisplayName() != null) {
                            user.bestUsername()?.let {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 1.dp, bottom = 1.dp)
                                ) {
                                    CreateTextWithEmoji(
                                        text = "@$it",
                                        tags = tags,
                                        color = MaterialTheme.colors.placeholderText
                                    )
                                }
                            }
                        }

                        user.nip05()?.let { nip05 ->
                            if (nip05.split("@").size == 2) {
                                val nip05Verified by nip05VerificationAsAState(user.info!!, user.pubkeyHex)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (nip05Verified == null) {
                                        Icon(
                                            tint = Color.Yellow,
                                            imageVector = Icons.Default.Downloading,
                                            contentDescription = "Downloading",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else if (nip05Verified == true) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_verified),
                                            "NIP-05 Verified",
                                            tint = Nip05,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            tint = Color.Red,
                                            imageVector = Icons.Default.Report,
                                            contentDescription = "Invalid Nip05",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    var domainPadStart = 5.dp

                                    if (nip05.split("@")[0] != "_") {
                                        Text(
                                            text = AnnotatedString(nip05.split("@")[0] + "@"),
                                            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        domainPadStart = 0.dp
                                    }

                                    ClickableText(
                                        text = AnnotatedString(nip05.split("@")[1]),
                                        onClick = { nip05.let { runCatching { uri.openUri("https://${it.split("@")[1]}") } } },
                                        style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
                                        modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = domainPadStart),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        DisplayLNAddress(lud16, pubkeyHex, accountViewModel.account)

                        user.info?.about?.let {
                            Row(
                                modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                            ) {
                                val defaultBackground = MaterialTheme.colors.background
                                val background = remember {
                                    mutableStateOf(defaultBackground)
                                }

                                TranslatableRichTextViewer(
                                    content = it,
                                    canPreview = false,
                                    tags = remember { ImmutableListOfLists(emptyList()) },
                                    backgroundColor = background,
                                    accountViewModel = accountViewModel,
                                    nav = nav
                                )
                            }
                        }
                    }
                }

                Section(stringResource(R.string.software))

                val url = (relayInfo.software ?: "").replace("git+", "")
                Box(modifier = Modifier.padding(start = 10.dp)) {
                    ClickableUrl(
                        urlText = url,
                        url = url
                    )
                }

                Section(stringResource(R.string.version))

                SectionContent(relayInfo.version ?: "")

                Section(stringResource(R.string.contact))

                Box(modifier = Modifier.padding(start = 10.dp)) {
                    ClickableEmail(relayInfo.contact ?: "")
                }

                Section(stringResource(R.string.supports))

                FlowRow {
                    relayInfo.supported_nips?.forEach { item ->
                        val text = item.toString().padStart(2, '0')
                        Box(Modifier.padding(10.dp)) {
                            ClickableUrl(
                                urlText = "Nip-$text",
                                url = "https://github.com/nostr-protocol/nips/blob/master/$text.md"
                            )
                        }
                    }

                    relayInfo.supported_nip_extensions?.forEach { item ->
                        val text = item.padStart(2, '0')
                        Box(Modifier.padding(10.dp)) {
                            ClickableUrl(
                                urlText = "Nip-$text",
                                url = "https://github.com/nostr-protocol/nips/blob/master/$text.md"
                            )
                        }
                    }
                }

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
