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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.wot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.web_of_trust
import com.vitorpamplona.amethyst.commons.resources.wot_brainstorm_description
import com.vitorpamplona.amethyst.commons.resources.wot_download_now
import com.vitorpamplona.amethyst.commons.resources.wot_downloading_progress
import com.vitorpamplona.amethyst.commons.resources.wot_downloading_progress_unknown
import com.vitorpamplona.amethyst.commons.resources.wot_downloading_title
import com.vitorpamplona.amethyst.commons.resources.wot_effect_messages_body
import com.vitorpamplona.amethyst.commons.resources.wot_effect_messages_title
import com.vitorpamplona.amethyst.commons.resources.wot_effect_notifications_body
import com.vitorpamplona.amethyst.commons.resources.wot_effect_notifications_title
import com.vitorpamplona.amethyst.commons.resources.wot_effect_replies_body
import com.vitorpamplona.amethyst.commons.resources.wot_effect_replies_title
import com.vitorpamplona.amethyst.commons.resources.wot_error_title
import com.vitorpamplona.amethyst.commons.resources.wot_in_use
import com.vitorpamplona.amethyst.commons.resources.wot_last_updated
import com.vitorpamplona.amethyst.commons.resources.wot_learn_more
import com.vitorpamplona.amethyst.commons.resources.wot_manual_explainer
import com.vitorpamplona.amethyst.commons.resources.wot_manual_key_error
import com.vitorpamplona.amethyst.commons.resources.wot_manual_key_label
import com.vitorpamplona.amethyst.commons.resources.wot_manual_relay_error
import com.vitorpamplona.amethyst.commons.resources.wot_manual_relay_label
import com.vitorpamplona.amethyst.commons.resources.wot_manual_title
import com.vitorpamplona.amethyst.commons.resources.wot_min_score_explainer
import com.vitorpamplona.amethyst.commons.resources.wot_min_score_title
import com.vitorpamplona.amethyst.commons.resources.wot_more_actions
import com.vitorpamplona.amethyst.commons.resources.wot_no_scores_body
import com.vitorpamplona.amethyst.commons.resources.wot_no_scores_title
import com.vitorpamplona.amethyst.commons.resources.wot_not_downloaded_title
import com.vitorpamplona.amethyst.commons.resources.wot_of_scored
import com.vitorpamplona.amethyst.commons.resources.wot_off_body
import com.vitorpamplona.amethyst.commons.resources.wot_off_title
import com.vitorpamplona.amethyst.commons.resources.wot_people_in_network
import com.vitorpamplona.amethyst.commons.resources.wot_private_entry_explainer
import com.vitorpamplona.amethyst.commons.resources.wot_private_entry_title
import com.vitorpamplona.amethyst.commons.resources.wot_redownload
import com.vitorpamplona.amethyst.commons.resources.wot_remove_provider
import com.vitorpamplona.amethyst.commons.resources.wot_retry
import com.vitorpamplona.amethyst.commons.resources.wot_section_change_provider
import com.vitorpamplona.amethyst.commons.resources.wot_section_choose_provider
import com.vitorpamplona.amethyst.commons.resources.wot_section_effects
import com.vitorpamplona.amethyst.commons.resources.wot_section_provider
import com.vitorpamplona.amethyst.commons.resources.wot_set_up
import com.vitorpamplona.amethyst.commons.resources.wot_set_up_again
import com.vitorpamplona.amethyst.commons.resources.wot_setup_error_rejected
import com.vitorpamplona.amethyst.commons.resources.wot_setup_error_signer
import com.vitorpamplona.amethyst.commons.resources.wot_setup_error_unexpected
import com.vitorpamplona.amethyst.commons.resources.wot_setup_error_unreachable
import com.vitorpamplona.amethyst.commons.resources.wot_setup_fetching_key
import com.vitorpamplona.amethyst.commons.resources.wot_setup_requesting_scores
import com.vitorpamplona.amethyst.commons.resources.wot_setup_saving
import com.vitorpamplona.amethyst.commons.resources.wot_setup_signing_in
import com.vitorpamplona.amethyst.commons.resources.wot_sync_now
import com.vitorpamplona.amethyst.commons.resources.wot_syncing
import com.vitorpamplona.amethyst.commons.resources.wot_use_provider
import com.vitorpamplona.amethyst.commons.resources.wot_waiting_wifi_body
import com.vitorpamplona.amethyst.commons.resources.wot_waiting_wifi_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.TintedPanel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustSetup
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustViewModel
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetwork
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkProblem
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkSyncStatus
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderException
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboarding
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboardingStep
import com.vitorpamplona.amethyst.commons.wot.onboarding.brainstorm.BrainstormOnboarding
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIndex
import org.jetbrains.compose.resources.StringResource
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the Web of Trust screen shows. Read from the account by [WebOfTrustScreen]. */
@Immutable
class WebOfTrustUiState(
    val provider: ServiceProviderTag?,
    /** The provider's display name, when [provider] is set. */
    val providerName: String?,
    /** The loaded network, only when it was built from [provider]'s cards. */
    val network: TrustNetwork?,
    val status: TrustNetworkSyncStatus,
    val minScore: Int,
    val guidedProviders: List<TrustProviderOnboarding>,
    val setup: WebOfTrustSetup,
    val isPrivate: Boolean,
)

