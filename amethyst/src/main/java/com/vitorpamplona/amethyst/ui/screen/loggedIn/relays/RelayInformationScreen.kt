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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.util.timeDiffAgoShortish
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.appendLink
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.graspLink
import com.vitorpamplona.amethyst.ui.note.nipLink
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size100dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.ErrorDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.IRelayDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.NoticeDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStat
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.SpamDebugMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private const val HTTPS_PREFIX = "https://"

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
        val relayInfo by loadRelayInfo(relay)

        val messages =
            remember(relay) {
                Amethyst.instance.relayStats
                    .get(url = relay)
                    .messages
                    .snapshot()
                    .values
                    .sortedByDescending { it.time }
                    .toImmutableList()
            }

        RelayInformationBody(relay, relayInfo, Amethyst.instance.relayStats.get(relay), messages, pad, accountViewModel, nav)
    }
}

@Composable
fun RelayInformationBody(
    relay: NormalizedRelayUrl,
    relayInfo: Nip11RelayInformation,
    relayStats: RelayStat,
    messages: ImmutableList<IRelayDebugMessage>,
    pad: PaddingValues,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LazyColumn(
        modifier =
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Header Section
        item {
            RelayHeader(relay, relayStats, relayInfo, accountViewModel)
        }

        val targetAudience =
            relayInfo.tags != null ||
                relayInfo.language_tags != null ||
                relayInfo.relay_countries != null

        if (targetAudience) {
            item { SectionHeader(stringRes(R.string.target_audience)) }
            item { TargetAudienceCard(relayInfo, nav) }
        }

        relayInfo.pubkey?.let {
            item {
                SectionHeader(stringRes(R.string.owner))
                DisplayOwnerInformation(it, accountViewModel, nav)
            }
        }

        relayInfo.self?.let {
            item {
                SectionHeader(stringRes(R.string.self))
                DisplayOwnerInformation(it, accountViewModel, nav)
            }
        }

        item { SectionHeader(stringRes(R.string.policies_and_links)) }
        item { PoliciesCard(relayInfo) }

        relayInfo.fees?.let { fees ->
            item { SectionHeader(stringRes(R.string.fees_and_payments)) }
            item { FeesCard(fees, relayInfo.payments_url) }
        }

        relayInfo.limitation?.let {
            item { SectionHeader(stringRes(R.string.limitations)) }
            item { LimitationsCard(it) }
        }

        val atLeastOneSoftware =
            relayInfo.software != null ||
                relayInfo.version != null ||
                relayInfo.supported_grasps != null ||
                relayInfo.supported_nips != null

        if (atLeastOneSoftware) {
            item { SectionHeader(stringRes(R.string.software)) }
            item { SoftwareCard(relayInfo) }
        }

        item {
            SectionHeader(stringRes(R.string.relay_error_messages))
        }

        items(messages) { msg ->
            RenderDebugMessage(msg)

            Spacer(modifier = StdVertSpacer)
        }
    }
}

