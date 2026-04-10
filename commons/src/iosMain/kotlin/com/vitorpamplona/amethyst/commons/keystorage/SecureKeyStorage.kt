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
package com.vitorpamplona.amethyst.commons.keystorage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.*
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.*

/**
 * iOS implementation of SecureKeyStorage using the iOS Keychain.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureKeyStorage private actual constructor() {
    actual companion object {
        private const val SERVICE_NAME = "com.vitorpamplona.amethyst.ios"

        actual fun create(context: Any?): SecureKeyStorage = SecureKeyStorage()
    }

    actual suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    ) {
        // Delete existing key first
        deletePrivateKey(npub)

        val data = (privKeyHex as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw SecureStorageException("Failed to encode private key")

        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to npub,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        )

        @Suppress("UNCHECKED_CAST")
        val status = SecItemAdd(query as CFDictionaryRef, null)
        if (status != errSecSuccess) {
            throw SecureStorageException("Failed to save private key to Keychain: $status")
        }
    }

    actual suspend fun getPrivateKey(npub: String): String? {
        memScoped {
            val result = alloc<CFTypeRefVar>()

            val query = mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to SERVICE_NAME,
                kSecAttrAccount to npub,
                kSecReturnData to true,
                kSecMatchLimit to kSecMatchLimitOne,
            )

            @Suppress("UNCHECKED_CAST")
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)

            if (status == errSecItemNotFound) return null
            if (status != errSecSuccess) {
                throw SecureStorageException("Failed to retrieve private key from Keychain: $status")
            }

            val data = CFBridgingRelease(result.value) as? NSData ?: return null
            return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
        }
    }

    actual suspend fun deletePrivateKey(npub: String): Boolean {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to npub,
        )

        @Suppress("UNCHECKED_CAST")
        val status = SecItemDelete(query as CFDictionaryRef)
        return status == errSecSuccess
    }

    actual suspend fun hasPrivateKey(npub: String): Boolean = getPrivateKey(npub) != null
}
