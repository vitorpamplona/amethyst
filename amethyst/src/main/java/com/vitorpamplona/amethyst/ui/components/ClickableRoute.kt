/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.note.njumpLink
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists
import com.vitorpamplona.quartz.nip04Dm.PrivateDmEvent
import com.vitorpamplona.quartz.nip19Bech32Entities.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32Entities.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32Entities.toNIP19
import com.vitorpamplona.quartz.nip28PublicChat.ChannelCreateEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

@Composable
fun ClickableRoute(
    word: String,
    nip19: Nip19Parser.ParseReturn,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val entity = nip19.entity) {
        is NPub -> DisplayUser(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NProfile -> DisplayUser(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is com.vitorpamplona.quartz.nip19Bech32Entities.entities.Note -> DisplayEvent(entity.hex, null, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NEvent -> DisplayEvent(entity.hex, entity.kind, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NEmbed -> LoadAndDisplayEvent(entity.event, nip19.additionalChars, accountViewModel, nav)
        is NAddress -> DisplayAddress(entity, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NRelay -> {
            Text(word)
        }
        is NSec -> {
            Text(word)
        }
        else -> {
            Text(word)
        }
    }
}

@Composable
fun LoadOrCreateNote(
    event: Event,
    accountViewModel: AccountViewModel,
    content: @Composable (Note?) -> Unit,
) {
    var note by
        remember(event.id) { mutableStateOf<Note?>(accountViewModel.getNoteIfExists(event.id)) }

    if (note == null) {
        LaunchedEffect(key1 = event.id) {
            accountViewModel.checkGetOrCreateNote(event) { note = it }
        }
    }

    content(note)
}

@Composable
private fun LoadAndDisplayEvent(
    event: Event,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadOrCreateNote(event, accountViewModel) {
        if (it != null) {
            DisplayNoteLink(it, event.id, event.kind, additionalChars, accountViewModel, nav)
        } else {
            val externalLink = event.toNIP19()
            val uri = LocalUriHandler.current

            CreateClickableText(
                clickablePart = "@$externalLink",
                suffix = additionalChars,
                maxLines = 1,
                onClick = {
                    runCatching { uri.openUri(njumpLink(externalLink)) }
                },
            )
        }
    }
}

@Composable
fun DisplayEvent(
    hex: HexKey,
    kind: Int?,
    nip19: String,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(hex, accountViewModel) {
        if (it != null) {
            DisplayNoteLink(it, hex, kind, additionalChars, accountViewModel, nav)
        } else {
            val externalLink = njumpLink(nip19)
            val uri = LocalUriHandler.current

            CreateClickableText(
                clickablePart = remember(nip19) { "@$nip19" },
                suffix = additionalChars,
                maxLines = 1,
                onClick = {
                    runCatching { uri.openUri(externalLink) }
                },
            )
        }
    }
}

@Composable
private fun DisplayNoteLink(
    it: Note,
    hex: HexKey,
    kind: Int?,
    addedCharts: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by it.live().metadata.observeAsState()

    val note = remember(noteState) { noteState?.note } ?: return

    val channelHex = remember(noteState) { note.channelHex() }
    val noteIdDisplayNote = remember(noteState) { "@${note.idDisplayNote()}" }

    if (note.event is ChannelCreateEvent || kind == ChannelCreateEvent.KIND) {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route = remember(noteState) { "Channel/$hex" },
            nav = nav,
        )
    } else if (note.event is PrivateDmEvent || kind == PrivateDmEvent.KIND) {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route =
                remember(noteState) { (note.author?.pubkeyHex ?: hex).let { "RoomByAuthor/$it" } },
            nav = nav,
        )
    } else if (channelHex != null) {
        LoadChannel(baseChannelHex = channelHex, accountViewModel) { baseChannel ->
            val channelState by baseChannel.live.observeAsState()
            val channelDisplayName by
                remember(channelState) {
                    derivedStateOf { channelState?.channel?.toBestDisplayName() ?: noteIdDisplayNote }
                }

            CreateClickableText(
                clickablePart = channelDisplayName,
                suffix = addedCharts,
                route = remember(noteState) { "Channel/${baseChannel.idHex}" },
                nav = nav,
            )
        }
    } else {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route = remember(noteState) { "Event/$hex" },
            nav = nav,
        )
    }
}

@Composable
private fun DisplayAddress(
    nip19: NAddress,
    originalNip19: String,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var noteBase by remember(nip19) { mutableStateOf(accountViewModel.getNoteIfExists(nip19.aTag())) }

    if (noteBase == null) {
        LaunchedEffect(key1 = nip19) {
            accountViewModel.checkGetOrCreateAddressableNote(nip19.aTag()) { noteBase = it }
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()

        val route = remember(noteState) { "Note/${nip19.aTag()}" }
        val displayName = remember(noteState) { "@${noteState?.note?.idDisplayNote()}" }

        CreateClickableText(
            clickablePart = displayName,
            suffix = additionalChars,
            route = route,
            nav = nav,
        )
    }

    if (noteBase == null) {
        val uri = LocalUriHandler.current

        CreateClickableText(
            clickablePart = "@$originalNip19",
            suffix = additionalChars,
            maxLines = 1,
            onClick = {
                runCatching { uri.openUri(njumpLink(originalNip19)) }
            },
        )
    }
}

@Composable
public fun DisplayUser(
    userHex: HexKey,
    originalNip19: String,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var userBase by
        remember(userHex) {
            mutableStateOf(
                accountViewModel.getUserIfExists(userHex),
            )
        }

    if (userBase == null) {
        LaunchedEffect(key1 = userHex) {
            accountViewModel.checkGetOrCreateUser(userHex) { userBase = it }
        }
    }

    userBase?.let { RenderUserAsClickableText(it, additionalChars, nav) }

    if (userBase == null) {
        val uri = LocalUriHandler.current

        CreateClickableText(
            clickablePart = "@$originalNip19",
            suffix = additionalChars,
            maxLines = 1,
            onClick = {
                runCatching { uri.openUri(njumpLink(originalNip19)) }
            },
        )
    }
}

@Composable
public fun RenderUserAsClickableText(
    baseUser: User,
    additionalChars: String?,
    nav: INav,
) {
    val userState by baseUser.live().userMetadataInfo.observeAsState()

    CreateClickableTextWithEmoji(
        clickablePart = userState?.bestName() ?: ("@" + baseUser.pubkeyDisplayHex()),
        suffix = additionalChars?.ifBlank { null },
        maxLines = 1,
        route = "User/${baseUser.pubkeyHex}",
        nav = nav,
        tags = userState?.tags ?: EmptyTagList,
    )
}

@Composable
fun CreateClickableText(
    clickablePart: String,
    suffix: String?,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    route: String,
    nav: INav,
) {
    CreateClickableText(
        clickablePart,
        suffix,
        maxLines,
        overrideColor,
        fontWeight,
        fontSize,
    ) { nav.nav(route) }
}

@Composable
fun CreateClickableText(
    clickablePart: String,
    suffix: String?,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    onClick: (Int) -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    val text =
        remember(clickablePart, suffix) {
            val clickablePartStyle =
                SpanStyle(
                    fontSize = fontSize,
                    color = overrideColor ?: primaryColor,
                    fontWeight = fontWeight,
                )

            val nonClickablePartStyle =
                SpanStyle(
                    fontSize = fontSize,
                    color = overrideColor ?: onBackgroundColor,
                    fontWeight = fontWeight,
                )

            buildAnnotatedString {
                withStyle(clickablePartStyle) { append(clickablePart) }
                if (!suffix.isNullOrBlank()) {
                    withStyle(nonClickablePartStyle) { append(suffix) }
                }
            }
        }

    ClickableText(
        text = text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onClick = onClick,
    )
}

@Composable
fun ClickableText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onClick: (Int) -> Unit,
) {
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val pressIndicator =
        Modifier.pointerInput(onClick) {
            detectTapGestures { pos ->
                layoutResult.value?.let { layoutResult ->
                    onClick(layoutResult.getOffsetForPosition(pos))
                }
            }
        }

    Text(
        text = text,
        modifier = modifier.then(pressIndicator),
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        onTextLayout = {
            layoutResult.value = it
            onTextLayout(it)
        },
    )
}

@Composable
fun CustomEmojiChecker(
    text: String,
    tags: ImmutableListOfLists<String>?,
    onRegularText: @Composable (String) -> Unit,
    onEmojiText: @Composable (ImmutableList<CustomEmoji.Renderable>) -> Unit,
) {
    val mayContainEmoji by remember(text, tags) {
        mutableStateOf(CustomEmoji.fastMightContainEmoji(text, tags))
    }

    if (mayContainEmoji) {
        var emojiList by
            remember(text, tags) {
                mutableStateOf<ImmutableList<CustomEmoji.Renderable>?>(null)
            }

        LaunchedEffect(text, tags) {
            val newEmojiList = CustomEmoji.assembleAnnotatedList(text, tags)
            if (newEmojiList != null) {
                emojiList = newEmojiList
            }
        }

        emojiList?.let {
            onEmojiText(it)
        } ?: run {
            onRegularText(text)
        }
    } else {
        onRegularText(text)
    }
}

@Composable
fun CustomEmojiChecker(
    text: String,
    emojis: ImmutableMap<String, String>,
    onRegularText: @Composable (String) -> Unit,
    onEmojiText: @Composable (ImmutableList<CustomEmoji.Renderable>) -> Unit,
) {
    val mayContainEmoji by remember(text, emojis) {
        mutableStateOf(CustomEmoji.fastMightContainEmoji(text, emojis))
    }

    if (mayContainEmoji) {
        var emojiList by
            remember(text, emojis) {
                mutableStateOf<ImmutableList<CustomEmoji.Renderable>?>(null)
            }

        LaunchedEffect(text, emojis) {
            val newEmojiList = CustomEmoji.assembleAnnotatedList(text, emojis)
            if (newEmojiList != null) {
                emojiList = newEmojiList
            }
        }

        emojiList?.let {
            onEmojiText(it)
        } ?: run {
            onRegularText(text)
        }
    } else {
        onRegularText(text)
    }
}

@Composable
fun CreateTextWithEmoji(
    text: String,
    tags: ImmutableListOfLists<String>?,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) {
    CustomEmojiChecker(
        text,
        tags,
        onEmojiText = {
            val textColor =
                color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }
            val style =
                LocalTextStyle.current
                    .merge(
                        TextStyle(
                            color = textColor,
                            textAlign = TextAlign.Unspecified,
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                        ),
                    ).toSpanStyle()

            InLineIconRenderer(it, style, fontSize, maxLines, overflow, modifier)
        },
        onRegularText = {
            val textColor =
                color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }
            Text(
                text = it,
                color = textColor,
                textAlign = textAlign,
                fontWeight = fontWeight,
                fontSize = fontSize,
                maxLines = maxLines,
                overflow = overflow,
                modifier = modifier,
            )
        },
    )
}

