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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordChannelRekey
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.StreamWrapOpener
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

/**
 * The channel-rekey drain judges every buffered rekey wrap once per held Private Channel on every
 * revision tick (ConcordPrivateChannels.receive). Measures that loop with every wrap opened afresh
 * against [ConcordWrapOpenCache], which opens each wrap once.
 */
class ConcordWrapOpenCacheBenchmarkTest {
    private val channels = 8
    private val ticks = 20

    @Test
    fun theDrainOpensEachWrapOnceInsteadOfOncePerChannelPerTick() =
        runBlocking {
            val admin = NostrSignerInternal(KeyPair())
            val member = NostrSignerInternal(KeyPair())
            val root = ByteArray(32) { 0x21 }
            val ids = List(channels) { i -> ByteArray(32) { (i + 1).toByte() } }
            val wraps = ArrayList<Event>()
            val keys = HashMap<HexKey, GroupKey>()
            for (id in ids) {
                wraps += ConcordChannelRekey.build(admin, root, id, ByteArray(32) { 0x10 }, 0, ByteArray(32) { 0x77 }, listOf(member.pubKey, admin.pubKey), 1_700_000_000L)
                ConcordChannelRekey.address(root, id, 1).let { keys[it.publicKeyHex] = it }
            }

            var direct = 0
            val counting = StreamWrapOpener { wrap, stream -> direct++.let { StreamWrapOpener.DIRECT.openOrNull(wrap, stream) } }
            val cache = ConcordWrapOpenCache()

            fun drain(open: StreamWrapOpener) = repeat(ticks) { ids.forEach { assertEquals(1, ConcordChannelRekey.rotations(wraps, keys, it.toHexKey(), open).size) } }

            val before = measureTime { drain(counting) }
            val after = measureTime { drain(cache) }
            println("Concord rekey drain, $channels channels x $ticks ticks: $direct opens in $before before, ${cache.opens()} opens in $after with the cache")

            assertEquals(channels * channels * ticks, direct)
            assertEquals(channels, cache.opens())
        }
}
