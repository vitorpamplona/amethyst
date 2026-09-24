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
package com.vitorpamplona.amethyst.commons.ui.settings

import com.vitorpamplona.amethyst.commons.model.AccentColorType
import com.vitorpamplona.amethyst.commons.model.FontSizeType
import com.vitorpamplona.amethyst.commons.model.ProfileGalleryType
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.model.WarningType
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accent_color_blue
import com.vitorpamplona.amethyst.commons.resources.accent_color_green
import com.vitorpamplona.amethyst.commons.resources.accent_color_orange
import com.vitorpamplona.amethyst.commons.resources.accent_color_pink
import com.vitorpamplona.amethyst.commons.resources.accent_color_purple
import com.vitorpamplona.amethyst.commons.resources.accent_color_red
import com.vitorpamplona.amethyst.commons.resources.content_warning_hide_all_sensitive_content_option
import com.vitorpamplona.amethyst.commons.resources.content_warning_see_warnings_option
import com.vitorpamplona.amethyst.commons.resources.content_warning_show_all_sensitive_content_option
import com.vitorpamplona.amethyst.commons.resources.dark
import com.vitorpamplona.amethyst.commons.resources.font_size_huge
import com.vitorpamplona.amethyst.commons.resources.font_size_large
import com.vitorpamplona.amethyst.commons.resources.font_size_normal
import com.vitorpamplona.amethyst.commons.resources.font_size_small
import com.vitorpamplona.amethyst.commons.resources.gallery_type_classic
import com.vitorpamplona.amethyst.commons.resources.gallery_type_modern
import com.vitorpamplona.amethyst.commons.resources.light
import com.vitorpamplona.amethyst.commons.resources.system
import org.jetbrains.compose.resources.StringResource

/**
 * The display label for each UI-settings enum.
 *
 * These used to be a constructor argument on the enums themselves, which pinned
 * `UiSettings` to `commonsUI` — a [StringResource] comes from the generated `Res`
 * class, and `commons` cannot see it. The settings are plain data that the CLI and
 * any headless front end may read, so the data moved to `commons/model` and the
 * labels stayed here, following the same extension-property shape the Tor settings
 * already use (`ui.tor.resourceId`).
 *
 * Each `when` is exhaustive over its enum, so a new constant is a compile error
 * here rather than a missing label at runtime — the same guarantee the constructor
 * argument gave.
 *
 * Only the enums something actually labels are here. `ConnectivityType`,
 * `FeatureSetType` and `FontFamilyType` carried a `resourceId` that nothing read —
 * their pickers are segmented rows and use the narrower `shortLabelRes` helpers in
 * `AppSettingsScreen` instead — and `BooleanType`'s was misspelled `reourceId`,
 * which is how it went unnoticed. They are dropped rather than carried over; the
 * strings they pointed at are still used by those short labels.
 */
val ThemeType.resourceId: StringResource
    get() =
        when (this) {
            ThemeType.SYSTEM -> Res.string.system
            ThemeType.LIGHT -> Res.string.light
            ThemeType.DARK -> Res.string.dark
        }

val AccentColorType.resourceId: StringResource
    get() =
        when (this) {
            AccentColorType.PURPLE -> Res.string.accent_color_purple
            AccentColorType.BLUE -> Res.string.accent_color_blue
            AccentColorType.GREEN -> Res.string.accent_color_green
            AccentColorType.ORANGE -> Res.string.accent_color_orange
            AccentColorType.RED -> Res.string.accent_color_red
            AccentColorType.PINK -> Res.string.accent_color_pink
        }

val FontSizeType.resourceId: StringResource
    get() =
        when (this) {
            FontSizeType.SMALL -> Res.string.font_size_small
            FontSizeType.NORMAL -> Res.string.font_size_normal
            FontSizeType.LARGE -> Res.string.font_size_large
            FontSizeType.HUGE -> Res.string.font_size_huge
        }

val ProfileGalleryType.resourceId: StringResource
    get() =
        when (this) {
            ProfileGalleryType.CLASSIC -> Res.string.gallery_type_classic
            ProfileGalleryType.MODERN -> Res.string.gallery_type_modern
        }

val WarningType.resourceId: StringResource
    get() =
        when (this) {
            WarningType.WARN -> Res.string.content_warning_see_warnings_option
            WarningType.SHOW -> Res.string.content_warning_show_all_sensitive_content_option
            WarningType.HIDE -> Res.string.content_warning_hide_all_sensitive_content_option
        }
