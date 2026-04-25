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
package com.vitorpamplona.quic.tls

import com.vitorpamplona.quic.QuicCodecException
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * JDK / Android system-trust-store backed certificate validator.
 *
 * Delegates chain validation to `TrustManagerFactory.getInstance(...).init(null)`,
 * which on Android resolves the system CA store and on the JVM resolves the
 * default truststore (cacerts). Then verifies the TLS 1.3 CertificateVerify
 * signature using `java.security.Signature` for RSA-PSS / ECDSA, and Quartz's
 * Ed25519 for Ed25519.
 */
class JdkCertificateValidator(
    /** If non-null, only certs whose chain validates against this set; defaults to system trust store. */
    private val trustManager: X509TrustManager = defaultTrustManager(),
) : CertificateValidator {
    private var leafCert: X509Certificate? = null

    override fun validateChain(
        chain: List<ByteArray>,
        expectedHost: String,
    ) {
        if (chain.isEmpty()) throw QuicCodecException("server sent empty certificate chain")
        val cf = CertificateFactory.getInstance("X.509")
        val parsed =
            chain.map {
                cf.generateCertificate(ByteArrayInputStream(it)) as X509Certificate
            }
        try {
            // RFC 8446 §4.4.2.4 — TLS 1.3 over QUIC negotiates ALPN h3.
            trustManager.checkServerTrusted(parsed.toTypedArray(), "ECDHE_ECDSA")
        } catch (t: Throwable) {
            throw QuicCodecException("certificate chain validation failed: ${t.message}", t)
        }
        // Hostname verification per RFC 6125.
        if (!hostnameMatches(parsed[0], expectedHost)) {
            throw QuicCodecException("certificate does not match host $expectedHost")
        }
        leafCert = parsed[0]
    }

    override fun verifySignature(
        signatureAlgorithm: Int,
        signature: ByteArray,
        transcriptHash: ByteArray,
    ) {
        val cert = leafCert ?: throw QuicCodecException("CertificateVerify before Certificate")

        // RFC 8446 §4.4.3 — the signed content is:
        //   64 spaces || "TLS 1.3, server CertificateVerify" || 0x00 || transcript_hash
        val context = "TLS 1.3, server CertificateVerify".encodeToByteArray()
        val signedData = ByteArray(64 + context.size + 1 + transcriptHash.size)
        for (i in 0 until 64) signedData[i] = 0x20
        context.copyInto(signedData, 64)
        signedData[64 + context.size] = 0x00
        transcriptHash.copyInto(signedData, 64 + context.size + 1)

        val sig = jcaSignatureFor(signatureAlgorithm)
        sig.initVerify(cert.publicKey)
        sig.update(signedData)
        if (!sig.verify(signature)) {
            throw QuicCodecException("CertificateVerify signature did not verify")
        }
    }

    private fun jcaSignatureFor(algorithm: Int): Signature =
        when (algorithm) {
            TlsConstants.SIG_ECDSA_SECP256R1_SHA256 -> Signature.getInstance("SHA256withECDSA")
            TlsConstants.SIG_ECDSA_SECP384R1_SHA384 -> Signature.getInstance("SHA384withECDSA")
            TlsConstants.SIG_RSA_PSS_RSAE_SHA256 -> rsaPss("SHA-256", 32)
            TlsConstants.SIG_RSA_PSS_RSAE_SHA384 -> rsaPss("SHA-384", 48)
            TlsConstants.SIG_RSA_PSS_RSAE_SHA512 -> rsaPss("SHA-512", 64)
            TlsConstants.SIG_RSA_PKCS1_SHA256 -> Signature.getInstance("SHA256withRSA")
            TlsConstants.SIG_ED25519 -> Signature.getInstance("Ed25519")
            else -> throw QuicCodecException("unsupported signature algorithm 0x${algorithm.toString(16)}")
        }

    private fun rsaPss(
        digest: String,
        saltLen: Int,
    ): Signature {
        val sig = Signature.getInstance("RSASSA-PSS")
        sig.setParameter(PSSParameterSpec(digest, "MGF1", MGF1ParameterSpec(digest), saltLen, 1))
        return sig
    }

    private fun hostnameMatches(
        cert: X509Certificate,
        host: String,
    ): Boolean {
        // SAN check — RFC 6125. Walk subject alt names and accept any DNS or IP match.
        val sans = cert.subjectAlternativeNames ?: return false
        for (entry in sans) {
            val type = entry[0] as Int
            val value = entry[1].toString()
            // GeneralName type 2 = dNSName, type 7 = iPAddress.
            if (type == 2 && dnsMatches(value, host)) return true
            if (type == 7 && value.equals(host, ignoreCase = true)) return true
        }
        return false
    }

    private fun dnsMatches(
        pattern: String,
        host: String,
    ): Boolean {
        if (pattern.equals(host, ignoreCase = true)) return true
        if (!pattern.startsWith("*.")) return false
        // Wildcards only match a single component.
        val suffix = pattern.substring(1).lowercase()
        val lhost = host.lowercase()
        if (!lhost.endsWith(suffix)) return false
        val prefix = lhost.substring(0, lhost.length - suffix.length)
        return prefix.isNotEmpty() && '.' !in prefix
    }

    companion object {
        private fun defaultTrustManager(): X509TrustManager {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            return tmf.trustManagers.firstNotNullOf { it as? X509TrustManager }
        }
    }
}
