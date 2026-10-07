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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.vitorpamplona.amethyst.commons.resources.wot_active_subtitle
import com.vitorpamplona.amethyst.commons.resources.wot_brainstorm_description
import com.vitorpamplona.amethyst.commons.resources.wot_download_now
import com.vitorpamplona.amethyst.commons.resources.wot_downloading_body
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
import com.vitorpamplona.amethyst.commons.resources.wot_min_score_pass
import com.vitorpamplona.amethyst.commons.resources.wot_min_score_title
import com.vitorpamplona.amethyst.commons.resources.wot_never_updated
import com.vitorpamplona.amethyst.commons.resources.wot_no_scores_body
import com.vitorpamplona.amethyst.commons.resources.wot_no_scores_title
import com.vitorpamplona.amethyst.commons.resources.wot_not_downloaded_title
import com.vitorpamplona.amethyst.commons.resources.wot_off_body
import com.vitorpamplona.amethyst.commons.resources.wot_off_title
import com.vitorpamplona.amethyst.commons.resources.wot_people_in_network
import com.vitorpamplona.amethyst.commons.resources.wot_private_entry_explainer
import com.vitorpamplona.amethyst.commons.resources.wot_private_entry_title
import com.vitorpamplona.amethyst.commons.resources.wot_provider_relay
import com.vitorpamplona.amethyst.commons.resources.wot_redownload
import com.vitorpamplona.amethyst.commons.resources.wot_redownload_explainer
import com.vitorpamplona.amethyst.commons.resources.wot_remove_provider
import com.vitorpamplona.amethyst.commons.resources.wot_remove_provider_explainer
import com.vitorpamplona.amethyst.commons.resources.wot_retry
import com.vitorpamplona.amethyst.commons.resources.wot_section_change_provider
import com.vitorpamplona.amethyst.commons.resources.wot_section_choose_provider
import com.vitorpamplona.amethyst.commons.resources.wot_section_effects
import com.vitorpamplona.amethyst.commons.resources.wot_section_filtering
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
import com.vitorpamplona.amethyst.commons.resources.wot_stat_in_network
import com.vitorpamplona.amethyst.commons.resources.wot_stat_scored
import com.vitorpamplona.amethyst.commons.resources.wot_stat_updated
import com.vitorpamplona.amethyst.commons.resources.wot_sync_now
import com.vitorpamplona.amethyst.commons.resources.wot_updating_title
import com.vitorpamplona.amethyst.commons.resources.wot_use_provider
import com.vitorpamplona.amethyst.commons.resources.wot_waiting_wifi_body
import com.vitorpamplona.amethyst.commons.resources.wot_waiting_wifi_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.StatTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.StatusTag
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.backups.TintedPanel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsBlockTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetwork
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkState
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetworkSyncStatus
import com.vitorpamplona.amethyst.commons.wot.onboarding.KnownTrustProviders
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderException
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboarding
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboardingStep
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

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

/** Where a guided sign-up is. */
@Immutable
sealed interface WebOfTrustSetup {
    data object Idle : WebOfTrustSetup

    data class Running(
        val providerId: String,
        val step: StringResource,
    ) : WebOfTrustSetup

