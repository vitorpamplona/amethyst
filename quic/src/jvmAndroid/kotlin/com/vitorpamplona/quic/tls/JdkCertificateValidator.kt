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
import java.lang.reflect.InvocationTargetException
import java.net.IDN
import java.net.InetAddress
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
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
        // Round-5 #5: parse certificates inside the try block so a malformed
        // DER blob throws QuicCodecException (which the read loop maps to
        // CONNECTION_CLOSE) instead of an uncaught CertificateException.
        val cf = CertificateFactory.getInstance("X.509")
        val parsed: List<X509Certificate>
        try {
            parsed =
                chain.map {
                    cf.generateCertificate(ByteArrayInputStream(it)) as X509Certificate
                }
        } catch (t: Throwable) {
            throw QuicCodecException("certificate chain parse failed: ${t.message}", t)
        }
        try {
            // X509TrustManager auth-type string is the TLS key-exchange / sig-alg
            // pair derived from the cipher suite name — for TLS 1.3 we use the
            // leaf cert's public-key algorithm to pick the right value, since
            // some Android trust managers (RootTrustManager, NetworkSecurityConfig)
            // gate algorithm-specific pinning on this string.
            val authType =
                when (parsed[0].publicKey.algorithm) {
                    "RSA" -> "ECDHE_RSA"

                    "EC" -> "ECDHE_ECDSA"

                    "EdDSA" -> "ECDHE_ECDSA"

                    // RFC 8422 ext, no dedicated TLS 1.3 string
                    else -> "ECDHE_ECDSA"
                }
            // Android's RootTrustManager rejects the 2-arg overload when the
            // app has Network Security Config domain-specific entries and
            // requires the hostname-aware 3-arg variant. That overload is
            // Android-specific (not on standard X509TrustManager), so we
            // discover it by reflection and fall back on plain JVM.
            val chainArray = parsed.toTypedArray()
            val hostnameAware = hostnameAwareCheckServerTrusted(trustManager)
            if (hostnameAware != null) {
                try {
                    hostnameAware.invoke(trustManager, chainArray, authType, expectedHost)
                } catch (e: InvocationTargetException) {
                    throw e.cause ?: e
                }
            } else {
                trustManager.checkServerTrusted(chainArray, authType)
            }
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
            TlsConstants.SIG_ECDSA_SECP256R1_SHA256 -> {
                Signature.getInstance("SHA256withECDSA")
            }

            TlsConstants.SIG_ECDSA_SECP384R1_SHA384 -> {
                Signature.getInstance("SHA384withECDSA")
            }

            TlsConstants.SIG_RSA_PSS_RSAE_SHA256 -> {
                rsaPss("SHA-256", 32)
            }

            TlsConstants.SIG_RSA_PSS_RSAE_SHA384 -> {
                rsaPss("SHA-384", 48)
            }

            TlsConstants.SIG_RSA_PSS_RSAE_SHA512 -> {
                rsaPss("SHA-512", 64)
            }

            TlsConstants.SIG_ED25519 -> {
                try {
                    // JCA "Ed25519" was added to Android Conscrypt in API 33.
                    // On API 26–32 (our minSdk floor) this throws — surface
                    // it as a clean QuicCodecException so the read loop maps
                    // to CONNECTION_CLOSE rather than crashing the parser.
                    Signature.getInstance("Ed25519")
                } catch (_: NoSuchAlgorithmException) {
                    throw QuicCodecException(
                        "Ed25519 not supported on this platform " +
                            "(requires Android API 33+ or a JDK with the EdDSA provider)",
                    )
                }
            }

            // Audit-4 #2: rsa_pkcs1_* schemes are forbidden in CertificateVerify
            // by RFC 8446 §4.2.3 (only allowed in CertificateRequest for
            // legacy compat). Accepting them allowed a server to sign with
            // weaker PKCS#1 v1.5 instead of RSA-PSS.
            else -> {
                throw QuicCodecException("unsupported signature algorithm 0x${algorithm.toString(16)}")
            }
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
        val sans = cert.subjectAlternativeNames ?: return false
        // Normalize host once: IDN → ASCII for DNS comparison, parsed-and-
        // re-stringified for IP literals so v6 forms compare equal.
        val normalizedHost = idnAscii(host)
        // Audit-4 #4: do NOT call InetAddress.getByName on a hostname — it
        // performs a DNS A/AAAA lookup, leaking the hostname over plaintext
        // DNS at the TLS-validation step. Only resolve confirmed IP literals.
        val hostAsIp =
            if (looksLikeIpLiteral(host)) {
                try {
                    InetAddress.getByName(host).hostAddress
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
        for (entry in sans) {
            val type = entry[0] as Int
            val value = entry[1].toString()
            // GeneralName type 2 = dNSName, type 7 = iPAddress.
            if (type == 2 && dnsMatches(idnAscii(value), normalizedHost)) return true
            if (type == 7 && hostAsIp != null) {
                // Defense-in-depth: a malformed cert could put a hostname in
                // a type 7 SAN. Without this gate, InetAddress.getByName(value)
                // would perform a DNS A/AAAA lookup on the validation path,
                // both leaking the name in plaintext and blocking the read
                // loop on the system resolver. Forcing a literal check keeps
                // the JDK call to pure parsing (no I/O, no name service).
                val sanIp =
                    if (looksLikeIpLiteral(value)) {
                        try {
                            InetAddress.getByName(value).hostAddress
                        } catch (_: Throwable) {
                            null
                        }
                    } else {
                        null
                    }
                if (sanIp != null && sanIp.equals(hostAsIp, ignoreCase = true)) return true
            }
        }
        return false
    }

    private fun idnAscii(name: String): String =
        try {
            IDN.toASCII(name).lowercase()
        } catch (_: Throwable) {
            name.lowercase()
        }

    /**
     * Strict pattern-match for IPv4 / IPv6 literals — round-5 #3 tightens
     * audit-4 #4. The previous check (`all digits/dots and contains a dot`)
     * accepted strings like "1.2.3.4.5" or "1.2" that Java's
     * `InetAddress.getByName` happily resolves via DNS, defeating the SNI-
     * leak fix. IPv4 must be exactly four dot-separated octets each in
     * 0..255; IPv6 must contain a colon (further parsing is left to the
     * JDK once we've confirmed it's a literal).
     */
    private fun looksLikeIpLiteral(host: String): Boolean {
        val unbracketed =
            if (host.startsWith("[") && host.endsWith("]")) host.substring(1, host.length - 1) else host
        if (unbracketed.contains(':')) return true // IPv6 — parse via JDK
        // IPv4: 4 octets 0..255, no leading zeros tolerated as integers.
        val parts = unbracketed.split('.')
        if (parts.size != 4) return false
        for (p in parts) {
            if (p.isEmpty() || p.length > 3) return false
            if (!p.all { it.isDigit() }) return false
            val n = p.toIntOrNull() ?: return false
            if (n !in 0..255) return false
        }
        return true
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
        if (prefix.isEmpty() || '.' in prefix) return false
        // RFC 6125 §6.4.3 — disallow wildcards in the public-suffix
        // label. The full Mozilla PSL is ~9000 entries; we ship a
        // hand-picked subset covering the common multi-label
        // effective-TLDs that come up in the wild for cert
        // mis-issuance scenarios (multi-tenant ccTLDs, hosting
        // platforms). Two layers of defence:
        //   1. The dot-count heuristic (≥ 2 dots in the suffix)
        //      catches the obvious `*.com` / `*.net` patterns.
        //   2. The denylist below catches `*.co.uk`,
        //      `*.s3.amazonaws.com`, `*.github.io`, etc. — the
        //      multi-label tldsets where the dot-count alone would
        //      let a rogue wildcard impersonate an entire tenant
        //      pool.
        //
        // This is intentionally conservative — false positives
        // (rejecting a legitimate but unusual wildcard) are recoverable
        // by the application using OS-level pinning, while false
        // negatives (accepting a rogue wildcard) silently break
        // hostname authentication. The full PSL would close the
        // remaining gap; until then, this catches the high-volume
        // attack surfaces.
        val suffixWithoutLeadingDot = suffix.removePrefix(".")
        if (suffixWithoutLeadingDot in MULTI_LABEL_PUBLIC_SUFFIXES) return false
        val suffixDots = suffix.count { it == '.' }
        return suffixDots >= 2
    }

    companion object {
        /**
         * Hand-picked subset of the Mozilla Public Suffix List covering
         * common multi-label effective-TLDs. Wildcards spanning these
         * suffixes (e.g. `*.co.uk`, `*.s3.amazonaws.com`) MUST be
         * rejected per RFC 6125 §6.4.3 — a rogue cert that captured
         * such a wildcard would impersonate every co-tenant.
         *
         * Strict subset of the full PSL — we don't ship the ~9000-entry
         * data file; entries here cover the high-frequency attack
         * surfaces (multi-tenant ccTLDs, major hosting platforms). The
         * full PSL would close the remaining gap; for now, callers of
         * sensitive endpoints should still layer OS-level pinning.
         *
         * Sources:
         *  - Top multi-label ccTLDs from publicsuffix.org/list/
         *    (uk / au / nz / jp / kr / br / mx / in / ar / il / etc.)
         *  - Major hosting platforms whose tenant subdomains all share
         *    one cert root (s3.amazonaws.com, github.io, herokuapp.com,
         *    vercel.app, netlify.app, web.app, blogspot.com,
         *    appspot.com, pages.dev, workers.dev, etc.)
         */
        private val MULTI_LABEL_PUBLIC_SUFFIXES: Set<String> =
            setOf(
                // UK
                "co.uk",
                "org.uk",
                "ac.uk",
                "gov.uk",
                "ltd.uk",
                "plc.uk",
                "me.uk",
                "net.uk",
                "sch.uk",
                "nhs.uk",
                "police.uk",
                // AU
                "com.au",
                "net.au",
                "org.au",
                "edu.au",
                "gov.au",
                "asn.au",
                "id.au",
                // NZ
                "co.nz",
                "net.nz",
                "org.nz",
                "ac.nz",
                "govt.nz",
                "school.nz",
                // JP
                "co.jp",
                "ne.jp",
                "or.jp",
                "ac.jp",
                "ad.jp",
                "ed.jp",
                "go.jp",
                "gr.jp",
                "lg.jp",
                // KR
                "co.kr",
                "ne.kr",
                "or.kr",
                "re.kr",
                "ac.kr",
                "go.kr",
                "mil.kr",
                "sc.kr",
                // BR
                "com.br",
                "net.br",
                "org.br",
                "edu.br",
                "gov.br",
                "mil.br",
                // MX
                "com.mx",
                "net.mx",
                "org.mx",
                "edu.mx",
                "gob.mx",
                // IN
                "co.in",
                "net.in",
                "org.in",
                "edu.in",
                "gov.in",
                "ac.in",
                "res.in",
                // AR
                "com.ar",
                "net.ar",
                "org.ar",
                "edu.ar",
                "gov.ar",
                "gob.ar",
                "mil.ar",
                // IL
                "co.il",
                "net.il",
                "org.il",
                "ac.il",
                "gov.il",
                "muni.il",
                "k12.il",
                // ZA
                "co.za",
                "net.za",
                "org.za",
                "ac.za",
                "gov.za",
                "edu.za",
                "law.za",
                // CN
                "com.cn",
                "net.cn",
                "org.cn",
                "edu.cn",
                "gov.cn",
                "ac.cn",
                "mil.cn",
                // TR
                "com.tr",
                "net.tr",
                "org.tr",
                "edu.tr",
                "gov.tr",
                "biz.tr",
                "info.tr",
                // RU
                "com.ru",
                "net.ru",
                "org.ru",
                "pp.ru",
                "msk.ru",
                "spb.ru",
                // PL
                "com.pl",
                "net.pl",
                "org.pl",
                "edu.pl",
                "gov.pl",
                "mil.pl",
                // ES
                "com.es",
                "nom.es",
                "org.es",
                "gob.es",
                "edu.es",
                // HK / SG / TW / MY
                "com.hk",
                "net.hk",
                "org.hk",
                "edu.hk",
                "gov.hk",
                "idv.hk",
                "com.sg",
                "net.sg",
                "org.sg",
                "edu.sg",
                "gov.sg",
                "per.sg",
                "com.tw",
                "net.tw",
                "org.tw",
                "edu.tw",
                "gov.tw",
                "idv.tw",
                "com.my",
                "net.my",
                "org.my",
                "edu.my",
                "gov.my",
                "mil.my",
                // Major hosting platforms — all tenants share root cert paths.
                "github.io",
                "github.com",
                "s3.amazonaws.com",
                "compute.amazonaws.com",
                "blogspot.com",
                "blogspot.co.uk",
                "blogspot.de",
                "blogspot.fr",
                "appspot.com",
                "googleapis.com",
                "googleusercontent.com",
                "herokuapp.com",
                "herokussl.com",
                "vercel.app",
                "now.sh",
                "netlify.app",
                "netlify.com",
                "web.app",
                "firebaseapp.com",
                "pages.dev",
                "workers.dev",
                "azurewebsites.net",
                "cloudapp.net",
                "trafficmanager.net",
                "fastly.net",
                "fastlylb.net",
                "cloudfront.net",
                "ngrok.io",
                "ngrok.app",
                "execute-api.us-east-1.amazonaws.com",
            )

        private fun defaultTrustManager(): X509TrustManager {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            return tmf.trustManagers.firstNotNullOf { it as? X509TrustManager }
        }

        private fun hostnameAwareCheckServerTrusted(tm: X509TrustManager): java.lang.reflect.Method? =
            try {
                tm.javaClass.getMethod(
                    "checkServerTrusted",
                    Array<X509Certificate>::class.java,
                    String::class.java,
                    String::class.java,
                )
            } catch (_: NoSuchMethodException) {
                null
            }
    }
}
