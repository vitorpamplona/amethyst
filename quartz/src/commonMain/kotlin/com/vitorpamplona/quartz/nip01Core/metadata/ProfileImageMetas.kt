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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip68Picture.PictureMeta
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag

/**
 * NIP-92 `imeta` descriptions of the images a kind 0 names in its `picture` and `banner`
 * fields: https://github.com/nostr-protocol/nips/pull/2494
 *
 * An `imeta` describes a field only when its `url` is that field's value; any other `imeta` on a
 * kind 0 is ignored. Both sides are compared trimmed, because profile values are trimmed
 * everywhere they are read and written, and a URL cannot carry surrounding whitespace anyway.
 */
class ProfileImageMetas(
    val picture: PictureMeta?,
    val banner: PictureMeta?,
) {
    companion object {
        val EMPTY = ProfileImageMetas(null, null)

        fun parse(
            tags: TagArray,
            picture: String?,
            banner: String?,
        ): ProfileImageMetas {
            val pictureUrl = picture?.trim()?.ifEmpty { null }
            val bannerUrl = banner?.trim()?.ifEmpty { null }
            if (pictureUrl == null && bannerUrl == null) return EMPTY

            var pictureMeta: PictureMeta? = null
            var bannerMeta: PictureMeta? = null

            tags.fastForEach { tag ->
                if (tag.isEmpty() || tag[0] != IMetaTag.TAG_NAME) return@fastForEach
                if ((pictureUrl == null || pictureMeta != null) && (bannerUrl == null || bannerMeta != null)) return@fastForEach
                IMetaTag.parse(tag)?.forEach { imeta ->
                    val url = imeta.url.trim()
                    if (pictureMeta == null && url == pictureUrl) pictureMeta = PictureMeta.parse(imeta)
                    if (bannerMeta == null && url == bannerUrl) bannerMeta = PictureMeta.parse(imeta)
                }
            }

            return if (pictureMeta == null && bannerMeta == null) EMPTY else ProfileImageMetas(pictureMeta, bannerMeta)
        }
    }
}

/** The `imeta` descriptions of this profile's `picture` and `banner`, if it carries any. */
fun MetadataEvent.profileImageMetas(metadata: UserMetadata? = contactMetaData()): ProfileImageMetas =
    if (metadata == null) {
        ProfileImageMetas.EMPTY
    } else {
        ProfileImageMetas.parse(tags, metadata.picture, metadata.banner)
    }

/** The trimmed first url of an `imeta` tag, or null if [tag] is not one. */
private fun imetaUrl(tag: Array<String>): String? {
    if (tag.isEmpty() || tag[0] != IMetaTag.TAG_NAME) return null
    return IMetaTag
        .parse(tag)
        ?.firstOrNull()
        ?.url
        ?.trim()
}

/**
 * Rewrites the kind 0's `imeta` tags after its `picture`/`banner` changed from
 * [previousPicture]/[previousBanner] to [picture]/[banner].
 *
 * Of the [previous] kind 0's `imeta`s, the ones for an image still in use are carried over and the
 * ones for an image the profile stopped using are dropped. An `imeta` whose url was never the
 * picture or banner is not ours to judge, so it is kept as is. A non-null [pictureMeta] /
 * [bannerMeta] whose url is the new field value replaces whatever described that image before.
 */
fun TagArrayBuilder<MetadataEvent>.updateProfileImageMetas(
    previous: TagArray,
    previousPicture: String?,
    previousBanner: String?,
    picture: String?,
    banner: String?,
    pictureMeta: PictureMeta? = null,
    bannerMeta: PictureMeta? = null,
) {
    val pictureUrl = picture?.trim()?.ifEmpty { null }
    val bannerUrl = banner?.trim()?.ifEmpty { null }

    val newPicture = pictureMeta?.takeIf { pictureUrl != null && it.url.trim() == pictureUrl }
    val newBanner = bannerMeta?.takeIf { bannerUrl != null && it.url.trim() == bannerUrl && it.url.trim() != newPicture?.url?.trim() }

    val replaced = setOfNotNull(newPicture?.url?.trim(), newBanner?.url?.trim())
    val inUse = setOfNotNull(pictureUrl, bannerUrl)
    val retired = setOfNotNull(previousPicture?.trim(), previousBanner?.trim()) - inUse
    val carried = mutableSetOf<String>()

    remove(IMetaTag.TAG_NAME)

    previous.fastForEach { tag ->
        val url = imetaUrl(tag) ?: return@fastForEach
        val keep =
            when (url) {
                in replaced -> false
                in inUse -> carried.add(url)
                in retired -> false
                else -> true
            }
        if (keep) add(tag)
    }

    newPicture?.let { add(it.toIMetaArray()) }
    newBanner?.let { add(it.toIMetaArray()) }
}
