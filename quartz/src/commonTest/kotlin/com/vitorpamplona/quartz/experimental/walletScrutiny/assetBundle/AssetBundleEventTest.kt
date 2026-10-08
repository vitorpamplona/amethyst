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
package com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.WalletScrutinyFixtures
import com.vitorpamplona.quartz.experimental.walletScrutiny.tags.AssetFileTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetBundleEventTest {
    private val baseApk = "bc63fb293d075a929abc0f216a179756e009c1bdb71e9f4878bdf334be293eb5"

    private fun create(
        tags: Array<Array<String>>,
        content: String = "",
        pubKey: String = "1".repeat(64),
        kind: Int = AssetBundleEvent.KIND,
    ): Event = EventFactory.create("0".repeat(64), pubKey, 1L, kind, tags, content, "")

    @Test
    fun factoryBuildsTheBundle() {
        assertTrue(EventFactory.isKnownKind(AssetBundleEvent.KIND))
        assertIs<AssetBundleEvent>(Event.fromJson(WalletScrutinyFixtures.REAL_ASSET_BUNDLE))
    }

    @Test
    fun realBundleAccessors() {
        val bundle = assertIs<AssetBundleEvent>(Event.fromJson(WalletScrutinyFixtures.REAL_ASSET_BUNDLE))
        assertEquals("io.hexawallet.bitcoinkeeper", bundle.productId())
        assertEquals("2.6.3", bundle.version())
        assertEquals("android", bundle.platform())
        assertEquals(
            listOf("base.apk", "split_config.arm64_v8a.apk", "split_config.es.apk", "split_config.xxhdpi.apk"),
            bundle.files().map { it.filename },
        )
        assertEquals(baseApk, bundle.hashes().first())
        assertEquals(4, bundle.hashes().size)
        assertEquals("installed from Play Store, uploaded by WalletScrutiny Android", bundle.note())
        assertEquals("base.apk", bundle.file(baseApk.uppercase())?.filename)
        assertNull(bundle.file("f".repeat(64)))
    }

    @Test
    fun carriesNoEdgesAndIsNotSearchable() {
        val bundle = Event.fromJson(WalletScrutinyFixtures.REAL_ASSET_BUNDLE)
        assertFalse(bundle is SearchableEvent)
        assertFalse(bundle is PubKeyHintProvider)
        assertFalse(bundle is EventHintProvider)
        assertFalse(bundle is AddressHintProvider)
    }

    @Test
    fun registersTheNip82AssetOfTheSameFile() {
        val bundle = assertIs<AssetBundleEvent>(Event.fromJson(WalletScrutinyFixtures.REAL_ASSET_BUNDLE))
        val sameFile = assertIs<SoftwareAssetEvent>(create(arrayOf(arrayOf("i", "store.republished.id"), arrayOf("x", baseApk)), kind = SoftwareAssetEvent.KIND))
        val otherFile = assertIs<SoftwareAssetEvent>(create(arrayOf(arrayOf("i", "io.hexawallet.bitcoinkeeper"), arrayOf("x", "e".repeat(64))), kind = SoftwareAssetEvent.KIND))
        assertTrue(bundle.registers(sameFile))
        assertFalse(bundle.registers(otherFile))
    }

    @Test
    fun malformedTagsAreSkipped() {
        val bundle =
            assertIs<AssetBundleEvent>(
                create(
                    arrayOf(
                        arrayOf("i"),
                        arrayOf("version", ""),
                        arrayOf("platform"),
                        arrayOf("x", "not-a-hash", "base.apk"),
                        arrayOf("x", "a".repeat(63), "short.apk"),
                        arrayOf("x"),
                        arrayOf("x", "B".repeat(64)),
                        arrayOf("x", "c".repeat(64), ""),
                    ),
                    content = "  ",
                ),
            )
        assertNull(bundle.productId())
        assertNull(bundle.version())
        assertNull(bundle.platform())
        assertNull(bundle.note())
        assertEquals(listOf(AssetFileTag("b".repeat(64)), AssetFileTag("c".repeat(64))), bundle.files())
    }

    @Test
    fun buildRoundTrips() {
        val files = listOf(AssetFileTag("a".repeat(64), "base.apk"), AssetFileTag("b".repeat(64), "split_config.en.apk"))
        val template = AssetBundleEvent.build("app.zeusln.zeus", "1.2.3", "android", files, "uploaded by hand", createdAt = 5L)
        assertEquals(AssetBundleEvent.KIND, template.kind)
        val bundle = assertIs<AssetBundleEvent>(create(template.tags, template.content))
        assertEquals("app.zeusln.zeus", bundle.productId())
        assertEquals("1.2.3", bundle.version())
        assertEquals("android", bundle.platform())
        assertEquals(files, bundle.files())
        assertEquals("uploaded by hand", bundle.note())
    }
}
