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
package com.vitorpamplona.quartz.nip86RelayManagement

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.toHttp
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.AllowedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedEvent
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BannedPubkey
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.BlockedIp
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.EventNeedingModeration
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class Nip86Client(
    val relayUrl: NormalizedRelayUrl,
    val signer: NostrSigner,
) {
    val httpUrl: String = relayUrl.toHttp()

    suspend fun buildAuthHeader(payload: ByteArray): String {
        val template =
            HTTPAuthorizationEvent.build(
                url = httpUrl,
                method = "POST",
                file = payload,
            )
        val signedEvent = signer.sign(template)
        return signedEvent.toAuthToken()
    }

    fun serializeRequest(request: Nip86Request): String = JsonMapper.toJson(request)

    fun parseResponse(json: String): Nip86Response = JsonMapper.fromJson<Nip86Response>(json)

    fun parseSupportedMethods(response: Nip86Response): List<String>? {
        val result = response.result ?: return null
        return (result as? JsonArray)?.map { it.jsonPrimitive.content }
    }

    fun parseBooleanResult(response: Nip86Response): Boolean? {
        val result = response.result ?: return null
        return (result as? JsonPrimitive)?.boolean
    }

    fun parseBannedPubkeys(response: Nip86Response): List<BannedPubkey>? {
        val result = response.result ?: return null
        return JsonMapper.fromJson<List<BannedPubkey>>(result.toString())
    }

    fun parseAllowedPubkeys(response: Nip86Response): List<AllowedPubkey>? {
        val result = response.result ?: return null
        return JsonMapper.fromJson<List<AllowedPubkey>>(result.toString())
    }

    fun parseBannedEvents(response: Nip86Response): List<BannedEvent>? {
        val result = response.result ?: return null
        return JsonMapper.fromJson<List<BannedEvent>>(result.toString())
    }

    fun parseEventsNeedingModeration(response: Nip86Response): List<EventNeedingModeration>? {
        val result = response.result ?: return null
        return JsonMapper.fromJson<List<EventNeedingModeration>>(result.toString())
    }

    fun parseAllowedKinds(response: Nip86Response): List<Int>? {
        val result = response.result ?: return null
        return (result as? JsonArray)?.map { it.jsonPrimitive.int }
    }

    fun parseBlockedIps(response: Nip86Response): List<BlockedIp>? {
        val result = response.result ?: return null
        return JsonMapper.fromJson<List<BlockedIp>>(result.toString())
    }

    // Convenience methods that build requests

    fun supportedMethodsRequest() = Nip86Request.supportedMethods()

    fun banPubkeyRequest(
        pubkey: String,
        reason: String? = null,
    ) = Nip86Request.banPubkey(pubkey, reason)

    fun unbanPubkeyRequest(
        pubkey: String,
        reason: String? = null,
    ) = Nip86Request.unbanPubkey(pubkey, reason)

    fun listBannedPubkeysRequest() = Nip86Request.listBannedPubkeys()

    fun allowPubkeyRequest(
        pubkey: String,
        reason: String? = null,
    ) = Nip86Request.allowPubkey(pubkey, reason)

    fun unallowPubkeyRequest(
        pubkey: String,
        reason: String? = null,
    ) = Nip86Request.unallowPubkey(pubkey, reason)

    fun listAllowedPubkeysRequest() = Nip86Request.listAllowedPubkeys()

    fun listEventsNeedingModerationRequest() = Nip86Request.listEventsNeedingModeration()

    fun allowEventRequest(
        eventId: String,
        reason: String? = null,
    ) = Nip86Request.allowEvent(eventId, reason)

    fun banEventRequest(
        eventId: String,
        reason: String? = null,
    ) = Nip86Request.banEvent(eventId, reason)

    fun listBannedEventsRequest() = Nip86Request.listBannedEvents()

    fun changeRelayNameRequest(newName: String) = Nip86Request.changeRelayName(newName)

    fun changeRelayDescriptionRequest(newDescription: String) = Nip86Request.changeRelayDescription(newDescription)

    fun changeRelayIconRequest(newIconUrl: String) = Nip86Request.changeRelayIcon(newIconUrl)

    fun allowKindRequest(kind: Int) = Nip86Request.allowKind(kind)

    fun disallowKindRequest(kind: Int) = Nip86Request.disallowKind(kind)

    fun listAllowedKindsRequest() = Nip86Request.listAllowedKinds()

    fun blockIpRequest(
        ip: String,
        reason: String? = null,
    ) = Nip86Request.blockIp(ip, reason)

    fun unblockIpRequest(ip: String) = Nip86Request.unblockIp(ip)

    fun listBlockedIpsRequest() = Nip86Request.listBlockedIps()

    companion object {
        fun supportsNip86(supportedNips: List<String>?): Boolean = supportedNips?.any { it == "86" } == true
    }
}
