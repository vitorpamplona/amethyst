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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip51Lists.relayLists.RelayListCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kc_proto_amount_sats
import com.vitorpamplona.amethyst.commons.resources.kc_proto_app_components
import com.vitorpamplona.amethyst.commons.resources.kc_proto_cashu_mint
import com.vitorpamplona.amethyst.commons.resources.kc_proto_client
import com.vitorpamplona.amethyst.commons.resources.kc_proto_encryption_key
import com.vitorpamplona.amethyst.commons.resources.kc_proto_mls_ciphersuite
import com.vitorpamplona.amethyst.commons.resources.kc_proto_mls_extensions
import com.vitorpamplona.amethyst.commons.resources.kc_proto_mls_proposals
import com.vitorpamplona.amethyst.commons.resources.kc_proto_mls_version
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nutzap_senders
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_encryption
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_extensions
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_methods
import com.vitorpamplona.amethyst.commons.resources.kc_proto_nwc_notifications
import com.vitorpamplona.amethyst.commons.resources.kc_proto_offer
import com.vitorpamplona.amethyst.commons.resources.kc_proto_relays
import com.vitorpamplona.amethyst.commons.resources.keypackage_section
import com.vitorpamplona.amethyst.commons.resources.kind_card_private_payload
import com.vitorpamplona.amethyst.commons.resources.zap_type_anonymous
import com.vitorpamplona.amethyst.commons.resources.zap_type_private
import com.vitorpamplona.amethyst.commons.resources.zap_type_public
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.util.PlatformNumberFormatter
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.PublishedKeyPackage
import com.vitorpamplona.quartz.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.intent.Bolt12ZapIntentEvent

/*
 * Cards for the protocol-plumbing kinds: payment requests and wallet capabilities, MLS key
 * packages and the other records that carry public fields worth reading. Kinds whose payload is
 * only ciphertext are drawn by [RenderPrivateKindCard] straight from the dispatch.
 */

