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
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The engine-independent policies, pinned to what Desktop's `CefNappletSession` and Android's host
 * decided before they moved here.
 */
class NappletSessionPoliciesTest {
    private val author = "a".repeat(64)
    private val account = "b".repeat(64)

    // ---- guards (Android's NappletWebRtcGuard.isNeeded) ----

    @Test
    fun theWebRtcGuardMatrixIsAndroids() {
        for (useTor in listOf(false, true)) {
            assertTrue(NappletLaunchPlan.webRtcGuardNeeded(HostProfile.NAPPLET, useTor))
            assertTrue(NappletLaunchPlan.webRtcGuardNeeded(HostProfile.WEBXDC, useTor))
            // A front page runs no script: there is no WebRTC to take.
            assertFalse(NappletLaunchPlan.webRtcGuardNeeded(HostProfile.PAPER, useTor))
        }
        // A site on the open web keeps its WebRTC; one on Tor must not show the user's address.
        assertFalse(NappletLaunchPlan.webRtcGuardNeeded(HostProfile.WEBSITE, useTor = false))
        assertTrue(NappletLaunchPlan.webRtcGuardNeeded(HostProfile.WEBSITE, useTor = true))
    }

    @Test
    fun cookiesAreAlwaysGuardedAndTheShellChecksWebRtcWhenNeeded() {
        val torSite = NappletLaunchPlan(HostProfile.WEBSITE, "https://n1.napplet.local", true, true, NappletLaunchPlan.guardsFor(HostProfile.WEBSITE, true), NappletWebContract.HTTPS)
        val openSite = NappletLaunchPlan(HostProfile.WEBSITE, "https://n1.napplet.local", false, false, NappletLaunchPlan.guardsFor(HostProfile.WEBSITE, false), NappletWebContract.HTTPS)
        assertEquals(setOf(NappletGuard.WEBRTC, NappletGuard.COOKIES), torSite.guards)
        assertEquals(setOf(NappletGuard.COOKIES), openSite.guards)
        assertEquals(NappletWebContract.WEBRTC_CHECK + "<b/>", torSite.shellHead("<b/>"))
        assertEquals("<b/>", openSite.shellHead("<b/>"))
    }

    // ---- launch plan ----

    @Test
    fun theOriginCarriesTheAccountOnlyForASharedJar() {
        val site = TestLaunches.site(author)
        val shared = NappletLaunchPlan.of(site, account, useTor = false, appletBlobsUseTor = false, sharedStorageJar = true)
        val split = NappletLaunchPlan.of(site, account, useTor = false, appletBlobsUseTor = false, sharedStorageJar = false)
        assertEquals(NappletWebContract.appOrigin(NappletWebContract.appId(author, "blog", partition = account, profile = HostProfile.WEBSITE)), shared.appOrigin)
        // Android's origin, which its per-account WebView profile (and iOS's data store) keeps apart.
        assertEquals(NappletWebContract.appOrigin(NappletWebContract.appId(author, "blog", profile = HostProfile.WEBSITE)), split.appOrigin)
        // A napplet's srcdoc is opaque: no partition, whatever the jar.
        val napplet = TestLaunches.napplet(author)
        assertEquals(
            NappletLaunchPlan.of(napplet, account, false, false, sharedStorageJar = true).appOrigin,
            NappletLaunchPlan.of(napplet, account, false, false, sharedStorageJar = false).appOrigin,
        )
    }

    @Test
    fun aSiteFetchesOnItsOwnRouteAndANappletOnTheAppletSwitch() {
        assertTrue(NappletLaunchPlan.of(TestLaunches.site(author), account, useTor = true, appletBlobsUseTor = false, sharedStorageJar = true).blobsUseTor)
        assertFalse(NappletLaunchPlan.of(TestLaunches.site(author), account, useTor = false, appletBlobsUseTor = true, sharedStorageJar = true).blobsUseTor)
        assertTrue(NappletLaunchPlan.of(TestLaunches.napplet(author), account, useTor = false, appletBlobsUseTor = true, sharedStorageJar = true).blobsUseTor)
        assertFalse(NappletLaunchPlan.of(TestLaunches.napplet(author), account, useTor = false, appletBlobsUseTor = false, sharedStorageJar = true).blobsUseTor)
    }

    @Test
    fun aSiteProbesItsRootAndEverythingElseItsIndex() {
        assertEquals("/", NappletLaunchPlan.of(TestLaunches.site(author), account, false, false, true).entryPath)
        assertEquals("/index.html", NappletLaunchPlan.of(TestLaunches.napplet(author), account, false, false, true).entryPath)
    }

