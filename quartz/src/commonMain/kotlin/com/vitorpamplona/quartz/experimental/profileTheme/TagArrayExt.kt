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

import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ColorRole
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ColorTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontRole
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.RgbColor
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ThemeColors
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag

// Readers shared by Ditto's two theme kinds (16767 active theme, 36767 theme definition), which
// use the same "Shared Tag Definitions" for `c`, `f` and `bg`.

/** Every well-formed `c` tag, in tag order. */
fun TagArray.themeColorTags(): List<ColorTag> = mapNotNull(ColorTag::parse)

/** The first `c` color for [role] ("only one `c` tag per marker is allowed": the first wins). */
fun TagArray.themeColor(role: ColorRole): RgbColor? = fastFirstNotNullOfOrNull { tag -> ColorTag.parse(tag)?.takeIf { it.role == role }?.color }

/** The three required colors, or null when any of them is missing or malformed. */
fun TagArray.themeColors(): ThemeColors? {
    val background = themeColor(ColorRole.BACKGROUND) ?: return null
    val text = themeColor(ColorRole.TEXT) ?: return null
    val primary = themeColor(ColorRole.PRIMARY) ?: return null
    return ThemeColors(background, text, primary)
}

/** Every `f` tag with a known (or legacy, absent) role, in tag order. */
fun TagArray.themeFontTags(): List<FontTag> = mapNotNull(FontTag::parse)

/** The first font for [role] ("at most one `f` tag per role": the first wins). */
fun TagArray.themeFont(role: FontRole): FontTag? = fastFirstNotNullOfOrNull { tag -> FontTag.parse(tag)?.takeIf { it.role == role } }

/** The first `bg` tag with a url ("at most one `bg` tag is allowed per event"). */
fun TagArray.themeBackground(): BackgroundTag? = fastFirstNotNullOfOrNull(BackgroundTag::parse)

fun TagArray.themeTitle(): String? = fastFirstNotNullOfOrNull(TitleTag::parse)

fun TagArray.themeDescription(): String? = fastFirstNotNullOfOrNull(DescriptionTag::parse)

/** Whether [tag] is an `a` pointing at a kind-36767 theme definition. */
private fun isThemeDefinitionAddress(tag: Array<String>) = ATag.isTaggedWithKind(tag, ThemeDefinitionEvent.KIND_STR)

/**
 * The kind-36767 Theme Definition this theme was adopted from: the first well-formed `a` whose
 * kind is 36767. `a` tags of any other kind, or with a malformed address, are not attribution.
 */
fun TagArray.adoptedThemeDefinition(): ATag? = fastFirstNotNullOfOrNull { if (isThemeDefinitionAddress(it)) ATag.parse(it) else null }

/** Relay hints of the `a` attribution tags (36767 only). */
fun TagArray.adoptedThemeDefinitionHints(): List<AddressHint> = mapNotNull { if (isThemeDefinitionAddress(it)) ATag.parseAsHint(it) else null }

/** The theme's original creator, credited with a `p` tag when it was adopted from another user. */
fun TagArray.themeCreator(): PTag? = fastFirstNotNullOfOrNull(PTag::parse)

/** Relay hints of the `p` credit tags. */
fun TagArray.themeCreatorHints(): List<PubKeyHint> = mapNotNull(PTag::parseAsHint)
