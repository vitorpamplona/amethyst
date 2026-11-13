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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.frontpage.AgentModel
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.routstr_err_http
import com.vitorpamplona.amethyst.commons.resources.routstr_err_insufficient
import com.vitorpamplona.amethyst.commons.resources.routstr_err_insufficient_plain
import com.vitorpamplona.amethyst.commons.resources.routstr_err_invalid_key
import com.vitorpamplona.amethyst.commons.resources.routstr_err_key_expired
import com.vitorpamplona.amethyst.commons.resources.routstr_err_key_not_found
import com.vitorpamplona.amethyst.commons.resources.routstr_err_not_found
import com.vitorpamplona.amethyst.commons.resources.routstr_err_not_kept
import com.vitorpamplona.amethyst.commons.resources.routstr_err_rate
import com.vitorpamplona.amethyst.commons.resources.routstr_err_settlement
import com.vitorpamplona.amethyst.commons.resources.routstr_err_token
import com.vitorpamplona.amethyst.commons.resources.routstr_err_unexpected
import com.vitorpamplona.amethyst.commons.resources.routstr_err_unreachable
import com.vitorpamplona.amethyst.commons.resources.routstr_err_unsupported
import com.vitorpamplona.amethyst.commons.resources.routstr_err_upstream
import com.vitorpamplona.amethyst.commons.resources.routstr_sats
import com.vitorpamplona.amethyst.commons.routstr.RoutstrAccount
import com.vitorpamplona.amethyst.commons.routstr.RoutstrError
import com.vitorpamplona.amethyst.commons.routstr.RoutstrFailure
import com.vitorpamplona.amethyst.commons.routstr.RoutstrKeyBalance
import com.vitorpamplona.amethyst.commons.routstr.RoutstrNodes
import com.vitorpamplona.amethyst.commons.routstr.RoutstrPricing
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag

/** "1,234 sats", with the count's plural. */
@Composable
fun satsText(sats: Long): String = pluralStringRes(Res.plurals.routstr_sats, sats.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), formatGrouped(sats))

/** A Routstr error, in the user's words (the spec's table); [notKept] when the device couldn't keep an invoice. */
@Composable
fun routstrErrorText(
    error: RoutstrError?,
    notKept: Boolean = false,
): String {
    if (notKept) return stringRes(Res.string.routstr_err_not_kept)
    if (error == null) return stringRes(Res.string.routstr_err_unexpected)
    return when (error.failure) {
        RoutstrFailure.INSUFFICIENT_BALANCE -> {
            val short = error.shortfallSats
            if (short != null) {
                pluralStringRes(Res.plurals.routstr_err_insufficient, short.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), formatGrouped(short))
            } else {
                stringRes(Res.string.routstr_err_insufficient_plain)
            }
        }
        RoutstrFailure.KEY_NOT_FOUND -> stringRes(Res.string.routstr_err_key_not_found)
        RoutstrFailure.INVALID_KEY -> stringRes(Res.string.routstr_err_invalid_key)
        RoutstrFailure.KEY_EXPIRED -> stringRes(Res.string.routstr_err_key_expired)
        RoutstrFailure.UPSTREAM_UNAVAILABLE -> stringRes(Res.string.routstr_err_upstream)
        RoutstrFailure.RATE_LIMITED -> stringRes(Res.string.routstr_err_rate)
        RoutstrFailure.PAYMENT_SETTLEMENT_FAILED -> stringRes(Res.string.routstr_err_settlement)
        RoutstrFailure.TOKEN_REJECTED -> stringRes(Res.string.routstr_err_token)
        RoutstrFailure.NOT_FOUND -> stringRes(Res.string.routstr_err_not_found)
        RoutstrFailure.UNEXPECTED -> stringRes(Res.string.routstr_err_unexpected)
        RoutstrFailure.UNREACHABLE -> stringRes(Res.string.routstr_err_unreachable)
        RoutstrFailure.UNSUPPORTED -> stringRes(Res.string.routstr_err_unsupported)
        RoutstrFailure.HTTP -> stringRes(Res.string.routstr_err_http, error.status ?: 0)
    }
}

/** This account's Routstr, where the platform has it. */
@Composable
fun rememberRoutstr(accountViewModel: AccountViewModel): RoutstrAccount? {
    val service = LocalAppServices.current.routstr ?: return null
    return remember(service, accountViewModel.account) { service.of(accountViewModel.account) }
}

/**
 * [key]'s Routstr balance, asked for when the screen shows it; null for a key that isn't a
 * Routstr key. A key whose endpoint might be one is asked `/v1/info` first (no key sent).
 */
@Composable
fun rememberRoutstrBalance(
    routstr: RoutstrAccount?,
    key: AgentKeyTag?,
): RoutstrKeyBalance? {
    if (routstr == null || key == null) return null
    val balances by routstr.balances.collectAsStateWithLifecycle()
    val info by routstr.info.collectAsStateWithLifecycle()
    LaunchedEffect(routstr, key) {
        val routstrKey = routstr.isRoutstrKey(key) || (routstr.mayBeRoutstr(key) && routstr.info(RoutstrNodes.root(key.url)) != null)
        if (routstrKey) routstr.refreshBalance(key)
    }
    // Recomposes once /v1/info says so.
    if (!routstr.isRoutstrKey(key) && info[RoutstrNodes.root(key.url).lowercase()] == null) return null
    return balances[key]
}

/** What the print card says about a Routstr key: the edition in sats, and whether the balance may not cover it. */
@Immutable
data class RoutstrPrintNote(
    val estimateSats: Long?,
    val lowBalance: Boolean,
    val onTopUp: (() -> Unit)?,
)

@Composable
fun rememberRoutstrPrintNote(
    accountViewModel: AccountViewModel,
    nav: INav,
    key: AgentKeyTag?,
    model: AgentModel?,
    estimateSats: Long?,
): RoutstrPrintNote? {
    val routstr = rememberRoutstr(accountViewModel)
    val balance = rememberRoutstrBalance(routstr, key)
    if (key == null) return null
    val known = balance as? RoutstrKeyBalance.Known
    val low = known != null && RoutstrPricing.isLow(known.balance.balanceMsats, model?.sats, estimateSats)
    if (estimateSats == null && !low) return null
    return RoutstrPrintNote(
        estimateSats = estimateSats,
        lowBalance = low,
        onTopUp = if (balance != null) ({ nav.nav(Route.RoutstrSetup(topUpUrl = key.url, topUpMask = AgentProviderPresets.maskKey(key.key))) }) else null,
    )
}

/** The Routstr node's name for a key at [keyUrl], once its announcement or `/v1/info` is read; null otherwise. */
@Composable
fun rememberRoutstrNodeName(
    routstr: RoutstrAccount?,
    keyUrl: String,
): String? {
    if (routstr == null) return null
    val nodes by routstr.nodes.collectAsStateWithLifecycle()
    val info by routstr.info.collectAsStateWithLifecycle()
    return remember(nodes, info, keyUrl) { routstr.nodeName(keyUrl) }
}
