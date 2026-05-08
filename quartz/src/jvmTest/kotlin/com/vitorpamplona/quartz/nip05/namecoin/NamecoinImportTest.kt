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
 * NIP-05 resolution path through [NamecoinNameResolver].
 *
 * Tests do NOT touch the network: every "imported" name is served by
 * an in-memory map keyed by Namecoin name.
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
                kotlinx.serialization.json.JsonNull,
                expanded["ip"],
            )
            assertEquals("keep", expanded["other"]?.jsonPrimitive?.content)
        }

    @Test
    fun `recursion depth four is supported`() =
        runTest {
            // ifa-0001 mandates implementations support a recursion degree
            // of at least 4. Test pins the 4-deep happy path.
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
            // record's own items still apply.
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
            // first). The empty-key and "*" wildcard rules apply too.
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

    @Test
    fun `selector falls back to wildcard star when exact label is missing`() =
        runTest {
            val obj = parse("""{"import":["d/lib","ghost"]}""")
            val expanded =
                NamecoinImportResolver.expandImports(obj) { name ->
                    when (name) {
                        "d/lib" -> """{"map":{"*":{"value":"wildcard"}}}"""
                        else -> null
                    }
                }
            assertEquals("wildcard", expanded["value"]?.jsonPrimitive?.content)
        }

    // ── Integration: NamecoinNameResolver (NIP-05) follows import ──────────

    @Test
    fun `NIP-05 lookup follows import for shared nostr names block`() =
        runTest {
            // The real-world `testls.bit` deployment: the apex record at
            // `d/testls` is up against the 520-byte per-name limit and
            // delegates its `nostr.names` block to a sibling name via
            // `"import":"dd/testls"`. Without import support, NIP-05
            // resolution sees no `nostr` field at d/testls and fails.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"import":"dd/testls","ip":"107.152.38.155"}""",
                    )
                    register(
                        "dd/testls",
                        """
                        {"nostr":{"names":{
                            "_":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                            "m":"6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d"
                        }}}
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)

            // Bare `.bit` resolves to root entry.
            val rootResult = resolver.resolve("testls.bit")
            assertNotNull("bare testls.bit should resolve via import", rootResult)
            assertEquals(
                "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                rootResult?.pubkey,
            )

            // Named identity `m@testls.bit` resolves through the same import.
            val mResult = resolver.resolve("m@testls.bit")
            assertNotNull("m@testls.bit should resolve via import", mResult)
            assertEquals(
                "6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d",
                mResult?.pubkey,
            )

            // Both names must have been queried (parent + import target).
            assertTrue("d/testls" in client.queriedNames)
            assertTrue("dd/testls" in client.queriedNames)
        }

    @Test
    fun `resolveDetailed returns Success when import supplies the names block`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"import":"dd/testls"}""",
                    )
                    register(
                        "dd/testls",
                        """
                        {"nostr":{"names":{
                            "m":"6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d"
                        }}}
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val outcome = resolver.resolveDetailed("m@testls.bit")
            assertTrue("expected Success, got $outcome", outcome is NamecoinResolveOutcome.Success)
            outcome as NamecoinResolveOutcome.Success
            assertEquals(
                "6cdebccabda1dfa058ab85352a79509b592b2bdfa0370325e28ec1cb4f18667d",
                outcome.result.pubkey,
            )
        }

    @Test
    fun `resolveDetailed returns NoNostrField when import target lacks nostr`() =
        runTest {
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """{"import":"dd/testls"}""",
                    )
                    register(
                        "dd/testls",
                        """{"ip":"1.2.3.4"}""", // no nostr field even after merge
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val outcome = resolver.resolveDetailed("testls.bit")
            assertTrue(
                "expected NoNostrField, got $outcome",
                outcome is NamecoinResolveOutcome.NoNostrField,
            )
        }

    @Test
    fun `record without import key skips import resolver entirely`() =
        runTest {
            // Pure regression guard: ensure non-import records pay zero
            // I/O cost (no extra ElectrumX queries beyond the parent).
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/plain",
                        """
                        {"nostr":{"names":{
                            "_":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
                        }}}
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val result = resolver.resolve("plain.bit")
            assertNotNull(result)
            assertEquals(
                "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                result?.pubkey,
            )
            // Exactly one query: d/plain. No import means no extra fetches.
            assertEquals(listOf("d/plain"), client.queriedNames)
        }

    @Test
    fun `importer wins for nostr names so apex can override a named entry`() =
        runTest {
            // Importer declares its own `nostr.names.m`; imported value
            // declares a different one. Importer wins on the whole `nostr`
            // key (shallow merge per spec).
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {"import":"dd/testls",
                         "nostr":{"names":{"m":"aaaa000000000000000000000000000000000000000000000000000000000001"}}}
                        """.trimIndent(),
                    )
                    register(
                        "dd/testls",
                        """
                        {"nostr":{"names":{"m":"bbbb000000000000000000000000000000000000000000000000000000000002"}}}
                        """.trimIndent(),
                    )
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val result = resolver.resolve("m@testls.bit")
            assertNotNull(result)
            assertEquals(
                "aaaa000000000000000000000000000000000000000000000000000000000001",
                result?.pubkey,
            )
        }

    @Test
    fun `failed import does not break NIP-05 if names are local`() =
        runTest {
            // Importer has its own `nostr.names`; the imported
            // boilerplate happens to be unreachable. Resolution still
            // succeeds from the importer's own data.
            val client =
                FakeElectrumXClient().apply {
                    register(
                        "d/testls",
                        """
                        {"import":"dd/missing",
                         "nostr":{"names":{"_":"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"}}}
                        """.trimIndent(),
                    )
                    // dd/missing is intentionally NOT registered.
                }
            val resolver = NamecoinNameResolver(client, lookupTimeoutMs = 1_000L)
            val result = resolver.resolve("testls.bit")
            assertNotNull(result)
            assertEquals(
                "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
                result?.pubkey,
            )
        }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun parse(s: String): JsonObject = json.parseToJsonElement(s).jsonObject

    /**
     * In-memory ElectrumX double. Lets each test register the records its
     * lookup will see and asserts on the names actually queried.
     */
    private class FakeElectrumXClient : IElectrumXClient {
        private val records = mutableMapOf<String, String>()
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
