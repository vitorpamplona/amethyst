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
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/**
 * Characterisation test for the Namecoin Core RPC path.
 *
 * The RPC client sets `hostnameVerifier { _, _ -> true }` whenever
 * `usePinnedTrustStore` is on, so a certificate issued for an unrelated
 * domain (but chaining to an anchor in the trust store — every system CA
 * is one) is accepted for the configured RPC host.
 *
 * The second assertion is the containment check: the very same shared
 * OkHttpClient still rejects that certificate for any other host, proving
 * the permissive verifier does not leak out of the Namecoin call.
 */
class NamecoinRpcHostnameVerificationTest {
    private fun keytool(vararg args: String) {
        val p = ProcessBuilder(listOf("keytool") + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "keytool failed: $out" }
    }

    private fun buildCaAndLeaf(dir: File): Triple<String, KeyStore, KeyStore> {
        val ca = File(dir, "ca.p12").path
        val srv = File(dir, "srv.p12").path
        val caPem = File(dir, "ca.pem")
        val csr = File(dir, "srv.csr")
        val leafPem = File(dir, "srv.pem")
        val chain = File(dir, "chain.pem")

        keytool(
            "-genkeypair",
            "-alias",
            "ca",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-dname",
            "CN=Fake Public CA",
            "-ext",
            "bc:c",
            "-validity",
            "1",
            "-keystore",
            ca,
            "-storetype",
            "PKCS12",
            "-storepass",
            "changeit",
            "-keypass",
            "changeit",
        )
        keytool("-exportcert", "-alias", "ca", "-keystore", ca, "-storepass", "changeit", "-rfc", "-file", caPem.path)
        keytool(
            "-genkeypair",
            "-alias",
            "srv",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-dname",
            "CN=attacker-owned.example.net",
            "-ext",
            "san=dns:attacker-owned.example.net",
            "-validity",
            "1",
            "-keystore",
            srv,
            "-storetype",
            "PKCS12",
            "-storepass",
            "changeit",
            "-keypass",
            "changeit",
        )
        keytool("-certreq", "-alias", "srv", "-keystore", srv, "-storepass", "changeit", "-file", csr.path)
        keytool(
            "-gencert",
            "-alias",
            "ca",
            "-keystore",
            ca,
            "-storepass",
            "changeit",
            "-infile",
            csr.path,
            "-outfile",
            leafPem.path,
            "-rfc",
            "-ext",
            "san=dns:attacker-owned.example.net",
            "-validity",
            "1",
        )
        chain.writeText(caPem.readText() + leafPem.readText())
        keytool("-importcert", "-alias", "ca", "-keystore", srv, "-storepass", "changeit", "-file", caPem.path, "-noprompt")
        keytool("-importcert", "-alias", "srv", "-keystore", srv, "-storepass", "changeit", "-file", chain.path, "-noprompt")

        val serverKs = KeyStore.getInstance("PKCS12")
        File(srv).inputStream().use { serverKs.load(it, "changeit".toCharArray()) }

        val trustKs = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        val cert =
            java.security.cert.CertificateFactory
                .getInstance("X.509")
                .generateCertificate(caPem.inputStream())
        trustKs.setCertificateEntry("fake_public_ca", cert)

        return Triple(caPem.readText(), serverKs, trustKs)
    }

    @Test
    fun `today, rpc accepts a cert for another domain - but the shared client does not`() {
        val dir =
            kotlin.io.path
                .createTempDirectory("nmc-rpc-poc")
                .toFile()
        try {
            val (caPem, serverKs, trustKs) = buildCaAndLeaf(dir)

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(serverKs, "changeit".toCharArray())
            val serverCtx = SSLContext.getInstance("TLS")
            serverCtx.init(kmf.keyManagers, null, null)

            val server =
                serverCtx.serverSocketFactory.createServerSocket(
                    0,
                    8,
                    InetAddress.getByName("127.0.0.1"),
                ) as SSLServerSocket
            val port = server.localPort

            thread(isDaemon = true) {
                while (true) {
                    val s = runCatching { server.accept() as SSLSocket }.getOrNull() ?: break
                    thread(isDaemon = true) {
                        runCatching {
                            val reader = s.inputStream.bufferedReader()
                            var line = reader.readLine()
                            var len = 0
                            while (!line.isNullOrBlank()) {
                                if (line.startsWith("Content-Length:", true)) {
                                    len = line.substringAfter(":").trim().toInt()
                                }
                                line = reader.readLine()
                            }
                            if (len > 0) reader.read(CharArray(len), 0, len)
                            val payload = "{\"result\":{\"chain\":\"main\",\"blocks\":7},\"error\":null,\"id\":\"amethyst\"}"
                            val out = s.outputStream.bufferedWriter()
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${payload.length}\r\nConnection: close\r\n\r\n$payload")
                            out.flush()
                            s.close()
                        }
                    }
                }
            }

            // Client-side trust of the "public" CA — as if the attacker simply
            // bought a DV certificate for a domain they own.
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(trustKs)
            val tm = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
            val clientCtx = SSLContext.getInstance("TLS")
            clientCtx.init(null, arrayOf<javax.net.ssl.TrustManager>(tm), null)

            // The app-wide client every other subsystem uses, with DNS pointed at
            // the rogue endpoint to simulate the network attacker.
            val shared =
                OkHttpClient
                    .Builder()
                    .dns { listOf(InetAddress.getByName("127.0.0.1")) }
                    .proxy(java.net.Proxy.NO_PROXY)
                    .sslSocketFactory(clientCtx.socketFactory, tm)
                    .build()

            val rpc = NamecoinCoreRpcClient(httpClientForUrl = { shared })
            rpc.setDynamicCerts(listOf(caPem))

            val probe =
                runBlocking {
                    rpc.probe(
                        NamecoinCoreRpcConfig(
                            url = "https://namecoin-node.invalid:$port/",
                            username = "u",
                            password = "p",
                            usePinnedTrustStore = true,
                            timeoutMs = 8_000L,
                        ),
                    )
                }
            println("RPC probe: success=${probe.success} chain=${probe.chain} error=${probe.error}")
            assertTrue("Namecoin RPC accepted a cert for another domain: ${probe.error}", probe.success)

            // Containment check: any other host through the same shared client.
            val leaked =
                runCatching {
                    shared
                        .newCall(Request.Builder().url("https://other-service.invalid:$port/").build())
                        .execute()
                        .use { it.code }
                }
            println("Other-domain call through the shared client: $leaked")
            assertFalse(
                "The permissive verifier leaked into the shared client",
                leaked.isSuccess,
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
