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
package com.vitorpamplona.amethyst.commons.llamagarden

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
import com.vitorpamplona.quartz.llamagarden.curation.LlamaGardenCuration
import com.vitorpamplona.quartz.llamagarden.modelRequest.ModelRequestEvent
import com.vitorpamplona.quartz.llamagarden.seederRequest.SeederRequestEvent
import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate

/**
 * llama.garden's actions for this account. Listings, model requests and seeder requests are mined to
 * [LlamaGardenDefaults.POW_BITS] first, as the viewer does, on the account's mining queue (or right
 * away where there is none); curation labels are not mined.
 */
object LlamaGardenActions {
    private suspend fun <T : Event> Account.sendMinedLikeTheViewer(template: EventTemplate<T>) {
        sendMined(template, PoWReplay.Broadcast(), LlamaGardenDefaults.POW_BITS) { signAndComputeBroadcast(it) }
    }

    /** Signals that [listing] has no seeders. Replaces this account's earlier request for the same torrent. */
    suspend fun requestSeeders(
        account: Account,
        listing: Note,
    ) {
        if (!account.signer.isWriteable()) return
        val hint = listing.toEventHint<ModelTorrentEvent>() ?: return
        account.sendMinedLikeTheViewer(SeederRequestEvent.build(hint, account.signer.pubKey))
    }

    /** Asks for [name] in the catalog. The viewer keeps one open request per user: this replaces the last. */
    suspend fun requestModel(
        account: Account,
        name: String,
        links: List<String>,
        type: String,
        vramGb: Int?,
    ) {
        if (!account.signer.isWriteable() || name.isBlank()) return
        account.sendMinedLikeTheViewer(ModelRequestEvent.build(account.signer.pubKey, name.trim(), links, type, vramGb))
    }

    /**
     * Lists the already uploaded `.torrent` [form] describes, the way the viewer's "Add a torrent" form
     * does. It shows in llama.garden's catalog once a curator approves it.
     */
    suspend fun submitTorrent(
        account: Account,
        form: TorrentSubmissionForm,
    ) {
        if (!account.signer.isWriteable() || !form.isPublishable) return
        account.sendMinedLikeTheViewer(form.template(account.signer.pubKey))
    }

    /** A curator's approval of [listing]: llama.garden's viewers then show it whoever wrote it. */
    suspend fun approve(
        account: Account,
        listing: Note,
    ) {
        if (!account.signer.isWriteable()) return
        val hint = listing.toEventHint<ModelTorrentEvent>() ?: return
        account.signAndComputeBroadcast(LlamaGardenCuration.buildApproval(hint))
    }

    /** A curator's rejection of [listing]. */
    suspend fun reject(
        account: Account,
        listing: Note,
    ) {
        if (!account.signer.isWriteable()) return
        val hint = listing.toEventHint<ModelTorrentEvent>() ?: return
        account.signAndComputeBroadcast(LlamaGardenCuration.buildRejection(hint))
    }
}
