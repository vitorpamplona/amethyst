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
package com.vitorpamplona.amethyst.commons.model.nip03Timestamp

import com.vitorpamplona.quartz.nip03Timestamp.BitcoinExplorerUrls
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolverBuilder
import com.vitorpamplona.quartz.nip03Timestamp.ots.BitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.ots.BlockHeader
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import com.vitorpamplona.quartz.nip03Timestamp.ots.RemoteCalendar
import com.vitorpamplona.quartz.nip03Timestamp.ots.StreamDeserializationContext
import com.vitorpamplona.quartz.nip03Timestamp.ots.Timestamp
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.CommitmentNotFoundException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.DeserializationException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.ExceededSizeException
import com.vitorpamplona.quartz.nip03Timestamp.ots.exceptions.UrlException
import com.vitorpamplona.quartz.utils.Hex
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// OpenTimestamps over Ktor for the targets without OkHttp: quartz's OkHttpBitcoinExplorer and
// OkHttpCalendar, call for call, and the Tor-aware builder the JVM apps use. Candidates to move into
// quartz beside the OkHttp ones once it carries a Ktor client.

/** Block headers and heights from an Esplora explorer (mempool.space, blockstream.info), cached. */
class KtorBitcoinExplorer(
    val baseUrl: () -> String,
    val client: HttpClient,
    val cache: OtsBlockHeightCache,
) : BitcoinExplorer {
    override suspend fun block(hash: String): BlockHeader {
        cache.getHeader(hash)?.let { return it }
        val url = "${baseUrl()}/block/$hash"
        val response = client.get(url) { header("Accept", "application/json") }
        if (!response.status.isSuccess()) throw UrlException("Couldn't open $url: ${response.status}")
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val merkleRoot = json["merkle_root"]?.jsonPrimitive?.content ?: error("Block $hash has no merkle_root")
        val time = (json["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L).toInt().toString()
        return BlockHeader(merkleRoot, hash, time).also { cache.putHeader(hash, it) }
    }

    override suspend fun blockHash(height: Int): String {
        cache.getHeight(height)?.let { return it }
        val url = "${baseUrl()}/block-height/$height"
        val response = client.get(url)
        if (!response.status.isSuccess()) throw UrlException("Couldn't open $url: ${response.status}")
        return response.bodyAsText().also { cache.putHeight(height, it) }
    }
}

/** An OpenTimestamps calendar server: submits digests and fetches upgraded timestamps. */
class KtorCalendar(
    val client: (url: String) -> HttpClient,
) : RemoteCalendar {
    override suspend fun submit(
        url: String,
        digest: ByteArray,
    ): Timestamp {
        val digestUrl = "$url/digest"
        return try {
            val response =
                client(digestUrl).post(digestUrl) {
                    header("Accept", "application/vnd.opentimestamps.v1")
                    header("Content-Type", "application/x-www-form-urlencoded")
                    setBody(digest)
                }
            if (!response.status.isSuccess()) throw UrlException("Failed to open $digestUrl")
            Timestamp.deserialize(StreamDeserializationContext(response.bodyAsBytes()), digest)
        } catch (e: UrlException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An unreachable calendar (or Tor proxy) is the UrlException the RemoteCalendar contract promises.
            throw UrlException("Failed to open $digestUrl: ${e.message}")
        }
    }

    override suspend fun getTimestamp(
        url: String,
        commitment: ByteArray,
    ): Timestamp {
        val timestampUrl = url + "/timestamp/" + Hex.encode(commitment)
        return try {
            val response =
                client(timestampUrl).get(timestampUrl) {
                    header("Accept", "application/vnd.opentimestamps.v1")
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
            if (!response.status.isSuccess()) throw CommitmentNotFoundException("Calendar response a status code != 200: ${response.status.value}")
            Timestamp.deserialize(StreamDeserializationContext(response.bodyAsBytes()), commitment)
        } catch (e: DeserializationException) {
            throw e
        } catch (e: ExceededSizeException) {
            throw e
        } catch (e: CommitmentNotFoundException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UrlException(e.message)
        }
    }
}

/**
 * The JVM's TorAwareOkHttpOtsResolverBuilder over Ktor: mempool.space when money goes through Tor
 * (it takes Tor), blockstream.info when not, or the user's own explorer; all over the money role.
 */
class TorAwareKtorOtsResolverBuilder(
    val client: (url: String) -> HttpClient,
    val isTorActive: (url: String) -> Boolean,
    val cache: OtsBlockHeightCache,
    val customExplorerUrl: () -> String? = { null },
) : OtsResolverBuilder {
    override fun build(): OtsResolver =
        OtsResolver(
            explorer =
                KtorBitcoinExplorer(
                    baseUrl = { BitcoinExplorerEndpoint.resolve(customExplorerUrl(), isTorActive(BitcoinExplorerUrls.MEMPOOL)) },
                    client = client(BitcoinExplorerUrls.MEMPOOL),
                    cache = cache,
                ),
            calendar = KtorCalendar(client),
        )
}
