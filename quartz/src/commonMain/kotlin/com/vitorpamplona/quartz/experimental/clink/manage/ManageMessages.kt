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
package com.vitorpamplona.quartz.experimental.clink.manage

import com.vitorpamplona.quartz.experimental.clink.common.GfyDelta
import com.vitorpamplona.quartz.experimental.clink.common.SatRange
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable

/**
 * The editable fields of a managed offer. `payer_data` is a list of field NAMES the offer
 * requests from the payer (e.g. `["email", "shipping_address"]`), per the CLINK Manage spec.
 */
class OfferFields(
    var label: String? = null,
    var price_sats: Long? = null,
    var callback_url: String? = null,
    var payer_data: List<String>? = null,
) : OptimizedSerializable

/**
 * The `offer` envelope on a Manage request: [id] identifies the target for update/get/delete,
 * [fields] carries the values for create/update.
 */
class ManageOffer(
    var id: String? = null,
    var fields: OfferFields? = null,
) : OptimizedSerializable

/**
 * Decrypted request to a CLINK Manage service (kind 21003) for delegated offer CRUD.
 * The offer data is NESTED under [offer] (not flat): `create` sets `offer.fields`, `update`
 * sets `offer.id` + `offer.fields`, `get`/`delete` set `offer.id`, and `list` filters by
 * [pointer]. (The published spec shows `create` with fields directly under `offer`; the
 * reference SDK nests both create and update under `offer.fields`, which we follow for
 * interop with SDK-built services.)
 */
class ManageRequest(
    var resource: String? = null,
    var action: String? = null,
    var pointer: String? = null,
    var offer: ManageOffer? = null,
) : OptimizedSerializable {
    companion object {
        const val RESOURCE_OFFER = "offer"
        const val ACTION_CREATE = "create"
        const val ACTION_UPDATE = "update"
        const val ACTION_GET = "get"
        const val ACTION_LIST = "list"
        const val ACTION_DELETE = "delete"
    }
}

/** A managed offer as returned by the service: [OfferFields] plus its [id] and server-generated [noffer]. */
class OfferData(
    var id: String? = null,
    var noffer: String? = null,
    var label: String? = null,
    var price_sats: Long? = null,
    var callback_url: String? = null,
    var payer_data: List<String>? = null,
) : OptimizedSerializable

/**
 * Decrypted response from a CLINK Manage service. On success [res] is `"ok"` and [details]
 * carries the affected offer(s); on failure [res] is `"GFY"` with [code]/[error] (and [field]
 * naming the invalid input for validation errors).
 *
 * NOTE: the spec types `details` as `OfferData | OfferData[]` (a single object for
 * create/update/get, an array for list). We model it as a list; a single-object response is
 * only fully parsed when the JSON mapper coerces single values to arrays. Amethyst is
 * consume-only for Manage (it never drives offer CRUD), so this is a documented limitation
 * rather than a live path.
 */
class ManageResponse(
    var res: String? = null,
    var resource: String? = null,
    var details: List<OfferData>? = null,
    var code: Int? = null,
    var error: String? = null,
    var field: String? = null,
    var range: SatRange? = null,
    var retry_after: Long? = null,
    var delta: GfyDelta? = null,
) : OptimizedSerializable {
    fun isOk(): Boolean = res == OK

    companion object {
        const val OK = "ok"
        const val GFY = "GFY"
    }
}
