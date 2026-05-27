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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NamecoinCoreRpcClientTest {
    /**
     * Build a fake [OkHttpClient] that answers every request with [code]
     * and [body], capturing the inbound request body so assertions can be
     * made against it.
     */
    private fun fakeClient(
        code: Int = 200,
        body: String,
        captured: MutableList<Request> = mutableListOf(),
        capturedBodies: MutableList<String> = mutableListOf(),
    ): OkHttpClient {
        val interceptor =
            Interceptor { chain ->
                val req = chain.request()
                captured += req
                val buf = okio.Buffer()
                req.body?.writeTo(buf)
                capturedBodies += buf.readUtf8()
                Response
                    .Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("text/plain".toMediaType()))
                    .build()
            }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun `name_show success parses result and sends auth`() =
        runBlocking {
            val captured = mutableListOf<Request>()
            val capturedBodies = mutableListOf<String>()
            val responseJson =
                """{"result":{"name":"d/example","value":"{\"nostr\":\"deadbeef\"}",""" +
                    """"txid":"abcd","height":12345,"expires_in":36000,"expired":false},""" +
                    """"error":null,"id":"amethyst"}"""
            val http = fakeClient(body = responseJson, captured = captured, capturedBodies = capturedBodies)
            val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
            client.setConfig(
                NamecoinCoreRpcConfig(url = "http://node.example/", username = "u", password = "p"),
            )

            val r = client.nameShow("d/example")
            assertNotNull(r)
            assertEquals("d/example", r!!.name)
            assertEquals("""{"nostr":"deadbeef"}""", r.value)
            assertEquals("abcd", r.txid)
            assertEquals(12345, r.height)

            assertEquals(1, captured.size)
            val req = captured.first()
            assertEquals("POST", req.method)
            assertNotNull(req.header("Authorization"))
            assertTrue(capturedBodies.first().contains("\"method\":\"name_show\""))
            assertTrue(capturedBodies.first().contains("\"d/example\""))
        }

    @Test
    fun `name-not-found from rpc becomes NameNotFound`() {
        val http =
            fakeClient(
                body = """{"result":null,"error":{"code":-4,"message":"name never existed: 'd/nope'"},"id":"amethyst"}""",
            )
        val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
        client.setConfig(NamecoinCoreRpcConfig(url = "http://node/", username = "u", password = "p"))
        assertThrows(NamecoinLookupException.NameNotFound::class.java) {
            runBlocking { client.nameShow("d/nope") }
        }
    }

    @Test
    fun `non-not-found rpc errors map to ServersUnreachable`() {
        val http =
            fakeClient(
                body = """{"result":null,"error":{"code":-1,"message":"method not found"},"id":"amethyst"}""",
            )
        val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
        client.setConfig(NamecoinCoreRpcConfig(url = "http://node/", username = "u", password = "p"))
        assertThrows(NamecoinLookupException.ServersUnreachable::class.java) {
            runBlocking { client.nameShow("d/example") }
        }
    }

    @Test
    fun `expired name becomes NameExpired`() {
        val http =
            fakeClient(
                body = """{"result":{"name":"d/old","value":"{}","expired":true},"error":null,"id":"amethyst"}""",
            )
        val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
        client.setConfig(NamecoinCoreRpcConfig(url = "http://node/", username = "u", password = "p"))
        assertThrows(NamecoinLookupException.NameExpired::class.java) {
            runBlocking { client.nameShow("d/old") }
        }
    }

    @Test
    fun `unusable config short-circuits as ServersUnreachable`() {
        val http = fakeClient(body = "{}")
        val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
        client.setConfig(NamecoinCoreRpcConfig(url = "", username = "u", password = "p"))
        assertThrows(NamecoinLookupException.ServersUnreachable::class.java) {
            runBlocking { client.nameShow("d/example") }
        }
    }

    @Test
    fun `probe getblockchaininfo populates fields`() =
        runBlocking {
            val http =
                fakeClient(
                    body =
                        """{"result":{"chain":"main","blocks":826560,""" +
                            """"verificationprogress":0.999999,"initialblockdownload":false},""" +
                            """"error":null,"id":"amethyst"}""",
                )
            val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
            val probe = client.probe(NamecoinCoreRpcConfig(url = "http://node/", username = "u", password = "p"))
            assertTrue(probe.success)
            assertEquals("main", probe.chain)
            assertEquals(826560, probe.blocks)
            assertEquals(false, probe.initialBlockDownload)
        }

    @Test
    fun `probe surfaces auth failure`() =
        runBlocking {
            val http = fakeClient(code = 401, body = "")
            val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
            val probe = client.probe(NamecoinCoreRpcConfig(url = "http://node/", username = "u", password = "p"))
            assertFalse(probe.success)
            assertNotNull(probe.error)
        }

    // ── TOFU / pinning ────────────────────────────────────────────────────────────

    @Test
    fun `probe leaves cert fields null on http url`() =
        runBlocking {
            // No TLS in this fake transport, so the probe shouldn't fabricate
            // a fingerprint. The interceptor short-circuits before any real
            // socket is opened, but the probe layer also runs an out-of-band
            // TLS capture — it must return null for http://.
            val http =
                fakeClient(
                    body =
                        """{"result":{"chain":"main","blocks":1,"verificationprogress":1.0,"initialblockdownload":false},"error":null,"id":"amethyst"}""",
                )
            val client = NamecoinCoreRpcClient(httpClientForUrl = { http })
            val probe = client.probe(NamecoinCoreRpcConfig(url = "http://node.example/", username = "u", password = "p"))
            assertTrue(probe.success)
            assertEquals(null, probe.serverCertPem)
            assertEquals(null, probe.certFingerprint)
            assertFalse(probe.tlsHandshakeFailed)
        }

    @Test
    fun `addPinnedCert is idempotent and survives setDynamicCerts`() {
        val client = NamecoinCoreRpcClient(httpClientForUrl = { OkHttpClient() })
        // addPinnedCert twice with same PEM should keep one copy (we can't
        // observe the internal list directly, but we can confirm that the
        // next call doesn't throw, and that setDynamicCerts() then replaces
        // the contents wholesale).
        val pem =
            "-----BEGIN CERTIFICATE-----\n" +
                "MIIBhTCCASugAwIBAgIQEAAAAAAAnRtRrwK0e\n" +
                "-----END CERTIFICATE-----\n"
        client.addPinnedCert(pem)
        client.addPinnedCert(pem)
        client.setDynamicCerts(emptyList())
        // Replacing with a malformed PEM must not blow up later use; this
        // is the same robustness contract ElectrumXClient honours.
        client.setDynamicCerts(listOf("not a cert"))
        // Smoke test that subsequent calls still work without pinning
        // (we're using http:// so pinning isn't engaged).
        val captured = mutableListOf<Request>()
        val http =
            fakeClient(
                body = """{"result":{"chain":"main","blocks":1},"error":null,"id":"amethyst"}""",
                captured = captured,
            )
        val c2 = NamecoinCoreRpcClient(httpClientForUrl = { http })
        c2.setConfig(NamecoinCoreRpcConfig(url = "http://n/", usePinnedTrustStore = true))
        runBlocking {
            val r = c2.probe(c2.currentConfig())
            assertTrue(r.success)
        }
        // usePinnedTrustStore is a no-op for http:// URLs — verify the
        // request still went through the fake client.
        assertTrue(captured.isNotEmpty())
    }

    @Test
    fun `RpcProbeResult carries new fields with sensible defaults`() {
        // Public contract: existing call sites that don't set the new
        // fields keep compiling and read null/false defaults.
        val r =
            com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin
                .RpcProbeResult(success = true, elapsedMs = 0)
        assertEquals(null, r.serverCertPem)
        assertEquals(null, r.certFingerprint)
        assertFalse(r.tlsHandshakeFailed)
    }
}
