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
package com.vitorpamplona.amethyst.commons.service.http

/*
 * What socket failures are called on the JVM, for the transports that have no java.net.
 *
 * The relay client tells a dead domain from a busy relay by a failure's type name and message
 * (BasicRelayClient parks an UnknownHostException or a "Host unreachable" for minutes), and the
 * boot diagnostics count failures by the same names. A native engine's error mapped to one of
 * these reads exactly as OkHttp's does on Android and the desktop.
 */

/** `java.net.SocketException`: a Tor refusal carries Java's SOCKS message ("SOCKS: Host unreachable"). */
class SocketException(
    message: String,
) : Exception(message)

/** `java.net.UnknownHostException`: the name did not resolve. */
class UnknownHostException(
    message: String,
) : Exception(message)

/** `java.net.SocketTimeoutException`. */
class SocketTimeoutException(
    message: String,
) : Exception(message)

/** `java.net.ConnectException`: nothing accepted the connection. */
class ConnectException(
    message: String,
) : Exception(message)

/** `java.io.EOFException`: the server hung up. Its message is null on OkHttp. */
class EOFException : Exception()

/** `javax.net.ssl.SSLHandshakeException`: a bad certificate, SNI or TLS alert. */
class SSLHandshakeException(
    message: String,
) : Exception(message)
