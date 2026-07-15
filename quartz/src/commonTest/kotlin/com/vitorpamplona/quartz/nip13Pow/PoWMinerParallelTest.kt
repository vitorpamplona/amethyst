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
package com.vitorpamplona.quartz.nip13Pow

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class PoWMinerParallelTest {
    val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    val baseTemplate =
        EventTemplate<TextNoteEvent>(
            1683596206,
            TextNoteEvent.KIND,
            emptyArray(),
            "A note to mine",
        )

    @Test
    fun parallelMinerFindsAValidPoW() =
        runTest {
            val desiredPoW = 12
            val mined = PoWMiner.mine(baseTemplate, pubKey, desiredPoW, workers = 4)

            val powTag = mined.tags.firstNotNullOfOrNull { PoWTag.parse(it) }
            assertNotNull(powTag, "mined template must carry a nonce tag")

            val id =
                sha256(
                    EventHasherSerializer.fastMakeJsonForId(
                        pubKey = pubKey,
                        createdAt = mined.createdAt,
                        kind = mined.kind,
                        tags = mined.tags,
                        content = mined.content,
                    ),
                )

            assertTrue(
                PoWRankEvaluator.atLeastPowRank(id, desiredPoW, desiredPoW / 8),
                "mined id must reach the desired PoW",
            )
        }

    @Test
    fun cancellationAbortsAllParallelWorkers() =
        runTest {
            val start = TimeSource.Monotonic.markNow()
            // 256 bits never completes; only the isActive deadline can end the run.
            assertFailsWith<CancellationException> {
                PoWMiner.mine(baseTemplate, pubKey, 256, workers = 4) {
                    start.elapsedNow() < 150.milliseconds
                }
            }
            assertTrue(
                start.elapsedNow() < 5_000.milliseconds,
                "all workers must stop shortly after isActive flips, took ${start.elapsedNow()}",
            )
        }
}
