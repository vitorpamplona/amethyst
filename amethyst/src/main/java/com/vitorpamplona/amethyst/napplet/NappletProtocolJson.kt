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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import org.json.JSONArray
import org.json.JSONObject

/**
 * Marshals the KMP-pure [NappletRequest] / [NappletResponse] types to and from the JSON the
 * applet exchanges over `window.napplet.*`. This is the only place the boundary parses applet
 * input, so it is deliberately strict: unknown ops decode to `null` (the broker rejects them)
 * and a malformed body throws rather than guessing.
 *
 * The host process only ever *shuttles* these strings; decoding/encoding happens in the
 * main-process broker so a compromised host cannot fabricate a typed request that skips a field.
 */
object NappletProtocolJson {
    /** Parses an applet request. Returns `null` for an unrecognized `op` so the broker can deny it. */
    fun decodeRequest(json: String): NappletRequest? {
        val o = JSONObject(json)
        return when (o.getString("op")) {
            "getPublicKey" -> NappletRequest.GetPublicKey
            "signEvent" ->
                NappletRequest.SignEvent(
                    kind = o.getInt("kind"),
                    tags = decodeTags(o.optJSONArray("tags")),
                    content = o.optString("content", ""),
                )
            "nip04Encrypt" -> NappletRequest.Nip04Encrypt(o.getString("peer"), o.getString("plaintext"))
            "nip04Decrypt" -> NappletRequest.Nip04Decrypt(o.getString("peer"), o.getString("ciphertext"))
            "nip44Encrypt" -> NappletRequest.Nip44Encrypt(o.getString("peer"), o.getString("plaintext"))
            "nip44Decrypt" -> NappletRequest.Nip44Decrypt(o.getString("peer"), o.getString("ciphertext"))
            "publish" -> NappletRequest.Publish(Event.fromJson(o.getJSONObject("event").toString()))
            else -> null
        }
    }

    fun encodeResponse(response: NappletResponse): String {
        val o = JSONObject()
        when (response) {
            is NappletResponse.PublicKey -> {
                o.put("type", "publicKey")
                o.put("pubkey", response.pubkey)
            }
            is NappletResponse.SignedEvent -> {
                o.put("type", "signedEvent")
                o.put("event", JSONObject(response.event.toJson()))
            }
            is NappletResponse.Text -> {
                o.put("type", "text")
                o.put("value", response.value)
            }
            is NappletResponse.Published -> {
                o.put("type", "published")
                o.put("relays", JSONArray(response.relays))
            }
            is NappletResponse.Denied -> {
                o.put("type", "denied")
                o.put("capability", response.capability.name)
                o.put("reason", response.reason)
            }
            is NappletResponse.Unsupported -> {
                o.put("type", "unsupported")
                o.put("operation", response.operation)
            }
            is NappletResponse.Failed -> {
                o.put("type", "failed")
                o.put("reason", response.reason)
            }
        }
        return o.toString()
    }

    private fun decodeTags(array: JSONArray?): Array<Array<String>> {
        if (array == null) return emptyArray()
        return Array(array.length()) { i ->
            val inner = array.getJSONArray(i)
            Array(inner.length()) { j -> inner.getString(j) }
        }
    }
}
