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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.instanceName
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.EscrowModeTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.MostroVersionTag
import com.vitorpamplona.quartz.nip69P2pOrderEvents.platform
import com.vitorpamplona.quartz.nip69P2pOrderEvents.tags.DocumentTypeTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Mostro instance's status and terms (kind 38385): its daemon version, fee, order limits,
 * accepted currencies, proof-of-work, protocol and escrow mode, bond policy and Lightning node.
 * Spec: Mostro protocol `other_events.md` ("Mostro Instance Status"), split off kind 38383 by
 * `mostro_separate_kinds.md`. Not a NIP; NIP-69 defines only the 38383 order.
 *
 * Addressable on `d` = the instance's own pubkey (hex), re-published on an interval and on every
 * maintenance-mode change, so the newest one is the instance's current terms. Content is empty;
 * every value is a tag, read through the accessors below, which return null when a tag is absent
 * or malformed. Several tags carry spec-defined meanings for absence (see each tag class): no
 * `escrow_mode` means Lightning, no `maintenance_mode` means open, no `pow_first_contact` means
 * unknown.
 *
 * **The kind is shared.** Other apps publish unrelated addressable events on 38385: Paygress
 * (a Cashu-paid compute marketplace) its lease revocations, a "bondtrade" app its bond
 * assignments, a game its hall-of-fame scores. `EventFactory` therefore builds this class only
 * for [isMostroInfo] tags, and [UnrecognizedKind38385Event] for the rest, so nothing else is ever
 * shown as a Mostro instance.
 *
 * Searchable by the human-chosen instance name (the `y` tag's second value) and the accepted
 * currency codes, the same keywords a [com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent]
 * indexes. Versions, hashes, node keys, URIs and numeric terms are machine data and stay out.
 */
@Immutable
class MostroInfoEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    SearchableEvent,
    PubKeyHintProvider {
    override fun indexableContent() = (listOfNotNull(instanceName()) + fiatCurrenciesAccepted().orEmpty()).joinToString(" ")

    // The read path: the same fields indexableContent() joins, without the join.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(instanceName())) return
        fiatCurrenciesAccepted()?.forEach { if (!visitor.visit(it)) return }
    }

    override fun indexableSeparator() = " "

    /** No tag here carries a relay hint. */
    override fun pubKeyHints() = emptyList<PubKeyHint>()

    /**
     * `SOLVER`: the instance's Serbero dispute assistant (`serbero`);
     * `REPUTATION_ISSUER`: the key it signs reputation attestations with (`reputation_issuer`);
     * `REPUTATION_IMPORT_ISSUER`: each issuer whose attestations it imports
     * (`reputation_import_issuers`).
     *
     * Not the `d`: it is the instance's own key, the event's `AUTHOR`, and the vocabulary derives
     * no links from an event's own `d`. Not `lnd_node_pubkey`: a Lightning node key, not a Nostr
     * user.
     */
    override fun linkedPubKeys(): List<HexKey> = listOfNotNull(serbero(), reputationIssuer()) + reputationImportIssuers().orEmpty()

    /** The instance's pubkey as published in `d`, when it is a 64-hex key. */
    fun instancePubKey(): HexKey? = dTag().takeIf { it.length == 64 && Hex.isHex64(it) }

    fun platform() = tags.platform()

    fun instanceName() = tags.instanceName()

    fun mostroVersion() = tags.mostroVersion()

    fun mostroCommitHash() = tags.mostroCommitHash()

    fun maxOrderAmount() = tags.maxOrderAmount()

    fun minOrderAmount() = tags.minOrderAmount()

    fun expirationHours() = tags.expirationHours()

    fun expirationSeconds() = tags.expirationSeconds()

    /** The accepted currency codes. Empty means every currency (the spec's empty value). */
    fun fiatCurrenciesAccepted() = tags.fiatCurrenciesAccepted()

    fun maxOrdersPerResponse() = tags.maxOrdersPerResponse()

    /** The trade fee as a fraction (`0.006` = 0.6%). */
    fun fee() = tags.fee()

    fun pow() = tags.pow()

    fun powFirstContact() = tags.powFirstContact()

    fun protocolVersion() = tags.protocolVersion()

    fun maintenanceMode() = tags.maintenanceMode()

    /** other_events.md: absence of `maintenance_mode` means the instance is open. */
    fun isInMaintenance() = maintenanceMode() == true

    fun escrowMode() = tags.escrowMode()

    /** other_events.md: clients treat a missing `escrow_mode` as `lightning`. */
    fun escrowModeOrDefault() = escrowMode() ?: EscrowModeTag.LIGHTNING

    fun holdInvoiceExpirationWindow() = tags.holdInvoiceExpirationWindow()

    fun holdInvoiceCltvDelta() = tags.holdInvoiceCltvDelta()

    fun invoiceExpirationWindow() = tags.invoiceExpirationWindow()

    fun lndVersion() = tags.lndVersion()

    fun lndNodePubKey() = tags.lndNodePubKey()

    fun lndCommitHash() = tags.lndCommitHash()

    fun lndNodeAlias() = tags.lndNodeAlias()

    fun lndChains() = tags.lndChains()

    fun lndNetworks() = tags.lndNetworks()

    fun lndUris() = tags.lndUris()

    fun bondEnabled() = tags.bondEnabled()

    fun bondApplyTo() = tags.bondApplyTo()

    fun bondSlashOnWaitingTimeout() = tags.bondSlashOnWaitingTimeout()

    fun bondAmountPct() = tags.bondAmountPct()

    fun bondBaseAmountSats() = tags.bondBaseAmountSats()

    fun bondSlashNodeSharePct() = tags.bondSlashNodeSharePct()

    fun bondPayoutClaimWindowDays() = tags.bondPayoutClaimWindowDays()

    fun cashuMintUrls() = tags.cashuMintUrls()

    fun cashuEscrowLocktimeDays() = tags.cashuEscrowLocktimeDays()

    fun serbero() = tags.serbero()

    fun reputationImportIssuers() = tags.reputationImportIssuers()

    fun reputationIssuer() = tags.reputationIssuer()

    companion object {
        const val KIND = 38385

        /**
         * True for a Mostro info shape: a `z` of `info` (every Mostro release since the split
         * publishes it), or, failing that, a `mostro_version` tag, which only a Mostro daemon
         * writes. What other apps put on 38385 carries neither.
         */
        fun isMostroInfo(tags: TagArray): Boolean =
            DocumentTypeTag.isType(tags, DocumentTypeTag.INFO) ||
                tags.fastAny { it.size > 1 && it[0] == MostroVersionTag.TAG_NAME && it[1].isNotEmpty() }

        /**
         * [mostroPubKey] is the `d`: the key the template will be signed with, since an instance
         * addresses its info by its own pubkey.
         */
        fun build(
            mostroPubKey: HexKey,
            mostroVersion: String,
            fee: Double,
            minOrderAmount: Long,
            maxOrderAmount: Long,
            fiatCurrenciesAccepted: List<String> = emptyList(),
            protocolVersion: Long? = null,
            instanceName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MostroInfoEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(mostroPubKey)
            mostroVersion(mostroVersion)
            maxOrderAmount(maxOrderAmount)
            minOrderAmount(minOrderAmount)
            fiatCurrenciesAccepted(fiatCurrenciesAccepted)
            fee(fee)
            protocolVersion?.let { protocolVersion(it) }
            platform(instanceName)
            documentType()
            initializer()
        }
    }
}