/** What the Web of Trust screen can ask for. */
@Immutable
class WebOfTrustActions(
    val onDownloadNow: () -> Unit,
    val onRedownload: () -> Unit,
    val onRemoveProvider: () -> Unit,
    val onMinScoreChange: (Int) -> Unit,
    val onSetUp: (TrustProviderOnboarding) -> Unit,
    val onManualProvider: (HexKey, NormalizedRelayUrl) -> Unit,
    val onPrivateChange: (Boolean) -> Unit,
    val onOpenProvider: () -> Unit,
)

/**
 * Web of Trust settings: which NIP-85 provider decides who is in the user's extended network,
 * the downloaded network's state, the minimum score, and what the filtering affects.
 *
 * Reads the account and runs the guided sign-ups; [WebOfTrustContent] draws.
 */
@Composable
fun WebOfTrustScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val viewModel: WebOfTrustViewModel =
        rememberViewModel(key = "wot-${account.signer.pubKey}", factory = WebOfTrustViewModel.Factory(account, accountViewModel.host.trustProviderHttp))
    val provider by account.trustProviderList.liveUserRankProvider.collectAsStateWithLifecycle()
    val network by account.trustNetwork.network.collectAsStateWithLifecycle()
    val status by account.trustNetwork.status.collectAsStateWithLifecycle()
    val minScore by account.trustNetwork.minTrustScore.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val isPrivate by viewModel.isPrivate.collectAsStateWithLifecycle()

    val actions =
        remember(accountViewModel, viewModel) {
            WebOfTrustActions(
                onDownloadNow = { account.trustNetwork.syncIfStale(force = true) },
                onRedownload = { account.trustNetwork.redownload() },
                onRemoveProvider = { accountViewModel.removeTrustScoreProvider() },
                onMinScoreChange = { accountViewModel.updateMinTrustScore(it) },
                onSetUp = viewModel::setUp,
                onManualProvider = viewModel::useProvider,
                onPrivateChange = viewModel::setPrivate,
                onOpenProvider = {
                    account.trustProviderList.liveUserRankProvider.value
                        ?.let { nav.nav(Route.Profile(it.pubkey)) }
                },
            )
        }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.web_of_trust), nav) },
    ) { padding ->
        WithProviderName(provider, accountViewModel) { providerName ->
            WebOfTrustContent(
                state =
                    WebOfTrustUiState(
                        provider = provider,
                        providerName = providerName,
                        // The index may still hold the previous provider's network right after a switch.
                        network = network?.takeIf { net -> provider?.let { net.isFrom(it) } == true },
                        status = status,
                        minScore = minScore,
                        guidedProviders = viewModel.providers,
                        setup = setup,
                        isPrivate = isPrivate,
                    ),
                actions = actions,
                providerAvatar = { UserPicture(it.pubkey, 40.dp, accountViewModel = accountViewModel, nav = nav) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun WithProviderName(
    provider: ServiceProviderTag?,
    accountViewModel: AccountViewModel,
    content: @Composable (String?) -> Unit,
) {
    if (provider == null) {
        content(null)
    } else {
        LoadUser(provider.pubkey) { user ->
            if (user != null) {
                val name by observeUserName(user, accountViewModel)
                content(name.ifBlank { provider.pubkey.toShortDisplay() })
            } else {
                content(provider.pubkey.toShortDisplay())
            }
        }
    }
}

/** What the guided sign-up is doing, in the user's language. */
@Composable
private fun setupStepText(setup: WebOfTrustSetup): String? =
    when (setup) {
        is WebOfTrustSetup.Running -> {
            when (setup.step) {
                TrustProviderOnboardingStep.SIGNING_IN -> stringRes(Res.string.wot_setup_signing_in)
                TrustProviderOnboardingStep.REQUESTING_SCORES -> stringRes(Res.string.wot_setup_requesting_scores)
                TrustProviderOnboardingStep.FETCHING_SERVICE_KEY -> stringRes(Res.string.wot_setup_fetching_key)
            }
        }

        is WebOfTrustSetup.Saving -> {
            stringRes(Res.string.wot_setup_saving)
        }

        else -> {
            null
        }
    }

/** Why a sign-up with [providerName] failed, in the user's language. */
@Composable
private fun setupErrorText(
    failed: WebOfTrustSetup.Failed,
    providerName: String,
): String =
    when (failed.reason) {
        TrustProviderException.Reason.UNREACHABLE -> stringRes(Res.string.wot_setup_error_unreachable, providerName)
        TrustProviderException.Reason.SIGN_IN_REJECTED -> stringRes(Res.string.wot_setup_error_rejected, providerName)
        TrustProviderException.Reason.SIGNER_DECLINED -> stringRes(Res.string.wot_setup_error_signer)
        TrustProviderException.Reason.UNEXPECTED_RESPONSE -> stringRes(Res.string.wot_setup_error_unexpected, providerName)
        null -> failed.message
    }

/**
 * The Web of Trust screen's body, without the top bar. Once a network is loaded the hero *is*
 * the control: the network's size over its rank distribution, and the minimum-score slider
 * that cuts it, so dragging shows who gets through. Then the provider (one row, rare actions in
 * its overflow menu), how to pick another, and what the filtering affects.
 *
 * Built from the settings kit (SettingsSection / SettingsControlRow / SettingsSwitchTile) and the
 * hero panel of the backup review screens (TintedPanel), so it reads like the rest
 * of Settings.
 */
@Composable
fun WebOfTrustContent(
    state: WebOfTrustUiState,
    actions: WebOfTrustActions,
    providerAvatar: @Composable (ServiceProviderTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val network = state.network
        if (state.provider != null && network != null) {
            NetworkHero(network, state.minScore, actions.onMinScoreChange)
        } else {
            StatusHero(state, actions.onDownloadNow)
        }

        state.provider?.let { ProviderSection(it, state, actions, providerAvatar) }

        ChooseProviderSection(state, actions)

        EffectsSection()
    }
}

// -------------------------------------------------------------------------------------------
// Hero, with a network: its size, its rank distribution and the minimum score
// -------------------------------------------------------------------------------------------

/** Rank buckets drawn by [RankHistogram]: two ranks per bar over 0..100. */
private const val HISTOGRAM_BARS = 50

@Composable
private fun NetworkHero(
    network: TrustNetwork,
    minScore: Int,
    onMinScoreChange: (Int) -> Unit,
) {
    // Local while dragging, so the count and the bars follow the thumb; saved (and synced) on release.
    var dragging by remember(minScore) { mutableFloatStateOf(minScore.toFloat()) }
    val threshold = dragging.roundToInt()
    val inNetwork = network.index.countAtLeast(threshold)
    val shown by animateIntAsState(inNetwork, tween(durationMillis = 220))
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.6f), scheme.surfaceContainer)))
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    formatGrouped(shown.toLong()),
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = scheme.primary,
                )
                Text(
                    pluralStringRes(Res.plurals.wot_people_in_network, inNetwork),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Text(
                    stringRes(Res.string.wot_of_scored, formatGrouped(network.index.size.toLong())),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.placeholderText,
                )
            }
            HeroIcon(MaterialSymbols.Shield, active = true)
        }

        Spacer(Modifier.height(20.dp))

        // Aligned with the slider's track, whose ends sit half a thumb in from the edges.
        RankHistogram(network.index, threshold, Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 2.dp))
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onMinScoreChange(dragging.roundToInt()) },
            valueRange = 0f..100f,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(Res.string.wot_min_score_title), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
            ScorePill(threshold)
        }
        Text(
            stringRes(Res.string.wot_min_score_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.placeholderText,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * How many people the provider scores at each rank, as bars over 0..100. Bars at or above
 * [threshold] are the people who get through. Heights are square-rooted so the long tail of
 * low scores does not flatten the rest.
 */
@Composable
private fun RankHistogram(
    index: TrustNetworkIndex,
    threshold: Int,
    modifier: Modifier = Modifier,
) {
    val buckets =
        remember(index) {
            IntArray(HISTOGRAM_BARS).also { bars ->
                for (rank in 0 until 128) bars[(rank / 2).coerceAtMost(HISTOGRAM_BARS - 1)] += index.countAt(rank)
            }
        }
    val tallest = remember(buckets) { max(1, buckets.max()) }
    val passing = MaterialTheme.colorScheme.primary
    val filtered = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)

    Canvas(modifier) {
        val slot = size.width / HISTOGRAM_BARS
        val barWidth = slot * 0.7f
        val corner = CornerRadius(barWidth / 2, barWidth / 2)
        val minHeight = 3.dp.toPx()
        buckets.forEachIndexed { bar, count ->
            if (count == 0) return@forEachIndexed
            val height = max(minHeight, size.height * sqrt(count.toFloat() / tallest))
            // A bar holds ranks 2·bar and 2·bar + 1; it passes when its upper rank does.
            val color = if (2 * bar + 1 >= threshold) passing else filtered
            drawRoundRect(
                color = color,
                topLeft = Offset(bar * slot + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
private fun ScorePill(value: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

// -------------------------------------------------------------------------------------------
// Hero, without a network: off, first download, waiting, errors
// -------------------------------------------------------------------------------------------

@Composable
private fun StatusHero(
    state: WebOfTrustUiState,
    onDownloadNow: () -> Unit,
) {
    val status = state.status
    TintedPanel(MaterialTheme.colorScheme.surfaceContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                state.provider == null -> {
                    HeroHeadline(MaterialSymbols.Shield, stringRes(Res.string.wot_off_title), stringRes(Res.string.wot_off_body), active = false)
                }

                status.running != null -> {
                    HeroHeadline(MaterialSymbols.CloudDownload, stringRes(Res.string.wot_downloading_title), null, active = true)
                    SyncProgress(status)
                }

                status.problem == TrustNetworkProblem.NoScoresYet -> {
                    HeroHeadline(MaterialSymbols.Schedule, stringRes(Res.string.wot_no_scores_title), stringRes(Res.string.wot_no_scores_body), active = true)
                    OutlinedButton(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) { Text(stringRes(Res.string.wot_retry)) }
                }

                status.problem is TrustNetworkProblem.Failed -> {
                    val failed = status.problem as TrustNetworkProblem.Failed
                    HeroHeadline(MaterialSymbols.Error, stringRes(Res.string.wot_error_title), failed.message, active = false, isError = true)
                    Button(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) { Text(stringRes(Res.string.wot_retry)) }
                }

                status.waitingForUnmetered -> {
                    HeroHeadline(MaterialSymbols.Schedule, stringRes(Res.string.wot_waiting_wifi_title), stringRes(Res.string.wot_waiting_wifi_body), active = true)
                    Button(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) { Text(stringRes(Res.string.wot_download_now)) }
                }

                else -> {
                    HeroHeadline(MaterialSymbols.CloudDownload, stringRes(Res.string.wot_not_downloaded_title), null, active = true)
                    Button(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) { Text(stringRes(Res.string.wot_download_now)) }
                }
            }
        }
    }
}

@Composable
private fun HeroHeadline(
    symbol: MaterialSymbol,
    title: String,
    body: String?,
    active: Boolean,
    isError: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroIcon(symbol, active, isError)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeroIcon(
    symbol: MaterialSymbol,
    active: Boolean,
    isError: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when {
            isError -> scheme.errorContainer to scheme.onErrorContainer
            active -> scheme.primary to scheme.onPrimary
            else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        }
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(symbol = symbol, contentDescription = null, tint = content, filled = active)
    }
}

@Composable
private fun SyncProgress(status: TrustNetworkSyncStatus) {
    val expected = status.expected
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (expected != null && expected > 0) {
            LinearProgressIndicator(
                progress = { (status.verified.toFloat() / expected).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
        }
        Text(
            syncProgressText(status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun syncProgressText(status: TrustNetworkSyncStatus): String {
    val expected = status.expected
    return when {
        expected != null && expected > 0 -> stringRes(Res.string.wot_downloading_progress, formatGrouped(status.verified.toLong()), formatGrouped(expected.toLong()))
        status.verified > 0 -> stringRes(Res.string.wot_downloading_progress_unknown, formatGrouped(status.verified.toLong()))
        else -> stringRes(Res.string.wot_syncing)
    }
}

// -------------------------------------------------------------------------------------------
// The current provider: one row, rare actions in its menu
// -------------------------------------------------------------------------------------------

@Composable
private fun ProviderSection(
    provider: ServiceProviderTag,
    state: WebOfTrustUiState,
    actions: WebOfTrustActions,
    providerAvatar: @Composable (ServiceProviderTag) -> Unit,
) {
    val network = state.network
    val syncing = state.status.running != null
    val scheme = MaterialTheme.colorScheme

    SettingsSection(Res.string.wot_section_provider) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions.onOpenProvider)
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape)) { providerAvatar(provider) }
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 4.dp)) {
                Text(
                    state.providerName ?: provider.pubkey.toShortDisplay(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    provider.relayUrl.displayUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        // With no network yet, the hero already shows the download's progress.
                        syncing && network == null -> stringRes(Res.string.wot_syncing)
                        syncing -> syncProgressText(state.status)
                        network != null -> stringRes(Res.string.wot_last_updated, timeAgoNoDot(network.header.lastUpdate).trim())
                        else -> stringRes(Res.string.wot_not_downloaded_title)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (syncing) scheme.primary else scheme.placeholderText,
                    maxLines = 1,
                )
            }
            if (syncing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = actions.onDownloadNow) {
                    Icon(symbol = MaterialSymbols.Sync, contentDescription = stringRes(Res.string.wot_sync_now), tint = scheme.primary)
                }
            }
            ProviderMenu(canRedownload = network != null && !syncing, actions)
        }
    }
}

@Composable
private fun ProviderMenu(
    canRedownload: Boolean,
    actions: WebOfTrustActions,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                symbol = MaterialSymbols.MoreVert,
                contentDescription = stringRes(Res.string.wot_more_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.wot_redownload)) },
                leadingIcon = { MenuIcon(MaterialSymbols.CloudDownload) },
                enabled = canRedownload,
                onClick = {
                    open = false
                    actions.onRedownload()
                },
            )
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.wot_remove_provider), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { MenuIcon(MaterialSymbols.Delete, isDanger = true) },
                onClick = {
                    open = false
                    actions.onRemoveProvider()
                },
            )
        }
    }
}

