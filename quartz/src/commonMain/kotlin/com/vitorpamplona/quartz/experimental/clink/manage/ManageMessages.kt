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
 * Decrypted request to a CLINK Manage service (kind 21003) for delegated offer CRUD.
 * [resource] is always `"offer"`; [action] is one of create/update/get/list/delete.
 * `create` omits [id] (the server generates it); update/get/delete require [id];
 * list filters by the requesting app.
 */
class ManageRequest(
    var resource: String? = null,
    var action: String? = null,
    var id: String? = null,
    var label: String? = null,
    var price_sats: Long? = null,
    var callback_url: String? = null,
    var payer_data: Map<String, Any?>? = null,
    var pointer: String? = null,
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

/** A managed offer as returned by the service, including the server-generated `noffer` pointer. */
class ManageOffer(
    var id: String? = null,
    var label: String? = null,
    var price_sats: Long? = null,
    var callback_url: String? = null,
    var payer_data: Map<String, Any?>? = null,
    var noffer: String? = null,
) : OptimizedSerializable

/**
 * Decrypted response from a CLINK Manage service. On success [res] is `"ok"` with
 * either [offer] (create/update/get) or [offers] (list) populated; on failure [res]
 * is `"GFY"` with [code]/[error] set.
 */
class ManageResponse(
    var res: String? = null,
    var resource: String? = null,
    var offer: ManageOffer? = null,
    var offers: List<ManageOffer>? = null,
    var code: Int? = null,
    var error: String? = null,
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
