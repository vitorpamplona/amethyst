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
package com.vitorpamplona.amethyst.commons.tor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The per-role Tor table, on every platform. `RoleBasedHttpClientBuilder` (JVM) delegates to
 * [TorRoleRouting], and an iOS builder will too, so these rows are what both promise.
 *
 * Each role is checked with every *other* role's switch set to the opposite value, so a role that
 * read the wrong switch fails here.
 */
class TorRoleRoutingTest {
    private val clearnet = "https://example.com/a.png"
    private val onion = "http://abcdefghijklmnop.onion/x"
    private val loopback = "http://127.0.0.1:8080/x"
    private val localhostName = "http://localhost:8080/x"
    private val privateLan = "http://192.168.1.100:8080/x"
    private val overlay = "http://[200:1234::1]/x"

    /** One role that takes a URL: the switch it follows and the decision it makes. */
    private class UrlRole(
        val name: String,
        val switch: (TorSettingsFlow) -> MutableStateFlow<Boolean>,
        val decide: (TorRoleRouting, String) -> Boolean,
        /** Images always send `.onion` through Tor; every other role follows `onionRelaysViaTor`. */
        val onionAlwaysTor: Boolean = false,
    )

    private val urlRoles =
        listOf(
            UrlRole("image", { it.imagesViaTor }, { r, u -> r.shouldUseTorForImageDownload(u) }, onionAlwaysTor = true),
            UrlRole("video", { it.videosViaTor }, { r, u -> r.shouldUseTorForVideoDownload(u) }),
            UrlRole("preview", { it.urlPreviewsViaTor }, { r, u -> r.shouldUseTorForPreviewUrl(u) }),
            UrlRole("money", { it.moneyOperationsViaTor }, { r, u -> r.shouldUseTorForMoneyOperations(u) }),
            UrlRole("nip05", { it.nip05VerificationsViaTor }, { r, u -> r.shouldUseTorForNIP05(u) }),
            UrlRole("uploads", { it.mediaUploadsViaTor }, { r, u -> r.shouldUseTorForUploads(u) }),
            UrlRole("relayDirectory", { it.newRelaysViaTor }, { r, u -> r.shouldUseTorForRelayDirectory(u) }),
        )

    private val allSwitches: (TorSettingsFlow) -> List<MutableStateFlow<Boolean>> = {
        listOf(
            it.imagesViaTor,
            it.videosViaTor,
            it.urlPreviewsViaTor,
            it.moneyOperationsViaTor,
            it.nip05VerificationsViaTor,
            it.mediaUploadsViaTor,
            it.newRelaysViaTor,
            it.trustedRelaysViaTor,
            it.dmRelaysViaTor,
            it.profilePicsViaTor,
            it.appletsAndWebsitesViaTor,
        )
    }

    /** Settings where [own] is [ownValue] and every other role switch is the opposite. */
    private fun settings(
        torType: TorType,
        onionRelaysViaTor: Boolean,
        own: (TorSettingsFlow) -> MutableStateFlow<Boolean>,
        ownValue: Boolean,
    ): TorSettingsFlow {
        val s = TorSettingsFlow(torType = MutableStateFlow(torType), onionRelaysViaTor = MutableStateFlow(onionRelaysViaTor))
        allSwitches(s).forEach { it.value = !ownValue }
        own(s).value = ownValue
        return s
    }

    @Test
    fun offNeverRoutesAnyRoleThroughTor() {
        for (role in urlRoles) {
            for (value in listOf(true, false)) {
                val routing = TorRoleRouting(settings(TorType.OFF, onionRelaysViaTor = true, own = role.switch, ownValue = value))
                for (url in listOf(clearnet, onion, loopback, localhostName, privateLan, overlay)) {
                    assertFalse(role.decide(routing, url), "${role.name} $url under OFF")
                }
                assertFalse(routing.shouldUseTorForVideoDownload(), "video (no url) under OFF")
                assertFalse(routing.shouldUseTorForTrustedRelays(), "push registration under OFF")
            }
        }
    }

    @Test
    fun internalAndExternalFollowEachRolesOwnSwitch() {
        for (torType in listOf(TorType.INTERNAL, TorType.EXTERNAL)) {
            for (role in urlRoles) {
                for (own in listOf(true, false)) {
                    for (onionSetting in listOf(true, false)) {
                        val routing = TorRoleRouting(settings(torType, onionSetting, role.switch, own))
                        val label = "${role.name} $torType own=$own onion=$onionSetting"

                        assertEquals(own, role.decide(routing, clearnet), "$label clearnet follows the role's switch")
                        assertEquals(
                            role.onionAlwaysTor || onionSetting,
                            role.decide(routing, onion),
                            "$label .onion",
                        )
                        for (local in listOf(loopback, localhostName, privateLan)) {
                            assertFalse(role.decide(routing, local), "$label $local is local, never Tor")
                        }
                        assertFalse(role.decide(routing, overlay), "$label overlay (0200::/7) is never Tor")
                    }
                }
            }
        }
    }

    @Test
    fun videoWithoutUrlAndPushRegistrationFollowOnlyTheirSwitch() {
        for (torType in listOf(TorType.INTERNAL, TorType.EXTERNAL)) {
            for (own in listOf(true, false)) {
                val video = TorRoleRouting(settings(torType, onionRelaysViaTor = !own, own = { it.videosViaTor }, ownValue = own))
                assertEquals(own, video.shouldUseTorForVideoDownload(), "video (no url) $torType")

                val push = TorRoleRouting(settings(torType, onionRelaysViaTor = !own, own = { it.trustedRelaysViaTor }, ownValue = own))
                assertEquals(own, push.shouldUseTorForTrustedRelays(), "push registration $torType")
            }
        }
    }

    @Test
    fun explicitTorTypeOverloadIgnoresTheStoredType() {
        val routing = TorRoleRouting(TorSettingsFlow(torType = MutableStateFlow(TorType.OFF)))
        assertEquals(true, routing.shouldUseTorFor(clearnet, TorType.INTERNAL, imagesViaTor = true))
        assertEquals(true, routing.shouldUseTorFor(onion, TorType.EXTERNAL, imagesViaTor = false))
        assertFalse(routing.shouldUseTorFor(clearnet, TorType.OFF, imagesViaTor = true))
        assertFalse(routing.shouldUseTorFor(loopback, TorType.INTERNAL, imagesViaTor = true))
    }

    @Test
    fun decisionsReadTheSettingsLive() {
        val settings = TorSettingsFlow(torType = MutableStateFlow(TorType.INTERNAL))
        val routing = TorRoleRouting(settings)
        settings.imagesViaTor.value = false
        assertFalse(routing.shouldUseTorForImageDownload(clearnet))
        settings.imagesViaTor.value = true
        assertEquals(true, routing.shouldUseTorForImageDownload(clearnet))
        settings.torType.value = TorType.OFF
        assertFalse(routing.shouldUseTorForImageDownload(clearnet))
    }
}
