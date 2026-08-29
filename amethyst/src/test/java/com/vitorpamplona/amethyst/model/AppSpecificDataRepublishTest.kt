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
package com.vitorpamplona.amethyst.model

import android.os.Looper
import com.vitorpamplona.amethyst.model.nip78AppSpecific.AppSpecificState
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The settings pickers (Side Menu, bottom bar) republish the NIP-78 app-specific data event on every
 * discrete toggle, and `created_at` is whole seconds. These pin the rule that keeps two toggles a few
 * hundred milliseconds apart from collapsing into one — the failure the user sees as "my side-menu
 * rows came back after a restart".
 */
class AppSpecificDataRepublishTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val dTag = "AmethystSettingsRepublishTest"

    private val address get() = AppSpecificDataEvent.createAddress(signer.pubKey, dTag)

    @Before
    fun setup() {
        // LocalCache.consume refuses the main thread; plain JVM tests have no Looper,
        // where null == null reads as "main". Distinct mocks make it a worker thread.
        mockkStatic(Looper::class)
        every { Looper.myLooper() } returns mockk<Looper>()
        every { Looper.getMainLooper() } returns mockk<Looper>()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
    }

    @Test
    fun theWallClockAloneLosesAnEditMadeInTheSameSecond() =
        runBlocking {
            // Why the fix has to exist: LocalCache keeps the strictly newest version per address, so
            // a same-second republish never reaches the collector that persists it.
            val sameSecond = TimeUtils.now()

            LocalCache.justConsumeMyOwnEvent(signer.sign(AppSpecificDataEvent.build(dTag, "first", createdAt = sameSecond)))
            LocalCache.justConsumeMyOwnEvent(signer.sign(AppSpecificDataEvent.build(dTag, "second", createdAt = sameSecond)))

            assertEquals("first", LocalCache.getOrCreateAddressableNote(address).event?.content)
        }

    @Test
    fun aStampedRepublishInTheSameSecondLands() =
        runBlocking {
            val sameSecond = TimeUtils.now()

            val first = signer.sign(AppSpecificDataEvent.build(dTag, "first", createdAt = AppSpecificState.nextCreatedAt(0L, sameSecond)))
            LocalCache.justConsumeMyOwnEvent(first)

            val second =
                signer.sign(
                    AppSpecificDataEvent.build(
                        dTag,
                        "second",
                        createdAt = AppSpecificState.nextCreatedAt(first.createdAt, sameSecond),
                    ),
                )
            LocalCache.justConsumeMyOwnEvent(second)

            assertEquals("second", LocalCache.getOrCreateAddressableNote(address).event?.content)
        }

    @Test
    fun theWallClockWinsOnceItHasMovedPastTheLastVersion() {
        // The common case — edits minutes apart carry the real time, not a drifting counter.
        assertEquals(1_000L, AppSpecificState.nextCreatedAt(newestKnown = 900L, now = 1_000L))
    }

    @Test
    fun aBurstStepsOneSecondPerEdit() {
        assertEquals(1_001L, AppSpecificState.nextCreatedAt(newestKnown = 1_000L, now = 1_000L))
        assertEquals(1_002L, AppSpecificState.nextCreatedAt(newestKnown = 1_001L, now = 1_000L))
    }

    @Test
    fun aVersionStampedInTheFutureIsStillSuperseded() {
        // A clock skew on another device (or our own burst) can leave the newest known version ahead
        // of this device's clock. Going back to `now` there would publish an event the cache drops.
        assertEquals(9_001L, AppSpecificState.nextCreatedAt(newestKnown = 9_000L, now = 1_000L))
    }
}
