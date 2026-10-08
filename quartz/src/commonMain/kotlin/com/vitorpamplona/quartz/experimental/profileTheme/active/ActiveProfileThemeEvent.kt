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
package com.vitorpamplona.quartz.experimental.profileTheme.active

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.profileTheme.adoptedThemeDefinition
import com.vitorpamplona.quartz.experimental.profileTheme.adoptedThemeDefinitionHints
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
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
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 16767, Active Profile Theme: the theme a user's profile is shown in (Ditto's `NIP.md`;
 * jotstr and Armada write it too). Replaceable, one per user: clients fetch
 * `{kinds: [16767], authors: [pubkey], limit: 1}` when showing a profile, and a NIP-09 deletion of
 * the kind removes it.
 *
 * Tags are Ditto's shared theme tags — three `c` colors, optional `f` fonts and `bg` — plus an
 * optional `title`/`description`, and the attribution of a theme worn from someone else: a `p`
 * with the creator and, when it came from a kind-36767 [ThemeDefinitionEvent], an `a` with its
 * address. "The adopter is wearing the theme, not claiming it": a theme whose [creatorPubKey]
 * is not the author should read "<title> by <creator>" and stay out of theme galleries.
 *
 * Not searchable: it is profile state, not a post, and its title is usually a copy of the
 * definition's, which is the event that search should find.
 *
 * The spec requires an `alt`; [build] does not write one (Quartz does not add boilerplate alt
 * tags). Live events always carry `["alt", "Active profile theme"]`; pass it through the
 * initializer when interop with a client that checks it matters.
 */
@Immutable
class ActiveProfileThemeEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    AddressHintProvider {
    override fun pubKeyHints(): List<PubKeyHint> = tags.themeCreatorHints()

    /** `ADOPTED_AUTHOR`: the original creator of the theme this user wears (`p`). */
    override fun linkedPubKeys(): List<HexKey> = listOfNotNull(creator()?.pubKey)

    override fun addressHints(): List<AddressHint> = tags.adoptedThemeDefinitionHints()

    /** `ADOPTED`: the kind-36767 Theme Definition this theme was adopted from (`a`). */
    override fun linkedAddressIds(): List<String> = listOfNotNull(adoptedFrom()?.toTag())

    /** The three required colors, or null when the theme is incomplete. */
    fun colors() = tags.themeColors()

    fun color(role: ColorRole) = tags.themeColor(role)

    fun fonts() = tags.themeFontTags()

    fun bodyFont() = tags.themeFont(FontRole.BODY)

    fun titleFont() = tags.themeFont(FontRole.TITLE)

    fun background() = tags.themeBackground()

    fun title() = tags.themeTitle()

    fun description() = tags.themeDescription()

    /** The kind-36767 definition this theme was adopted from, if any. */
    fun adoptedFrom() = tags.adoptedThemeDefinition()

    /** The credited creator (`p`), if any. */
    fun creator() = tags.themeCreator()

    /** Who made the theme: the credited `p`, else the adopted definition's author, else null (the wearer made it). */
    fun creatorPubKey(): HexKey? = creator()?.pubKey ?: adoptedFrom()?.pubKeyHex

    /** True when the theme is credited to someone other than its wearer. */
    fun isWornFromAnotherUser(): Boolean = creatorPubKey()?.let { it != pubKey } ?: false

    companion object {
        const val KIND = 16767

        fun build(
            colors: ThemeColors,
            fonts: List<FontTag> = emptyList(),
            background: BackgroundTag? = null,
            title: String? = null,
            description: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ActiveProfileThemeEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            themeColors(colors)
            themeFonts(fonts)
            background?.let { themeBackground(it) }
            title?.let { themeTitle(it) }
            description?.let { themeDescription(it) }
            initializer()
        }

        /**
         * Wears [definition]: copies its colors, fonts, background, title and description, and
         * credits it with an `a` to its address and a `p` to its author, as the spec's
         * Attribution section requires. Null when the definition lacks one of the three colors.
         */
        fun adopt(
            definition: ThemeDefinitionEvent,
            relayHint: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ActiveProfileThemeEvent>.() -> Unit = {},
        ) = definition.colors()?.let { colors ->
            build(colors, definition.fonts(), definition.background(), definition.title(), definition.description(), createdAt) {
                adoptedThemeDefinition(ATag(definition.kind, definition.pubKey, definition.dTag(), relayHint))
                themeCreator(PTag(definition.pubKey, relayHint))
                initializer()
            }
        }

        /**
         * Wears another user's active [theme]: "Copying another user's 16767 carries its `a`/`p`
         * tags forward, or credits that user if it has none." Null when the theme lacks one of the
         * three colors.
         */
        fun adopt(
            theme: ActiveProfileThemeEvent,
            relayHint: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ActiveProfileThemeEvent>.() -> Unit = {},
        ) = theme.colors()?.let { colors ->
            build(colors, theme.fonts(), theme.background(), theme.title(), theme.description(), createdAt) {
                val definition = theme.adoptedFrom()
                val creator = theme.creator()
                if (definition == null && creator == null) {
                    themeCreator(PTag(theme.pubKey, relayHint))
                } else {
                    definition?.let { adoptedThemeDefinition(it) }
                    creator?.let { themeCreator(it) }
                }
                initializer()
            }
        }
    }
}