    @Test
    fun onlyASiteWithTorOnAndChosenGoesThroughTor() {
        assertTrue(NappletLaunchPlan.useTorFor(HostProfile.WEBSITE, torEnabled = true) { true })
        assertFalse(NappletLaunchPlan.useTorFor(HostProfile.WEBSITE, torEnabled = true) { false })
        assertFalse(NappletLaunchPlan.useTorFor(HostProfile.WEBSITE, torEnabled = false) { true })
        assertFalse(NappletLaunchPlan.useTorFor(HostProfile.NAPPLET, torEnabled = true) { true })
    }

    // ---- requests ----

    @Test
    fun internalIsReadFromTheText() {
        assertTrue(NappletRequestPolicy.isInternal("https://napplet.local/__shell__"))
        assertTrue(NappletRequestPolicy.isInternal("https://n1.napplet.local/a.js?x={|}"))
        assertTrue(NappletRequestPolicy.isInternal("https://N1.NAPPLET.LOCAL:443/"))
        assertTrue(NappletRequestPolicy.isInternal("https://n1.xdc.napplet.local/index.html"))
        assertFalse(NappletRequestPolicy.isInternal("http://napplet.local/"))
        assertFalse(NappletRequestPolicy.isInternal("https://napplet.local.evil.com/"))
        assertFalse(NappletRequestPolicy.isInternal("https://evil.com@napplet.local/"))
        assertFalse(NappletRequestPolicy.isInternal("https://evilnapplet.local/"))
        // iOS's origins live on their own scheme.
        assertTrue(NappletRequestPolicy.isInternal("napplet://n1.napplet.local/", NappletWebContract.NAPPLET_SCHEME))
        assertFalse(NappletRequestPolicy.isInternal("https://n1.napplet.local/", NappletWebContract.NAPPLET_SCHEME))
    }

    @Test
    fun aNappletHasNoNetworkAndASiteHasItsOwn() {
        val outside = "https://example.com/x.js"
        assertEquals(NappletRequestKind.CANCEL, NappletRequestPolicy.classify(outside, HostProfile.NAPPLET, isImage = false))
        assertEquals(NappletRequestKind.CANCEL, NappletRequestPolicy.classify("wss://relay.example.com", HostProfile.WEBXDC, isImage = false))
        assertEquals(NappletRequestKind.NETWORK, NappletRequestPolicy.classify(outside, HostProfile.WEBSITE, isImage = false))
        assertEquals(NappletRequestKind.SERVE, NappletRequestPolicy.classify("https://n1.napplet.local/", HostProfile.NAPPLET, isImage = false))
        assertEquals(NappletRequestKind.LOCAL, NappletRequestPolicy.classify("about:srcdoc", HostProfile.NAPPLET, isImage = false))
        assertEquals(NappletRequestKind.LOCAL, NappletRequestPolicy.classify("blob:https://n1.napplet.local/1", HostProfile.NAPPLET, isImage = false))
        assertEquals(NappletRequestKind.PAPER_ART, NappletRequestPolicy.classify("https://example.com/a.png", HostProfile.PAPER, isImage = true))
        assertEquals(NappletRequestKind.NETWORK, NappletRequestPolicy.classify("https://example.com/a.css", HostProfile.PAPER, isImage = false))
    }

    @Test
    fun theContentRulesBlockEverythingButOurHostsForANapplet() {
        val origins = NappletWebContract.NAPPLET_SCHEME
        val appOrigin = origins.appOrigin("n1", HostProfile.WEBXDC)
        val rules = NappletRequestPolicy.contentRules(HostProfile.WEBXDC, appOrigin, origins)
        assertEquals(NappletContentRule(".*", block = true), rules.first())
        val allowed = rules.filter { !it.block }.map { Regex(it.urlFilter) }

        fun allowed(url: String) = allowed.any { it.containsMatchIn(url) }
        assertTrue(allowed("napplet://napplet.local/__shell__"))
        assertTrue(allowed("$appOrigin/index.html"))
        assertTrue(allowed("data:image/png;base64,AA"))
        assertFalse(allowed("https://example.com/"))
        assertFalse(allowed("napplet://n2.xdc.napplet.local/index.html"))
        assertFalse(allowed("napplet://napplet.local.evil.com/"))

        val json = Json.parseToJsonElement(NappletRequestPolicy.contentRuleListJson(rules)).jsonArray
        assertEquals(rules.size, json.size)
        assertEquals(
            "block",
            json[0]
                .jsonObject["action"]!!
                .jsonObject["type"]!!
                .jsonPrimitive.content,
        )
        assertEquals(
            "ignore-previous-rules",
            json[1]
                .jsonObject["action"]!!
                .jsonObject["type"]!!
                .jsonPrimitive.content,
        )
    }

