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

import com.vitorpamplona.quic.tls.CertificateValidator
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

internal actual fun platformCertificateValidator(): CertificateValidator = JdkCertificateValidator()

internal actual fun resolvePreferIpv4(host: String): String =
    try {
        val addresses = InetAddress.getAllByName(host)
        (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())?.hostAddress ?: host
    } catch (_: UnknownHostException) {
        host
    }
