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
package com.vitorpamplona.amethyst.commons.napplet.socks

import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletPublicAddress

/** The two sides of one SOCKS connection, as bytes: whatever socket library carries them. */
interface NappletSocksIo {
    /** Exactly [count] bytes, or an exception when the peer hangs up first. */
    suspend fun readFully(count: Int): ByteArray

    /** Sends [bytes] at once (flushed). */
    suspend fun write(bytes: ByteArray)
}

/** Where a SOCKS client asked to go: a host name or an IP literal (IPv6 without brackets), and a port. */
data class NappletSocksTarget(
    val host: String,
    val port: Int,
)

/**
 * The SOCKS5 wire format (RFC 1928) a napplet router speaks, both ways: as the server WebKit's (or
 * Chromium's) proxy talks to, accepting "no authentication" and CONNECT only, and as a client of Tor's
 * SOCKS port, handing it the name unresolved. Pure bytes, so every platform's router shares it.
 */
object NappletSocks5 {
    const val VERSION = 5
    const val NO_AUTH = 0
    const val NO_ACCEPTABLE = 0xFF
    const val CMD_CONNECT = 1
    const val ATYP_IPV4 = 1
    const val ATYP_DOMAIN = 3
    const val ATYP_IPV6 = 4

    const val REPLY_OK = 0
    const val REPLY_GENERAL_FAILURE = 1
    const val REPLY_NOT_ALLOWED = 2
    const val REPLY_HOST_UNREACHABLE = 4
    const val REPLY_COMMAND_UNSUPPORTED = 7
    const val REPLY_ADDRESS_UNSUPPORTED = 8

    /**
     * The server side of a handshake: the greeting (only "no authentication" is accepted) and the
     * request. Returns where the client wants to go, or null after answering a request this does not
     * serve (another version, another command, an unknown address type); the caller then closes.
     */
    suspend fun accept(io: NappletSocksIo): NappletSocksTarget? {
        val greeting = io.readFully(2)
        if (greeting[0].toInt() != VERSION) return null
        val methods = io.readFully(greeting[1].toInt() and 0xFF)
        if (methods.none { it.toInt() == NO_AUTH }) {
            io.write(byteArrayOf(VERSION.toByte(), NO_ACCEPTABLE.toByte()))
            return null
        }
        io.write(byteArrayOf(VERSION.toByte(), NO_AUTH.toByte()))

        val head = io.readFully(4)
        if (head[0].toInt() != VERSION) return null
        val host =
            when (head[3].toInt()) {
                ATYP_IPV4 -> NappletIpLiterals.format(io.readFully(4))
                ATYP_IPV6 -> NappletIpLiterals.format(io.readFully(16))
                ATYP_DOMAIN -> io.readFully(io.readFully(1)[0].toInt() and 0xFF).decodeToString()
                else -> {
                    io.write(reply(REPLY_ADDRESS_UNSUPPORTED))
                    return null
                }
            }
        val portBytes = io.readFully(2)
        if (head[1].toInt() != CMD_CONNECT) {
            io.write(reply(REPLY_COMMAND_UNSUPPORTED))
            return null
        }
        return NappletSocksTarget(host, ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF))
    }

    /** A reply to a request: [code], bound to 0.0.0.0:0 (clients don't use it). */
    fun reply(code: Int): ByteArray = byteArrayOf(VERSION.toByte(), code.toByte(), 0, ATYP_IPV4.toByte(), 0, 0, 0, 0, 0, 0)

    /**
     * The client side, to an upstream SOCKS5 server (Tor's): asks it to CONNECT to [target], a name
     * passed as a name so the upstream resolves it. Returns the upstream's reply code ([REPLY_OK] when
     * the tunnel is up); an upstream that refuses "no authentication" answers [REPLY_GENERAL_FAILURE].
     */
    suspend fun connect(
        io: NappletSocksIo,
        target: NappletSocksTarget,
    ): Int {
        io.write(byteArrayOf(VERSION.toByte(), 1, NO_AUTH.toByte()))
        val chosen = io.readFully(2)
        if (chosen[0].toInt() != VERSION || chosen[1].toInt() != NO_AUTH) return REPLY_GENERAL_FAILURE
        io.write(request(target))
        val head = io.readFully(4)
        // The bound address follows; read it whatever its form, so the tunnel starts clean.
        when (head[3].toInt()) {
            ATYP_IPV4 -> io.readFully(4 + 2)
            ATYP_IPV6 -> io.readFully(16 + 2)
            ATYP_DOMAIN -> io.readFully((io.readFully(1)[0].toInt() and 0xFF) + 2)
            else -> return REPLY_GENERAL_FAILURE
        }
        if (head[0].toInt() != VERSION) return REPLY_GENERAL_FAILURE
        return head[1].toInt() and 0xFF
    }

    /** A CONNECT request for [target]: an IP literal as an address, anything else as a name. */
    fun request(target: NappletSocksTarget): ByteArray {
        val literal = NappletIpLiterals.parse(target.host)
        val address =
            when (literal?.size) {
                4 -> byteArrayOf(ATYP_IPV4.toByte()) + literal
                16 -> byteArrayOf(ATYP_IPV6.toByte()) + literal
                else -> {
                    val name = target.host.encodeToByteArray()
                    require(name.size in 1..255) { "A SOCKS name is 1 to 255 bytes" }
                    byteArrayOf(ATYP_DOMAIN.toByte(), name.size.toByte()) + name
                }
            }
        return byteArrayOf(VERSION.toByte(), CMD_CONNECT.toByte(), 0) + address + byteArrayOf((target.port shr 8).toByte(), target.port.toByte())
    }
}

