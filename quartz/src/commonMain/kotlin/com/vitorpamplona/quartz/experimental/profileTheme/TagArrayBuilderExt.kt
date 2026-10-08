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
package com.vitorpamplona.quartz.experimental.profileTheme

import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ColorTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ThemeColors
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag

// Writers shared by Ditto's two theme kinds (16767, 36767).

/** Replaces every `c` tag with exactly one per role, as the spec allows. */
fun <T : Event> TagArrayBuilder<T>.themeColors(colors: ThemeColors): TagArrayBuilder<T> {
    remove(ColorTag.TAG_NAME)
    return addAll(colors.toTags().map(ColorTag::toTagArray))
}

/**
 * Replaces every `f` tag with at most one per role, body first: "The `body` font tag MUST be
 * ordered before the `title` font tag", so clients that only read the first `f` get the body font.
 */
fun <T : Event> TagArrayBuilder<T>.themeFonts(fonts: List<FontTag>): TagArrayBuilder<T> {
    remove(FontTag.TAG_NAME)
    return addAll(fonts.distinctBy { it.role }.sortedBy { it.role.ordinal }.map(FontTag::toTagArray))
}

fun <T : Event> TagArrayBuilder<T>.themeBackground(background: BackgroundTag) = addUnique(background.toTagArray())

fun <T : Event> TagArrayBuilder<T>.themeTitle(title: String) = addUnique(TitleTag.assemble(title))

fun <T : Event> TagArrayBuilder<T>.themeDescription(description: String) = addUnique(DescriptionTag.assemble(description))

/** Credits the kind-36767 Theme Definition a theme was adopted from. */
fun <T : Event> TagArrayBuilder<T>.adoptedThemeDefinition(definition: ATag) = addUnique(definition.toATagArray())

/** Credits the theme's original creator. */
fun <T : Event> TagArrayBuilder<T>.themeCreator(creator: PTag) = addUnique(creator.toTagArray())
