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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
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
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.PlatformTag

fun TagArrayBuilder<MostroInfoEvent>.platform(instanceName: String?) = addUnique(PlatformTag.assemble(PlatformTag.MOSTRO, instanceName))

fun TagArrayBuilder<MostroInfoEvent>.documentType() = addUnique(DocumentTypeTag.assemble(DocumentTypeTag.INFO))

fun TagArrayBuilder<MostroInfoEvent>.mostroVersion(version: String) = addUnique(MostroVersionTag.assemble(version))

fun TagArrayBuilder<MostroInfoEvent>.mostroCommitHash(hash: String) = addUnique(MostroCommitHashTag.assemble(hash))

fun TagArrayBuilder<MostroInfoEvent>.maxOrderAmount(sats: Long) = addUnique(MaxOrderAmountTag.assemble(sats))

fun TagArrayBuilder<MostroInfoEvent>.minOrderAmount(sats: Long) = addUnique(MinOrderAmountTag.assemble(sats))

fun TagArrayBuilder<MostroInfoEvent>.expirationHours(hours: Long) = addUnique(ExpirationHoursTag.assemble(hours))

fun TagArrayBuilder<MostroInfoEvent>.expirationSeconds(seconds: Long) = addUnique(ExpirationSecondsTag.assemble(seconds))

fun TagArrayBuilder<MostroInfoEvent>.fiatCurrenciesAccepted(currencies: List<String>) = addUnique(FiatCurrenciesAcceptedTag.assemble(currencies))

fun TagArrayBuilder<MostroInfoEvent>.maxOrdersPerResponse(count: Long) = addUnique(MaxOrdersPerResponseTag.assemble(count))

fun TagArrayBuilder<MostroInfoEvent>.fee(fraction: Double) = addUnique(FeeTag.assemble(fraction))

fun TagArrayBuilder<MostroInfoEvent>.pow(bits: Long) = addUnique(PowTag.assemble(bits))

fun TagArrayBuilder<MostroInfoEvent>.powFirstContact(bits: Long) = addUnique(PowFirstContactTag.assemble(bits))

fun TagArrayBuilder<MostroInfoEvent>.protocolVersion(version: Long) = addUnique(ProtocolVersionTag.assemble(version))

fun TagArrayBuilder<MostroInfoEvent>.maintenanceMode(enabled: Boolean) = addUnique(MaintenanceModeTag.assemble(enabled))

fun TagArrayBuilder<MostroInfoEvent>.escrowMode(mode: String) = addUnique(EscrowModeTag.assemble(mode))

fun TagArrayBuilder<MostroInfoEvent>.holdInvoiceExpirationWindow(seconds: Long) = addUnique(HoldInvoiceExpirationWindowTag.assemble(seconds))

fun TagArrayBuilder<MostroInfoEvent>.holdInvoiceCltvDelta(blocks: Long) = addUnique(HoldInvoiceCltvDeltaTag.assemble(blocks))

fun TagArrayBuilder<MostroInfoEvent>.invoiceExpirationWindow(seconds: Long) = addUnique(InvoiceExpirationWindowTag.assemble(seconds))

fun TagArrayBuilder<MostroInfoEvent>.lndVersion(version: String) = addUnique(LndVersionTag.assemble(version))

fun TagArrayBuilder<MostroInfoEvent>.lndNodePubKey(nodePubKey: String) = addUnique(LndNodePubKeyTag.assemble(nodePubKey))

fun TagArrayBuilder<MostroInfoEvent>.lndCommitHash(hash: String) = addUnique(LndCommitHashTag.assemble(hash))

fun TagArrayBuilder<MostroInfoEvent>.lndNodeAlias(alias: String) = addUnique(LndNodeAliasTag.assemble(alias))

fun TagArrayBuilder<MostroInfoEvent>.lndChains(chains: List<String>) = addUnique(LndChainsTag.assemble(chains))

fun TagArrayBuilder<MostroInfoEvent>.lndNetworks(networks: List<String>) = addUnique(LndNetworksTag.assemble(networks))

fun TagArrayBuilder<MostroInfoEvent>.lndUris(uris: List<String>) = addUnique(LndUrisTag.assemble(uris))

fun TagArrayBuilder<MostroInfoEvent>.bondEnabled(enabled: Boolean) = addUnique(BondEnabledTag.assemble(enabled))

fun TagArrayBuilder<MostroInfoEvent>.bondApplyTo(side: String) = addUnique(BondApplyToTag.assemble(side))

fun TagArrayBuilder<MostroInfoEvent>.bondSlashOnWaitingTimeout(slash: Boolean) = addUnique(BondSlashOnWaitingTimeoutTag.assemble(slash))

fun TagArrayBuilder<MostroInfoEvent>.bondAmountPct(fraction: Double) = addUnique(BondAmountPctTag.assemble(fraction))

fun TagArrayBuilder<MostroInfoEvent>.bondBaseAmountSats(sats: Long) = addUnique(BondBaseAmountSatsTag.assemble(sats))

fun TagArrayBuilder<MostroInfoEvent>.bondSlashNodeSharePct(fraction: Double) = addUnique(BondSlashNodeSharePctTag.assemble(fraction))

fun TagArrayBuilder<MostroInfoEvent>.bondPayoutClaimWindowDays(days: Long) = addUnique(BondPayoutClaimWindowDaysTag.assemble(days))

fun TagArrayBuilder<MostroInfoEvent>.cashuMintUrls(mintUrls: List<String>) = addUnique(CashuMintUrlTag.assemble(mintUrls))

fun TagArrayBuilder<MostroInfoEvent>.cashuEscrowLocktimeDays(days: Long) = addUnique(CashuEscrowLocktimeDaysTag.assemble(days))

fun TagArrayBuilder<MostroInfoEvent>.serbero(pubKey: HexKey) = addUnique(SerberoTag.assemble(pubKey))

fun TagArrayBuilder<MostroInfoEvent>.reputationImportIssuers(issuers: List<HexKey>) = addUnique(ReputationImportIssuersTag.assemble(issuers))

fun TagArrayBuilder<MostroInfoEvent>.reputationIssuer(pubKey: HexKey) = addUnique(ReputationIssuerTag.assemble(pubKey))
