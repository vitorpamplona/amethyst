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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.moderation.LocalSpamExemptKeys
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.wot.LocalWoTReady
import com.vitorpamplona.amethyst.commons.wot.LocalWoTService

/**
 * Drop-in replacement for [UserAvatar] that overlays a Web-of-Trust
 * score chip when four gates all pass:
 *
 * 1. `LocalWoTService.current` is non-null (Desktop provides it; Android
 *    leaves it null and this composable falls back to a plain avatar).
 * 2. `LocalWoTReady.current == true` (initial batch fetch complete OR
 *    startup timeout elapsed).
 * 3. `userHex !in LocalSpamExemptKeys.current` — same set the hashtag-spam
 *    filter uses; contains the active user's pubkey plus everyone they
 *    follow. Skips self-badge and already-trusted accounts in one check.
 *
 * The score is read as a plain snapshot access from
 * `WoTService.scores` — Compose tracks the read per key, so avatars only
 * recompose when their own score changes.
 */
@Composable
fun WoTBadgedAvatar(
    userHex: String,
    pictureUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadProfilePicture: Boolean = true,
    loadRobohash: Boolean = true,
    useThumbnailCache: Boolean = false,
) {
    val service = LocalWoTService.current
    val ready = LocalWoTReady.current
    val exemptKeys = LocalSpamExemptKeys.current

    val score =
        if (service != null && ready && userHex !in exemptKeys) {
            service.scores[userHex] ?: 0
        } else {
            0
        }

    UserAvatar(
        userHex = userHex,
        pictureUrl = pictureUrl,
        size = size,
        modifier = modifier,
        contentDescription = contentDescription,
        loadProfilePicture = loadProfilePicture,
        loadRobohash = loadRobohash,
        useThumbnailCache = useThumbnailCache,
        badge =
            if (score > 0) {
                {
                    WoTBadge(
                        count = score,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            } else {
                null
            },
    )
}
