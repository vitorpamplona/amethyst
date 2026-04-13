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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TorSettingsTest {
    @Test
    fun parseTorType_code0_returnsOff() = assertEquals(TorType.OFF, parseTorType(0))

    @Test
    fun parseTorType_code1_returnsInternal() = assertEquals(TorType.INTERNAL, parseTorType(1))

    @Test
    fun parseTorType_code2_returnsExternal() = assertEquals(TorType.EXTERNAL, parseTorType(2))

    @Test
    fun parseTorType_null_defaultsToInternal() = assertEquals(TorType.INTERNAL, parseTorType(null))

    @Test
    fun parseTorType_unknownCode_defaultsToInternal() = assertEquals(TorType.INTERNAL, parseTorType(99))

    @Test
    fun parseTorType_negativeCode_defaultsToInternal() = assertEquals(TorType.INTERNAL, parseTorType(-1))

    @Test
    fun torType_screenCodes_areUnique() {
        val codes = TorType.entries.map { it.screenCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun torType_allValues_roundTripViaParse() {
        TorType.entries.forEach { type ->
            assertEquals(type, parseTorType(type.screenCode))
        }
    }

    @Test
    fun parseTorPresetType_code0_returnsOnlyWhenNeeded() = assertEquals(TorPresetType.ONLY_WHEN_NEEDED, parseTorPresetType(0))

    @Test
    fun parseTorPresetType_code1_returnsDefault() = assertEquals(TorPresetType.DEFAULT, parseTorPresetType(1))

    @Test
    fun parseTorPresetType_code2_returnsSmallPayloads() = assertEquals(TorPresetType.SMALL_PAYLOADS, parseTorPresetType(2))

    @Test
    fun parseTorPresetType_code3_returnsFullPrivacy() = assertEquals(TorPresetType.FULL_PRIVACY, parseTorPresetType(3))

    @Test
    fun parseTorPresetType_unknownCode_defaultsToCustom() = assertEquals(TorPresetType.CUSTOM, parseTorPresetType(99))

    @Test
    fun parseTorPresetType_null_defaultsToCustom() = assertEquals(TorPresetType.CUSTOM, parseTorPresetType(null))

    @Test
    fun torPresetType_screenCodes_areUnique() {
        val codes = TorPresetType.entries.map { it.screenCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun onlyWhenNeededPreset_onlyOnionEnabled() {
        assertTrue(torOnlyWhenNeededPreset.onionRelaysViaTor)
        assertFalse(torOnlyWhenNeededPreset.dmRelaysViaTor)
        assertFalse(torOnlyWhenNeededPreset.newRelaysViaTor)
        assertFalse(torOnlyWhenNeededPreset.trustedRelaysViaTor)
        assertFalse(torOnlyWhenNeededPreset.urlPreviewsViaTor)
        assertFalse(torOnlyWhenNeededPreset.imagesViaTor)
        assertFalse(torOnlyWhenNeededPreset.videosViaTor)
        assertFalse(torOnlyWhenNeededPreset.moneyOperationsViaTor)
        assertFalse(torOnlyWhenNeededPreset.nip05VerificationsViaTor)
        assertFalse(torOnlyWhenNeededPreset.mediaUploadsViaTor)
    }

    @Test
    fun defaultPreset_onionDmNewEnabled() {
        assertTrue(torDefaultPreset.onionRelaysViaTor)
        assertTrue(torDefaultPreset.dmRelaysViaTor)
        assertTrue(torDefaultPreset.newRelaysViaTor)
        assertFalse(torDefaultPreset.trustedRelaysViaTor)
        assertFalse(torDefaultPreset.urlPreviewsViaTor)
    }

    @Test
    fun fullPrivacyPreset_allEnabled() {
        assertTrue(torFullyPrivate.onionRelaysViaTor)
        assertTrue(torFullyPrivate.dmRelaysViaTor)
        assertTrue(torFullyPrivate.newRelaysViaTor)
        assertTrue(torFullyPrivate.trustedRelaysViaTor)
        assertTrue(torFullyPrivate.urlPreviewsViaTor)
        assertTrue(torFullyPrivate.imagesViaTor)
        assertTrue(torFullyPrivate.videosViaTor)
        assertTrue(torFullyPrivate.moneyOperationsViaTor)
        assertTrue(torFullyPrivate.nip05VerificationsViaTor)
        assertTrue(torFullyPrivate.mediaUploadsViaTor)
    }

    @Test
    fun whichPreset_matchesOnlyWhenNeeded() = assertEquals(TorPresetType.ONLY_WHEN_NEEDED, whichPreset(torOnlyWhenNeededPreset))

    @Test
    fun whichPreset_matchesDefault() = assertEquals(TorPresetType.DEFAULT, whichPreset(torDefaultPreset))

    @Test
    fun whichPreset_matchesSmallPayloads() = assertEquals(TorPresetType.SMALL_PAYLOADS, whichPreset(torSmallPayloadsPreset))

    @Test
    fun whichPreset_matchesFullPrivacy() = assertEquals(TorPresetType.FULL_PRIVACY, whichPreset(torFullyPrivate))

    @Test
    fun whichPreset_returnsCustomForMixedSettings() {
        val mixed =
            TorSettings(
                onionRelaysViaTor = true,
                dmRelaysViaTor = true,
                newRelaysViaTor = false,
                trustedRelaysViaTor = true,
            )
        assertEquals(TorPresetType.CUSTOM, whichPreset(mixed))
    }

    @Test
    fun whichPreset_ignoresProfilePicsInComparison() {
        val withProfilePics = torDefaultPreset.copy(profilePicsViaTor = true)
        assertEquals(TorPresetType.DEFAULT, whichPreset(withProfilePics))
    }

    @Test
    fun isPreset_exactMatch_returnsTrue() = assertTrue(isPreset(torFullyPrivate, torFullyPrivate))

    @Test
    fun isPreset_differentFlag_returnsFalse() {
        val modified = torFullyPrivate.copy(imagesViaTor = false)
        assertFalse(isPreset(modified, torFullyPrivate))
    }

    @Test
    fun torSettings_defaultValues() {
        val defaults = TorSettings()
        assertEquals(TorType.INTERNAL, defaults.torType)
        assertEquals(9050, defaults.externalSocksPort)
    }

    @Test
    fun torSettings_equality() {
        val a = TorSettings(torType = TorType.INTERNAL, externalSocksPort = 9050)
        val b = TorSettings(torType = TorType.INTERNAL, externalSocksPort = 9050)
        assertEquals(a, b)
    }

    @Test
    fun torSettings_copy_changesOneField() {
        val original = TorSettings()
        val modified = original.copy(torType = TorType.OFF)
        assertEquals(TorType.OFF, modified.torType)
        assertNotEquals(original, modified)
    }
}
