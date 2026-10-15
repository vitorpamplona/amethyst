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
package com.vitorpamplona.amethyst.commons.unicodeEmoji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiPackGroup
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.Favorite
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.custom_emoji_picker_no_match
import com.vitorpamplona.amethyst.commons.resources.custom_emoji_picker_search
import com.vitorpamplona.amethyst.commons.resources.emoji_group_activities
import com.vitorpamplona.amethyst.commons.resources.emoji_group_animals_nature
import com.vitorpamplona.amethyst.commons.resources.emoji_group_flags
import com.vitorpamplona.amethyst.commons.resources.emoji_group_food_drink
import com.vitorpamplona.amethyst.commons.resources.emoji_group_objects
import com.vitorpamplona.amethyst.commons.resources.emoji_group_people_body
import com.vitorpamplona.amethyst.commons.resources.emoji_group_smileys_emotion
import com.vitorpamplona.amethyst.commons.resources.emoji_group_symbols
import com.vitorpamplona.amethyst.commons.resources.emoji_group_travel_places
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_favorites
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_open
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_skin_tone
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.insertAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.insertWordAtCursor
import com.vitorpamplona.amethyst.commons.unicodeEmoji.SkinTone
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmoji
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmojiCatalog
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmojiGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

private val PickerWidth = 352.dp
private val GridHeight = 280.dp
private val CellSize = 40.dp
private val SectionIconSize = 36.dp
private val EmojiStyle by lazy { TextStyle(fontFamily = EmojiFontFamily, fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.Center) }
private val TabEmojiStyle by lazy { TextStyle(fontFamily = EmojiFontFamily, fontSize = 18.sp, lineHeight = 22.sp, textAlign = TextAlign.Center) }

private val NoFavorites: StateFlow<List<Favorite>> = MutableStateFlow(emptyList())
private val NoPacks: StateFlow<List<EmojiPackGroup>> = MutableStateFlow(emptyList())

/**
 * A composer button that opens the emoji picker and writes the choice into [field] at the
 * cursor: a Unicode emoji as itself, a NIP-30 custom emoji as its `:shortcode:` (sending turns
 * that into an `emoji` tag, as typing `:word` and picking a suggestion does).
 *
 * Desktop has no system emoji keyboard, so this is the only way to type an emoji there. With
 * [emojiPacks] the picker also shows the account's favorites (its reaction choices) and a section
 * per selected custom emoji pack.
 */
@Composable
fun EmojiPickerButton(
    emojiPacks: EmojiPackState?,
    field: TextFieldState,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        EmojiPickerIcon(tint) { open = true }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EmojiPicker(
                emojiPacks = emojiPacks,
                onPickUnicode = {
                    field.insertAtCursor(it)
                    open = false
                },
                onPickCustom = {
                    field.insertWordAtCursor(":${it.code}:")
                    open = false
                },
            )
        }
    }
}

/** A button that opens the Unicode emoji palette and hands the chosen emoji to [onPick]. */
@Composable
fun UnicodeEmojiPickerButton(
    onPick: (String) -> Unit,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        EmojiPickerIcon(tint) { open = true }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EmojiPicker(
                emojiPacks = null,
                onPickUnicode = {
                    onPick(it)
                    open = false
                },
                onPickCustom = {},
            )
        }
    }
}