    data class Failed(
        val providerId: String,
        val message: String,
    ) : WebOfTrustSetup
}

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
    val provider by account.trustProviderList.liveUserRankProvider.collectAsStateWithLifecycle()
    val network by account.trustNetwork.network.collectAsStateWithLifecycle()
    val status by account.trustNetwork.status.collectAsStateWithLifecycle()
    val minScore by account.trustNetwork.minTrustScore.collectAsStateWithLifecycle()

    val providers = remember(accountViewModel) { KnownTrustProviders.all(accountViewModel.host.trustProviderHttp) }
    var isPrivate by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<WebOfTrustSetup>(WebOfTrustSetup.Idle) }
    val scope = rememberCoroutineScope()
    val errorTexts = setupErrorTexts(providers)

    val actions =
        remember(accountViewModel, providers, isPrivate) {
            WebOfTrustActions(
                onDownloadNow = { account.trustNetwork.syncIfStale(force = true) },
                onRedownload = { account.trustNetwork.redownload() },
                onRemoveProvider = { accountViewModel.removeTrustScoreProvider() },
                onMinScoreChange = { accountViewModel.updateMinTrustScore(it) },
                onSetUp = { onboarding ->
                    setup = WebOfTrustSetup.Running(onboarding.id, Res.string.wot_setup_signing_in)
                    scope.launch {
                        setup =
                            try {
                                val registration = onboarding.register(account.signer) { step -> setup = WebOfTrustSetup.Running(onboarding.id, step.label()) }
                                setup = WebOfTrustSetup.Running(onboarding.id, Res.string.wot_setup_saving)
                                accountViewModel.setTrustScoreProvider(registration.serviceKey, registration.relay, isPrivate)
                                WebOfTrustSetup.Idle
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: TrustProviderException) {
                                WebOfTrustSetup.Failed(onboarding.id, errorTexts[onboarding.id]?.get(e.reason) ?: e.message.orEmpty())
                            } catch (e: Exception) {
                                WebOfTrustSetup.Failed(onboarding.id, e.message ?: e::class.simpleName.orEmpty())
                            }
                    }
                },
                onManualProvider = { key, relay -> accountViewModel.setTrustScoreProvider(key, relay, isPrivate) },
                onPrivateChange = { isPrivate = it },
                onOpenProvider = { provider?.let { nav.nav(Route.Profile(it.pubkey)) } },
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
                        guidedProviders = providers,
                        setup = setup,
                        isPrivate = isPrivate,
                    ),
                actions = actions,
                providerAvatar = { UserPicture(it.pubkey, 36.dp, accountViewModel = accountViewModel, nav = nav) },
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

@Composable
private fun setupErrorTexts(providers: List<TrustProviderOnboarding>): Map<String, Map<TrustProviderException.Reason, String>> =
    providers.associate { p ->
        p.id to
            mapOf(
                TrustProviderException.Reason.UNREACHABLE to stringRes(Res.string.wot_setup_error_unreachable, p.name),
                TrustProviderException.Reason.SIGN_IN_REJECTED to stringRes(Res.string.wot_setup_error_rejected, p.name),
                TrustProviderException.Reason.SIGNER_DECLINED to stringRes(Res.string.wot_setup_error_signer),
                TrustProviderException.Reason.UNEXPECTED_RESPONSE to stringRes(Res.string.wot_setup_error_unexpected, p.name),
            )
    }

private fun TrustProviderOnboardingStep.label(): StringResource =
    when (this) {
        TrustProviderOnboardingStep.SIGNING_IN -> Res.string.wot_setup_signing_in
        TrustProviderOnboardingStep.REQUESTING_SCORES -> Res.string.wot_setup_requesting_scores
        TrustProviderOnboardingStep.FETCHING_SERVICE_KEY -> Res.string.wot_setup_fetching_key
    }

/**
 * The Web of Trust screen's body, without the top bar: a hero with the network's state, the
 * minimum score, the provider, how to pick one, and what the filtering affects.
 *
 * Built from the settings kit (SettingsSection / SettingsBlockTile / SettingsControlRow) and the
 * hero pieces of the backup review screens (TintedPanel, StatTile), so it reads like the
 * rest of Settings.
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
        StatusHero(state, actions.onDownloadNow)

        val network = state.network
        if (state.provider != null && network != null) {
            FilteringSection(network, state.minScore, actions.onMinScoreChange)
        }

        state.provider?.let { ProviderSection(it, state, actions, providerAvatar) }

        ChooseProviderSection(state, actions)

        EffectsSection()
    }
}

// -------------------------------------------------------------------------------------------
// Hero: what the network is doing right now
// -------------------------------------------------------------------------------------------

@Composable
private fun StatusHero(
    state: WebOfTrustUiState,
    onDownloadNow: () -> Unit,
) {
    val network = state.network
    val status = state.status
    TintedPanel(MaterialTheme.colorScheme.surfaceContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                state.provider == null -> {
                    HeroHeadline(MaterialSymbols.Shield, stringRes(Res.string.wot_off_title), stringRes(Res.string.wot_off_body), active = false)
                }

                status.running != null -> {
                    val title = if (network == null) Res.string.wot_downloading_title else Res.string.wot_updating_title
                    HeroHeadline(MaterialSymbols.CloudDownload, stringRes(title), stringRes(Res.string.wot_downloading_body), active = true)
                    SyncProgress(status)
                }

                network != null -> {
                    ActiveHeadline(state.providerName, network, state.minScore)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            value = formatGrouped(network.index.countAtLeast(state.minScore).toLong()),
                            caption = stringRes(Res.string.wot_stat_in_network),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = formatGrouped(network.index.size.toLong()),
                            caption = stringRes(Res.string.wot_stat_scored),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = timeAgoNoDot(network.header.lastUpdate).trim(),
                            caption = stringRes(Res.string.wot_stat_updated),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                status.lastError == TrustNetworkState.NO_SCORES_YET -> {
                    HeroHeadline(MaterialSymbols.Schedule, stringRes(Res.string.wot_no_scores_title), stringRes(Res.string.wot_no_scores_body), active = true)
                    OutlinedButton(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) { Text(stringRes(Res.string.wot_retry)) }
                }

                status.lastError != null -> {
                    HeroHeadline(MaterialSymbols.Error, stringRes(Res.string.wot_error_title), status.lastError.orEmpty(), active = false, isError = true)
                    Button(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) { Text(stringRes(Res.string.wot_retry)) }
                }

                status.waitingForUnmetered -> {
                    HeroHeadline(MaterialSymbols.Schedule, stringRes(Res.string.wot_waiting_wifi_title), stringRes(Res.string.wot_waiting_wifi_body), active = true)
                    Button(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) { Text(stringRes(Res.string.wot_download_now)) }
                }

                else -> {
                    HeroHeadline(MaterialSymbols.CloudDownload, stringRes(Res.string.wot_not_downloaded_title), stringRes(Res.string.wot_waiting_wifi_body), active = true)
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
    body: String,
    active: Boolean,
    isError: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroIcon(symbol, active, isError)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.placeholderText)
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
            active -> scheme.primaryContainer to scheme.onPrimaryContainer
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
private fun ActiveHeadline(
    providerName: String?,
    network: TrustNetwork,
    minScore: Int,
) {
    val inNetwork = network.index.countAtLeast(minScore)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroIcon(MaterialSymbols.Shield, active = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                pluralStringRes(Res.plurals.wot_people_in_network, inNetwork, formatGrouped(inNetwork.toLong())),
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (providerName != null) {
                Text(
                    stringRes(Res.string.wot_active_subtitle, providerName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SyncProgress(status: TrustNetworkSyncStatus) {
    val expected = status.expected
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (expected != null && expected > 0) {
            LinearProgressIndicator(
                progress = { (status.verified.toFloat() / expected).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Text(
                stringRes(Res.string.wot_downloading_progress, formatGrouped(status.verified.toLong()), formatGrouped(expected.toLong())),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            Text(
                stringRes(Res.string.wot_downloading_progress_unknown, formatGrouped(status.verified.toLong())),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------
// Minimum score
// -------------------------------------------------------------------------------------------

@Composable
private fun FilteringSection(
    network: TrustNetwork,
    minScore: Int,
    onMinScoreChange: (Int) -> Unit,
) {
    // Local while dragging, so the pass count follows the thumb; saved (and synced) on release.
    var dragging by remember(minScore) { mutableFloatStateOf(minScore.toFloat()) }
    val value = dragging.roundToInt()

    SettingsSection(Res.string.wot_section_filtering) {
        SettingsBlockTile(
            icon = MaterialSymbols.Tune,
            title = stringRes(Res.string.wot_min_score_title),
            description = stringRes(Res.string.wot_min_score_explainer),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = dragging,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { onMinScoreChange(dragging.roundToInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                ScorePill(value)
            }
            Text(
                stringRes(
                    Res.string.wot_min_score_pass,
                    formatGrouped(network.index.countAtLeast(value).toLong()),
                    formatGrouped(network.index.size.toLong()),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ScorePill(value: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// -------------------------------------------------------------------------------------------
// The current provider
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

    SettingsSection(Res.string.wot_section_provider) {
        ProviderRow(provider, state.providerName, providerAvatar, actions.onOpenProvider)
        SettingsDivider()
        SettingsControlRow(
            icon = MaterialSymbols.Sync,
            title = stringRes(Res.string.wot_sync_now),
            description =
                network?.let { stringRes(Res.string.wot_last_updated, timeAgoNoDot(it.header.lastUpdate).trim()) }
                    ?: stringRes(Res.string.wot_never_updated),
            onClick = if (syncing) null else actions.onDownloadNow,
        ) {
            if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (network != null) {
            SettingsDivider()
            SettingsControlRow(
                icon = MaterialSymbols.CloudDownload,
                title = stringRes(Res.string.wot_redownload),
                description = stringRes(Res.string.wot_redownload_explainer),
                onClick = if (syncing) null else actions.onRedownload,
            ) {}
        }
        SettingsDivider()
        SettingsControlRow(
            icon = MaterialSymbols.Delete,
            title = stringRes(Res.string.wot_remove_provider),
            description = stringRes(Res.string.wot_remove_provider_explainer),
            onClick = actions.onRemoveProvider,
        ) {}
    }
}

@Composable
private fun ProviderRow(
    provider: ServiceProviderTag,
    name: String?,
    avatar: @Composable (ServiceProviderTag) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape)) { avatar(provider) }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                name ?: provider.pubkey.toShortDisplay(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringRes(Res.string.wot_provider_relay, provider.relayUrl.displayUrl()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    val busy = state.setup is WebOfTrustSetup.Running

    SettingsSection(if (state.provider != null) Res.string.wot_section_change_provider else Res.string.wot_section_choose_provider) {
        state.guidedProviders.forEachIndexed { index, provider ->
            if (index > 0) SettingsDivider()
            val inUse = state.provider?.relayUrl == provider.relay
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
    val uriHandler = LocalUriHandler.current
    val running = (setup as? WebOfTrustSetup.Running)?.takeIf { it.providerId == provider.id }
    val failed = (setup as? WebOfTrustSetup.Failed)?.takeIf { it.providerId == provider.id }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    provider.name.take(1),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(provider.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (inUse) StatusTag(stringRes(Res.string.wot_in_use), MaterialTheme.colorScheme.primary)
                }
                Text(
                    stringRes(providerDescription(provider.id)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (running != null) {
            Row(
                Modifier.padding(start = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringRes(running.step), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            if (failed != null) {
                Row(
                    Modifier
                        .padding(start = 52.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Error,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(failed.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 52.dp)) {
                Button(onClick = onSetUp, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(symbol = MaterialSymbols.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(if (inUse) Res.string.wot_set_up_again else Res.string.wot_set_up))
                }
                TextButton(onClick = { runCatching { uriHandler.openUri(provider.homepage) } }) {
                    Text(stringRes(Res.string.wot_learn_more))
                }
            }
        }
    }
}

/** What each guided provider does, in the user's language. */
private fun providerDescription(id: String): StringResource =
    when (id) {
        else -> Res.string.wot_brainstorm_description
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
// What the network changes
// -------------------------------------------------------------------------------------------

@Composable
private fun EffectsSection() {
    SettingsSection(Res.string.wot_section_effects) {
        EffectRow(MaterialSymbols.Mail, Res.string.wot_effect_messages_title, Res.string.wot_effect_messages_body)
        SettingsDivider()
        EffectRow(MaterialSymbols.Notifications, Res.string.wot_effect_notifications_title, Res.string.wot_effect_notifications_body)
        SettingsDivider()
        EffectRow(MaterialSymbols.Forum, Res.string.wot_effect_replies_title, Res.string.wot_effect_replies_body)
    }
}

@Composable
private fun EffectRow(
    symbol: MaterialSymbol,
    title: StringResource,
    body: StringResource,
) {
    SettingsControlRow(icon = symbol, title = stringRes(title), description = stringRes(body)) {}
}
