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
package com.vitorpamplona.quartz.nipCCGeocaching.foundLog.tags

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `verification` tag of a found log (kind 7516): a whole kind 7517 event, as JSON, inline.
 *
 * NIP-CC allows the verification to be embedded, published standalone, or both; embedding is what
 * makes a log self-contained, so this is the common case.
 *
 * The payload is attacker-controlled — anybody can publish a found log with any string in this
 * tag — so a parse failure has to be an ordinary null rather than an exception that takes the
 * surrounding feed down with it.
 *
 * Note this returns a [GeocacheVerificationEvent] only because kind 7517 is registered with
 * [com.vitorpamplona.quartz.utils.EventFactory]. Unregistered, the same JSON would parse to a
 * plain [Event] and every embedded verification in the wild would read as absent.
 */
class EmbeddedVerificationTag {
    companion object {
        const val TAG_NAME = "verification"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): GeocacheVerificationEvent? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            return try {
                Event.fromJson(tag[1]) as? GeocacheVerificationEvent
            } catch (e: Exception) {
                Log.w("EmbeddedVerificationTag") { "Could not parse the embedded verification event: ${e.message}" }
                null
            }
        }

        fun assemble(verification: GeocacheVerificationEvent) = arrayOf(TAG_NAME, verification.toJson())
    }
}
