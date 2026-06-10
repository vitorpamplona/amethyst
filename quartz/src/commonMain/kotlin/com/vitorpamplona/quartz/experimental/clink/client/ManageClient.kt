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
package com.vitorpamplona.quartz.experimental.clink.client

import com.vitorpamplona.quartz.experimental.clink.manage.ManageEvent
import com.vitorpamplona.quartz.experimental.clink.manage.ManageOffer
import com.vitorpamplona.quartz.experimental.clink.manage.ManageRequest
import com.vitorpamplona.quartz.experimental.clink.manage.ManageResponse
import com.vitorpamplona.quartz.experimental.clink.manage.OfferFields
import com.vitorpamplona.quartz.experimental.clink.pointers.NManage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * High-level CLINK Manage app client for delegated offer CRUD (kind 21003) against a
 * wallet server addressed by an [NManage] pointer.
 */
class ManageClient(
    val pointer: NManage,
    val signer: NostrSigner,
) {
    val serverPubKey: HexKey get() = pointer.pubKey
    val relays: List<NormalizedRelayUrl> get() = pointer.relays

    suspend fun createOffer(
        label: String? = null,
        priceSats: Long? = null,
        callbackUrl: String? = null,
        payerData: List<String>? = null,
        createdAt: Long = TimeUtils.now(),
    ): ManageEvent =
        send(
            ManageRequest(
                resource = ManageRequest.RESOURCE_OFFER,
                action = ManageRequest.ACTION_CREATE,
                pointer = pointer.pointer,
                offer = ManageOffer(fields = OfferFields(label, priceSats, callbackUrl, payerData)),
            ),
            createdAt,
        )

    suspend fun updateOffer(
        id: String,
        label: String? = null,
        priceSats: Long? = null,
        callbackUrl: String? = null,
        payerData: List<String>? = null,
        createdAt: Long = TimeUtils.now(),
    ): ManageEvent =
        send(
            ManageRequest(
                resource = ManageRequest.RESOURCE_OFFER,
                action = ManageRequest.ACTION_UPDATE,
                offer = ManageOffer(id = id, fields = OfferFields(label, priceSats, callbackUrl, payerData)),
            ),
            createdAt,
        )

    suspend fun getOffer(
        id: String,
        createdAt: Long = TimeUtils.now(),
    ): ManageEvent = send(ManageRequest(ManageRequest.RESOURCE_OFFER, ManageRequest.ACTION_GET, offer = ManageOffer(id = id)), createdAt)

    suspend fun listOffers(createdAt: Long = TimeUtils.now()): ManageEvent =
        send(
            ManageRequest(ManageRequest.RESOURCE_OFFER, ManageRequest.ACTION_LIST, pointer = pointer.pointer),
            createdAt,
        )

    suspend fun deleteOffer(
        id: String,
        createdAt: Long = TimeUtils.now(),
    ): ManageEvent = send(ManageRequest(ManageRequest.RESOURCE_OFFER, ManageRequest.ACTION_DELETE, offer = ManageOffer(id = id)), createdAt)

    private suspend fun send(
        request: ManageRequest,
        createdAt: Long,
    ): ManageEvent = ManageEvent.createRequest(request, serverPubKey, signer, createdAt)

    fun responseFilter(requestId: HexKey): Filter =
        Filter(
            kinds = listOf(ManageEvent.KIND),
            authors = listOf(serverPubKey),
            tags = mapOf("e" to listOf(requestId)),
        )

    suspend fun parseResponse(event: ManageEvent): ManageResponse = event.decryptResponse(signer)
}
