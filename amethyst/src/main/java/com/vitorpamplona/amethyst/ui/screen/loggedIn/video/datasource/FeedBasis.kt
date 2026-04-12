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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource

// Re-export from commons for backwards compatibility
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.LegacyMimeTypeMap as CommonsLegacyMimeTypeMap
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.LegacyMimeTypes as CommonsLegacyMimeTypes
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.PictureAndVideoKTags as CommonsPictureAndVideoKTags
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.PictureAndVideoKinds as CommonsPictureAndVideoKinds
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.PictureAndVideoLegacyKTags as CommonsPictureAndVideoLegacyKTags
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.PictureAndVideoLegacyKinds as CommonsPictureAndVideoLegacyKinds
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.SUPPORTED_VIDEO_FEED_MIME_TYPES as CommonsSUPPORTED_VIDEO_FEED_MIME_TYPES
import com.vitorpamplona.amethyst.commons.ui.screens.video.datasource.SUPPORTED_VIDEO_FEED_MIME_TYPES_SET as CommonsSUPPORTED_VIDEO_FEED_MIME_TYPES_SET

val SUPPORTED_VIDEO_FEED_MIME_TYPES = CommonsSUPPORTED_VIDEO_FEED_MIME_TYPES
val SUPPORTED_VIDEO_FEED_MIME_TYPES_SET = CommonsSUPPORTED_VIDEO_FEED_MIME_TYPES_SET
val PictureAndVideoKinds = CommonsPictureAndVideoKinds
val PictureAndVideoKTags = CommonsPictureAndVideoKTags
val PictureAndVideoLegacyKinds = CommonsPictureAndVideoLegacyKinds
val PictureAndVideoLegacyKTags = CommonsPictureAndVideoLegacyKTags
val LegacyMimeTypes = CommonsLegacyMimeTypes
val LegacyMimeTypeMap = CommonsLegacyMimeTypeMap
