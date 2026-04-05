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
 * Interface for encrypted local storage of MLS group state.
 *
 * Implementations MUST encrypt all data at rest — the stored blobs contain
 * private keys and epoch secrets. Platform-specific implementations:
 * - Android: EncryptedSharedPreferences or encrypted DataStore
 * - Desktop: Encrypted file with AES-GCM keyed from system keychain
 *
 * Each group's state is stored independently, keyed by the hex-encoded
 * Nostr group ID (the `h` tag value from MIP-01, NOT the internal MLS
 * group ID). This allows the storage layer to list/prune groups without
 * parsing MLS state.
 *
 * Retained epoch secrets are stored alongside the current state so
 * late-arriving messages from the previous epoch can still be decrypted.
 */
interface MlsGroupStateStore {
    /**
     * Persist the current group state.
     * Overwrites any previously saved state for this group.
     *
     * @param nostrGroupId hex-encoded Nostr group ID (`h` tag)
     * @param state TLS-encoded group state bytes from [MlsGroupState.encodeTls]
     */
    suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    )

    /**
     * Load a previously saved group state.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @return TLS-encoded state bytes, or null if no state exists
     */
    suspend fun load(nostrGroupId: String): ByteArray?

    /**
     * Delete all stored state for a group (after leaving).
     * This should securely delete the encrypted blob.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     */
    suspend fun delete(nostrGroupId: String)

    /**
     * List all Nostr group IDs that have saved state.
     * Used during startup to restore active group memberships.
     */
    suspend fun listGroups(): List<String>

    /**
     * Save retained epoch secrets for late-message decryption.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @param retainedSecrets list of TLS-encoded [RetainedEpochSecrets]
     */
    suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    )

    /**
     * Load retained epoch secrets.
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @return list of TLS-encoded [RetainedEpochSecrets], empty if none
     */
    suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray>
}
