/**
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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.ui.theme.isLight

/**
 * Shared avatar component that displays a user's profile picture with Robohash fallback.
 *
 * @param userHex The user's public key hex (used for Robohash generation)
 * @param pictureUrl Optional URL to the user's profile picture
 * @param size Size of the avatar (both width and height)
 * @param modifier Additional modifiers to apply
 * @param contentDescription Accessibility description
 * @param loadProfilePicture Whether to load the profile picture (false = show robohash only)
 * @param loadRobohash Whether to generate robohash (false = show generic icon)
 */
@Composable
fun UserAvatar(
    userHex: String,
    pictureUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loadProfilePicture: Boolean = true,
    loadRobohash: Boolean = true,
) {
    val avatarModifier =
        remember(size, modifier) {
            modifier
                .size(size)
                .clip(shape = CircleShape)
        }

    if (pictureUrl != null && loadProfilePicture) {
        // Show profile picture with robohash/icon as fallback
        val fallbackPainter =
            if (loadRobohash) {
                rememberVectorPainter(
                    image = CachedRobohash.get(userHex, MaterialTheme.colorScheme.isLight),
                )
            } else {
                rememberVectorPainter(
                    image = Icons.Default.Face,
                )
            }

        AsyncImage(
            model = pictureUrl,
            contentDescription = contentDescription,
            modifier = avatarModifier,
            placeholder = fallbackPainter,
            fallback = fallbackPainter,
            error = fallbackPainter,
            alignment = Alignment.Center,
            contentScale = ContentScale.Crop,
            alpha = DefaultAlpha,
            colorFilter = null,
            filterQuality = DrawScope.DefaultFilterQuality,
        )
    } else if (loadRobohash) {
        // Show robohash only
        Image(
            imageVector = CachedRobohash.get(userHex, MaterialTheme.colorScheme.isLight),
            contentDescription = contentDescription,
            modifier = avatarModifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        // Show generic icon
        Image(
            imageVector = Icons.Default.Face,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            modifier = avatarModifier,
            contentScale = ContentScale.Crop,
        )
    }
}
