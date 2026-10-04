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
 * An `imeta` describes a field only when its `url` is exactly that field's value; any other
 * `imeta` on a kind 0 is ignored.
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
            if (picture.isNullOrBlank() && banner.isNullOrBlank()) return EMPTY

            var pictureMeta: PictureMeta? = null
            var bannerMeta: PictureMeta? = null

            tags.fastForEach { tag ->
                if (pictureMeta != null && bannerMeta != null) return@fastForEach
                IMetaTag.parse(tag)?.forEach { imeta ->
                    if (pictureMeta == null && imeta.url == picture) pictureMeta = PictureMeta.parse(imeta)
                    if (bannerMeta == null && imeta.url == banner) bannerMeta = PictureMeta.parse(imeta)
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

/** The first url of an `imeta` tag, or null if [tag] is not one. */
private fun imetaUrl(tag: Array<String>): String? = IMetaTag.parse(tag)?.firstOrNull()?.url

/**
 * Rewrites the kind 0's `imeta` tags so they describe exactly the images it now names.
 *
 * [previous] are the tags of the kind 0 being replaced: its `imeta`s for a `picture` or
 * `banner` that did not change are carried over, and the ones for images no longer in use
 * are dropped. A non-null [pictureMeta] / [bannerMeta] whose url is the current field value
 * replaces whatever described that image before.
 */
fun TagArrayBuilder<MetadataEvent>.updateProfileImageMetas(
    previous: TagArray,
    picture: String?,
    banner: String?,
    pictureMeta: PictureMeta? = null,
    bannerMeta: PictureMeta? = null,
) {
    remove(IMetaTag.TAG_NAME)

    val newPicture = pictureMeta?.takeIf { it.url == picture }
    val newBanner = bannerMeta?.takeIf { it.url == banner && it.url != newPicture?.url }

    val replaced = setOfNotNull(newPicture?.url, newBanner?.url)
    val inUse = setOfNotNull(picture?.ifBlank { null }, banner?.ifBlank { null })
    val carried = mutableSetOf<String>()

    previous.fastForEach { tag ->
        val url = imetaUrl(tag) ?: return@fastForEach
        if (url in inUse && url !in replaced && carried.add(url)) {
            add(tag)
        }
    }

    newPicture?.let { add(it.toIMetaArray()) }
    newBanner?.let { add(it.toIMetaArray()) }
}