@Composable
private fun MenuIcon(
    symbol: MaterialSymbol,
    isDanger: Boolean = false,
) {
    Icon(
        symbol = symbol,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// -------------------------------------------------------------------------------------------
// Choosing a provider: guided sign-ups, then any provider by hand
// -------------------------------------------------------------------------------------------

@Composable
private fun ChooseProviderSection(
    state: WebOfTrustUiState,
    actions: WebOfTrustActions,
) {
    var showManual by remember { mutableStateOf(false) }
    val busy = state.setup is WebOfTrustSetup.Running || state.setup is WebOfTrustSetup.Saving

    SettingsSection(if (state.provider != null) Res.string.wot_section_change_provider else Res.string.wot_section_choose_provider) {
        state.guidedProviders.forEachIndexed { index, provider ->
            if (index > 0) SettingsDivider()
            val inUse = state.provider?.let(provider::serves) == true
            GuidedProviderRow(provider, state.setup, inUse = inUse, enabled = !busy) { actions.onSetUp(provider) }
        }
        SettingsDivider()
        SettingsControlRow(
            icon = MaterialSymbols.Key,
            title = stringRes(Res.string.wot_manual_title),
            description = stringRes(Res.string.wot_manual_explainer),
            onClick = { showManual = !showManual },
        ) {
            Icon(
                symbol = if (showManual) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(showManual) {
            ManualProviderForm(enabled = !busy) { key, relay ->
                actions.onManualProvider(key, relay)
                showManual = false
            }
        }
        (state.setup as? WebOfTrustSetup.Failed)?.takeIf { it.providerId == WebOfTrustViewModel.MANUAL }?.let { failed ->
            SetupError(setupErrorText(failed, stringRes(Res.string.wot_manual_title)), Modifier.padding(start = 68.dp, end = 16.dp, bottom = 12.dp))
        }
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Lock,
            title = Res.string.wot_private_entry_title,
            description = Res.string.wot_private_entry_explainer,
            checked = state.isPrivate,
            onCheckedChange = actions.onPrivateChange,
        )
    }
}

@Composable
private fun GuidedProviderRow(
    provider: TrustProviderOnboarding,
    setup: WebOfTrustSetup,
    inUse: Boolean,
    enabled: Boolean,
    onSetUp: () -> Unit,
) {
    val inProgress =
        when (setup) {
            is WebOfTrustSetup.Running -> setup.providerId == provider.id
            is WebOfTrustSetup.Saving -> setup.providerId == provider.id
            else -> false
        }
    val failed = (setup as? WebOfTrustSetup.Failed)?.takeIf { it.providerId == provider.id }
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(scheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(provider.name.take(1), fontWeight = FontWeight.Bold, color = scheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(provider.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (inUse) {
                        Icon(
                            symbol = MaterialSymbols.CheckCircle,
                            contentDescription = stringRes(Res.string.wot_in_use),
                            modifier = Modifier.size(18.dp),
                            tint = scheme.primary,
                            filled = true,
                        )
                    }
                }
                if (inProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Text(setupStepText(setup).orEmpty(), style = MaterialTheme.typography.bodySmall, color = scheme.primary)
                    }
                } else {
                    val description = providerDescription(provider)?.let { stringRes(it) }
                    val learnMore = stringRes(Res.string.wot_learn_more)
                    val linkStyle = TextLinkStyles(SpanStyle(color = scheme.primary, fontWeight = FontWeight.SemiBold))
                    Text(
                        buildAnnotatedString {
                            if (description != null) {
                                append(description)
                                append(" · ")
                            }
                            withLink(LinkAnnotation.Url(provider.homepage, linkStyle)) { append(learnMore) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (!inProgress) {
                if (inUse) {
                    FilledTonalButton(onClick = onSetUp, enabled = enabled) { Text(stringRes(Res.string.wot_set_up_again)) }
                } else {
                    Button(onClick = onSetUp, enabled = enabled) { Text(stringRes(Res.string.wot_set_up)) }
                }
            }
        }

        if (failed != null) SetupError(setupErrorText(failed, provider.name), Modifier.padding(start = 52.dp))
    }
}

@Composable
private fun SetupError(
    text: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.errorContainer)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(symbol = MaterialSymbols.Error, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onErrorContainer)
        Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer)
    }
}

/** What a guided provider is, in the user's language; null for one with no description yet. */
private fun providerDescription(provider: TrustProviderOnboarding): StringResource? =
    when (provider) {
        is BrainstormOnboarding -> Res.string.wot_brainstorm_description
        else -> null
    }

@Composable
private fun ManualProviderForm(
    enabled: Boolean,
    onSave: (key: HexKey, relay: NormalizedRelayUrl) -> Unit,
) {
    var keyText by remember { mutableStateOf("") }
    var relayText by remember { mutableStateOf("") }

    val key = remember(keyText) { keyText.trim().takeIf { it.isNotEmpty() }?.let { decodePublicKeyAsHexOrNull(it) } }
    val relay = remember(relayText) { relayText.trim().takeIf { it.isNotEmpty() }?.let { RelayUrlNormalizer.normalizeOrNull(it) } }
    val keyError = keyText.isNotBlank() && key == null
    val relayError = relayText.isNotBlank() && relay == null

    Column(
        Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            label = { Text(stringRes(Res.string.wot_manual_key_label)) },
            isError = keyError,
            supportingText = if (keyError) ({ Text(stringRes(Res.string.wot_manual_key_error)) }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = relayText,
            onValueChange = { relayText = it },
            label = { Text(stringRes(Res.string.wot_manual_relay_label)) },
            isError = relayError,
            supportingText = if (relayError) ({ Text(stringRes(Res.string.wot_manual_relay_error)) }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { if (key != null && relay != null) onSave(key, relay) },
            enabled = enabled && key != null && relay != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringRes(Res.string.wot_use_provider))
        }
    }
}

// -------------------------------------------------------------------------------------------
// What the network changes: three tiles
// -------------------------------------------------------------------------------------------

@Composable
private fun EffectsSection() {
    SettingsSection(Res.string.wot_section_effects) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EffectTile(MaterialSymbols.Mail, Res.string.wot_effect_messages_title, Res.string.wot_effect_messages_body, Modifier.weight(1f))
            EffectTile(MaterialSymbols.Notifications, Res.string.wot_effect_notifications_title, Res.string.wot_effect_notifications_body, Modifier.weight(1f))
            EffectTile(MaterialSymbols.Forum, Res.string.wot_effect_replies_title, Res.string.wot_effect_replies_body, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EffectTile(
    symbol: MaterialSymbol,
    title: StringResource,
    body: StringResource,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(stringRes(title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            stringRes(body),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.placeholderText,
            textAlign = TextAlign.Center,
        )
    }
}
