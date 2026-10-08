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
package com.vitorpamplona.quartz.experimental.profileTheme.definition

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.profileTheme.adoptedThemeDefinition
import com.vitorpamplona.quartz.experimental.profileTheme.adoptedThemeDefinitionHints
import com.vitorpamplona.quartz.experimental.profileTheme.tags.BackgroundTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ColorRole
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontRole
import com.vitorpamplona.quartz.experimental.profileTheme.tags.FontTag
import com.vitorpamplona.quartz.experimental.profileTheme.tags.ThemeColors
import com.vitorpamplona.quartz.experimental.profileTheme.themeBackground
import com.vitorpamplona.quartz.experimental.profileTheme.themeColor
import com.vitorpamplona.quartz.experimental.profileTheme.themeColors
import com.vitorpamplona.quartz.experimental.profileTheme.themeCreator
import com.vitorpamplona.quartz.experimental.profileTheme.themeCreatorHints
import com.vitorpamplona.quartz.experimental.profileTheme.themeDescription
import com.vitorpamplona.quartz.experimental.profileTheme.themeFont
import com.vitorpamplona.quartz.experimental.profileTheme.themeFontTags
import com.vitorpamplona.quartz.experimental.profileTheme.themeFonts
import com.vitorpamplona.quartz.experimental.profileTheme.themeTitle
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 36767, Theme Definition: a named, shareable custom UI theme (Ditto's `NIP.md`, also
 * published by Armada). One author may publish many, one per `d` slug; republishing the slug
 * edits it. Its colors, fonts and background use the same tags as the kind-16767
 * `ActiveProfileThemeEvent`, which is how a user "wears" a definition.
 *
 * Live events beyond the spec, all accepted:
 * - `t` = `theme` on every Ditto and Armada definition (written by [build] too, so theme galleries
 *   that query `#t` find it).
 * - `description`, which the spec lists only for 16767.
 * - `a`/`p` credit tags, which Armada writes when a definition is copied from someone else's
 *   (`["a", "36767:<creator>:<their-slug>"]`, `["p", "<creator>"]`) — the 16767 attribution
 *   convention applied to a definition. Read with the same accessors as 16767's.
 *
 * Searchable by [title] and [description], the only human-written text it carries. Colors, font
 * families, URLs and the slug are not indexed; `content` MUST be empty.
 */
@Immutable
class ThemeDefinitionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope,
    PubKeyHintProvider,
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() = listOfNotNull(title(), description()).joinToString("\n")

    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        if (!visitor.visit(title())) return
        visitor.visit(description())
    }

    override fun pubKeyHints(): List<PubKeyHint> = tags.themeCreatorHints()

    /** `ADOPTED_AUTHOR`: the creator of the definition this one was copied from (Armada's `p` credit). */
    override fun linkedPubKeys(): List<HexKey> = listOfNotNull(creator()?.pubKey)

    override fun addressHints(): List<AddressHint> = tags.adoptedThemeDefinitionHints()

    /** `ADOPTED`: the kind-36767 definition this one was copied from (Armada's `a` credit). */
    override fun linkedAddressIds(): List<String> = listOfNotNull(adoptedFrom()?.toTag())

    /** Human-readable theme name; required by the spec, null when the event omits it. */
    fun title() = tags.themeTitle()

    fun description() = tags.themeDescription()

    /** The three required colors, or null when the definition is incomplete. */
    fun colors() = tags.themeColors()

    fun color(role: ColorRole) = tags.themeColor(role)

    fun fonts() = tags.themeFontTags()

    fun bodyFont() = tags.themeFont(FontRole.BODY)

    fun titleFont() = tags.themeFont(FontRole.TITLE)

    fun background() = tags.themeBackground()

    fun adoptedFrom() = tags.adoptedThemeDefinition()

    fun creator() = tags.themeCreator()

    companion object {
        const val KIND = 36767
        const val KIND_STR = "36767"
        const val THEME_HASHTAG = "theme"

        fun build(
            dTag: String,
            title: String,
            colors: ThemeColors,
            fonts: List<FontTag> = emptyList(),
            background: BackgroundTag? = null,
            description: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ThemeDefinitionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(dTag)
            themeTitle(title)
            themeColors(colors)
            themeFonts(fonts)
            background?.let { themeBackground(it) }
            description?.let { themeDescription(it) }
            hashtag(THEME_HASHTAG)
            initializer()
        }
    }
}
