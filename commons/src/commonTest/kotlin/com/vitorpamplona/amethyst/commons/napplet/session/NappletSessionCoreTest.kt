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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletIncBus
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [NappletSessionCore] against a [FakeNappletWebEngine]: lifecycle, guards, bridge, attendance, teardown. */
class NappletSessionCoreTest {
    private val author = "aa".repeat(32)
    private val account = "bb".repeat(32)
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))
    private val shellTemplate = "<html><head></head><body><script>var appOrigin='__APP_ORIGIN__';__APP_BOOTSTRAP__</script></body></html>".encodeToByteArray()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attendance = NappletAttendance<String>()
    private val services = NappletSessionServices(attendance, NappletLiveSubscriptions(scope, attendance), NappletIncBus<String>()) { null }

    @AfterTest
    fun tearDown() = scope.cancel()

    private class Prompt : NappletConsentPrompt {
        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState = GrantState.ALLOW_ONCE
    }

    private class Relay : NappletRelayGateway {
        override suspend fun publish(event: Event): List<String> = emptyList()

        override suspend fun query(filters: List<Filter>): List<Event> = emptyList()
    }

    private fun broker() = NappletBroker(signer, NappletPermissionLedger(InMemoryNappletPermissionStore()), Prompt(), relay = Relay())

    private class Opened(
        val core: NappletSessionCore<String>,
        val engine: FakeNappletWebEngine,
        val route: FakeRoute,
        val files: FakeFiles,
        val links: FakeLinks,
    )

    private fun open(
        launch: NappletLaunch = TestLaunches.napplet(author),
        useTor: Boolean = false,
        appletBlobsUseTor: Boolean = false,
        engine: FakeNappletWebEngine = FakeNappletWebEngine(),
        route: FakeRoute = FakeRoute(),
        files: FakeFiles = FakeFiles(),
        attended: Boolean = true,
        owner: String = "surface",
    ): Opened {
        val links = FakeLinks()
        val core =
            NappletSessionCore(
                owner = owner,
                launch = launch,
                accountPubKey = account,
                plan = NappletLaunchPlan.of(launch, account, useTor, appletBlobsUseTor, sharedStorageJar = true),
                account = null,
                broker = broker(),
                services = services,
                route = route,
                files = { files },
                shellHtml = { shellTemplate },
                shimJs = { "/*shim*/" },
                links = links,
                filePort = null,
                attendedWhenShown = attended,
            ).also { it.engine = engine }
        return Opened(core, engine, route, files, links)
    }

    private suspend fun NappletSessionCore<String>.settle(): NappletSessionState =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { state.first { it is NappletSessionState.Showing || it is NappletSessionState.Failed } }
        }

    private fun idOf(payload: String) =
        Json
            .parseToJsonElement(payload)
            .jsonObject["id"]
            ?.jsonPrimitive
            ?.content

    @Test
    fun aNappletOpensBehindItsGuardsAndTheShellRefusesWithoutThem() =
        runTest {
            val opened = open()
            opened.core.start()
            assertEquals(NappletSessionState.Showing, opened.core.settle())
            assertEquals(listOf(NappletWebContract.SHELL_URL), opened.engine.loaded)
            assertEquals(setOf(NappletGuard.WEBRTC, NappletGuard.COOKIES), opened.engine.installed.toSet())
            val shell =
                opened.core
                    .serve("GET", NappletWebContract.SHELL_URL, acceptsHtml = true, mainFrameNavigation = true)!!
                    .readBody()
                    .decodeToString()
            assertContains(shell, "<head>" + NappletWebContract.WEBRTC_CHECK + FakeNappletWebEngine.BRIDGE_HEAD)
            assertTrue(attendance.isAttended("surface"))
        }

    @Test
    fun aSiteOnTorNeedsTheGuardAndOneOnTheOpenWebDoesNot() =
        runTest {
            val tor = open(TestLaunches.site(author), useTor = true, owner = "tor")
            tor.core.start()
            assertEquals(NappletSessionState.Showing, tor.core.settle())
            assertEquals(true, tor.route.claimed)
            assertContains(
                tor.core
                    .serve("GET", NappletWebContract.SHELL_URL, true, true)!!
                    .readBody()
                    .decodeToString(),
                NappletWebContract.WEBRTC_CHECK,
            )

            val open = open(TestLaunches.site(author), useTor = false, owner = "open")
            open.core.start()
            assertEquals(NappletSessionState.Showing, open.core.settle())
            assertEquals(false, open.route.claimed)
            assertFalse(
                open.core
                    .serve("GET", NappletWebContract.SHELL_URL, true, true)!!
                    .readBody()
                    .decodeToString()
                    .contains(NappletWebContract.WEBRTC_CHECK),
            )
            assertEquals(listOf(NappletGuard.COOKIES), open.engine.installed)
        }

    @Test
    fun anEngineThatCantTakeWebRtcAwayNeverLoads() =
        runTest {
            val opened = open(engine = FakeNappletWebEngine(supported = setOf(NappletGuard.COOKIES)))
            opened.core.start()
            assertEquals(NappletSessionState.Failed(NappletFailure.ENGINE), opened.core.settle())
            assertTrue(opened.engine.loaded.isEmpty())
            // A missing cookie guard is logged, not refused.
            val noCookies = open(engine = FakeNappletWebEngine(supported = setOf(NappletGuard.WEBRTC)), owner = "other")
            noCookies.core.start()
            assertEquals(NappletSessionState.Showing, noCookies.core.settle())
        }

    @Test
    fun torOnWithoutTorFailsClosedAndNeverLoads() =
        runTest {
            // A napplet whose blobs go through Tor: it waits, then fails.
            val napplet = open(appletBlobsUseTor = true, route = FakeRoute(torRunning0 = false, awaitResult = false))
            napplet.core.start()
            assertEquals(NappletSessionState.Failed(NappletFailure.ROUTE_BLOCKED), napplet.core.settle())
            assertTrue(napplet.engine.loaded.isEmpty())

            // A site on Tor: its claim fails, and the failed window gives the route back.
            val site = open(TestLaunches.site(author), useTor = true, route = FakeRoute(torRunning0 = false, claimResult = false), owner = "site")
            site.core.start()
            assertEquals(NappletSessionState.Failed(NappletFailure.ROUTE_BLOCKED), site.core.settle())
            assertTrue(site.engine.loaded.isEmpty())
            assertEquals(1, site.route.released)
        }

    @Test
    fun torOnWithoutTorShowsTheWaitFirst() =
        runTest {
            val route = FakeRoute(torRunning0 = false)
            val tor = CompletableDeferred<Unit>()
            val waiting =
                object : NappletRoutePort by route {
                    override suspend fun awaitTor(): Boolean {
                        tor.await()
                        return true
                    }
                }
            val launch = TestLaunches.napplet(author)
            val engine = FakeNappletWebEngine()
            val core =
                NappletSessionCore("w", launch, account, NappletLaunchPlan.of(launch, account, false, true, true), null, broker(), services, waiting, { FakeFiles() }, { shellTemplate }, { "" }, FakeLinks(), null)
                    .also { it.engine = engine }
            core.start()
            withContext(Dispatchers.Default) { withTimeout(5_000) { core.state.first { it == NappletSessionState.WaitingForTor } } }
            assertTrue(engine.loaded.isEmpty())
            tor.complete(Unit)
            assertEquals(NappletSessionState.Showing, core.settle())
        }

    @Test
    fun missingFilesAreUnreachable() =
        runTest {
            val opened = open(TestLaunches.site(author), files = FakeFiles(available = false))
            opened.core.start()
            assertEquals(NappletSessionState.Failed(NappletFailure.UNREACHABLE), opened.core.settle())
            assertEquals(1, opened.route.released)
            assertTrue(opened.engine.loaded.isEmpty())
        }

    @Test
    fun aMessageFromAnyoneButTheShellIsDropped() =
        runTest {
            val opened = open()
            opened.core.start()
            opened.core.settle()
            val request = """{"type":"relay.query","id":"q1"}"""
            assertFalse(opened.core.onShellMessage(request, isMainFrame = false, frameUrl = NappletWebContract.SHELL_URL))
            assertFalse(opened.core.onShellMessage(request, isMainFrame = true, frameUrl = "https://n1.napplet.local/"))
            assertFalse(opened.core.onShellMessageFromOrigin(request, isMainFrame = true, frameOrigin = "null"))
            delay(100)
            withContext(Dispatchers.Default) { delay(200) }
            assertTrue(opened.engine.delivered().isEmpty())
        }

    @Test
    fun aReplyCarriesTheRequestsIdAndAPushDoesNot() =
        runTest {
            val opened = open()
            opened.core.start()
            opened.core.settle()
            assertTrue(opened.core.onShellMessage("""{"type":"relay.publish","id":"r1"}""", true, NappletWebContract.SHELL_URL))
            eventually { opened.engine.delivered().isNotEmpty() }
            assertEquals("r1", idOf(opened.engine.delivered().single()))

            // A push is keyed by its subscription, never by the request's id.
            opened.core.onShellMessage("""{"type":"outbox.subscribe","id":"x9","subId":"s1","filters":"bad"}""", true, NappletWebContract.SHELL_URL)
            eventually { opened.engine.delivered().size == 2 }
            val push = opened.engine.delivered()[1]
            assertContains(push, "outbox.closed")
            assertEquals(null, idOf(push))
        }

    @Test
    fun anActingRequestWaitsForTheUser() =
        runTest {
            val opened = open(attended = false)
            opened.core.start()
            opened.core.settle()
            assertFalse(attendance.isAttended("surface"))
            opened.core.onShellMessage("""{"type":"relay.publish","id":"r1"}""", true, NappletWebContract.SHELL_URL)
            withContext(Dispatchers.Default) { delay(200) }
            assertTrue(opened.engine.delivered().isEmpty(), "held while nobody looks")
            opened.core.setAttended(true)
            eventually { opened.engine.delivered().isNotEmpty() }
            assertEquals("r1", idOf(opened.engine.delivered().single()))
        }

    @Test
    fun themeGoesOnlyToALaunchThatDeclaredIt() =
        runTest {
            val opened = open()
            opened.core.start()
            opened.core.settle()
            opened.core.pushTheme(NappletThemeColors("#000000", "#ffffff", "#ff0000"))
            eventually { opened.engine.delivered().isNotEmpty() }
            assertContains(opened.engine.delivered().single(), "theme")

            val plain = open(TestLaunches.napplet(author, declared = emptySet()), owner = "plain")
            plain.core.start()
            plain.core.settle()
            plain.core.pushTheme(NappletThemeColors("#000000", "#ffffff", "#ff0000"))
            withContext(Dispatchers.Default) { delay(100) }
            assertTrue(plain.engine.delivered().isEmpty())
        }

    @Test
    fun linksOpenOutsideOnAGestureAndAPapersNostrLinksInTheApp() =
        runTest {
            val opened = open()
            assertFalse(opened.core.onNavigation("https://example.com/", NappletFrame.APP, userGesture = true))
            assertFalse(opened.core.onNavigation("https://example.com/other", NappletFrame.APP, userGesture = false))
            assertEquals(listOf("https://example.com/"), opened.links.external)
            assertTrue(opened.core.onNavigation(NappletWebContract.SHELL_URL, NappletFrame.SHELL, userGesture = false))

            val paper = open(TestLaunches.paper(author), owner = "paper")
            paper.core.openLink("https://njump.me/npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m")
            assertEquals(1, paper.links.nostr.size + paper.links.external.size)
        }

    @Test
    fun closeReleasesEverythingAndDropsLateReplies() =
        runTest {
            val opened = open(TestLaunches.site(author))
            opened.core.start()
            opened.core.settle()
            assertTrue(attendance.isAttended("surface"))
            opened.core.close()
            assertTrue(opened.core.isClosed)
            assertTrue(opened.engine.closed)
            assertTrue(opened.files.closed)
            assertEquals(1, opened.route.released)
            assertFalse(attendance.isAttended("surface"))
            // After close, nothing more reaches the page.
            opened.core.onShellMessage("""{"type":"relay.query","id":"q1"}""", true, NappletWebContract.SHELL_URL)
            opened.core.pushTheme(NappletThemeColors("#000000", "#ffffff", "#ff0000"))
            withContext(Dispatchers.Default) { delay(200) }
            assertTrue(opened.engine.delivered().isEmpty())
            // A second close is a no-op.
            opened.core.close()
            assertEquals(1, opened.route.released)
        }

    @Test
    fun afterTheRendererIsGoneLateRepliesAreDropped() =
        runTest {
            val opened = open()
            opened.core.start()
            opened.core.settle()
            opened.core.onRendererGone()
            assertIs<NappletSessionState.Failed>(opened.core.state.value)
            assertEquals(NappletFailure.RENDERER_GONE, (opened.core.state.value as NappletSessionState.Failed).reason)
            opened.core.pushTheme(NappletThemeColors("#000000", "#ffffff", "#ff0000"))
            withContext(Dispatchers.Default) { delay(100) }
            assertTrue(opened.engine.delivered().isEmpty())
            assertFalse(attendance.isAttended("surface"))
        }

    @Test
    fun theConsoleRefusalFailsTheLaunch() =
        runTest {
            val opened = open()
            opened.core.start()
            opened.core.settle()
            opened.core.onConsoleMessage("Uncaught Error: " + NappletWebContract.WEBRTC_PRESENT)
            assertEquals(NappletSessionState.Failed(NappletFailure.ENGINE), opened.core.state.value)
        }

    @Test
    fun requestsAreClassifiedForTheLaunchsProfile() {
        val napplet = open()
        assertEquals(NappletRequestKind.CANCEL, napplet.core.classifyRequest("https://example.com/", isImage = false))
        val site = open(TestLaunches.site(author), owner = "site")
        assertEquals(NappletRequestKind.NETWORK, site.core.classifyRequest("https://example.com/", isImage = false))
        assertEquals(HostProfile.WEBSITE, site.core.launch.profile)
        assertEquals(NappletHeldRequests.MAX_AGE_MS, 120_000L)
    }
}
