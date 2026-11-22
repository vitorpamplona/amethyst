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
package com.vitorpamplona.amethyst.commons.mavenReleases

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05FetchException
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NIP-FC's trust rule: a group's releases count only when signed by the key its domain names as
 * `releases` in NIP-05. The groupId reversed is a host (`com.vitorpamplona.crypto` →
 * `crypto.vitorpamplona.com`), walked down to its domain (`vitorpamplona.com`); the first host that
 * lists `releases` gives the key.
 *
 * NIP-FC's resolvers stop at a host they can't read, so an outage never hands a group to its parent
 * domain's key. A card can't tell an outage from a subdomain that doesn't exist: behind a proxy or
 * Tor, a missing name fails like an unreachable one, and most groups (`com.vitorpamplona.crypto`)
 * have no subdomain at all. So the walk goes on past an unreadable host and remembers it
 * ([Found.skipped]): a parent that names the signer is still reported, with the domain that vouched,
 * but a parent that names another key is no proof of an impostor, since the real key may sit on
 * the host that couldn't be read.
 */
@Immutable
sealed interface ReleaseKeyLookup {
    /** [domain] names [key] as the group's release key; [skipped] is a more specific host that couldn't be read. */
    data class Found(
        val domain: String,
        val key: HexKey,
        val skipped: String? = null,
    ) : ReleaseKeyLookup

    /** No host on the walk names a release key; [domain] is the last one asked. */
    data class None(
        val domain: String,
    ) : ReleaseKeyLookup

    /** [domain] could not be read, and no host after it names a key. */
    data class Unreachable(
        val domain: String,
    ) : ReleaseKeyLookup

    /** The groupId is not a reversed domain (`_` in a segment, one segment): a key must be pinned. */
    data object NotADomain : ReleaseKeyLookup
}

/** What the lookup says about one event's signer. */
enum class ReleaseKeyVerdict {
    /** Signed by the key the group's domain names. */
    VERIFIED,

    /** The domain names another key: builds that trust the domain ignore this event. */
    OTHER_KEY,

    /** The domain names no release key, or the group is no domain: only a pinned key can use it. */
    NO_KEY,

    /** A host couldn't be read, so whether the signer is the group's key is unknown. */
    UNKNOWN,
}

object MavenReleaseKeys {
    private const val NAME = "releases"
    private const val TTL_SECONDS = 60L * 60
    const val RETRY_SECONDS = 60L
    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")

    /**
     * The hosts to ask, most specific first: the groupId reversed, then each parent down to the
     * last two labels. Without the public suffix list a group under a multi-label suffix
     * (`uk.co.example`) also asks the suffix (`co.uk`), which names no release key.
     */
    fun hostsFor(groupId: String): List<String> {
        val labels = groupId.lowercase().split('.').asReversed()
        if (labels.size < 2 || labels.any { !LABEL.matches(it) }) return emptyList()
        return (labels.size downTo 2).map { n -> labels.takeLast(n).joinToString(".") }
    }

    fun verdict(
        lookup: ReleaseKeyLookup,
        signer: HexKey,
    ): ReleaseKeyVerdict =
        when (lookup) {
            is ReleaseKeyLookup.Found -> {
                when {
                    lookup.key.equals(signer, ignoreCase = true) -> ReleaseKeyVerdict.VERIFIED
                    lookup.skipped != null -> ReleaseKeyVerdict.UNKNOWN
                    else -> ReleaseKeyVerdict.OTHER_KEY
                }
            }

            is ReleaseKeyLookup.None, ReleaseKeyLookup.NotADomain -> ReleaseKeyVerdict.NO_KEY
            is ReleaseKeyLookup.Unreachable -> ReleaseKeyVerdict.UNKNOWN
        }

    /** Walks [hostsFor] with [client]. */
    suspend fun lookup(
        groupId: String,
        client: INip05Client,
    ): ReleaseKeyLookup {
        val hosts = hostsFor(groupId)
        if (hosts.isEmpty()) return ReleaseKeyLookup.NotADomain
        var skipped: String? = null
        for (host in hosts) {
            val key =
                try {
                    client.get(Nip05Id(NAME, host))?.pubkey
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (skipped == null && !isDefinitelyAbsent(e)) skipped = host
                    null
                }
            if (key != null) return ReleaseKeyLookup.Found(host, key.lowercase(), skipped)
        }
        return skipped?.let { ReleaseKeyLookup.Unreachable(it) } ?: ReleaseKeyLookup.None(hosts.last())
    }

    private const val MAX_CACHED_GROUPS = 256

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val cache = HashMap<String, Pair<Long, ReleaseKeyLookup>>()
    private val inFlight = HashMap<String, Deferred<ReleaseKeyLookup>>()

    /** Whether [lookup] could not read a host, so asking again later may answer differently. */
    fun isPartial(lookup: ReleaseKeyLookup) = lookup is ReleaseKeyLookup.Unreachable || (lookup is ReleaseKeyLookup.Found && lookup.skipped != null)

    /**
     * [lookup], remembered per group for an hour (a minute when a host couldn't be read). Cards of
     * the same group asking at once share one walk, which runs on its own so a card scrolling away
     * does not cancel it for the others.
     */
    suspend fun cachedLookup(
        groupId: String,
        client: INip05Client,
    ): ReleaseKeyLookup {
        val running =
            mutex.withLock {
                cache[groupId]?.let { (until, result) -> if (until > TimeUtils.now()) return result }
                inFlight.getOrPut(groupId) {
                    scope.async {
                        // lookup() already turns a failed fetch into a verdict; this keeps anything
                        // else from leaving the group stuck in [inFlight].
                        val result =
                            try {
                                lookup(groupId, client)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                ReleaseKeyLookup.Unreachable(hostsFor(groupId).firstOrNull() ?: groupId)
                            }
                        val ttl = if (isPartial(result)) RETRY_SECONDS else TTL_SECONDS
                        mutex.withLock {
                            if (cache.size >= MAX_CACHED_GROUPS) cache.clear()
                            cache[groupId] = (TimeUtils.now() + ttl) to result
                            inFlight.remove(groupId)
                        }
                        result
                    }
                }
            }
        return running.await()
    }

    /**
     * Whether a failed fetch says the host has no document: a 404 or 410, or a name that doesn't
     * resolve, which the JVM fetcher reports as an `UnknownHostException` cause that common code
     * can only recognise by name. Anything else (a timeout, a 5xx, a proxy's refusal) is a host
     * that couldn't be read.
     */
    internal fun isDefinitelyAbsent(error: Throwable): Boolean {
        var e: Throwable? = error
        while (e != null) {
            if (e is Nip05FetchException.Status) return e.code == 404 || e.code == 410
            if (e::class.simpleName == "UnknownHostException") return true
            if (e.cause === e) break
            e = e.cause
        }
        return false
    }
}