@Composable
fun CreateTextWithEmoji(
    text: String,
    emojis: ImmutableMap<String, String>,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) {
    val textColor =
        color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }

    CustomEmojiChecker(
        text,
        emojis,
        onEmojiText = {
            val currentStyle = LocalTextStyle.current
            val style =
                remember(currentStyle) {
                    currentStyle
                        .merge(
                            TextStyle(
                                color = textColor,
                                textAlign = TextAlign.Unspecified,
                                fontWeight = fontWeight,
                                fontSize = fontSize,
                            ),
                        ).toSpanStyle()
                }

            InLineIconRenderer(it, style, fontSize, maxLines, overflow, modifier)
        },
        onRegularText = {
            Text(
                text = it,
                color = textColor,
                textAlign = textAlign,
                fontWeight = fontWeight,
                fontSize = fontSize,
                maxLines = maxLines,
                overflow = overflow,
                modifier = modifier,
            )
        },
    )
}

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    maxLines: Int = Int.MAX_VALUE,
    tags: ImmutableListOfLists<String>?,
    style: TextStyle,
    onClick: (Int) -> Unit,
) {
    CustomEmojiChecker(
        text = clickablePart,
        tags = tags,
        onRegularText = {
            ClickableText(
                text = AnnotatedString(clickablePart),
                style = style,
                maxLines = maxLines,
                onClick = onClick,
            )
        },
        onEmojiText = {
            ClickableInLineIconRenderer(it, maxLines, style.toSpanStyle()) { onClick(it) }
        },
    )
}

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    suffix: String? = null,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = TextUnit.Unspecified,
    route: String,
    nav: INav,
    tags: ImmutableListOfLists<String>?,
) {
    CustomEmojiChecker(
        text = clickablePart,
        tags = tags,
        onRegularText = {
            CreateClickableText(it, suffix, maxLines, overrideColor, fontWeight, fontSize, route, nav)
        },
        onEmojiText = {
            val nonClickablePartStyle =
                SpanStyle(
                    fontSize = fontSize,
                    color = overrideColor ?: MaterialTheme.colorScheme.onBackground,
                    fontWeight = fontWeight,
                )

            val clickablePartStyle =
                SpanStyle(
                    fontSize = fontSize,
                    color = overrideColor ?: MaterialTheme.colorScheme.primary,
                    fontWeight = fontWeight,
                )

            ClickableInLineIconRenderer(
                it,
                maxLines,
                clickablePartStyle,
                suffix,
                nonClickablePartStyle,
            ) {
                nav.nav(route)
            }
        },
    )
}