/**
 * What a napplet router lets through, the same on every platform: the app's own `napplet.local` names
 * never leave (they would tell the network, or a Tor exit, which apps are open), and neither this
 * device nor its local network is reachable, by name or by address, on any route. Mirrors Desktop's
 * `DesktopSocksRouter`.
 */
object NappletSocksPolicy {
    /** Names that only ever mean this device or the local network: refused without resolving anything. */
    private val LOCAL_SUFFIXES = listOf("localhost", "local", "lan", "internal", "home.arpa")

    /** [host] as the router compares it: lowercase, no trailing dot, an IPv6 literal without brackets. */
    fun normalize(host: String): String =
        host
            .lowercase()
            .trimEnd('.')
            .removePrefix("[")
            .removeSuffix("]")

    /**
     * Whether [host] is refused before anything is resolved or connected: an app's internal name, a
     * name that only means the local network, or an IP literal that isn't public. A host that looks
     * like a literal but doesn't parse as one is refused too. [allowLocal] is for tests that stand a
     * local server in for the web.
     */
    fun refusesName(
        host: String,
        allowLocal: Boolean = false,
    ): Boolean {
        val name = normalize(host)
        if (name.isEmpty() || NappletWebContract.isInternalHost(name)) return true
        if (allowLocal) return false
        if (LOCAL_SUFFIXES.any { name == it || name.endsWith(".$it") }) return true
        val looksLiteral = name.all { it.isDigit() || it == '.' } || ':' in name
        if (!looksLiteral) return false
        val literal = NappletIpLiterals.parse(name) ?: return true
        return !NappletPublicAddress.isPublic(literal)
    }

    /** The resolved [addresses] a direct connection may use: public ones only (all, with [allowLocal]). */
    fun connectable(
        addresses: List<ByteArray>,
        allowLocal: Boolean = false,
    ): List<ByteArray> = if (allowLocal) addresses.filter { it.size == 4 || it.size == 16 } else addresses.filter(NappletPublicAddress::isPublic)
}

/** IP literals as bytes and back, without a platform's address classes. */
object NappletIpLiterals {
    /** `a.b.c.d` for 4 bytes, RFC 5952 text for 16 (lowercase, the longest zero run as `::`). */
    fun format(address: ByteArray): String =
        when (address.size) {
            4 -> address.joinToString(".") { (it.toInt() and 0xFF).toString() }
            16 -> {
                val groups = IntArray(8) { ((address[it * 2].toInt() and 0xFF) shl 8) or (address[it * 2 + 1].toInt() and 0xFF) }
                var bestStart = -1
                var bestLength = 1
                var i = 0
                while (i < 8) {
                    if (groups[i] == 0) {
                        var j = i
                        while (j < 8 && groups[j] == 0) j++
                        if (j - i > bestLength) {
                            bestStart = i
                            bestLength = j - i
                        }
                        i = j
                    } else {
                        i++
                    }
                }
                if (bestStart < 0) {
                    groups.joinToString(":") { it.toString(16) }
                } else {
                    groups.take(bestStart).joinToString(":") { it.toString(16) } + "::" +
                        groups.drop(bestStart + bestLength).joinToString(":") { it.toString(16) }
                }
            }
            else -> throw IllegalArgumentException("An IP address is 4 or 16 bytes")
        }

    /** The bytes of a canonical dotted IPv4 or an IPv6 literal (brackets allowed), or null when [text] is neither. */
    fun parse(text: String): ByteArray? {
        val host = text.removePrefix("[").removeSuffix("]")
        return if (':' in host) parseV6(host) else parseV4(host)
    }

    private fun parseV4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        for ((index, part) in parts.withIndex()) {
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            val value = part.toInt()
            if (value > 255) return null
            bytes[index] = value.toByte()
        }
        return bytes
    }

    private fun parseV6(host: String): ByteArray? {
        // A zone id (`fe80::1%en0`) is local by definition; it is not a literal the web can name.
        if ('%' in host) return null
        var text = host
        var tail = ByteArray(0)
        // An embedded IPv4 tail (`::ffff:1.2.3.4`).
        val lastColon = text.lastIndexOf(':')
        if ('.' in text.substring(lastColon + 1)) {
            tail = parseV4(text.substring(lastColon + 1)) ?: return null
            text = text.substring(0, lastColon + 1) + "0:0"
        }
        val halves = text.split("::")
        if (halves.size > 2) return null

        fun groups(part: String): List<Int>? =
            if (part.isEmpty()) {
                emptyList()
            } else {
                part.split(':').map { group ->
                    if (group.isEmpty() || group.length > 4 || !group.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
                    group.toInt(16)
                }
            }
        val head = groups(halves[0]) ?: return null
        val rest = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
        val all =
            if (halves.size == 2) {
                if (head.size + rest.size > 7) return null
                head + List(8 - head.size - rest.size) { 0 } + rest
            } else {
                if (head.size != 8) return null
                head
            }
        val bytes = ByteArray(16)
        all.forEachIndexed { i, group ->
            bytes[i * 2] = (group shr 8).toByte()
            bytes[i * 2 + 1] = group.toByte()
        }
        if (tail.isNotEmpty()) tail.copyInto(bytes, 12)
        return bytes
    }
}
