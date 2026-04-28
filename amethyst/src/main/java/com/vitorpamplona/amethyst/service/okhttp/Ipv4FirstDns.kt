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
package com.vitorpamplona.amethyst.service.okhttp

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * System-DNS wrapper that puts IPv4 addresses ahead of IPv6 in the
 * resolved list. OkHttp's [okhttp3.internal.connection.RouteSelector]
 * iterates routes in order, so v4 is tried first; v6 is still kept
 * as a fallback for genuinely v6-only hosts.
 *
 * Mitigates two real-world cases that surface as a "Reconnecting"
 * loop on the Nests auth POST (and on plain media loads):
 *   - Android emulator: the emulator's userspace networking advertises
 *     IPv6 connectivity but its NAT consistently drops outbound v6
 *     packets, so any host with an AAAA record times out / fast-fails.
 *   - Hosts whose AAAA record points at a stale or otherwise
 *     unreachable address while v4 still works (e.g. Linode VMs that
 *     lost v6 routing). System resolver may return AAAA-only on some
 *     networks; we can't recover that case here, but we can stop
 *     v6-first from breaking the dual-stack case.
 */
class Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        if (all.size <= 1) return all
        val v4 = all.filterIsInstance<Inet4Address>()
        if (v4.isEmpty() || v4.size == all.size) return all
        return v4 + all.filterNot { it is Inet4Address }
    }
}