    @Test
    fun aSiteMayNotLookUpTheInternalNames() {
        val rules = NappletRequestPolicy.contentRules(HostProfile.WEBSITE, "napplet://n1.napplet.local", NappletWebContract.NAPPLET_SCHEME)
        val rule = Regex(rules.single().urlFilter)
        assertTrue(rules.single().block)
        assertTrue(rule.containsMatchIn("https://n1.napplet.local/x"))
        assertTrue(rule.containsMatchIn("http://napplet.local:80/"))
        assertFalse(rule.containsMatchIn("https://example.com/napplet.local/"))
        assertFalse(rule.containsMatchIn("https://napplet.localhost/"))
    }

    // ---- navigation ----

    @Test
    fun aLinkOpensOutsideOnlyOnAGesture() {
        val url = "https://example.com/"
        assertEquals(NappletNavigation.OpenLink(url), NappletNavigationPolicy.decide(url, NappletFrame.APP, userGesture = true, HostProfile.NAPPLET))
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.decide(url, NappletFrame.APP, userGesture = false, HostProfile.NAPPLET))
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.decide("intent://x", NappletFrame.APP, userGesture = true, HostProfile.NAPPLET))
        // A webxdc app opens no links at all.
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.decide(url, NappletFrame.APP, userGesture = true, HostProfile.WEBXDC))
        assertEquals(NappletNavigation.Allow, NappletNavigationPolicy.decide("https://n1.napplet.local/page", NappletFrame.APP, userGesture = false, HostProfile.WEBSITE))
    }

    @Test
    fun onlyASitesEmbedsLeaveTheOrigin() {
        val url = "https://player.example.com/embed"
        assertEquals(NappletNavigation.Allow, NappletNavigationPolicy.decide(url, NappletFrame.EMBED, userGesture = false, HostProfile.WEBSITE))
        // The site's own frame leaving its origin could say hello as someone else.
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.decide(url, NappletFrame.APP, userGesture = false, HostProfile.WEBSITE))
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.decide(url, NappletFrame.SHELL, userGesture = false, HostProfile.WEBSITE))
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.decide(url, NappletFrame.EMBED, userGesture = false, HostProfile.NAPPLET))
    }

    @Test
    fun aPopupIsNeverAWindowOfItsOwn() {
        assertEquals(NappletNavigation.OpenLink("https://example.com/"), NappletNavigationPolicy.popup("https://example.com/", HostProfile.WEBSITE))
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.popup("https://example.com/", HostProfile.WEBXDC))
        assertEquals(NappletNavigation.Cancel, NappletNavigationPolicy.popup("javascript:alert(1)", HostProfile.WEBSITE))
    }

    @Test
    fun framesAreToldApart() {
        assertEquals(NappletFrame.SHELL, NappletNavigationPolicy.frameOf(isMainFrame = true, parentIsMainFrame = null))
        assertEquals(NappletFrame.APP, NappletNavigationPolicy.frameOf(isMainFrame = false, parentIsMainFrame = true))
        assertEquals(NappletFrame.EMBED, NappletNavigationPolicy.frameOf(isMainFrame = false, parentIsMainFrame = false))
        assertEquals(NappletFrame.APP, NappletNavigationPolicy.frameOf(isMainFrame = false, parentIsMainFrame = null))
        val app = "napplet://n1.napplet.local"
        assertEquals(NappletFrame.SHELL, NappletNavigationPolicy.frameByOrigin(true, "napplet://napplet.local", app))
        assertEquals(NappletFrame.APP, NappletNavigationPolicy.frameByOrigin(false, app, app))
        assertEquals(NappletFrame.EMBED, NappletNavigationPolicy.frameByOrigin(false, "https://player.example.com", app))
    }

    // ---- bridge gate ----

    @Test
    fun onlyTheShellInTheTopFrameReachesTheBridge() {
        assertTrue(NappletBridgeGate.acceptsUrl(true, NappletWebContract.SHELL_URL))
        assertFalse(NappletBridgeGate.acceptsUrl(false, NappletWebContract.SHELL_URL))
        assertFalse(NappletBridgeGate.acceptsUrl(true, "https://n1.napplet.local/"))
        assertTrue(NappletBridgeGate.acceptsOrigin(true, "napplet://napplet.local", NappletWebContract.NAPPLET_SCHEME))
        assertFalse(NappletBridgeGate.acceptsOrigin(true, "napplet://n1.napplet.local", NappletWebContract.NAPPLET_SCHEME))
        assertFalse(NappletBridgeGate.acceptsOrigin(false, "napplet://napplet.local", NappletWebContract.NAPPLET_SCHEME))
        assertNotEquals(NappletWebContract.HTTPS.origin, NappletWebContract.NAPPLET_SCHEME.origin)
    }
}