@Composable
private fun EmojiPickerIcon(
    tint: Color,
    onClick: () -> Unit,
) {
    val description = stringRes(Res.string.emoji_picker_open)
    HoverTooltip(description) {
        IconButton(onClick = onClick) {
            Icon(
                symbol = MaterialSymbols.EmojiEmotions,
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

@Immutable
private sealed class PaletteItem {
    /** A palette emoji, drawn in the picked skin tone. */
    @Immutable
    class Palette(
        val entry: UnicodeEmoji,
    ) : PaletteItem()

    /** A favorite, inserted exactly as the account saved it. */
    @Immutable
    class Literal(
        val emoji: String,
        val name: String?,
    ) : PaletteItem()

    @Immutable
    class Custom(
        val media: EmojiMedia,
    ) : PaletteItem()
}

@Immutable
private sealed class SectionIcon {
    @Immutable
    class Glyph(
        val emoji: String,
    ) : SectionIcon()

    @Immutable
    object Favorites : SectionIcon()

    @Immutable
    class Pack(
        val image: String?,
    ) : SectionIcon()
}

@Immutable
private class PaletteSection(
    val key: String,
    val label: String,
    val icon: SectionIcon,
    val items: List<PaletteItem>,
)

/**
 * One scrolling grid: the favorites, the Unicode groups, then one section per selected pack, with
 * a strip of section icons to jump between them, a name search across all of them, and the skin
 * tone. Without [emojiPacks] it shows only the Unicode groups.
 */
@Composable
fun EmojiPicker(
    emojiPacks: EmojiPackState?,
    onPickUnicode: (String) -> Unit,
    onPickCustom: (EmojiMedia) -> Unit,
) {
    val catalog by produceState(UnicodeEmojiPalette.cachedOrNull()) {
        if (value == null) value = UnicodeEmojiPalette.load()
    }
    val favorites by (emojiPacks?.favorites ?: NoFavorites).collectAsStateWithLifecycle()
    val packs by (emojiPacks?.myPacks ?: NoPacks).collectAsStateWithLifecycle()
    val search = rememberTextFieldState()
    val tone = UnicodeEmojiPalette.skinTone

    Column(Modifier.width(PickerWidth).padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                state = search,
                placeholder = { Text(stringRes(Res.string.custom_emoji_picker_search)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.weight(1f),
            )
            SkinToneSelector(tone) { UnicodeEmojiPalette.skinTone = it }
        }

        val loaded = catalog
        if (loaded == null) {
            Box(Modifier.fillMaxWidth().height(GridHeight), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val onPick: (PaletteItem) -> Unit = { item ->
            when (item) {
                is PaletteItem.Palette -> onPickUnicode(item.entry.withTone(tone))
                is PaletteItem.Literal -> onPickUnicode(item.emoji)
                is PaletteItem.Custom -> onPickCustom(item.media)
            }
        }

        val query =
            search.text
                .toString()
                .trim()
                .removePrefix(":")
                .removeSuffix(":")
        if (query.isEmpty()) {
            val sections = rememberSections(loaded, favorites, packs)
            SectionedPalette(sections, tone, onPick)
        } else {
            SearchResults(loaded, packs, query, tone, onPick)
        }
    }
}

@Composable
private fun rememberSections(
    catalog: UnicodeEmojiCatalog,
    favorites: List<Favorite>,
    packs: List<EmojiPackGroup>,
): List<PaletteSection> {
    val favoritesLabel = stringRes(Res.string.emoji_picker_favorites)
    val groupLabels = catalog.groups.map { it.label() }

    return remember(catalog, favorites, packs, favoritesLabel, groupLabels) {
        buildList {
            if (favorites.isNotEmpty()) {
                val items =
                    favorites.map {
                        when (it) {
                            is Favorite.Unicode -> PaletteItem.Literal(it.emoji, catalog.find(it.emoji)?.name)
                            is Favorite.Custom -> PaletteItem.Custom(it.media)
                        }
                    }
                add(PaletteSection("favorites", favoritesLabel, SectionIcon.Favorites, items))
            }
            catalog.groups.forEachIndexed { index, group ->
                add(PaletteSection("group:${group.name}", groupLabels[index], SectionIcon.Glyph(group.icon()), group.emojis.map { PaletteItem.Palette(it) }))
            }
            packs.forEach { pack ->
                val title = pack.title?.ifBlank { null } ?: pack.emojis.first().code
                add(PaletteSection("pack:${pack.address}", title, SectionIcon.Pack(pack.image ?: pack.emojis.first().link), pack.emojis.map { PaletteItem.Custom(it) }))
            }
        }
    }
}

@Composable
private fun SectionedPalette(
    sections: List<PaletteSection>,
    tone: SkinTone,
    onPick: (PaletteItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Each section is laid out as its header followed by its items.
    val headerIndices =
        remember(sections) {
            var next = 0
            sections.map { section -> next.also { next += 1 + section.items.size } }
        }
    val currentSection by remember(headerIndices) {
        derivedStateOf { headerIndices.indexOfLast { it <= gridState.firstVisibleItemIndex }.coerceAtLeast(0) }
    }

    // A lazy grid keeps its place by key, so favorites that load after the picker opened would sit
    // above the first visible row. Still parked on the old first section means nobody scrolled: go up.
    val firstKey = sections.firstOrNull()?.key
    LaunchedEffect(firstKey) {
        withFrameNanos { }
        if (headerIndices.size > 1 && gridState.firstVisibleItemIndex == headerIndices[1] && gridState.firstVisibleItemScrollOffset == 0) {
            gridState.scrollToItem(0)
        }
    }

    // The strip scrolls once packs push it past the picker's width; keep the current section in view.
    val stripScroll = rememberScrollState()
    val iconPx = with(LocalDensity.current) { SectionIconSize.roundToPx() }
    LaunchedEffect(currentSection, stripScroll.maxValue) {
        val centered = currentSection * iconPx - (stripScroll.viewportSize - iconPx) / 2
        stripScroll.animateScrollTo(centered.coerceIn(0, stripScroll.maxValue))
    }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(stripScroll)) {
        sections.forEachIndexed { index, section ->
            HoverTooltip(section.label) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(SectionIconSize)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (index == currentSection) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { scope.launch { gridState.scrollToItem(headerIndices[index]) } },
                ) {
                    SectionIconContent(section)
                }
            }
        }
    }

    EmojiGrid(gridState) {
        sections.forEach { section ->
            item(key = "header:" + section.key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            paletteItems(section.key, section.items, tone, onPick)
        }
    }
}

@Composable
private fun SectionIconContent(section: PaletteSection) {
    when (val icon = section.icon) {
        is SectionIcon.Glyph -> {
            Text(icon.emoji, style = TabEmojiStyle)
        }

        SectionIcon.Favorites -> {
            Icon(
                symbol = MaterialSymbols.Star,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is SectionIcon.Pack -> {
            AsyncImage(model = icon.image, contentDescription = null, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun SearchResults(
    catalog: UnicodeEmojiCatalog,
    packs: List<EmojiPackGroup>,
    query: String,
    tone: SkinTone,
    onPick: (PaletteItem) -> Unit,
) {
    val found =
        remember(catalog, packs, query) {
            // The account's own packs first: a search for a shortcode means one of those.
            val custom = packs.flatMap { it.emojis }.distinctBy { it.link }.filter { it.code.contains(query, ignoreCase = true) }
            val (starts, contains) = custom.partition { it.code.startsWith(query, ignoreCase = true) }
            (starts + contains).map { PaletteItem.Custom(it) } + catalog.search(query).map { PaletteItem.Palette(it) }
        }

    if (found.isEmpty()) {
        Text(
            text = stringRes(Res.string.custom_emoji_picker_no_match),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(GridHeight).padding(16.dp),
        )
        return
    }

    EmojiGrid(rememberLazyGridState()) {
        paletteItems("search", found, tone, onPick)
    }
}

/** A fixed height: the menu scrolls its content, and a lazy grid needs a bounded one. */
@Composable
private fun EmojiGrid(
    state: LazyGridState,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CellSize),
        state = state,
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth().height(GridHeight),
        content = content,
    )
}

private fun LazyGridScope.paletteItems(
    sectionKey: String,
    items: List<PaletteItem>,
    tone: SkinTone,
    onPick: (PaletteItem) -> Unit,
) {
    // The same emoji can sit in several sections (a favorite is also in its group), so keys are per section.
    itemsIndexed(
        items,
        key = { index, _ -> "$sectionKey#$index" },
        contentType = { _, item -> if (item is PaletteItem.Custom) "custom" else "emoji" },
    ) { _, item ->
        PaletteCell(item, tone, onPick)
    }
}

@Composable
private fun PaletteCell(
    item: PaletteItem,
    tone: SkinTone,
    onPick: (PaletteItem) -> Unit,
) {
    val tooltip =
        when (item) {
            is PaletteItem.Palette -> item.entry.name
            is PaletteItem.Literal -> item.name ?: item.emoji
            is PaletteItem.Custom -> ":${item.media.code}:"
        }

    HoverTooltip(tooltip) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(item) },
        ) {
            when (item) {
                is PaletteItem.Palette -> Text(item.entry.withTone(tone), style = EmojiStyle)
                is PaletteItem.Literal -> Text(item.emoji, style = EmojiStyle)
                is PaletteItem.Custom -> AsyncImage(model = item.media.link, contentDescription = tooltip, modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp))
            }
        }
    }
}

private val ToneSamples = listOf("✋", "✋🏻", "✋🏼", "✋🏽", "✋🏾", "✋🏿")

@Composable
private fun SkinToneSelector(
    tone: SkinTone,
    onChange: (SkinTone) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val description = stringRes(Res.string.emoji_picker_skin_tone)

    Box {
        HoverTooltip(description) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .padding(start = 4.dp)
                        .size(CellSize)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { open = true },
            ) {
                Text(ToneSamples[tone.ordinal], style = EmojiStyle)
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SkinTone.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(ToneSamples[option.ordinal], style = EmojiStyle) },
                    onClick = {
                        onChange(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** Group names as emoji-test.txt writes them. */
private val GroupLabels: Map<String, StringResource> =
    mapOf(
        "Smileys & Emotion" to Res.string.emoji_group_smileys_emotion,
        "People & Body" to Res.string.emoji_group_people_body,
        "Animals & Nature" to Res.string.emoji_group_animals_nature,
        "Food & Drink" to Res.string.emoji_group_food_drink,
        "Travel & Places" to Res.string.emoji_group_travel_places,
        "Activities" to Res.string.emoji_group_activities,
        "Objects" to Res.string.emoji_group_objects,
        "Symbols" to Res.string.emoji_group_symbols,
        "Flags" to Res.string.emoji_group_flags,
    )

@Composable
private fun UnicodeEmojiGroup.label(): String = GroupLabels[name]?.let { stringRes(it) } ?: name

private fun UnicodeEmojiGroup.icon(): String =
    when (name) {
        "Smileys & Emotion" -> "😀"
        "People & Body" -> "👋"
        "Animals & Nature" -> "🐻"
        "Food & Drink" -> "🍔"
        "Travel & Places" -> "🚗"
        "Activities" -> "⚽"
        "Objects" -> "💡"
        "Symbols" -> "🔣"
        "Flags" -> "🏁"
        else -> emojis.first().emoji
    }
