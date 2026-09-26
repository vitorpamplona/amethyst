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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.codec.TlsWriter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * The coordinators one account talks to, across launches.
 *
 * Separate from [CordnGroupStore] and [CordnKeyPackageStore] because its scope
 * is different: those are per (account, coordinator) — a `gid` and a `kp_ref`
 * only mean anything relative to one coordinator — while this is the list of
 * coordinators itself, and so belongs to the account alone.
 *
 * Without it every cordn group on the device is unreachable after a relaunch.
 * The MLS state survives on disk, but nothing knows which coordinator to ask
 * for its stream, and a `gid` with no coordinator is not a group anyone can
 * open. That is why this is storage rather than a preference.
 *
 * **Encrypt it at rest.** The contents are not secret the way key material is,
 * but they are the sharpest metadata cordn produces about this account: which
 * coordinators it uses is exactly what §8 spends its length keeping a
 * coordinator from learning about *other* coordinators.
 */
interface CordnCoordinatorStore {
    suspend fun save(configs: List<CoordinatorConfig>)

    suspend fun load(): List<CoordinatorConfig>
}

/** For tests and for a front end that deliberately keeps nothing. */
class InMemoryCordnCoordinatorStore(
    initial: List<CoordinatorConfig> = emptyList(),
) : CordnCoordinatorStore {
    private var configs = initial

    override suspend fun save(configs: List<CoordinatorConfig>) {
        this.configs = configs
    }

    override suspend fun load(): List<CoordinatorConfig> = configs
}

/**
 * The on-disk layout of a coordinator list.
 *
 * TLS-framed like everything else that gets persisted here, for the reason
 * [com.vitorpamplona.quartz.mls.messages.KeyPackageBundleCodec] gives: a
 * length-prefixed format cannot silently mis-parse a label containing whatever
 * separator a line-based one picked, and a version prefix means a future layout
 * change is refused rather than guessed at.
 */
object CoordinatorListCodec {
    const val VERSION = 1

    fun encode(configs: List<CoordinatorConfig>): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(VERSION)
        writer.putUint16(configs.size)
        configs.forEach { config ->
            writer.putOpaque2(config.pubKey.encodeToByteArray())
            writer.putOpaque2(config.origin.name.encodeToByteArray())
            writer.putOpaque2(config.label.orEmpty().encodeToByteArray())
            writer.putUint16(config.relays.size)
            config.relays.forEach { writer.putOpaque2(it.url.encodeToByteArray()) }
        }
        return writer.toByteArray()
    }

    /**
     * Reads a list back, dropping entries that no longer make sense.
     *
     * A coordinator whose relays all fail to normalise, or whose `origin` this
     * build does not know, is skipped rather than throwing: one unreadable
     * entry must not take the rest of the account's coordinators with it, and
     * a coordinator the app cannot reach is not better represented by a crash
     * at login.
     */
    fun decode(bytes: ByteArray): List<CoordinatorConfig> {
        val reader = TlsReader(bytes)
        val version = reader.readUint16()
        require(version == VERSION) { "unknown coordinator list layout version $version" }

        val count = reader.readUint16()
        val out = mutableListOf<CoordinatorConfig>()
        repeat(count) {
            val pubKey = reader.readOpaque2().decodeToString()
            val originName = reader.readOpaque2().decodeToString()
            val label = reader.readOpaque2().decodeToString()
            val relayCount = reader.readUint16()
            val relays = (0 until relayCount).mapNotNull { RelayUrlNormalizer.normalizeOrNull(reader.readOpaque2().decodeToString()) }

            val origin = CoordinatorConfig.Origin.entries.firstOrNull { it.name == originName }
            if (relays.isNotEmpty() && origin != null) {
                out += CoordinatorConfig(pubKey, relays, origin, label.ifEmpty { null })
            }
        }
        return out
    }
}