@Composable
fun ClickableInLineIconRenderer(
    wordsInOrder: ImmutableList<CustomEmoji.Renderable>,
    maxLines: Int = Int.MAX_VALUE,
    style: SpanStyle,
    suffix: String? = null,
    nonClickableStype: SpanStyle? = null,
    onClick: (Int) -> Unit,
) {
    val placeholderSize =
        remember(style) {
            if (style.fontSize == TextUnit.Unspecified) {
                22.sp
            } else {
                style.fontSize.times(1.1f)
            }
        }

    val inlineContent =
        wordsInOrder
            .mapIndexedNotNull { idx, value ->
                if (value is CustomEmoji.ImageUrlType) {
                    Pair(
                        "inlineContent$idx",
                        InlineTextContent(
                            Placeholder(
                                width = placeholderSize,
                                height = placeholderSize,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            ),
                        ) {
                            AsyncImage(
                                model = value.url,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(1.dp),
                            )
                        },
                    )
                } else {
                    null
                }
            }.associate { it.first to it.second }

    val annotatedText =
        buildAnnotatedString {
            wordsInOrder.forEachIndexed { idx, value ->
                withStyle(
                    style,
                ) {
                    if (value is CustomEmoji.TextType) {
                        append(value.text)
                    } else if (value is CustomEmoji.ImageUrlType) {
                        appendInlineContent("inlineContent$idx", "[icon]")
                    }
                }
            }

            if (suffix != null && nonClickableStype != null) {
                withStyle(nonClickableStype) {
                    append(suffix)
                }
            }
        }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val pressIndicator =
        Modifier.pointerInput(onClick) {
            detectTapGestures { pos ->
                layoutResult.value?.let { layoutResult -> onClick(layoutResult.getOffsetForPosition(pos)) }
            }
        }

    Text(
        text = annotatedText,
        modifier = pressIndicator,
        inlineContent = inlineContent,
        maxLines = maxLines,
        onTextLayout = { layoutResult.value = it },
    )
}

@Composable
fun InLineIconRenderer(
    wordsInOrder: ImmutableList<CustomEmoji.Renderable>,
    style: SpanStyle,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) {
    val placeholderSize =
        remember(fontSize) {
            if (fontSize == TextUnit.Unspecified) {
                22.sp
            } else {
                fontSize.times(1.1f)
            }
        }

    val inlineContent =
        wordsInOrder
            .mapIndexedNotNull { idx, value ->
                if (value is CustomEmoji.ImageUrlType) {
                    Pair(
                        "inlineContent$idx",
                        InlineTextContent(
                            Placeholder(
                                width = placeholderSize,
                                height = placeholderSize,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                            ),
                        ) {
                            AsyncImage(
                                model = value.url,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 0.dp),
                            )
                        },
                    )
                } else {
                    null
                }
            }.associate { it.first to it.second }

    val annotatedText =
        remember {
            buildAnnotatedString {
                wordsInOrder.forEachIndexed { idx, value ->
                    withStyle(
                        style,
                    ) {
                        if (value is CustomEmoji.TextType) {
                            append(value.text)
                        } else if (value is CustomEmoji.ImageUrlType) {
                            appendInlineContent("inlineContent$idx", "[icon]")
                        }
                    }
                }
            }
        }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}
