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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import javax.net.SocketFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * Characterisation test for the ElectrumX TLS path.
 *
 * [ElectrumXClient.createSocket] upgrades a plain socket with
 * `SSLSocketFactory.createSocket(base, host, port, true)` and never sets
 * `endpointIdentificationAlgorithm`, so JSSE performs **no hostname
 * verification**. The trust store used for the handshake also contains every
 * system CA (see `buildPinnedSslFactory`), so *any* certificate that chains
 * to a public CA — a DV cert for a domain the attacker owns — is accepted
 * for *any* ElectrumX host.
 *
 * This test asserts the behaviour as it is today. When hostname verification
 * is added, invert the assertion (the handshake must fail) rather than
 * deleting the test.
 */
class NamecoinElectrumXHostnameVerificationTest {
    private fun keytool(vararg args: String) {
        val p =
            ProcessBuilder(listOf("keytool") + args)
                .redirectErrorStream(true)
                .start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        check(code == 0) { "keytool ${args.joinToString(" ")} failed: $out" }
    }

    private fun buildCaAndLeaf(dir: File): Pair<String, KeyStore> {
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

        val ks = KeyStore.getInstance("PKCS12")
        File(srv).inputStream().use { ks.load(it, "changeit".toCharArray()) }
        return caPem.readText() to ks
    }

    @Test
    fun `today, a cert for an unrelated domain is accepted for any electrumx host`() {
        val dir =
            kotlin.io.path
                .createTempDirectory("nmc-mitm-poc")
                .toFile()
        try {
            val (caPem, serverKs) = buildCaAndLeaf(dir)

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(serverKs, "changeit".toCharArray())
            val serverCtx = SSLContext.getInstance("TLS")
            serverCtx.init(kmf.keyManagers, null, null)

            val server =
                serverCtx.serverSocketFactory.createServerSocket(
                    0,
                    1,
                    InetAddress.getByName("127.0.0.1"),
                ) as SSLServerSocket
            val port = server.localPort

            thread(isDaemon = true) {
                runCatching {
                    val s = server.accept() as SSLSocket
                    val reader = s.inputStream.bufferedReader()
                    val writer = s.outputStream.bufferedWriter()
                    reader.readLine()
                    writer.write("{\"jsonrpc\":\"2.0\",\"result\":[\"ElectrumX 1.16\",\"1.4\"],\"id\":1}\n")
                    writer.flush()
                    Thread.sleep(500)
                    s.close()
                }
            }

            // Simulates the network attacker: every TCP connect, whatever host
            // the client believes it is dialling, lands on the rogue server.
            val redirecting =
                object : SocketFactory() {
                    override fun createSocket(): Socket =
                        object : Socket() {
                            override fun connect(
                                endpoint: java.net.SocketAddress?,
                                timeout: Int,
                            ) = super.connect(InetSocketAddress("127.0.0.1", port), timeout)
                        }

                    override fun createSocket(
                        host: String?,
                        port: Int,
                    ): Socket = throw UnsupportedOperationException()

                    override fun createSocket(
                        host: String?,
                        port: Int,
                        localHost: InetAddress?,
                        localPort: Int,
                    ): Socket = throw UnsupportedOperationException()

                    override fun createSocket(
                        host: InetAddress?,
                        port: Int,
                    ): Socket = throw UnsupportedOperationException()

                    override fun createSocket(
                        address: InetAddress?,
                        port: Int,
                        localAddress: InetAddress?,
                        localPort: Int,
                    ): Socket = throw UnsupportedOperationException()
                }

            val client = ElectrumXClient(socketFactory = { redirecting })
            // Stands in for a system CA anchor: buildPinnedSslFactory() inserts
            // every system trust anchor into the very same keystore.
            client.setDynamicCerts(listOf(caPem))

            val result =
                runBlocking {
                    client.testServer(
                        ElectrumxServer("electrumx.testls.space", 50002, useSsl = true, usePinnedTrustStore = true),
                        testName = null,
                    )
                }

            println("PoC result: success=${result.success} tls=${result.tlsVersion} error=${result.error}")
            assertTrue(
                "Expected the handshake to succeed, proving no hostname verification: ${result.error}",
                result.success,
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
