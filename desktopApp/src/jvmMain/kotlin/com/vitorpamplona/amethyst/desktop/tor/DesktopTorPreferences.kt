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
package com.vitorpamplona.amethyst.desktop.tor

import com.vitorpamplona.amethyst.commons.tor.ITorSettingsPersistence
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import java.util.prefs.Preferences

/**
 * Desktop persistence for Tor settings using java.util.prefs.Preferences.
 *
 * Per-device settings (not synced across accounts).
 * Default TorType is OFF — user must opt-in.
 * No explicit flush() — matches existing DesktopPreferences pattern.
 */
object DesktopTorPreferences : ITorSettingsPersistence {
    private val prefs = Preferences.userNodeForPackage(DesktopTorPreferences::class.java)

    override fun load(): TorSettings =
        TorSettings(
            torType = TorType.entries.firstOrNull { it.name == prefs.get("tor_type", TorType.OFF.name) } ?: TorType.OFF,
            externalSocksPort = prefs.getInt("tor_external_port", 9050),
            onionRelaysViaTor = prefs.getBoolean("tor_onion_relays", true),
            dmRelaysViaTor = prefs.getBoolean("tor_dm_relays", false),
            newRelaysViaTor = prefs.getBoolean("tor_new_relays", false),
            trustedRelaysViaTor = prefs.getBoolean("tor_trusted_relays", false),
            urlPreviewsViaTor = prefs.getBoolean("tor_url_previews", false),
            profilePicsViaTor = prefs.getBoolean("tor_profile_pics", false),
            imagesViaTor = prefs.getBoolean("tor_images", false),
            videosViaTor = prefs.getBoolean("tor_videos", false),
            moneyOperationsViaTor = prefs.getBoolean("tor_money", false),
            nip05VerificationsViaTor = prefs.getBoolean("tor_nip05", false),
            mediaUploadsViaTor = prefs.getBoolean("tor_media_uploads", false),
        )

    override fun save(settings: TorSettings) {
        prefs.put("tor_type", settings.torType.name)
        prefs.putInt("tor_external_port", settings.externalSocksPort)
        prefs.putBoolean("tor_onion_relays", settings.onionRelaysViaTor)
        prefs.putBoolean("tor_dm_relays", settings.dmRelaysViaTor)
        prefs.putBoolean("tor_new_relays", settings.newRelaysViaTor)
        prefs.putBoolean("tor_trusted_relays", settings.trustedRelaysViaTor)
        prefs.putBoolean("tor_url_previews", settings.urlPreviewsViaTor)
        prefs.putBoolean("tor_profile_pics", settings.profilePicsViaTor)
        prefs.putBoolean("tor_images", settings.imagesViaTor)
        prefs.putBoolean("tor_videos", settings.videosViaTor)
        prefs.putBoolean("tor_money", settings.moneyOperationsViaTor)
        prefs.putBoolean("tor_nip05", settings.nip05VerificationsViaTor)
        prefs.putBoolean("tor_media_uploads", settings.mediaUploadsViaTor)
        // No explicit flush() — JVM auto-flushes on shutdown (matches DesktopPreferences pattern)
    }
}
