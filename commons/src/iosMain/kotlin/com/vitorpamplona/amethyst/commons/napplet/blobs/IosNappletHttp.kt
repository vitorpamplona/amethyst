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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.vitorpamplona.amethyst.commons.service.http.DualKtorClients
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6

/**
 * The iOS [NappletHttp]: the app's Darwin clients, Tor or direct as each request asks. [clients]'s
 * Tor route fails every request while Tor has no port (unless the user bypassed Tor for the session,
 * which counts as direct, as for relays), so a fetch that must go through Tor never leaves direct.
 */
fun iosNappletHttp(clients: DualKtorClients): NappletHttp = KtorNappletHttp(clients::get, ::resolvesOnlyToPublicAddresses)

/**
 * Whether [host] resolves, through the system resolver, to at least one address and to public
 * addresses only ([NappletPublicAddress]). NSURLSession has no resolver hook, so this is asked before
 * the request and the session resolves again: see [KtorNappletHttp] for what that leaves open.
 */
suspend fun resolvesOnlyToPublicAddresses(host: String): Boolean = withContext(Dispatchers.IO) { resolvedAddresses(host)?.let { it.isNotEmpty() && it.all(NappletPublicAddress::isPublic) } ?: false }

/** Every address [host] resolves to (4 or 16 bytes each), or null when it doesn't resolve. */
@OptIn(ExperimentalForeignApi::class)
fun resolvedAddresses(host: String): List<ByteArray>? =
    memScoped {
        val hints =
            alloc<addrinfo>().apply {
                ai_family = AF_UNSPEC
                ai_socktype = SOCK_STREAM
            }
        val result = allocPointerTo<addrinfo>()
        if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) return@memScoped null
        try {
            val addresses = ArrayList<ByteArray>()
            var next = result.value
            while (next != null) {
                val info = next.pointed
                val socketAddress = info.ai_addr
                when {
                    socketAddress == null -> {}
                    info.ai_family == AF_INET ->
                        addresses +=
                            socketAddress
                                .reinterpret<sockaddr_in>()
                                .pointed.sin_addr.ptr
                                .bytes(4)
                    info.ai_family == AF_INET6 ->
                        addresses +=
                            socketAddress
                                .reinterpret<sockaddr_in6>()
                                .pointed.sin6_addr.ptr
                                .bytes(16)
                    // A family that isn't IP can't be checked, so it isn't public.
                    else -> return@memScoped listOf(ByteArray(0))
                }
                next = info.ai_next
            }
            addresses
        } finally {
            freeaddrinfo(result.value)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<*>.bytes(count: Int): ByteArray = reinterpret<ByteVar>().readBytes(count)
