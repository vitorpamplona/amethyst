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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.payment.ProfilePaymentMethod
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTarget
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.SegwitAddress

/** Lightning-family target types Amethyst can pay in-app through the Send Payment screen. */
private val LIGHTNING_TARGET_TYPES = setOf("lightning", "ln", "lnurl")

/** Bitcoin-family target types the in-app on-chain wallet can pay directly. */
private val BITCOIN_TARGET_TYPES = setOf("bitcoin", "btc", "onchain")

fun isLightningPaymentTarget(rawType: String): Boolean = rawType.trim().lowercase() in LIGHTNING_TARGET_TYPES

/**
 * Route into the in-app Send Payment screen when one of the user's wallets can
 * pay [target] directly: lightning targets through the default lightning
 * source (NWC / CLINK debit / wallet app), bitcoin targets through the NIP-BC
 * on-chain wallet (native segwit mainnet addresses, and only when the chain
 * backend is configured). Null means no in-app wallet can pay this target —
 * callers fall back to the external URI handoff.
 */
fun inAppPaymentRouteFor(
    userHex: String,
    target: PaymentTarget,
): Route.SendPayment? {
    val type = target.type.trim().lowercase()
    return when {
        type in LIGHTNING_TARGET_TYPES ->
            Route.SendPayment(userHex, ProfilePaymentMethod.LIGHTNING.routeKey, lnAddressOverride = target.authority)

        type in BITCOIN_TARGET_TYPES &&
            LocalCache.onchainBackend != null &&
            SegwitAddress.isPayableMainnetAddress(target.authority.trim()) ->
            Route.SendPayment(userHex, ProfilePaymentMethod.ONCHAIN.routeKey, btcAddressOverride = target.authority.trim())

        else -> null
    }
}

/**
 * The URI an external wallet app should receive for [target]: the type's own
 * scheme (`bitcoin:`, `lightning:`, `https://cash.app/…`) and RFC 8905
 * `payto://` for types Amethyst has no dedicated scheme for. Shared with the
 * payment-target dialog so the same pill hands off to the same app wherever
 * it is tapped.
 */
fun paymentTargetUri(target: PaymentTarget): String = paymentTargetStyleFor(target.type).uriFor(target.authority)

/**
 * Chip for a NIP-A3 payment target. Rendered inside [DisplayPaymentRailChips]'s
 * FlowRow alongside the wallet-rail chips so all payment chips share one
 * wrapping row and spacing.
 */
@Composable
fun PaymentTargetChip(
    baseUser: User,
    target: PaymentTarget,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val style = remember(target.type) { paymentTargetStyleFor(target.type) }
    val uriHandler = LocalUriHandler.current

    PaymentTargetPill(
        target = target,
        onClick = {
            // Targets one of the user's in-app wallets can pay (lightning,
            // bitcoin) go to the Send Payment screen, which collects the
            // amount and pays this exact target; everything else hands off
            // to an external wallet app via its payment URI.
            val inAppRoute = inAppPaymentRouteFor(baseUser.pubkeyHex, target)
            if (inAppRoute != null) {
                nav.nav(inAppRoute)
            } else {
                runCatching { uriHandler.openUri(style.uriFor(target.authority)) }
                    .onFailure {
                        accountViewModel.toastManager.toast(
                            R.string.error_dialog_payment_error,
                            R.string.no_payment_app_found_for_type,
                            style.label,
                        )
                    }
            }
        },
    )
}

/**
 * The pill for a single NIP-A3 payment target: the type's icon and tinted
 * label followed by the shortened authority, with a long-press copy of the
 * full authority. Shared by the profile's payment rail and the payment-target
 * dialog so a target looks the same wherever it shows up.
 */
@Composable
fun PaymentTargetPill(
    target: PaymentTarget,
    onClick: () -> Unit,
) {
    val style = remember(target.type) { paymentTargetStyleFor(target.type) }

    ProfilePaymentChip(
        color = style.color,
        label = style.label,
        detail = remember(target.authority) { shortAddress(target.authority) },
        copyValue = target.authority,
        onClick = onClick,
    ) {
        Icon(
            symbol = style.symbol,
            contentDescription = null,
            tint = style.color,
            modifier = Size16Modifier,
        )
    }
}

