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
package com.vitorpamplona.quartz.marmot.mls.group

/**
 * Encrypted local storage for decrypted Marmot inner event JSONs.
 *
 * Marmot MLS group messages are encrypted with per-message ratchet keys that
 * advance as each message is decrypted. After the ratchet has advanced past
 * a message, that ciphertext can no longer be decrypted — even by its
 * original recipient. To make group history survive app restarts we must
 * persist the *plaintext* inner events at the moment they are first
 * decrypted, rather than relying on relay redelivery.
 *
 * Implementations MUST encrypt all data at rest — the stored blobs contain
 * the contents of private group conversations.
 *
 * Each group's messages are stored independently, keyed by the hex-encoded
 * Nostr group ID (the `h` tag value from MIP-01).
 */
interface MarmotMessageStore {
    /**
     * Append a decrypted inner event JSON to the group's persisted message log.
     * Implementations should be tolerant to duplicate appends.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @param innerEventJson the decrypted inner Nostr event JSON (e.g., kind:9 chat)
     */
    suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    )

    /**
     * Load all persisted inner event JSONs for a group, in append order.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @return list of inner event JSON strings, empty if none
     */
    suspend fun loadMessages(nostrGroupId: String): List<String>

    /**
     * Delete all persisted messages for a group (after leaving).
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     */
    suspend fun delete(nostrGroupId: String)
}
