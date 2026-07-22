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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.UserFinderQueryState
import com.vitorpamplona.quartz.nip01Core.core.Address
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class AddressableAuthorRelayLoaderSubAssemblerTest {
    /**
     * Unique per-test-class identities so the shared [LocalCache] singleton isn't polluted with
     * notes another test class also claims. The prefix keeps the key a valid 64-char hex pubkey.
     */
    private fun stubKeys(count: Int): Set<EventFinderQueryState> {
        val account = mockk<Account>()
        return (1..count).mapTo(mutableSetOf()) { i ->
            val address = Address(30023, "ad04%060x".format(i), "d$i")
            EventFinderQueryState(LocalCache.getOrCreateAddressableNoteInternal(address), account)
        }
    }

    /**
     * Regression test for the `ConcurrentModificationException` in
     * `SetsKt.minus` reported from `DefaultDispatcher-worker-70`.
     *
     * `ComposeSubscriptionManager.subscribe`/`unsubscribe` call `invalidateKeys()` *after*
     * releasing their own lock, and `LifecycleAwareSubscription`'s 30s grace-period unsubscribe
     * fires on a `Dispatchers.Default` worker — so this manager is genuinely re-entered from
     * several threads at once.
     *
     * The invariant asserted here is the one that makes the crash impossible: **the body that
     * reads and swaps the subscription state never runs concurrently with itself.** It's checked
     * via the injected `allKeys()` lambda (called exactly once per body) rather than by catching
     * the exception, because once the body is bundled its throwables are swallowed by
     * `BundledUpdate`'s `CoroutineExceptionHandler` and would never reach the test thread.
     */
    @Test
    fun concurrentInvalidateFiltersNeverOverlap() {
        val errors = CopyOnWriteArrayList<Throwable>()
        val userFinder = mockk<UserFinderFilterAssembler>(relaxed = true)
        val keys = stubKeys(50)

        val inFlight = AtomicInteger(0)
        val assembler =
            AddressableAuthorRelayLoaderSubAssembler(
                LocalCache,
                {
                    if (inFlight.incrementAndGet() > 1) {
                        errors.add(IllegalStateException("forceInvalidate bodies overlapped"))
                    }
                    try {
                        keys
                    } finally {
                        inFlight.decrementAndGet()
                    }
                },
                userFinder,
            )

        val start = CountDownLatch(1)
        try {
            val threads =
                (1..8).map {
                    thread(start = false) {
                        start.await()
                        repeat(500) {
                            try {
                                assembler.invalidateFilters()
                            } catch (t: Throwable) {
                                errors.add(t)
                            }
                        }
                    }
                }
            threads.forEach { it.start() }
            start.countDown()
            threads.forEach { it.join() }
        } finally {
            assembler.destroy()
        }

        assertTrue(
            "invalidateFilters raced under concurrency: " +
                errors.map { "${it::class.simpleName}: ${it.message}" }.distinct(),
            errors.isEmpty(),
        )
    }

    /** The manager still does its job: unresolved stub authors reach the user finder. */
    @Test
    fun bridgesMissingAuthorsIntoUserFinder() {
        val userFinder = mockk<UserFinderFilterAssembler>(relaxed = true)
        val keys = stubKeys(3)
        val assembler = AddressableAuthorRelayLoaderSubAssembler(LocalCache, { keys }, userFinder)

        try {
            assembler.invalidateFilters()

            verify(timeout = 3000) {
                userFinder.subscribe(
                    match<List<UserFinderQueryState>> { it.size == 3 },
                )
            }
        } finally {
            assembler.destroy()
        }
    }

    /**
     * `bundler.cancel()` cannot stop a body that is already executing — the body has no
     * suspension points, so it runs to completion after `destroy()` returns. Without the
     * `destroyed` handshake the body re-subscribes authors that `destroy()` just released,
     * leaving live kind-0/10002 REQs (and retained `User`/`Account` references) for a dead
     * account after logout.
     *
     * The body is gated inside the injected `allKeys()` lambda so `destroy()` provably runs
     * underneath an in-flight run rather than racing it by luck.
     */
    @Test
    fun destroyDuringInFlightInvalidateDoesNotLeakSubscriptions() {
        val userFinder = mockk<UserFinderFilterAssembler>(relaxed = true)
        val subscribed = CopyOnWriteArrayList<UserFinderQueryState>()
        val unsubscribed = CopyOnWriteArrayList<UserFinderQueryState>()
        val subscribeHappened = CountDownLatch(1)
        every { userFinder.subscribe(any<List<UserFinderQueryState>>()) } answers {
            subscribed.addAll(firstArg<List<UserFinderQueryState>>())
            subscribeHappened.countDown()
        }
        every { userFinder.unsubscribe(any<List<UserFinderQueryState>>()) } answers {
            unsubscribed.addAll(firstArg<List<UserFinderQueryState>>())
        }

        val keys = stubKeys(3)
        val invalidateEntered = CountDownLatch(1)
        val destroyFinished = CountDownLatch(1)
        val assembler =
            AddressableAuthorRelayLoaderSubAssembler(
                LocalCache,
                {
                    invalidateEntered.countDown()
                    destroyFinished.await(5, TimeUnit.SECONDS)
                    keys
                },
                userFinder,
            )

        try {
            assembler.invalidateFilters()
            assertTrue("bundled run never started", invalidateEntered.await(5, TimeUnit.SECONDS))
        } finally {
            assembler.destroy()
            destroyFinished.countDown()
        }

        // Let the in-flight run finish (it either subscribes — the leak — or
        // observes the teardown and skips; both settle within the timeout).
        subscribeHappened.await(2, TimeUnit.SECONDS)

        val leaked = subscribed - unsubscribed.toSet()
        assertTrue(
            "destroy() left ${leaked.size} subscriptions alive in userFinder",
            leaked.isEmpty(),
        )
    }
}
