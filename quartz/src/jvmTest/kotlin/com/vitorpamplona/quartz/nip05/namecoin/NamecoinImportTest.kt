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
package com.vitorpamplona.quartz.nip05.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.BitRelayResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinImportResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hermetic tests for [NamecoinImportResolver] and the import-aware
 * resolution paths in [BitRelayResolver] / [NamecoinNameResolver].
 *
 * These tests do NOT touch the network: every "imported" name is served
 * by an in-memory map keyed by Namecoin name (the same fake the existing
 * [BitRelayResolverTest] uses).
 */
class NamecoinImportTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    // ── Pure unit tests (NamecoinImportResolver only) ─────────────────────

    @Test
    fun `no import key returns object unchanged`() =
        runTest {
            val obj = parse("""{"ip":"1.2.3.4"}""")
            val expanded = NamecoinImportResolver.expandImports(obj) { error("should not be called") }
            assertEquals(obj, expanded)
        }

    @Test
    fun `string shorthand import merges imported items into importer`() =
        runTest {
            // ifa-0001 §"import" canonical form is array-of-arrays, but the
            // string form `"import": "d/foo"` is widely used in practice; we
            // accept it as shorthand for `[["d/foo"]]`.
            val obj = parse("""{"import":"d/lib","ip":"1.1.1.1"}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/lib" -> """{"ip":"9.9.9.9","nostr":{"names":{"_":"abc"}}}"""
                        else -> null
                    }
                }
            // importer wins on `ip`, imports fill in `nostr.names`.
            assertEquals("1.1.1.1", expanded["ip"]?.jsonPrimitive?.content)
            assertEquals(
                "abc",
                expanded["nostr"]
                    ?.jsonObject
                    ?.get("names")
                    ?.jsonObject
                    ?.get("_")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertFalse("import key must not survive expansion", expanded.containsKey("import"))
        }

    @Test
    fun `canonical array of arrays form processes each in order`() =
        runTest {
            val obj = parse("""{"import":[["d/a"],["d/b"]]}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/a" -> """{"ip":"10.0.0.1","tag":"from-a"}"""
                        "d/b" -> """{"ip":"10.0.0.2","extra":"from-b"}"""
                        else -> null
                    }
                }
            // d/b is processed AFTER d/a, so its `ip` (10.0.0.2) overrides d/a's.
            // The importer itself has no `ip`, so the last imported one wins.
            assertEquals("10.0.0.2", expanded["ip"]?.jsonPrimitive?.content)
            assertEquals("from-a", expanded["tag"]?.jsonPrimitive?.content)
            assertEquals("from-b", expanded["extra"]?.jsonPrimitive?.content)
        }

    @Test
    fun `pair-array shorthand uses subdomain selector`() =
        runTest {
            val obj = parse("""{"import":["d/lib","relay"]}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/lib" -> {
                            """
                            {"ip":"1.1.1.1",
                             "map":{"relay":{"ip":"7.7.7.7","tag":"selected"}}}
                            """.trimIndent()
                        }

                        else -> {
                            null
                        }
                    }
                }
            // We selected `map.relay` from d/lib, so its contents (ip=7.7.7.7,
            // tag=selected) are merged at the top level of the importer.
            // d/lib's top-level ip (1.1.1.1) is NOT seen because we descended.
            assertEquals("7.7.7.7", expanded["ip"]?.jsonPrimitive?.content)
            assertEquals("selected", expanded["tag"]?.jsonPrimitive?.content)
        }

    @Test
    fun `importer items take precedence over imported items`() =
        runTest {
            val obj = parse("""{"import":"d/lib","ip":"1.1.1.1","extra":"local"}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/lib" -> """{"ip":"9.9.9.9","extra":"remote","only-imported":"yes"}"""
                        else -> null
                    }
                }
            assertEquals("1.1.1.1", expanded["ip"]?.jsonPrimitive?.content)
            assertEquals("local", expanded["extra"]?.jsonPrimitive?.content)
            assertEquals("yes", expanded["only-imported"]?.jsonPrimitive?.content)
        }

    @Test
    fun `null in importer suppresses imported value`() =
        runTest {
            // ifa-0001: null is "present for precedence" — semantic
            // suppression. The importer says ip=null, so imported ip is gone.
            val obj = parse("""{"import":"d/lib","ip":null}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/lib" -> """{"ip":"9.9.9.9","other":"keep"}"""
                        else -> null
                    }
                }
            // The merged object still has `ip` as a JsonNull, not removed.
            // Downstream parsers ignore null as if absent (same outcome).
            assertTrue(expanded.containsKey("ip"))
            assertEquals(
                "kotlinx.serialization JsonNull is not equal to a missing key",
                kotlinx.serialization.json.JsonNull,
                expanded["ip"],
            )
            assertEquals("keep", expanded["other"]?.jsonPrimitive?.content)
        }

    @Test
    fun `recursion depth four is supported`() =
        runTest {
            // ifa-0001 mandates implementations support a recursion degree
            // of at least 4. "Degree" counts the number of `import` items
            // PROCESSED, so a chain importer→a→b→c→d means 4 imports
            // (importer's, a's, b's, c's). d's items propagate; if d itself
            // had its own import, that would be the 5th level and may be
            // skipped per spec. Test pins the 4-deep happy path.
            val obj = parse("""{"import":"d/a"}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/a" -> """{"import":"d/b","layer":"a"}"""
                        "d/b" -> """{"import":"d/c","layer":"b"}"""
                        "d/c" -> """{"import":"d/d","layer":"c"}"""
                        "d/d" -> """{"layer":"d","deep":"reached"}"""
                        else -> null
                    }
                }
            // Each layer overrides "layer" so the importer sees "a".
            // "deep" only exists on d/d and survives to the top.
            assertEquals("a", expanded["layer"]?.jsonPrimitive?.content)
            assertEquals("reached", expanded["deep"]?.jsonPrimitive?.content)
        }

    @Test
    fun `recursion deeper than max-depth is silently truncated`() =
        runTest {
            // Anything past the depth limit is dropped, but the importing
            // record's own items still apply. We use a small budget so the
            // test runs without 5+ named maps.
            val obj = parse("""{"import":"d/a","local":"keep"}""")
            val expanded =
                NamecoinImportResolver.expandImports(
                    root = obj,
                    maxDepth = 1, // only one level of imports
                ) { name ->
                    when (name) {
                        "d/a" -> """{"import":"d/b","tag":"from-a"}"""
                        "d/b" -> """{"tag":"from-b","leaf":"won't-show"}"""
                        else -> null
                    }
                }
            assertEquals("from-a", expanded["tag"]?.jsonPrimitive?.content)
            assertEquals("keep", expanded["local"]?.jsonPrimitive?.content)
            // d/b was never expanded so its keys are NOT present.
            assertNull(expanded["leaf"])
        }

    @Test
    fun `failed import lookup is treated as empty object`() =
        runTest {
            // Per our docs: spec says a failed import MAY fail the whole
            // record; we choose the more lenient "empty object" semantics
            // so transient ElectrumX hiccups don't kill resolution.
            val obj = parse("""{"import":"d/missing","local":"survives"}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { _ ->
                    null // simulate name not found
                }
            assertEquals("survives", expanded["local"]?.jsonPrimitive?.content)
            assertFalse(expanded.containsKey("import"))
        }

    @Test
    fun `cycle in imports is broken without infinite recursion`() =
        runTest {
            // d/a imports d/b which imports d/a. A naive resolver would
            // hang. The visited-set guard breaks the loop on the second
            // appearance of d/a; importer's own items still apply.
            val obj = parse("""{"import":"d/a","local":"top"}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/a" -> """{"import":"d/b","fromA":"yes"}"""
                        "d/b" -> """{"import":"d/a","fromB":"yes"}"""
                        else -> null
                    }
                }
            assertEquals("top", expanded["local"]?.jsonPrimitive?.content)
            // At least one of fromA/fromB should have made it through —
            // we don't pin which because the cycle break point is an
            // implementation detail, but the call MUST terminate.
            assertTrue(
                expanded.containsKey("fromA") || expanded.containsKey("fromB"),
            )
        }

    @Test
    fun `malformed import value is skipped without throwing`() =
        runTest {
            val obj = parse("""{"import":42,"local":"keep"}""")
            val expanded = NamecoinImportResolver.expandImports(obj) { _ -> null }
            assertEquals("keep", expanded["local"]?.jsonPrimitive?.content)
            assertFalse(expanded.containsKey("import"))
        }

    @Test
    fun `import target with malformed JSON is skipped`() =
        runTest {
            val obj = parse("""{"import":"d/broken","local":"keep"}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/broken" -> """not valid json {{{"""
                        else -> null
                    }
                }
            assertEquals("keep", expanded["local"]?.jsonPrimitive?.content)
        }

    @Test
    fun `selector with multiple labels descends in DNS order`() =
        runTest {
            // selector "a.b" means: descend map.b, then map.a (DNS-rightmost
            // first). Reuse the same walker used elsewhere so the empty-key
            // and "*" wildcard rules apply identically.
            val obj = parse("""{"import":[["d/lib","a.b"]]}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/lib" -> {
                            """
                            {"map":{"b":{"map":{"a":{"value":"deep"}}}}}
                            """.trimIndent()
                        }

                        else -> {
                            null
                        }
                    }
                }
            assertEquals("deep", expanded["value"]?.jsonPrimitive?.content)
        }

    // ── Integration: BitRelayResolver follows import to find relay/tls/onion ──

    @Test
    fun `BitRelayResolver follows import on the parent record`() =
        runTest {
            // d/testls is space-constrained, so it imports map.relay defaults
            // from a sibling name dd/testls-relay. The relay handshake must
            // still resolve correctly.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"import":"dd/testls-relay","ip":"107.152.38.155"}""",
                    )
                    register(
                        "dd/testls-relay",
                        """
                        {"map":{"relay":{
                            "ip":"23.158.233.10",
                            "relay":"wss://relay.testls.bit/",
                            "tls":[[2,1,1,"H"]]
                        }}}
                        """.trimIndent(),
                    )
                }
            val resolver = newRelayResolver(client)
            val outcome = resolver.resolveRaw("wss://relay.testls.bit")
            assertTrue("got $outcome", outcome is BitRelayResolver.Resolution.Resolved)
            outcome as BitRelayResolver.Resolution.Resolved
            assertEquals("wss://relay.testls.bit/", outcome.resolvedUrl)
            assertEquals("H", outcome.tlsaRecords.single().associationDataBase64)
            // Both names must have been queried (parent + import target).
            assertTrue("d/testls" in client.queriedNames)
            assertTrue("dd/testls-relay" in client.queriedNames)
        }

    @Test
    fun `BitRelayResolver merges importer-wins for relay map`() =
        runTest {
            // Importer declares its own map.relay.relay; imported value
            // declares a different one. Importer wins.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {"import":"dd/lib",
                         "map":{"relay":{"relay":"wss://local.example/"}}}
                        """.trimIndent(),
                    )
                    register(
                        "dd/lib",
                        """
                        {"map":{"relay":{"relay":"wss://imported.example/",
                                          "tls":[[2,1,1,"FROM-IMPORT"]]}}}
                        """.trimIndent(),
                    )
                }
            val resolver = newRelayResolver(client)
            val outcome = resolver.resolveRaw("wss://relay.testls.bit") as BitRelayResolver.Resolution.Resolved
            assertEquals("wss://local.example/", outcome.resolvedUrl)
            // The whole `map` was overridden by the importer (the importer's
            // map.relay does not have `tls`), so no TLSA from the import.
            assertTrue(
                "importer-wins on map should suppress imported tls",
                outcome.tlsaRecords.isEmpty(),
            )
        }

    @Test
    fun `BitRelayResolver still resolves when imported name is missing`() =
        runTest {
            // Lenient failure: the importer has the relay info itself, the
            // imported boilerplate happens to be unreachable. We still
            // resolve from the importer.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {"import":"dd/missing",
                         "map":{"relay":{"relay":"wss://relay.testls.bit/"}}}
                        """.trimIndent(),
                    )
                    // dd/missing is intentionally NOT registered → fetcher returns null.
                }
            val resolver = newRelayResolver(client)
            val outcome = resolver.resolveRaw("wss://relay.testls.bit")
            assertTrue("got $outcome", outcome is BitRelayResolver.Resolution.Resolved)
            outcome as BitRelayResolver.Resolution.Resolved
            assertEquals("wss://relay.testls.bit/", outcome.resolvedUrl)
        }

    // ── Integration: NamecoinNameResolver (NIP-05) follows import too ──

    @Test
    fun `NIP-05 lookup follows import for shared nostr names block`() =
        runTest {
            // Common pattern: keep the apex small by importing a shared
            // nostr.names block from a stable second name. NIP-05 verification
            // for `m@testls.bit` must still succeed.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"import":"dd/testls-names","ip":"107.152.38.155"}""",
                    )
                    register(
                        "dd/testls-names",
                        """
                        {"nostr":{"names":{
                            "_":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                            "m":"6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d"
                        }}}
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val result = resolver.resolve("m@testls.bit")
            assertNotNull(result)
            assertEquals(
                "6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d",
                result?.pubkey,
            )
        }

    @Test
    fun `NIP-05 lookup surfaces MalformedRecord with parser detail when value is broken JSON`() =
        runTest {
            // Real-world failure mode: a hand-built `name_update` with one
            // closing brace short of balanced ends up published as broken
            // JSON. The publisher then sees "name not found / no nostr
            // field" and chases a phantom bug. Surface the parser's
            // diagnostic so they know the value itself is the problem.
            val truncated =
                """{"ip":"1.2.3.4","nostr":{"names":{"_":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"}""".trimIndent()
            val client =
                FakeElectrumXClient().apply {
                    register("d/broken", truncated)
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val outcome = resolver.resolveDetailed("_@broken.bit")
            assertTrue(
                "expected MalformedRecord, got $outcome",
                outcome is NamecoinResolveOutcome.MalformedRecord,
            )
            outcome as NamecoinResolveOutcome.MalformedRecord
            assertEquals("d/broken", outcome.name)
            // Don't pin the exact parser text — different kotlinx.serialization
            // versions phrase it differently. Just require it's a non-empty
            // diagnostic, NOT "no nostr field" or similar misdirection.
            assertTrue(
                "error must be a non-empty diagnostic: ${outcome.error}",
                outcome.error.isNotBlank(),
            )
        }

    @Test
    fun `NIP-05 lookup with subdomain follows import to find names at that node`() =
        runTest {
            // alice@relay.testls.bit walks d/testls -> map.relay; the
            // imported name supplies the WHOLE map.relay node, including
            // both `relay` and `nostr.names`. The importer (d/testls)
            // doesn't declare its own map.relay so importer-wins doesn't
            // suppress anything.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"import":"dd/relay-names","ip":"107.152.38.155"}""",
                    )
                    register(
                        "dd/relay-names",
                        """
                        {"map":{"relay":{
                            "relay":"wss://relay.testls.bit/",
                            "nostr":{"names":{
                                "alice":"6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d"
                            }}
                        }}}
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val result = resolver.resolve("alice@relay.testls.bit")
            assertNotNull("expected import-based names to make alice resolvable", result)
            assertEquals(
                "6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d",
                result?.pubkey,
            )
        }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun parse(s: String): JsonObject = json.parseToJsonElement(s).jsonObject

    private fun newRelayResolver(client: IElectrumXClient): BitRelayResolver =
        BitRelayResolver(
            nameResolver =
                NamecoinNameResolver(
                    electrumxClient = client,
                    lookupTimeoutMs = 1_000L,
                ),
        )

    /**
     * Same in-memory ElectrumX double the existing tests use. Duplicated
     * here so this file can run in isolation when only this test class is
     * exercised.
     */
    private class FakeElectrumXClient : IElectrumXClient {
        val records = mutableMapOf<String, String>()
        val queriedNames: MutableList<String> = mutableListOf()

        fun register(
            name: String,
            value: String,
        ) {
            records[name] = value
        }

        override suspend fun nameShowWithFallback(
            identifier: String,
            servers: List<ElectrumxServer>,
        ): NameShowResult? {
            queriedNames += identifier
            val value = records[identifier] ?: return null
            return NameShowResult(name = identifier, value = value)
        }
    }
}