/** NIP-57 zap request (kind 9734): who is zapped, how much, what, and the public comment. */
@Composable
fun RenderZapRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ZapRequestEvent ?: return
    val card =
        remember(event) {
            ZapRequestCard(
                amountSats =
                    event.tags
                        .firstOrNull { it.size > 1 && it[0] == "amount" }
                        ?.get(1)
                        ?.toLongOrNull()
                        ?.div(1000)
                        ?.takeIf { it > 0 },
                recipients = event.zappedAuthor(),
                zappedEvent = event.zappedPost().firstOrNull(),
                zappedAddress = event.zappedAddresses().firstOrNull()?.let { Address.parse(it) },
                isPrivate = event.isPrivateZap(),
                isAnonymous = event.hasAnonTag() && !event.isPrivateZap(),
            )
        }

    val typeLabel =
        when {
            card.isPrivate -> stringRes(Res.string.zap_type_private)
            card.isAnonymous -> stringRes(Res.string.zap_type_anonymous)
            else -> stringRes(Res.string.zap_type_public)
        }

    KindCard(
        icon = MaterialSymbols.Bolt,
        title = kindName(event.kind),
        subtitle = card.amountSats?.let { stringRes(Res.string.kc_proto_amount_sats, PlatformNumberFormatter().format(it)) },
        iconTint = MaterialTheme.colorScheme.bitcoinColor,
    ) {
        KindCardChips(listOf(typeLabel))
        KindCardUsers(card.recipients, accountViewModel, nav)
        // A private zap keeps its message encrypted in the `anon` tag; content is not the comment.
        if (!card.isPrivate) {
            KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        }
        ZapTarget(card.zappedEvent, card.zappedAddress, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

private class ZapRequestCard(
    val amountSats: Long?,
    val recipients: List<HexKey>,
    val zappedEvent: HexKey?,
    val zappedAddress: Address?,
    val isPrivate: Boolean,
    val isAnonymous: Boolean,
)

/** NIP-B1 BOLT12 zap intent (kind 9737): recipient, amount, offer, target and comment. */
@Composable
fun RenderBolt12ZapIntent(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? Bolt12ZapIntentEvent ?: return
    val amountSats = remember(event) { event.amount()?.div(1000)?.takeIf { it > 0 } }
    val offer = remember(event) { event.offer() }
    val recipient = remember(event) { event.recipient() }
    val zappedEvent = remember(event) { event.zappedEvent() }
    val zappedAddress = remember(event) { event.zappedAddress()?.let { Address.parse(it) } }

    KindCard(
        icon = MaterialSymbols.Bolt,
        title = kindName(event.kind),
        subtitle = amountSats?.let { stringRes(Res.string.kc_proto_amount_sats, PlatformNumberFormatter().format(it)) },
        iconTint = MaterialTheme.colorScheme.bitcoinColor,
    ) {
        recipient?.let { KindCardUsers(listOf(it), accountViewModel, nav) }
        KindCardField(stringRes(Res.string.kc_proto_offer), offer?.toShortDisplay(), monospace = true, maxLines = 1)
        KindCardText(baseNote, event.content, backgroundColor, accountViewModel, nav, quotesLeft)
        ZapTarget(zappedEvent, zappedAddress, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** Quotes the event a zap points at: the `e` event first, else the `a` address. */
@Composable
private fun ZapTarget(
    eventId: HexKey?,
    address: Address?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (eventId != null) {
        KindCardQuoteEvent(eventId, quotesLeft, backgroundColor, accountViewModel, nav)
    } else if (address != null) {
        KindCardQuoteAddress(address, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

/** NIP-47 wallet service info (kind 13194): the methods, encryptions and notifications it supports. */
@Composable
fun RenderNwcInfo(baseNote: Note) {
    val event = baseNote.event as? NwcInfoEvent ?: return
    val methods = remember(event) { event.capabilities() }
    val encryptions = remember(event) { event.encryptionSchemes() }
    val notifications = remember(event) { event.notificationTypes() }
    val extensions = remember(event) { event.extensions() }

    KindCard(
        icon = MaterialSymbols.AccountBalanceWallet,
        title = kindName(event.kind),
    ) {
        LabeledChips(stringRes(Res.string.kc_proto_nwc_methods), methods)
        LabeledChips(stringRes(Res.string.kc_proto_nwc_notifications), notifications)
        LabeledChips(stringRes(Res.string.kc_proto_nwc_encryption), encryptions)
        LabeledChips(stringRes(Res.string.kc_proto_nwc_extensions), extensions)
    }
}

/**
 * Marmot MLS KeyPackage, both the addressable kind 30443 and the legacy kind 443: the MLS
 * parameters it advertises. The content is the serialized KeyPackage and is not shown.
 */
@Composable
fun RenderMarmotKeyPackage(baseNote: Note) {
    val event = baseNote.event as? PublishedKeyPackage ?: return
    val ciphersuite =
        remember(event) {
            event.mlsCiphersuite()?.let { code -> MlsCiphersuite.fromCode(code)?.let { "${it.name} ($code)" } ?: code }
        }
    val version = remember(event) { event.mlsProtocolVersion() }
    val extensions = remember(event) { event.mlsExtensions() ?: emptyList() }
    val proposals = remember(event) { event.mlsProposals() ?: emptyList() }
    val components = remember(event) { event.appComponents() ?: emptyList() }
    val relays = remember(event) { event.relays()?.map { it.displayUrl() } ?: emptyList() }
    val client = remember(event) { event.clientName() }

    KindCard(
        icon = MaterialSymbols.Key,
        title = kindName(event.kind),
        subtitle = client,
    ) {
        KindCardField(stringRes(Res.string.kc_proto_mls_ciphersuite), ciphersuite, monospace = true)
        KindCardField(stringRes(Res.string.kc_proto_mls_version), version)
        LabeledChips(stringRes(Res.string.kc_proto_mls_extensions), extensions)
        LabeledChips(stringRes(Res.string.kc_proto_mls_proposals), proposals)
        LabeledChips(stringRes(Res.string.kc_proto_app_components), components)
        LabeledChips(stringRes(Res.string.kc_proto_relays), relays)
    }
}

/** Marmot KeyPackage relay list (kind 10051), drawn like the other relay lists. */
@Composable
fun RenderKeyPackageRelayList(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? KeyPackageRelayListEvent ?: return
    val relays = remember(event) { RelayListCard(event.relays()) }

    DisplayRelaySet(
        relays,
        stringRes(Res.string.keypackage_section),
        null,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

/** NIP-60 mint quote (kind 7374): the quote id is encrypted, the mint it was opened at is public. */
@Composable
fun RenderCashuMintQuote(baseNote: Note) {
    val event = baseNote.event as? CashuMintQuoteEvent ?: return
    val mint = remember(event) { event.mint() }

    PrivateCardWithBody(baseNote, MaterialSymbols.Paid) {
        KindCardField(stringRes(Res.string.kc_proto_cashu_mint), mint)
    }
}

/** NIP-60 spending history (kind 7376): the amounts are encrypted, the redeemed nutzaps' senders are public. */
@Composable
fun RenderCashuSpendingHistory(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? CashuSpendingHistoryEvent ?: return
    val senders = remember(event) { event.nutzapSenders().distinct() }

    PrivateCardWithBody(baseNote, MaterialSymbols.History) {
        if (senders.isNotEmpty()) {
            SectionLabel(stringRes(Res.string.kc_proto_nutzap_senders))
            KindCardUsers(senders, accountViewModel, nav)
        }
    }
}

/** Key decoupling: the encryption keys an account announces (kind 10044). */
@Composable
fun RenderEncryptionKeyList(baseNote: Note) {
    val event = baseNote.event as? EncryptionKeyListEvent ?: return
    val keys = remember(event) { event.keys().map { it.pubkey.toShortDisplay() } }

    KindCard(
        icon = MaterialSymbols.Key,
        title = kindName(event.kind),
    ) {
        keys.forEach { KindCardField(stringRes(Res.string.kc_proto_encryption_key), it, monospace = true, maxLines = 1) }
    }
}

/** Key decoupling: a device asking for the account's encryption key (kind 4454). */
@Composable
fun RenderEncryptionKeyRequest(baseNote: Note) {
    val event = baseNote.event as? EncryptionKeyRequestEvent ?: return
    val client = remember(event) { event.clientName() }

    PrivateCardWithBody(baseNote, MaterialSymbols.Key) {
        KindCardField(stringRes(Res.string.kc_proto_client), client)
    }
}

/** [RenderPrivateKindCard]'s title and alt subtitle, plus the few public fields a kind carries. */
@Composable
private fun PrivateCardWithBody(
    note: Note,
    icon: MaterialSymbol,
    body: @Composable () -> Unit,
) {
    val event = note.event ?: return
    val alt = remember(event) { event.alt() }
    KindCard(
        icon = icon,
        title = kindName(event.kind),
        subtitle = alt ?: stringRes(Res.string.kind_card_private_payload),
    ) {
        body()
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A small label over a row of chips; nothing when [labels] is empty. */
@Composable
private fun LabeledChips(
    label: String,
    labels: List<String>,
) {
    if (labels.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(label)
        KindCardChips(labels)
    }
}
