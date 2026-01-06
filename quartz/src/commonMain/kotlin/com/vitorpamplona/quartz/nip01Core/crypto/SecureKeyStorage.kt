/**
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
package com.vitorpamplona.quartz.nip01Core.crypto

/**
 * Secure storage for private keys using platform-specific secure storage mechanisms.
 *
 * Android: Android Keystore + EncryptedDataStore (AES-256-GCM, StrongBox when available)
 * Desktop: OS native credential managers (macOS Keychain, Windows Credential Manager, Linux Secret Service)
 */
expect class SecureKeyStorage {
    /**
     * Saves a private key securely for the given npub.
     *
     * @param npub The public key in npub (Bech32) format
     * @param privKeyHex The private key in hexadecimal format
     * @throws SecureStorageException if storage operation fails
     */
    suspend fun savePrivateKey(npub: String, privKeyHex: String)

    /**
     * Retrieves a private key for the given npub.
     *
     * @param npub The public key in npub (Bech32) format
     * @return The private key in hexadecimal format, or null if not found
     * @throws SecureStorageException if retrieval operation fails
     */
    suspend fun getPrivateKey(npub: String): String?

    /**
     * Deletes a private key for the given npub.
     *
     * @param npub The public key in npub (Bech32) format
     * @return true if the key was deleted, false if it didn't exist
     * @throws SecureStorageException if deletion operation fails
     */
    suspend fun deletePrivateKey(npub: String): Boolean

    /**
     * Checks if a private key exists for the given npub.
     *
     * @param npub The public key in npub (Bech32) format
     * @return true if a private key exists, false otherwise
     */
    suspend fun hasPrivateKey(npub: String): Boolean
}

/**
 * Exception thrown when secure storage operations fail.
 */
class SecureStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
