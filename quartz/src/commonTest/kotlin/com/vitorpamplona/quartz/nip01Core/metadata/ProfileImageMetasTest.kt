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
package com.vitorpamplona.quartz.nip01Core.metadata

import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.nsecToSigner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ProfileImageMetasTest {
    val signer = "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToSigner()

    val picture = "https://blossom.example/1f2e.jpg"
    val banner = "https://blossom.example/7c3d.webp"

    val pictureMeta =
        PictureMeta(
            url = picture,
            mimeType = "image/jpeg",
            hash = "1f2e",
            dimension = DimensionTag(400, 400),
            blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
            fallback = listOf("https://mirror.example/1f2e.jpg"),
        )

    val bannerMeta =
        PictureMeta(
            url = banner,
            mimeType = "image/webp",
            hash = "7c3d",
            dimension = DimensionTag(1500, 500),
        )

    fun assertMeta(
        expected: PictureMeta,
        actual: PictureMeta?,
    ) = assertContentEquals(expected.toIMetaArray(), assertNotNull(actual).toIMetaArray())

    fun event(
        content: String,
        vararg tags: Array<String>,
    ) = MetadataEvent(
        id = "a".repeat(64),
        pubKey = "b".repeat(64),
        createdAt = 1,
        tags = arrayOf(*tags),
        content = content,
        sig = "c".repeat(128),
    )

    @Test
    fun parsesSpecExample() {
        val event =
            event(
                "{\"name\":\"luna\",\"picture\":\"$picture\",\"banner\":\"$banner\"}",
                arrayOf(
                    "imeta",
                    "url $picture",
                    "m image/jpeg",
                    "x 1f2e",
                    "dim 400x400",
                    "blurhash LEHV6nWB2yk8pyo0adR*.7kCMdnj",
                    "fallback https://mirror.example/1f2e.jpg",
                ),
                arrayOf("imeta", "url $banner", "m image/webp", "x 7c3d", "dim 1500x500"),
            )

        val metas = event.profileImageMetas()
        assertMeta(pictureMeta, metas.picture)
        assertMeta(bannerMeta, metas.banner)
    }

    @Test
    fun ignoresImetaThatMatchesNoField() {
        val event =
            event(
                "{\"picture\":\"$picture\"}",
                arrayOf("imeta", "url https://other.example/x.jpg", "fallback https://mirror.example/x.jpg"),
                arrayOf("imeta", "url $banner", "dim 1500x500"),
            )

        val metas = event.profileImageMetas()
        assertNull(metas.picture)
        assertNull(metas.banner)
    }

    @Test
    fun matchesOnlyExactUrl() {
        val event =
            event(
                "{\"picture\":\"$picture\"}",
                arrayOf("imeta", "url $picture?size=200", "dim 200x200"),
            )

        assertNull(event.profileImageMetas().picture)
    }

    @Test
    fun noImagesIsEmpty() {
        assertSame(ProfileImageMetas.EMPTY, event("{\"name\":\"luna\"}", arrayOf("imeta", "url $picture")).profileImageMetas())
    }

    @Test
    fun createNewWritesImetas() {
        val event =
            signer.sign(
                MetadataEvent.createNew(
                    name = "luna",
                    picture = picture,
                    banner = banner,
                    pictureMeta = pictureMeta,
                    bannerMeta = bannerMeta,
                    createdAt = 1740669816,
                ),
            )

        val metas = event.profileImageMetas()
        assertMeta(pictureMeta, metas.picture)
        assertMeta(bannerMeta, metas.banner)
        assertEquals(2, event.tags.count { it[0] == "imeta" })
    }

    @Test
    fun createNewDropsMetaForAnotherUrl() {
        val event =
            signer.sign(
                MetadataEvent.createNew(
                    picture = picture,
                    pictureMeta = bannerMeta,
                    createdAt = 1740669816,
                ),
            )

        assertEquals(0, event.tags.count { it[0] == "imeta" })
    }

    @Test
    fun updateCarriesOverUnchangedImages() {
        val first =
            signer.sign(
                MetadataEvent.createNew(
                    name = "luna",
                    picture = picture,
                    banner = banner,
                    pictureMeta = pictureMeta,
                    bannerMeta = bannerMeta,
                    createdAt = 1740669816,
                ),
            )

        val second = signer.sign(MetadataEvent.updateFromPast(first, name = "luna 2", createdAt = 1740669817))

        val metas = second.profileImageMetas()
        assertMeta(pictureMeta, metas.picture)
        assertMeta(bannerMeta, metas.banner)
        assertEquals(2, second.tags.count { it[0] == "imeta" })
    }

    @Test
    fun updateDropsImetaOfReplacedImage() {
        val first =
            signer.sign(
                MetadataEvent.createNew(
                    picture = picture,
                    banner = banner,
                    pictureMeta = pictureMeta,
                    bannerMeta = bannerMeta,
                    createdAt = 1740669816,
                ),
            )

        val newPicture = "https://blossom.example/9999.png"
        val second = signer.sign(MetadataEvent.updateFromPast(first, picture = newPicture, createdAt = 1740669817))

        val metas = second.profileImageMetas()
        assertNull(metas.picture)
        assertMeta(bannerMeta, metas.banner)
        assertEquals(1, second.tags.count { it[0] == "imeta" })
    }

    @Test
    fun updateDropsImetaOfRemovedImage() {
        val first =
            signer.sign(
                MetadataEvent.createNew(
                    picture = picture,
                    banner = banner,
                    pictureMeta = pictureMeta,
                    bannerMeta = bannerMeta,
                    createdAt = 1740669816,
                ),
            )

        val second = signer.sign(MetadataEvent.updateFromPast(first, banner = "", createdAt = 1740669817))

        assertNull(second.contactMetaData()?.banner)
        assertMeta(pictureMeta, second.profileImageMetas().picture)
        assertEquals(1, second.tags.count { it[0] == "imeta" })
    }

    @Test
    fun updateReplacesImetaOfNewImage() {
        val first = signer.sign(MetadataEvent.createNew(picture = "https://old.example/a.jpg", createdAt = 1740669816))

        val second =
            signer.sign(
                MetadataEvent.updateFromPast(
                    first,
                    picture = picture,
                    pictureMeta = pictureMeta,
                    createdAt = 1740669817,
                ),
            )

        assertMeta(pictureMeta, assertNotNull(second.profileImageMetas().picture))
        assertEquals(1, second.tags.count { it[0] == "imeta" })
    }

    @Test
    fun sameImageForPictureAndBannerGetsOneImeta() {
        val event =
            signer.sign(
                MetadataEvent.createNew(
                    picture = picture,
                    banner = picture,
                    pictureMeta = pictureMeta,
                    bannerMeta = pictureMeta,
                    createdAt = 1740669816,
                ),
            )

        assertEquals(1, event.tags.count { it[0] == "imeta" })
        assertMeta(pictureMeta, event.profileImageMetas().banner)
    }

    @Test
    fun updateKeepsImetaThatWasNeverPictureOrBanner() {
        val unrelated = arrayOf("imeta", "url https://other.example/badge.png", "dim 64x64")
        val first =
            event(
                "{\"picture\":\"$picture\"}",
                arrayOf("imeta", "url $picture", "dim 400x400"),
                unrelated,
            )

        val second = signer.sign(MetadataEvent.updateFromPast(first, picture = "https://blossom.example/new.png", createdAt = 1740669817))

        assertNull(second.profileImageMetas().picture)
        val imetas = second.tags.filter { it[0] == "imeta" }
        assertEquals(1, imetas.size)
        assertContentEquals(unrelated, imetas[0])
    }

    @Test
    fun matchesIgnoringSurroundingWhitespace() {
        val event =
            event(
                "{\"picture\":\"$picture \"}",
                arrayOf("imeta", "url $picture", "dim 400x400"),
            )

        assertNotNull(event.profileImageMetas().picture)

        // A name-only edit leaves the untrimmed picture alone and must keep its imeta.
        val second = signer.sign(MetadataEvent.updateFromPast(event, name = "luna", createdAt = 1740669817))
        assertEquals(1, second.tags.count { it[0] == "imeta" })
    }
}
