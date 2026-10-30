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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wotNudge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.wot.setupErrorText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.wot.setupStepText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustSetup
import com.vitorpamplona.amethyst.commons.viewmodels.WebOfTrustViewModel
import com.vitorpamplona.amethyst.commons.wot.nudge.WebOfTrustNudgeRules
import com.vitorpamplona.amethyst.commons.wot.nudge.followsOfFollowsCount
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderHttp
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderOnboarding
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home's invitation to turn on web of trust: the circle card ([CircleRingsCard]) for an account
 * with enough follows and no trust provider, until the user activates it or puts it away.
 * Activation runs the same guided sign-up as the Web of Trust settings.
 */
@Composable
fun WebOfTrustNudge(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val account = accountViewModel.account
    // Signing up needs a signer, and a front end that can reach the provider's API.
    if (!account.isWriteable() || accountViewModel.host.trustProviderHttp === TrustProviderHttp.Unsupported) return

    val provider by account.trustProviderList.liveUserRankProvider.collectAsStateWithLifecycle()
    val follows by account.kind3FollowList.flow.collectAsStateWithLifecycle()
    val hiddenUntil by account.settings.webOfTrustNudgeHiddenUntil.collectAsStateWithLifecycle()

    // Set on Activate, so the card stays through the sign-up and its acknowledgement even after
    // the provider is saved (which ends the offer).
    var started by rememberSaveable(account.signer.pubKey) { mutableStateOf(false) }

    val offered = WebOfTrustNudgeRules.shouldOffer(follows.authors.size, provider != null, hiddenUntil, TimeUtils.now())
    if (!offered && !started) return

    WebOfTrustNudgeCard(
        accountViewModel = accountViewModel,
        nav = nav,
        follows = follows.authors,
        hasProvider = provider != null,
        started = started,
        onStart = { started = true },
        onHide = { until ->
            started = false
            account.settings.hideWebOfTrustNudgeUntil(until)
        },
        modifier = modifier,
    )
}

@Composable
private fun WebOfTrustNudgeCard(
    accountViewModel: AccountViewModel,
    nav: INav,
    follows: Set<HexKey>,
    hasProvider: Boolean,
    started: Boolean,
    onStart: () -> Unit,
    onHide: (Long) -> Unit,
    modifier: Modifier,
) {
    val account = accountViewModel.account
    val viewModel: WebOfTrustViewModel =
        rememberViewModel(key = "wot-nudge-${account.signer.pubKey}", factory = WebOfTrustViewModel.Factory(account, accountViewModel.host.trustProviderHttp, accountViewModel.nip05ClientBuilder()))
    val setup by viewModel.setup.collectAsStateWithLifecycle()

    // Faces with a picture first, and the follows-of-follows estimate: both read the whole cache.
    val sample by produceState(CircleSample.EMPTY, follows) {
        value =
            withContext(Dispatchers.Default) {
                val withPicture = follows.filter { account.cache.getUserIfExists(it)?.profilePicture() != null }
                CircleSample(
                    faces = (withPicture + follows).distinct().take(6),
                    extended = account.followsOfFollowsCount(follows),
                )
            }
    }

    val providers =
        remember(viewModel) {
            viewModel.providers.map { onboarding ->
                CircleProvider(onboarding.id, onboarding.name) { size -> ProviderPicture(onboarding, viewModel, size, accountViewModel, nav) }
            }
        }
    if (providers.isEmpty()) return
    var selectedId by rememberSaveable { mutableStateOf(providers.first().id) }
    val selected = providers.firstOrNull { it.id == selectedId } ?: providers.first()
    val onboarding = viewModel.providers.first { it.id == selected.id }

    val activation =
        when (val current = setup) {
            is WebOfTrustSetup.Running, is WebOfTrustSetup.Saving -> CircleActivation.Working(setupStepText(current).orEmpty())
            is WebOfTrustSetup.Failed -> CircleActivation.Failed(setupErrorText(current, selected.name))
            WebOfTrustSetup.Idle -> if (started && hasProvider) CircleActivation.Activated else CircleActivation.Offer
        }
    val working = activation is CircleActivation.Working

    CircleRingsCard(
        self = { size -> UserPicture(account.signer.pubKey, size, accountViewModel = accountViewModel, nav = nav) },
        faces = sample.faces.map { key -> userFace(key, accountViewModel, nav) },
        followCount = follows.size,
        extendedCount = sample.extended,
        providers = providers,
        selected = selected,
        onSelect = { selectedId = it.id },
        activation = activation,
        onActivate = {
            onStart()
            viewModel.setUp(onboarding)
        },
        onNotNow = { onHide(TimeUtils.now() + WebOfTrustNudgeRules.SNOOZE_SECONDS) },
        // The sign-up runs in this card's view model: closing it mid-way would cancel it.
        onDismiss = { if (!working) onHide(WebOfTrustNudgeRules.FOREVER) },
        modifier = modifier,
    )
}

private fun userFace(
    key: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
): NudgeFace = { size -> UserPicture(key, size, accountViewModel = accountViewModel, nav = nav) }

private class CircleSample(
    val faces: List<HexKey>,
    val extended: Int,
) {
    companion object {
        val EMPTY = CircleSample(emptyList(), 0)
    }
}

/** The provider's own account picture when it has one, else a plain tile. */
@Composable
private fun ProviderPicture(
    onboarding: TrustProviderOnboarding,
    viewModel: WebOfTrustViewModel,
    size: Dp,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val key by produceState<HexKey?>(null, onboarding.id) { value = viewModel.houseObserverKey(onboarding) }
    val house = key
    if (house != null) {
        UserPicture(house, size, accountViewModel = accountViewModel, nav = nav)
    } else {
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
