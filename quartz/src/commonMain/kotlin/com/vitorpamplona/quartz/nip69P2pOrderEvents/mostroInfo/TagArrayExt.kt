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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondAmountPctTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondApplyToTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondBaseAmountSatsTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondEnabledTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondPayoutClaimWindowDaysTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondSlashNodeSharePctTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.BondSlashOnWaitingTimeoutTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.CashuEscrowLocktimeDaysTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.CashuMintUrlTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.EscrowModeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.ExpirationHoursTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.ExpirationSecondsTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.FeeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.FiatCurrenciesAcceptedTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.HoldInvoiceCltvDeltaTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.HoldInvoiceExpirationWindowTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.InvoiceExpirationWindowTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.LndChainsTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.LndCommitHashTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.LndNetworksTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.LndNodeAliasTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.LndNodePubKeyTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.LndUrisTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.LndVersionTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.MaintenanceModeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.MaxOrderAmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.MaxOrdersPerResponseTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.MinOrderAmountTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.MostroCommitHashTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.MostroVersionTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.PowFirstContactTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.PowTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.ProtocolVersionTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.ReputationImportIssuersTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.ReputationIssuerTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.SerberoTag

fun TagArray.mostroVersion() = fastFirstNotNullOfOrNull(MostroVersionTag::parse)

fun TagArray.mostroCommitHash() = fastFirstNotNullOfOrNull(MostroCommitHashTag::parse)

fun TagArray.maxOrderAmount() = fastFirstNotNullOfOrNull(MaxOrderAmountTag::parse)

fun TagArray.minOrderAmount() = fastFirstNotNullOfOrNull(MinOrderAmountTag::parse)

fun TagArray.expirationHours() = fastFirstNotNullOfOrNull(ExpirationHoursTag::parse)

fun TagArray.expirationSeconds() = fastFirstNotNullOfOrNull(ExpirationSecondsTag::parse)

fun TagArray.fiatCurrenciesAccepted() = fastFirstNotNullOfOrNull(FiatCurrenciesAcceptedTag::parse)

fun TagArray.maxOrdersPerResponse() = fastFirstNotNullOfOrNull(MaxOrdersPerResponseTag::parse)

fun TagArray.fee() = fastFirstNotNullOfOrNull(FeeTag::parse)

fun TagArray.pow() = fastFirstNotNullOfOrNull(PowTag::parse)

fun TagArray.powFirstContact() = fastFirstNotNullOfOrNull(PowFirstContactTag::parse)

fun TagArray.protocolVersion() = fastFirstNotNullOfOrNull(ProtocolVersionTag::parse)

fun TagArray.maintenanceMode() = fastFirstNotNullOfOrNull(MaintenanceModeTag::parse)

fun TagArray.escrowMode() = fastFirstNotNullOfOrNull(EscrowModeTag::parse)

fun TagArray.holdInvoiceExpirationWindow() = fastFirstNotNullOfOrNull(HoldInvoiceExpirationWindowTag::parse)

fun TagArray.holdInvoiceCltvDelta() = fastFirstNotNullOfOrNull(HoldInvoiceCltvDeltaTag::parse)

fun TagArray.invoiceExpirationWindow() = fastFirstNotNullOfOrNull(InvoiceExpirationWindowTag::parse)

fun TagArray.lndVersion() = fastFirstNotNullOfOrNull(LndVersionTag::parse)

fun TagArray.lndNodePubKey() = fastFirstNotNullOfOrNull(LndNodePubKeyTag::parse)

fun TagArray.lndCommitHash() = fastFirstNotNullOfOrNull(LndCommitHashTag::parse)

fun TagArray.lndNodeAlias() = fastFirstNotNullOfOrNull(LndNodeAliasTag::parse)

fun TagArray.lndChains() = fastFirstNotNullOfOrNull(LndChainsTag::parse)

fun TagArray.lndNetworks() = fastFirstNotNullOfOrNull(LndNetworksTag::parse)

fun TagArray.lndUris() = fastFirstNotNullOfOrNull(LndUrisTag::parse)

fun TagArray.bondEnabled() = fastFirstNotNullOfOrNull(BondEnabledTag::parse)

fun TagArray.bondApplyTo() = fastFirstNotNullOfOrNull(BondApplyToTag::parse)

fun TagArray.bondSlashOnWaitingTimeout() = fastFirstNotNullOfOrNull(BondSlashOnWaitingTimeoutTag::parse)

fun TagArray.bondAmountPct() = fastFirstNotNullOfOrNull(BondAmountPctTag::parse)

fun TagArray.bondBaseAmountSats() = fastFirstNotNullOfOrNull(BondBaseAmountSatsTag::parse)

fun TagArray.bondSlashNodeSharePct() = fastFirstNotNullOfOrNull(BondSlashNodeSharePctTag::parse)

fun TagArray.bondPayoutClaimWindowDays() = fastFirstNotNullOfOrNull(BondPayoutClaimWindowDaysTag::parse)

fun TagArray.cashuMintUrls() = fastFirstNotNullOfOrNull(CashuMintUrlTag::parse)

fun TagArray.cashuEscrowLocktimeDays() = fastFirstNotNullOfOrNull(CashuEscrowLocktimeDaysTag::parse)

fun TagArray.serbero() = fastFirstNotNullOfOrNull(SerberoTag::parse)

fun TagArray.reputationImportIssuers() = fastFirstNotNullOfOrNull(ReputationImportIssuersTag::parse)

fun TagArray.reputationIssuer() = fastFirstNotNullOfOrNull(ReputationIssuerTag::parse)
