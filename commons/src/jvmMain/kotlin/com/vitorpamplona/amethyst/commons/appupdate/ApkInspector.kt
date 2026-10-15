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
package com.vitorpamplona.amethyst.commons.appupdate

import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkUtils
import com.android.apksig.internal.apk.AndroidBinXmlParser
import com.android.apksig.util.DataSources
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Reads what a release needs from an APK, with Google's apksig: verifies its signature (v1 to v3,
 * as Android would) and takes the signing certificate's SHA-256, the package name, version and SDK
 * range from the binary manifest, and the native ABIs from `lib/`. zsp does the same with Go
 * libraries; an APK that does not verify is refused, as there.
 */
object ApkInspector {
    /** `android:versionName`, which apksig's public API does not read. */
    private const val ATTR_VERSION_NAME = 0x0101021c

    class InvalidApkException(
        message: String,
    ) : Exception(message)

    fun inspect(apk: File): ApkInfo {
        val result = ApkVerifier.Builder(apk).build().verify()
        if (!result.isVerified) {
            throw InvalidApkException("${apk.name} does not verify: ${result.errors.joinToString("; ")}")
        }
        // The highest scheme's signer; with key rotation (v3) that is the current key, as zsp picks.
        val certificate = result.signerCertificates.firstOrNull() ?: throw InvalidApkException("${apk.name} has no signer certificate")

        val manifest =
            RandomAccessFile(apk, "r").use { file ->
                ApkUtils.getAndroidManifest(DataSources.asDataSource(file))
            }

        return ApkInfo(
            packageName = ApkUtils.getPackageNameFromBinaryAndroidManifest(manifest.duplicate()),
            versionName = versionName(manifest.duplicate()),
            versionCode = ApkUtils.getLongVersionCodeFromBinaryAndroidManifest(manifest.duplicate()),
            minSdk = ApkUtils.getMinSdkVersionFromBinaryAndroidManifest(manifest.duplicate()),
            targetSdk = ApkUtils.getTargetSdkVersionFromBinaryAndroidManifest(manifest.duplicate()),
            nativeAbis = nativeAbis(apk),
            certificateSha256 = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).toHex(),
        )
    }

    /** The `android:versionName` of the root `<manifest>` element, when it is a literal string. */
    private fun versionName(manifest: ByteBuffer): String? {
        val parser = AndroidBinXmlParser(manifest)
        while (parser.next() != AndroidBinXmlParser.EVENT_END_DOCUMENT) {
            if (parser.eventType == AndroidBinXmlParser.EVENT_START_ELEMENT && parser.depth == 1 && parser.name == "manifest") {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeNameResourceId(i) == ATTR_VERSION_NAME &&
                        parser.getAttributeValueType(i) == AndroidBinXmlParser.VALUE_TYPE_STRING
                    ) {
                        return parser.getAttributeStringValue(i)
                    }
                }
                return null
            }
        }
        return null
    }

    /** The ABI folders under `lib/`, sorted. */
    private fun nativeAbis(apk: File): List<String> =
        ZipFile(apk).use { zip ->
            zip
                .entries()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && !it.contains("..") }
                .mapNotNull { it.split('/').getOrNull(1)?.takeIf(String::isNotEmpty) }
                .toSortedSet()
                .toList()
        }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
