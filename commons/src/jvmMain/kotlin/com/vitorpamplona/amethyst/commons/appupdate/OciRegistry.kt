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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest

/**
 * Reads a Flatpak image's manifest from an OCI registry (anonymously, as a public ghcr package
 * allows) and checks it is the release's: its bytes hash to the digest the registry names, and
 * its Flatpak annotations say this app, this architecture and this version. The digest is what the
 * signed asset pins, so a tag moved later changes nothing.
 */
class OciRegistry(
    private val client: OkHttpClient = OkHttpClient(),
    /** `http` only for a test registry on localhost. */
    private val scheme: String = "https",
) {
    /** The image at `<repository>:<reference>` (a tag or a digest), checked against [platform] and [version]. */
    suspend fun flatpakImage(
        repository: String,
        reference: String,
        platform: String,
        version: String,
    ): FlatpakImage =
        withContext(Dispatchers.IO) {
            val manifest = fetchManifest(repository, reference)
            val digest = "sha256:" + MessageDigest.getInstance("SHA-256").digest(manifest).joinToString("") { "%02x".format(it) }
            if (reference.startsWith("sha256:") && reference != digest) {
                throw ReleaseFilesException("$repository@$reference serves a manifest that hashes to $digest")
            }
            checkedImage(manifest.decodeToString(), digest, repository, platform, version)
        }

    private fun fetchManifest(
        repository: String,
        reference: String,
    ): ByteArray {
        val host = repository.substringBefore('/')
        val name = repository.substringAfter('/')
        val url = "$scheme://$host/v2/$name/manifests/$reference"
        val first = get(url, null)
        val response =
            if (first.code == 401) {
                val challenge = first.header("WWW-Authenticate").orEmpty()
                first.close()
                get(url, bearerToken(challenge))
            } else {
                first
            }
        response.use {
            if (!it.isSuccessful) throw ReleaseFilesException("$repository:$reference: HTTP ${it.code}")
            val bytes = it.body.bytes()
            it.header("Docker-Content-Digest")?.let { named ->
                val actual = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { b -> "%02x".format(b) }
                if (named != actual) throw ReleaseFilesException("$repository:$reference: the registry names $named, the bytes hash to $actual")
            }
            return bytes
        }
    }

    private fun get(
        url: String,
        token: String?,
    ): Response =
        client
            .newCall(
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/vnd.oci.image.manifest.v1+json")
                    .apply { token?.let { header("Authorization", "Bearer $it") } }
                    .build(),
            ).execute()

    /** The anonymous pull token a `Bearer realm=…,service=…,scope=…` challenge points to. */
    private fun bearerToken(challenge: String): String {
        val params = Regex("""(\w+)="([^"]*)"""").findAll(challenge).associate { it.groupValues[1] to it.groupValues[2] }
        val realm = params["realm"] ?: throw ReleaseFilesException("Registry asks for an unknown authentication: $challenge")
        val query = listOfNotNull(params["service"]?.let { "service=$it" }, params["scope"]?.let { "scope=$it" }).joinToString("&")
        client.newCall(Request.Builder().url(if (query.isEmpty()) realm else "$realm?$query").build()).execute().use {
            if (!it.isSuccessful) throw ReleaseFilesException("Registry token: HTTP ${it.code}")
            val json = Json.parseToJsonElement(it.body.string()).jsonObject
            return (json["token"] ?: json["access_token"])?.jsonPrimitive?.content ?: throw ReleaseFilesException("Registry token missing")
        }
    }

    companion object {
        /** Checks a manifest's Flatpak annotations (orchard-publish copies the config labels there). */
        fun checkedImage(
            manifestJson: String,
            digest: String,
            repository: String,
            platform: String,
            version: String,
        ): FlatpakImage {
            val annotations =
                Json
                    .parseToJsonElement(manifestJson)
                    .jsonObject["annotations"]
                    ?.jsonObject
                    ?.mapValues { it.value.jsonPrimitive.content }
                    .orEmpty()
            val ref = annotations["org.flatpak.ref"]
            val arch = FlatpakRelease.refArch(platform)
            if (ref == null || !ref.startsWith("app/${FlatpakRelease.APP_ID}/$arch/")) {
                throw ReleaseFilesException("$repository@$digest is $ref, not ${FlatpakRelease.APP_ID} for $arch")
            }
            val subject = annotations["org.flatpak.subject"]
            if (subject != "${FlatpakRelease.APP_ID} $version") {
                throw ReleaseFilesException("$repository@$digest is \"$subject\", not version $version")
            }
            val runtime =
                annotations["org.flatpak.metadata"]
                    ?.lineSequence()
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("runtime=") }
                    ?.substringAfter('=')
            return FlatpakImage(platform, version, digest, repository, runtime)
        }
    }
}
