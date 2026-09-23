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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.resources.profile_image_of_user
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * A user's round avatar: their picture when [loadProfilePicture] allows it, otherwise (or while
 * loading / on error) their robohash, or a plain face icon when [loadRobohash] is off.
 * Animated pictures play only when [autoPlayGif].
 */
@Composable
fun UserPictureImage(
    userHex: String,
    userPicture: String?,
    userName: String?,
    size: Dp,
    modifier: Modifier,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    autoPlayGif: Boolean,
) {
    val myImageModifier =
        remember {
            modifier.size(size).clip(shape = CircleShape)
        }

    RobohashFallbackAsyncImage(
        robot = userHex,
        model = userPicture,
        contentDescription =
            if (userName != null) {
                stringRes(id = Res.string.profile_image_of_user, userName)
            } else {
                stringRes(id = Res.string.profile_image)
            },
        modifier = myImageModifier,
        contentScale = ContentScale.Crop,
        loadProfilePicture = loadProfilePicture,
        loadRobohash = loadRobohash,
        autoPlayGif = autoPlayGif,
    )
}
