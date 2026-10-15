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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.CustomEmojiGrid
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
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_open
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_skin_tone
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_tab_custom
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_tab_unicode
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.insertAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.insertWordAtCursor
import com.vitorpamplona.amethyst.commons.unicodeEmoji.SkinTone
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmoji
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmojiCatalog
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmojiGroup
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

private val PickerWidth = 352.dp
private val GridHeight = 280.dp
private val CellSize = 40.dp
private val EmojiStyle by lazy { TextStyle(fontFamily = EmojiFontFamily, fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.Center) }
private val TabEmojiStyle by lazy { TextStyle(fontFamily = EmojiFontFamily, fontSize = 18.sp, lineHeight = 22.sp, textAlign = TextAlign.Center) }

/**
 * A composer button that opens the emoji picker and writes the choice into [field] at the
 * cursor: a Unicode emoji as itself, a NIP-30 custom emoji as its `:shortcode:` (sending turns
 * that into an `emoji` tag, as typing `:word` and picking a suggestion does).
 *
 * Desktop has no system emoji keyboard, so the Unicode palette is the only way to type one there.
 * Without [emojiPacks] the picker shows only the Unicode palette.
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
            Column(Modifier.width(PickerWidth)) {
                UnicodeEmojiPane {
                    onPick(it)
                    open = false
                }
            }
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

/** The Unicode palette, with a tab for the account's NIP-30 custom emoji when [emojiPacks] is given. */
@Composable
fun EmojiPicker(
    emojiPacks: EmojiPackState?,
    onPickUnicode: (String) -> Unit,
    onPickCustom: (EmojiPackState.EmojiMedia) -> Unit,
) {
    Column(Modifier.width(PickerWidth)) {
        val showCustom = emojiPacks != null && UnicodeEmojiPalette.showCustomTab
        if (emojiPacks != null) {
            // Not a Material TabRow: the menu sizes itself by intrinsic width, which a TabRow
            // cannot answer (it measures its tabs at an infinite width and throws).
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                PickerTab(stringRes(Res.string.emoji_picker_tab_unicode), selected = !showCustom) { UnicodeEmojiPalette.showCustomTab = false }
                PickerTab(stringRes(Res.string.emoji_picker_tab_custom), selected = showCustom) { UnicodeEmojiPalette.showCustomTab = true }
            }
        }

        if (showCustom && emojiPacks != null) {
            CustomEmojiGrid(emojiPacks, Modifier.fillMaxWidth().padding(top = 8.dp), onPickCustom)
        } else {
            UnicodeEmojiPane(onPickUnicode)
        }
    }
}

@Composable
private fun RowScope.PickerTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    )
}

@Composable
private fun UnicodeEmojiPane(onPick: (String) -> Unit) {
    val catalog by produceState(UnicodeEmojiPalette.cachedOrNull()) {
        if (value == null) value = UnicodeEmojiPalette.load()
    }
    val search = rememberTextFieldState()
    val tone = UnicodeEmojiPalette.skinTone

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
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

        val query = search.text.toString().trim()
        if (query.isEmpty()) {
            GroupedPalette(loaded, tone, onPick)
        } else {
            SearchResults(loaded, query, tone, onPick)
        }
    }
}

@Composable
private fun GroupedPalette(
    catalog: UnicodeEmojiCatalog,
    tone: SkinTone,
    onPick: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Each group is laid out as its header followed by its emoji.
    val headerIndices =
        remember(catalog) {
            var next = 0
            catalog.groups.map { group -> next.also { next += 1 + group.emojis.size } }
        }
    val currentGroup by remember(headerIndices) {
        derivedStateOf { headerIndices.indexOfLast { it <= gridState.firstVisibleItemIndex }.coerceAtLeast(0) }
    }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        catalog.groups.forEachIndexed { index, group ->
            Box(Modifier.weight(1f)) {
                HoverTooltip(group.label()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (index == currentGroup) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { scope.launch { gridState.scrollToItem(headerIndices[index]) } },
                    ) {
                        Text(group.icon(), style = TabEmojiStyle)
                    }
                }
            }
        }
    }

    EmojiGrid(gridState) {
        catalog.groups.forEach { group ->
            item(key = "@" + group.name, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    text = group.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            emojiItems(group.emojis, tone, onPick)
        }
    }
}

@Composable
private fun SearchResults(
    catalog: UnicodeEmojiCatalog,
    query: String,
    tone: SkinTone,
    onPick: (String) -> Unit,
) {
    val found = remember(catalog, query) { catalog.search(query) }
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
        emojiItems(found, tone, onPick)
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

private fun LazyGridScope.emojiItems(
    emojis: List<UnicodeEmoji>,
    tone: SkinTone,
    onPick: (String) -> Unit,
) {
    items(emojis, key = { it.emoji }, contentType = { "emoji" }) { entry ->
        val emoji = entry.withTone(tone)
        HoverTooltip(entry.name) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(emoji) },
            ) {
                Text(emoji, style = EmojiStyle)
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
