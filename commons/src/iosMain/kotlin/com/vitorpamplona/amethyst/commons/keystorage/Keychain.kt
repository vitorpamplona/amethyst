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
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Generic-password items in the iOS Keychain, one per ([service], account) pair.
 *
 * Items are readable after the first unlock and never leave this device: not synced to iCloud,
 * not restored onto another phone (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class Keychain(
    private val service: String,
) {
    /** The item's bytes, or null when there is none. Throws when the Keychain refuses the read. */
    fun read(account: String): ByteArray? =
        memScoped {
            val query =
                dictionary(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to retained(service),
                    kSecAttrAccount to retained(account),
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                )
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
                errSecItemNotFound -> null
                else -> throw SecureStorageException("Keychain read failed: OSStatus $status")
            }
        }

    /** Stores [bytes], replacing what the item held. */
    fun write(
        account: String,
        bytes: ByteArray,
    ) {
        delete(account)
        memScoped {
            val item =
                dictionary(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to retained(service),
                    kSecAttrAccount to retained(account),
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    kSecValueData to retained(bytes.toNSData()),
                )
            val status = SecItemAdd(item, null)
            if (status != errSecSuccess) throw SecureStorageException("Keychain write failed: OSStatus $status")
        }
    }

    /** Removes the item; returns whether there was one. */
    fun delete(account: String): Boolean =
        memScoped {
            val query =
                dictionary(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to retained(service),
                    kSecAttrAccount to retained(account),
                )
            when (val status = SecItemDelete(query)) {
                errSecSuccess -> true
                errSecItemNotFound -> false
                else -> throw SecureStorageException("Keychain delete failed: OSStatus $status")
            }
        }

    /** A retained CF reference to [value], released when the scope ends. */
    private fun MemScope.retained(value: Any): CFTypeRef? = CFBridgingRetain(value).also { ref -> defer { CFRelease(ref) } }

    private fun MemScope.dictionary(vararg items: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dict = CFDictionaryCreateMutable(null, items.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        items.forEach { (key, value) -> CFDictionaryAddValue(dict, key, value) }
        defer { CFRelease(dict) }
        return dict
    }

    private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

    private fun NSData.toByteArray(): ByteArray {
        val out = ByteArray(length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
        return out
    }
}
