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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE_NAME = "com.vitorpamplona.amethyst.keys"

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class SecureKeyStorage private actual constructor() {
    actual companion object {
        actual fun create(context: Any?): SecureKeyStorage = SecureKeyStorage()
    }

    actual suspend fun savePrivateKey(
        npub: String,
        privKeyHex: String,
    ) {
        // Delete existing key first
        deletePrivateKey(npub)

        val data =
            (privKeyHex as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                ?: throw SecureStorageException("Failed to encode key data")

        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
            CFDictionarySetValue(query, CFBridgingRetain(kSecClassGenericPassword), CFBridgingRetain(kSecClassGenericPassword))
            CFDictionarySetValue(query, CFBridgingRetain(kSecAttrService), CFBridgingRetain(SERVICE_NAME as NSString))
            CFDictionarySetValue(query, CFBridgingRetain(kSecAttrAccount), CFBridgingRetain(npub as NSString))
            CFDictionarySetValue(query, CFBridgingRetain(kSecValueData), CFBridgingRetain(data))

            val status = SecItemAdd(query as CFDictionaryRef, null)
            if (status != errSecSuccess) {
                throw SecureStorageException("Failed to save key: OSStatus $status")
            }
        }
    }

    actual suspend fun getPrivateKey(npub: String): String? =
        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
            CFDictionarySetValue(query, CFBridgingRetain(kSecClassGenericPassword), CFBridgingRetain(kSecClassGenericPassword))
            CFDictionarySetValue(query, CFBridgingRetain(kSecAttrService), CFBridgingRetain(SERVICE_NAME as NSString))
            CFDictionarySetValue(query, CFBridgingRetain(kSecAttrAccount), CFBridgingRetain(npub as NSString))
            CFDictionarySetValue(query, CFBridgingRetain(kSecReturnData), CFBridgingRetain(true as Any))
            CFDictionarySetValue(query, CFBridgingRetain(kSecMatchLimit), CFBridgingRetain(kSecMatchLimitOne))

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)

            if (status == errSecSuccess) {
                val data = CFBridgingRelease(result.value) as? NSData
                data?.let {
                    NSString.create(data = it, encoding = NSUTF8StringEncoding) as? String
                }
            } else {
                null
            }
        }

    actual suspend fun deletePrivateKey(npub: String): Boolean =
        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null)
            CFDictionarySetValue(query, CFBridgingRetain(kSecClassGenericPassword), CFBridgingRetain(kSecClassGenericPassword))
            CFDictionarySetValue(query, CFBridgingRetain(kSecAttrService), CFBridgingRetain(SERVICE_NAME as NSString))
            CFDictionarySetValue(query, CFBridgingRetain(kSecAttrAccount), CFBridgingRetain(npub as NSString))

            val status = SecItemDelete(query as CFDictionaryRef)
            status == errSecSuccess
        }

    actual suspend fun hasPrivateKey(npub: String): Boolean = getPrivateKey(npub) != null
}
