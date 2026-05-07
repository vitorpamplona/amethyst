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
package com.vitorpamplona.quartz.relay.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RelayConfigTest {
    @Test
    fun emptyTomlYieldsAllDefaults() {
        val c = RelayConfig.fromToml("")
        assertEquals("0.0.0.0", c.network.host)
        assertEquals(7447, c.network.port)
        assertEquals("/", c.network.path)
        assertEquals(true, c.database.in_memory)
        assertEquals(false, c.options.require_auth)
        assertEquals(false, c.options.verify_signatures)
        assertTrue(c.authorization.pubkey_whitelist.isEmpty())
    }

    @Test
    fun parsesAllSectionsTogether() {
        val toml =
            """
            [info]
            relay_url = "wss://relay.example.com/"
            name = "Example"
            contact = "ops@example.com"
            supported_nips = [1, 9, 11, 42]

            [network]
            host = "127.0.0.1"
            port = 9988
            path = "/relay"
            remote_ip_header = "X-Forwarded-For"

            [database]
            in_memory = false
            file = "/var/lib/quartz-relay/events.db"

            [options]
            verify_signatures = true
            require_auth = true
            reject_future_seconds = 1800

            [limits]
            max_event_bytes = 131072
            messages_per_sec = 10
            max_filters_per_req = 12

            [authorization]
            pubkey_blacklist = ["aaaa", "bbbb"]
            kind_blacklist = [4, 1059]
            """.trimIndent()

        val c = RelayConfig.fromToml(toml)

        assertEquals("wss://relay.example.com/", c.info.relay_url)
        assertEquals("Example", c.info.name)
        assertEquals(listOf(1, 9, 11, 42), c.info.supported_nips)

        assertEquals("127.0.0.1", c.network.host)
        assertEquals(9988, c.network.port)
        assertEquals("/relay", c.network.path)
        assertEquals("X-Forwarded-For", c.network.remote_ip_header)

        assertEquals(false, c.database.in_memory)
        assertEquals("/var/lib/quartz-relay/events.db", c.database.file)

        assertEquals(true, c.options.verify_signatures)
        assertEquals(true, c.options.require_auth)
        assertEquals(1800, c.options.reject_future_seconds)

        assertEquals(131072, c.limits.max_event_bytes)
        assertEquals(10, c.limits.messages_per_sec)
        assertEquals(12, c.limits.max_filters_per_req)

        assertEquals(listOf("aaaa", "bbbb"), c.authorization.pubkey_blacklist)
        assertEquals(listOf(4, 1059), c.authorization.kind_blacklist)
    }

    @Test
    fun supportedNipsRenderedAsStringsInNip11Doc() {
        val c =
            RelayConfig.fromToml(
                """
                [info]
                supported_nips = [1, 11, 42]
                """.trimIndent(),
            )
        val info =
            c.resolveInfo(
                "ws://127.0.0.1:7447/".let {
                    com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                        .normalize(it)
                },
            )
        assertEquals(listOf("1", "11", "42"), info.document.supported_nips)
    }

    @Test
    fun loadsTheBundledExampleConfigCleanly() {
        // The example file lives at the module root so operators have a
        // canonical reference. Read it via a relative path resolved
        // against the working directory (gradle runs tests from the
        // module dir).
        val candidates =
            listOf(
                File("config.example.toml"),
                File("quartz-relay/config.example.toml"),
            )
        val example =
            candidates.firstOrNull { it.exists() }
                ?: error(
                    "config.example.toml not found in any of: ${candidates.joinToString { it.absolutePath }}",
                )

        val c = RelayConfig.fromFile(example)

        assertEquals("wss://relay.example.com/", c.info.relay_url)
        assertEquals(true, c.options.verify_signatures)
        assertEquals(false, c.database.in_memory)
        assertNotNull(c.database.file)
    }

    @Test
    fun missingSectionsAreOptional() {
        val c = RelayConfig.fromToml("[info]\nname = \"only-info\"")
        assertEquals("only-info", c.info.name)
        // Defaults preserved for unspecified sections.
        assertEquals(7447, c.network.port)
        assertEquals(true, c.database.in_memory)
    }
}
