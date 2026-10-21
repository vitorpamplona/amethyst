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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerMessage
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestParser
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse

/*
 * NIP-46 messages, the plaintext of a kind 24133 event: `{"id","method","params"}` from a client,
 * `{"id","result","error"}` from a signer.
 *
 * Read like the Jackson deserializers they replace, with one difference: a JSON `null` field
 * reads as absent, as the kotlinx path on native always did, instead of the text "null" (which
 * made `"error": null` an error).
 */

/** One NIP-46 object: a request when it carries a `method`, else a response. */
internal fun NostrJsonReader.readBunkerObject(): BunkerMessage {
    var id: String? = null
    var method: String? = null
    var hasMethod = false
    var result: String? = null
    var error: String? = null
    var params: Array<String> = EMPTY_PARAMS

    forEachField { key ->
        when (key) {
            "id" -> {
                id = bunkerText()
            }

            "method" -> {
                hasMethod = true
                method = bunkerText()
            }

            "result" -> {
                result = bunkerText()
            }

            "error" -> {
                error = bunkerText()
            }

            "params" -> {
                params = bunkerParams()
            }

            else -> {
                skipValue(1)
            }
        }
    }

    val messageId = id ?: fail("NIP-46 message without an id")
    return if (hasMethod) {
        BunkerRequestParser.parse(messageId, method ?: fail("NIP-46 request without a method"), params)
    } else {
        BunkerResponse.parse(messageId, result, error)
    }
}

/** A field's text: a string as it is, a number or boolean by its text, `null` as absent. */
private fun NostrJsonReader.bunkerText(): String? =
    when (s[i]) {
        '"' -> readString()
        't' -> literal("true", "true")
        'f' -> literal("false", "false")
        'n' -> literal("null", "").let { null }
        '{', '[' -> fail("expected a string")
        else -> numberText()
    }

/**
 * `params`, leniently, so a malformed request still yields its id and can be answered with an
 * error: anything but an array is no params, a scalar entry reads as its text (`null` as "null"),
 * and an object or array entry as its compact JSON.
 */
private fun NostrJsonReader.bunkerParams(): Array<String> {
    if (s[i] != '[') {
        skipValue(1)
        return EMPTY_PARAMS
    }
    i++
    if (peek() == ']') {
        i++
        return EMPTY_PARAMS
    }
    val out = ArrayList<String>(4)
    while (true) {
        val value =
            when (peek()) {
                '"' -> {
                    readString()
                }

                't' -> {
                    literal("true", "true")
                }

                'f' -> {
                    literal("false", "false")
                }

                'n' -> {
                    literal("null", "null")
                }

                '{', '[' -> {
                    val start = i
                    skipValue(2)
                    compactJson(s, start, i)
                }

                else -> {
                    numberText()
                }
            }
        out.add(value)
        val c = peek()
        i++
        if (c == ',') continue
        if (c == ']') return out.toTypedArray()
        i--
        fail("expected ',' or ']'")
    }
}

/** `json[start until end]` without the whitespace between its tokens. */
private fun compactJson(
    json: String,
    start: Int,
    end: Int,
): String {
    val sb = StringBuilder(end - start)
    var k = start
    while (k < end) {
        val c = json[k]
        when (c) {
            ' ', '\t', '\n', '\r' -> {
                k++
            }

            '"' -> {
                val from = k
                k++
                while (k < end) {
                    val x = json[k]
                    if (x == '\\') {
                        k += 2
                    } else {
                        k++
                        if (x == '"') break
                    }
                }
                sb.append(json, from, k)
            }

            else -> {
                sb.append(c)
                k++
            }
        }
    }
    return sb.toString()
}

private val EMPTY_PARAMS = emptyArray<String>()

/** `{"id":…,"method":…,"params":[…]}`, as `BunkerRequestSerializer` writes it. */
internal fun NostrJsonWriter.bunkerRequest(request: BunkerRequest) {
    raw("{\"id\":")
    string(request.id)
    raw(",\"method\":")
    string(request.method)
    raw(",\"params\":[")
    val params = request.params
    for (k in params.indices) {
        if (k > 0) raw(',')
        string(params[k])
    }
    raw("]}")
}

/** `{"id":…}` plus `result` and `error` when set, as `BunkerResponseSerializer` writes it. */
internal fun NostrJsonWriter.bunkerResponse(response: BunkerResponse) {
    raw("{\"id\":")
    string(response.id)
    response.result?.let {
        raw(",\"result\":")
        string(it)
    }
    response.error?.let {
        raw(",\"error\":")
        string(it)
    }
    raw('}')
}