@Composable
private fun RenderDebugMessage(msg: IRelayDebugMessage) {
    val context = LocalContext.current

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Timestamp with Monospace font for alignment
            Text(
                text = timeAgoNoDot(msg.time, context),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )

            // Type Tag
            Text(
                text =
                    when (msg) {
                        is ErrorDebugMessage -> stringRes(R.string.errors)
                        is NoticeDebugMessage -> stringRes(R.string.relay_notice)
                        is SpamDebugMessage -> stringRes(R.string.spam)
                    },
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                color =
                    when (msg) {
                        is ErrorDebugMessage -> MaterialTheme.colorScheme.error
                        is NoticeDebugMessage -> MaterialTheme.colorScheme.primary
                        is SpamDebugMessage -> MaterialTheme.colorScheme.outline
                    },
            )
        }

        when (msg) {
            is ErrorDebugMessage -> {
                SelectionContainer {
                    Text(
                        text = msg.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            is NoticeDebugMessage -> {
                SelectionContainer {
                    Text(
                        text = msg.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            is SpamDebugMessage -> {
                SelectionContainer {
                    val uri = LocalUriHandler.current
                    val start = stringRes(R.string.duplicated_post)
                    Text(
                        text =
                            remember {
                                buildAnnotatedString {
                                    append(start)
                                    append(" ")
                                    appendLink(msg.link1) {
                                        runCatching {
                                            uri.openUri(msg.link1)
                                        }
                                    }
                                    appendLink(msg.link2) {
                                        runCatching {
                                            uri.openUri(msg.link2)
                                        }
                                    }
                                }
                            },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayOwnerInformation(
    userHex: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = userHex, accountViewModel) { loadedUser ->
        CrossfadeIfEnabled(loadedUser, accountViewModel = accountViewModel) {
            if (it != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    UserCompose(
                        baseUser = it,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayHeader(
    relay: NormalizedRelayUrl,
    relayStats: RelayStat,
    relayInfo: Nip11RelayInformation,
    accountViewModel: AccountViewModel,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        RenderRelayIcon(
            displayUrl = relay.displayUrl(),
            iconUrl = relayInfo.icon,
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
            pingInMs = relayStats.pingInMs,
            iconModifier =
                Modifier
                    .size(Size100dp)
                    .clip(shape = CircleShape),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = relayInfo.description ?: relay.displayUrl(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun FeesCard(
    fees: Nip11RelayInformation.RelayInformationFees,
    payUrl: String?,
) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            fees.admission?.forEach {
                FeeRow(stringRes(R.string.admission), it)
            }
            fees.subscription?.forEach {
                FeeRow(stringRes(R.string.subscription), it)
            }
            fees.publication?.forEach {
                FeeRow(stringRes(R.string.publication), it)
            }
            payUrl?.let {
                val uri = LocalUriHandler.current
                ClickableInfoRow(Icons.Default.Payment, stringRes(R.string.payments_url), it.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(it)
                    }
                }
            }
        }
    }
}

@Composable
fun LimitationsCard(lim: Nip11RelayInformation.RelayInformationLimitation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val atLeastOneAccessControl =
                lim.auth_required != null ||
                    lim.payment_required != null ||
                    lim.restricted_writes != null ||
                    lim.min_pow_difficulty != null ||
                    lim.min_prefix != null

            if (atLeastOneAccessControl) {
                Column {
                    Text(stringRes(R.string.access_control), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    val yes = stringRes(R.string.yes)
                    val no = stringRes(R.string.no)

                    lim.auth_required?.let {
                        InfoRow(Icons.Default.History, stringRes(R.string.auth_required), if (it) yes else no)
                    }
                    lim.payment_required?.let {
                        InfoRow(Icons.Default.Lock, stringRes(R.string.payment_required), if (it) yes else no)
                    }
                    lim.restricted_writes?.let {
                        InfoRow(Icons.Default.EditOff, stringRes(R.string.restricted_writes), if (it) yes else no)
                    }

                    val minPoW = lim.min_pow_difficulty

                    if (minPoW != null && minPoW > 0) {
                        InfoRow(Icons.Default.Bolt, stringRes(R.string.minimum_pow), stringRes(R.string.amount_in_bits, minPoW))
                    } else {
                        lim.min_prefix?.let {
                            if (it > 0) {
                                InfoRow(Icons.Default.Key, stringRes(R.string.minimum_prefix), stringRes(R.string.amount_in_bits, it * 8))
                            }
                        }
                    }
                }
            }

            val atLeastOneConnectivity =
                lim.max_message_length.isNotNullAndNotZero() ||
                    lim.max_subscriptions.isNotNullAndNotZero() ||
                    lim.max_filters.isNotNullAndNotZero() ||
                    lim.max_limit.isNotNullAndNotZero() ||
                    lim.default_limit.isNotNullAndNotZero() ||
                    lim.max_subid_length.isNotNullAndNotZero()

            if (atLeastOneConnectivity) {
                Column {
                    Text(stringRes(R.string.connectivity), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.max_message_length?.let {
                        InfoRow(Icons.AutoMirrored.Default.Message, stringRes(R.string.max_message_length), "${it / 1024} kb")
                    }
                    lim.max_subscriptions?.let {
                        InfoRow(Icons.Default.Dns, stringRes(R.string.max_subs), it.toString())
                    }
                    lim.max_filters?.let {
                        InfoRow(Icons.Default.FilterAlt, stringRes(R.string.max_filters_per_sub), it.toString())
                    }
                    lim.max_limit?.let {
                        InfoRow(Icons.AutoMirrored.Default.List, stringRes(R.string.max_limit_events_returning), it.toString())
                    }
                    lim.default_limit?.let {
                        InfoRow(Icons.AutoMirrored.Default.List, stringRes(R.string.max_limit_events_returning), it.toString())
                    }
                    lim.max_subid_length?.let {
                        InfoRow(Icons.AutoMirrored.Default.Label, stringRes(R.string.max_subid_length), it.toString())
                    }
                }
            }

            val atLeastOneContentSize =
                lim.max_event_tags != null ||
                    lim.max_content_length != null

            if (atLeastOneContentSize) {
                Column {
                    Text(stringRes(R.string.content_size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.max_event_tags?.let {
                        InfoRow(Icons.Default.Tag, stringRes(R.string.maximum_event_tags), it.toString())
                    }

                    lim.max_content_length?.let {
                        InfoRow(Icons.AutoMirrored.Default.Article, stringRes(R.string.max_content_length), "${it / 1024} kb")
                    }
                }
            }

            val atLeastOneRestriction =
                lim.created_at_lower_limit.isNotNullAndNotZero() ||
                    lim.created_at_upper_limit.isNotNullAndNotZero()

            if (atLeastOneRestriction) {
                Column {
                    Text(stringRes(R.string.event_retention), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    lim.created_at_lower_limit?.let {
                        if (it > 0) {
                            InfoRow(Icons.Default.History, stringRes(R.string.discards_older_than), stringRes(R.string.time_in_the_past, timeDiffAgoShortish(it)))
                        }
                    }
                    lim.created_at_upper_limit?.let {
                        if (it > 0) {
                            InfoRow(Icons.Default.History, stringRes(R.string.accepts_up_to), stringRes(R.string.time_in_the_future, timeDiffAgoShortish(it)))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalContracts::class)
fun Int?.isNotNullAndNotZero(): Boolean {
    contract {
        returns(true) implies (this@isNotNullAndNotZero != null)
    }

    return this != null && this != 0
}

@Composable
fun SoftwareCard(relayInfo: Nip11RelayInformation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val uri = LocalUriHandler.current
            relayInfo.software?.let {
                if (it.contains(HTTPS_PREFIX)) {
                    ClickableInfoRow(Icons.Default.Code, stringRes(R.string.software), it.removePrefix("git+https://").removePrefix(HTTPS_PREFIX)) {
                        runCatching {
                            uri.openUri(it.removePrefix("git+"))
                        }
                    }
                } else {
                    InfoRow(Icons.Default.Code, stringRes(R.string.software), it)
                }
            }

            relayInfo.version?.let {
                InfoRow(Icons.Default.Storage, stringRes(R.string.version), it)
            }

            relayInfo.supported_nips?.let { nips ->
                Text(
                    stringRes(R.string.supports),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val uri = LocalUriHandler.current
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nips.forEach { nip ->
                        val nipStr = nip.padStart(2, '0')
                        SuggestionChip(
                            onClick = {
                                runCatching {
                                    uri.openUri(nipLink(nipStr))
                                }
                            },
                            label = {
                                Text(nipStr)
                            },
                        )
                    }
                }
            }

            relayInfo.supported_grasps?.let { grasps ->
                Text(
                    stringRes(R.string.supported_grasps),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val uri = LocalUriHandler.current
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grasps.forEach { grasp ->
                        val graspStr = grasp.padStart(2, '0')
                        SuggestionChip(
                            onClick = {
                                runCatching {
                                    uri.openUri(graspLink(graspStr))
                                }
                            },
                            label = {
                                Text(graspStr)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TargetAudienceCard(
    relay: Nip11RelayInformation,
    nav: INav,
) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            relay.tags?.let { tags ->
                if (tags.size > 2) {
                    Column {
                        Text(stringRes(R.string.topics), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { nav.nav(Route.Hashtag(tag)) },
                                    label = {
                                        Text(tag)
                                    },
                                )
                            }
                        }
                    }
                } else if (tags.isNotEmpty()) {
                    InfoRow(Icons.Default.Topic, stringRes(R.string.topics), tags.joinToString())
                }
            }
            relay.relay_countries?.let { countries ->
                val allCountries = stringRes(R.string.all_countries)
                if (countries.size > 2) {
                    Column {
                        Text(stringRes(R.string.countries), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            countries.forEach { country ->
                                if (country == "*") {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(allCountries)
                                        },
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(country)
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else if (countries.isNotEmpty()) {
                    InfoRow(
                        Icons.Default.Language,
                        stringRes(R.string.countries),
                        countries.joinToString {
                            if (it == "*") allCountries else it
                        },
                    )
                }
            }
            relay.language_tags?.let { languages ->
                val allLang = stringRes(R.string.all_languages)
                if (languages.size > 2) {
                    Column {
                        Text(stringRes(R.string.languages), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            languages.forEach { lang ->
                                if (lang == "*") {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(allLang)
                                        },
                                    )
                                } else {
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(lang)
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (languages.isNotEmpty()) {
                    InfoRow(
                        Icons.Default.Translate,
                        stringRes(R.string.languages),
                        languages.joinToString {
                            if (it == "*") allLang else it
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PoliciesCard(relay: Nip11RelayInformation) {
    OutlinedCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val uri = LocalUriHandler.current
            relay.contact?.let {
                if (it.contains("@")) {
                    ClickableInfoRow(Icons.AutoMirrored.Default.ContactSupport, stringRes(R.string.contact), it) {
                        runCatching {
                            uri.openUri("mailto:$it")
                        }
                    }
                } else {
                    InfoRow(Icons.AutoMirrored.Default.ContactSupport, stringRes(R.string.contact), it)
                }
            }

            relay.posting_policy?.let {
                ClickableInfoRow(Icons.Default.EditNote, stringRes(R.string.posting_policy), it) {
                    runCatching {
                        uri.openUri(it)
                    }
                }
            }

            val pp = relay.privacy_policy

            if (pp != null) {
                ClickableInfoRow(Icons.Default.PrivacyTip, stringRes(R.string.privacy_policy), pp.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(pp)
                    }
                }
            } else {
                InfoRow(Icons.Default.PrivacyTip, stringRes(R.string.privacy_policy), stringRes(R.string.not_available_acronym))
            }

            val ts = relay.terms_of_service
            if (ts != null) {
                ClickableInfoRow(Icons.Default.Gavel, stringRes(R.string.terms_and_conditions), ts.removePrefix(HTTPS_PREFIX)) {
                    runCatching {
                        uri.openUri(ts)
                    }
                }
            } else {
                InfoRow(Icons.Default.Gavel, stringRes(R.string.terms_and_conditions), stringRes(R.string.not_available_acronym))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 5.dp),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(text = value, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

@Composable
private fun ClickableInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClickValue: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(text = value, textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClickValue).weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

@Composable
fun FeeRow(
    label: String,
    fee: Nip11RelayInformation.RelayInformationFee,
) {
    fee.amount?.let {
        val period = fee.period
        val combinedLabel =
            if (period != null) {
                label + " (${timeDiffAgoShortish(period)})"
            } else {
                label
            }

        if (fee.unit == "msats") {
            InfoRow(Icons.Default.AttachMoney, combinedLabel, "${it / 1000} sats")
        } else {
            InfoRow(Icons.Default.AttachMoney, combinedLabel, "$it ${fee.unit}")
        }
    }
}

@Composable
@Preview(showBackground = true, name = "Nost.wine Relay Info", device = "spec:width=2160px,height=5640px,dpi=440")
fun RelayHeaderPreview() {
    ThemeComparisonRow {
        RelayInformationBody(
            relay = NormalizedRelayUrl("wss://nostr.wine/"),
            relayInfo =
                Nip11RelayInformation(
                    name = "Nostr.wine",
                    icon = "https://image.nostr.build/30acdce4a81926f386622a07343228ae99fa68d012d54c538c0b2129dffe400c.png",
                    description = "A paid nostr relay for wine enthusiasts and everyone else",
                    software = "https://nostr.wine",
                    contact = "wino@nostr.wine",
                    version = "0.3.3",
                    self = "4918eb332a41b71ba9a74b1dc64276cfff592e55107b93baae38af3520e55975",
                    pubkey = "4918eb332a41b71ba9a74b1dc64276cfff592e55107b93baae38af3520e55975",
                    payments_url = "https://nostr.wine/invoices",
                    privacy_policy = "https://nostr.wine/terms",
                    terms_of_service = "https://nostr.wine/terms",
                    tags = listOf("Bitcoin", "Amethyst"),
                    supported_nips = listOf("1", "2", "4", "9", "11", "40", "42", "50", "70", "77"),
                    relay_countries = listOf("*"),
                    language_tags = listOf("*"),
                    limitation =
                        Nip11RelayInformation.RelayInformationLimitation(
                            auth_required = false,
                            created_at_lower_limit = 94608000,
                            created_at_upper_limit = 300,
                            max_event_tags = 4000,
                            max_limit = 1000,
                            default_limit = 20,
                            max_message_length = 524288,
                            max_subid_length = 71,
                            max_subscriptions = 50,
                            min_pow_difficulty = 0,
                            payment_required = true,
                            restricted_writes = true,
                        ),
                    fees =
                        Nip11RelayInformation.RelayInformationFees(
                            admission =
                                listOf(
                                    Nip11RelayInformation.RelayInformationFee(
                                        amount = 3000,
                                        unit = "msats",
                                        period = 2628003,
                                    ),
                                    Nip11RelayInformation.RelayInformationFee(
                                        amount = 8000,
                                        unit = "sats",
                                        period = 7884009,
                                    ),
                                ),
                        ),
                    supported_grasps = listOf("GRASP-01"),
                ),
            pad = PaddingValues(0.dp),
            relayStats = RelayStat(),
            messages =
                persistentListOf(
                    NoticeDebugMessage(
                        time = TimeUtils.now(),
                        message = "Subscription closed: AccountNotificationsEoseFromRandomRelaysManagerugZU9o auth-required: At least one matching event requires AUTH",
                    ),
                    ErrorDebugMessage(
                        time = TimeUtils.now() - 24000,
                        message = "No such subscription",
                    ),
                    SpamDebugMessage(
                        time = TimeUtils.now() - 24000,
                        link1 = "http://test1.com",
                        link2 = "http://test2.com",
                    ),
                ),
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
