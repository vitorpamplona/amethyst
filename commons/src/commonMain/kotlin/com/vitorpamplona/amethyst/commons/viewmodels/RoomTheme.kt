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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.BackgroundTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.ColorTag

/**
 * Materialised room theme — the small surface the renderer
 * actually consumes. Each color is `0xAARRGGBB` packed into a
 * Long so commons stays free of the Android Color type;
 * `androidx.compose.ui.graphics.Color(value)` recovers the
 * platform type at the call site.
 *
 * `null` fields mean "use the platform default" — clients without
 * theming support pass a null [RoomTheme] entirely; clients with
 * partial support fall back per-field.
 */
@Immutable
data class RoomTheme(
    val backgroundArgb: Long?,
    val textArgb: Long?,
    val primaryArgb: Long?,
    val backgroundImageUrl: String?,
    val backgroundMode: BackgroundMode,
) {
    enum class BackgroundMode { COVER, TILE }

    companion object {
        val Empty = RoomTheme(null, null, null, null, BackgroundMode.COVER)

        /**
         * Project the theme tags off a kind-30312 event. Returns
         * [Empty] when the event has no theme tags so the renderer
         * can pass it unconditionally without an extra null branch.
         */
        fun from(event: MeetingSpaceEvent): RoomTheme {
            val colors = event.colors()
            val bg = event.background()

            fun pickHex(target: ColorTag.Target): Long? =
                colors
                    .firstOrNull { it.target == target }
                    ?.hex
                    ?.let { hexToOpaqueArgb(it) }

            return RoomTheme(
                backgroundArgb = pickHex(ColorTag.Target.BACKGROUND),
                textArgb = pickHex(ColorTag.Target.TEXT),
                primaryArgb = pickHex(ColorTag.Target.PRIMARY),
                backgroundImageUrl = bg?.url,
                backgroundMode =
                    when (bg?.mode) {
                        BackgroundTag.Mode.TILE -> BackgroundMode.TILE
                        else -> BackgroundMode.COVER
                    },
            )
        }

        /**
         * Pack a 6-char uppercase hex (validated by [ColorTag]) into
         * a fully-opaque ARGB Long: `0xFFRRGGBB`.
         */
        internal fun hexToOpaqueArgb(hex: String): Long {
            // ColorTag's parser already validated the input — this
            // is a straight string-to-int.
            val rgb = hex.toLong(radix = 16)
            return 0xFF000000L or rgb
        }
    }
}