private data class PaymentTargetStyle(
    val symbol: MaterialSymbol,
    val color: Color,
    val label: String,
    val uriFor: (String) -> String,
)

private fun paymentTargetStyleFor(rawType: String): PaymentTargetStyle {
    val type = rawType.trim().lowercase()
    val walletIcon = MaterialSymbols.AccountBalanceWallet
    return when (type) {
        "bitcoin", "btc", "onchain" ->
            PaymentTargetStyle(MaterialSymbols.CurrencyBitcoin, BitcoinOrange, "BITCOIN") { "bitcoin:$it" }
        "lightning", "ln" ->
            PaymentTargetStyle(MaterialSymbols.Bolt, BitcoinOrange, "LIGHTNING") { "lightning:$it" }
        "lnurl" ->
            PaymentTargetStyle(MaterialSymbols.Bolt, BitcoinOrange, "LNURL") { "lightning:$it" }
        "liquid" ->
            PaymentTargetStyle(MaterialSymbols.CurrencyBitcoin, BitcoinOrange, "LIQUID") { "liquidnetwork:$it" }
        "ethereum", "eth" ->
            PaymentTargetStyle(walletIcon, ETHEREUM_PURPLE, "ETHEREUM") { "ethereum:$it" }
        "monero", "xmr" ->
            PaymentTargetStyle(walletIcon, MONERO_ORANGE, "MONERO") { "monero:$it" }
        "dash" ->
            PaymentTargetStyle(walletIcon, DASH_BLUE, "DASH") { "dash:$it" }
        "zcash", "zec" ->
            PaymentTargetStyle(walletIcon, ZCASH_YELLOW, "ZCASH") { "zcash:$it" }
        "bitcoincash", "bch" ->
            PaymentTargetStyle(walletIcon, BITCOINCASH_GREEN, "BITCOINCASH") { "bitcoincash:$it" }
        "litecoin", "ltc" ->
            PaymentTargetStyle(walletIcon, LITECOIN_STEEL_BLUE, "LITECOIN") { "litecoin:$it" }
        "dogecoin", "doge" ->
            PaymentTargetStyle(walletIcon, DOGECOIN_SAND, "DOGECOIN") { "dogecoin:$it" }
        "solana", "sol" ->
            PaymentTargetStyle(walletIcon, SOLANA_PURPLE, "SOLANA") { "solana:$it" }
        "tron", "trx" ->
            PaymentTargetStyle(walletIcon, TRON_RED, "TRON") { "tron:$it" }
        "cashapp" ->
            PaymentTargetStyle(walletIcon, CASHAPP_LIME, "CASHAPP") { "https://cash.app/$it" }
        "venmo" ->
            PaymentTargetStyle(walletIcon, VENMO_BLUE, "VENMO") { "https://venmo.com/$it" }
        "paypal" ->
            PaymentTargetStyle(walletIcon, PAYPAL_DEEP_BLUE, "PAYPAL") { "https://paypal.me/$it" }
        else -> {
            val label = rawType.trim().ifEmpty { "PAY" }.uppercase()
            PaymentTargetStyle(walletIcon, GENERIC_TARGET_COLOR, label) { "payto://$type/$it" }
        }
    }
}

private fun shortAddress(authority: String): String {
    if (authority.contains('@') || authority.contains('/') || authority.length <= 18) return authority
    return authority.take(8) + "…" + authority.takeLast(4)
}

private val ETHEREUM_PURPLE = Color(0xFF627EEA)
private val MONERO_ORANGE = Color(0xFFFF6600)
private val DASH_BLUE = Color(0xFF008CE7)
private val ZCASH_YELLOW = Color(0xFFF4B728)
private val BITCOINCASH_GREEN = Color(0xFF4BB449)
private val LITECOIN_STEEL_BLUE = Color(0xFF6A8BA8)
private val DOGECOIN_SAND = Color(0xFFC9B037)
private val SOLANA_PURPLE = Color(0xFFB884F8)
private val TRON_RED = Color(0xFFEF0027)
private val CASHAPP_LIME = Color(0xFF00E64D)
private val VENMO_BLUE = Color(0xFF008CFF)
private val PAYPAL_DEEP_BLUE = Color(0xFF003087)
private val GENERIC_TARGET_COLOR = Color(0xFF7C8DA0)
