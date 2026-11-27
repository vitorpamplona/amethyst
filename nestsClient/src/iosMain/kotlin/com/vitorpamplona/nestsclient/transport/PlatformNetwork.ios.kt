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
package com.vitorpamplona.nestsclient.transport

import com.vitorpamplona.quic.tls.AppleCertificateValidator
import com.vitorpamplona.quic.tls.CertificateValidator
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.SOCK_DGRAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.memset
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6

internal actual fun platformCertificateValidator(): CertificateValidator = AppleCertificateValidator()

/** getaddrinfo, the first IPv4 answer, else the first IPv6 one, as a literal. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun resolvePreferIpv4(host: String): String =
    memScoped {
        val hints = alloc<addrinfo>()
        memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_DGRAM
        val result = allocPointerTo<addrinfo>()
        if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) return@memScoped host
        try {
            var v4: String? = null
            var v6: String? = null
            var cursor = result.value
            while (cursor != null && v4 == null) {
                val info = cursor.pointed
                when (info.ai_family) {
                    AF_INET -> {
                        val address = info.ai_addr!!.reinterpret<sockaddr_in>().pointed
                        val bytes =
                            address.sin_addr.ptr
                                .reinterpret<ByteVar>()
                                .readBytes(4)
                        v4 = bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
                    }
                    AF_INET6 ->
                        if (v6 == null) {
                            val address = info.ai_addr!!.reinterpret<sockaddr_in6>().pointed
                            val bytes =
                                address.sin6_addr.ptr
                                    .reinterpret<ByteVar>()
                                    .readBytes(16)
                            // Uncompressed hex groups: a valid literal, without inet_ntop (not bound on iOS).
                            v6 = (0 until 8).joinToString(":") { i -> (((bytes[2 * i].toInt() and 0xFF) shl 8) or (bytes[2 * i + 1].toInt() and 0xFF)).toString(16) }
                        }
                }
                cursor = info.ai_next
            }
            v4 ?: v6 ?: host
        } finally {
            freeaddrinfo(result.value)
        }
    }
