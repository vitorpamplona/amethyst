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
package com.vitorpamplona.quartz.relay

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import java.io.File

/**
 * Relay-side handle for the NIP-11 information document. Wraps the
 * client-side [Nip11RelayInformation] model and provides loaders for
 * config files plus a default doc that advertises the NIPs this relay
 * actually implements.
 */
data class RelayInfo(
    val document: Nip11RelayInformation,
) {
    /** Pre-rendered JSON, ready to write into the HTTP response body. */
    val json: String by lazy { JsonMapper.toJson(document) }

    companion object {
        /** Pre-built default for `Relay(url = ...)` — advertises the supported NIPs. */
        fun default(url: NormalizedRelayUrl): RelayInfo =
            RelayInfo(
                Nip11RelayInformation(
                    name = "quartz-relay",
                    description = "Embedded Nostr relay from the Amethyst quartz library.",
                    software = "https://github.com/vitorpamplona/amethyst/tree/main/quartz-relay",
                    version = "1.08.0",
                    // Currently implemented: NIP-01 (basic), NIP-09 (deletion via
                    // DeletionRequestModule), NIP-11 (this doc), NIP-40 (expiration
                    // via ExpirationModule), NIP-42 (AUTH — when policy enables),
                    // NIP-45 (COUNT), NIP-50 (search via FTS), NIP-62 (right to vanish),
                    // NIP-77 (negentropy reconciliation), NIP-86 (relay management API
                    // — when admin pubkeys are configured).
                    supported_nips = listOf("1", "9", "11", "40", "42", "45", "50", "62", "77", "86"),
                ),
            )

        /** Loads a NIP-11 doc from a JSON file (e.g. a relay operator's config). */
        fun fromFile(file: File): RelayInfo = RelayInfo(Nip11RelayInformation.fromJson(file.readText()))

        /** Parses a NIP-11 doc from a raw JSON string. */
        fun fromJson(json: String): RelayInfo = RelayInfo(Nip11RelayInformation.fromJson(json))
    }
}
