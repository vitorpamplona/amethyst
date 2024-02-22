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
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip30CustomEmoji
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

@Composable
fun ClickableRoute(
    nip19: Nip19Bech32.Return,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    when (nip19.type) {
        Nip19Bech32.Type.USER -> {
            DisplayUser(nip19, accountViewModel, nav)
        }
        Nip19Bech32.Type.ADDRESS -> {
            DisplayAddress(nip19, accountViewModel, nav)
        }
        Nip19Bech32.Type.NOTE -> {
            DisplayNote(nip19, accountViewModel, nav)
        }
        Nip19Bech32.Type.EVENT -> {
            DisplayEvent(nip19, accountViewModel, nav)
        }
        else -> {
            Text(
                remember { "@${nip19.hex}${nip19.additionalChars}" },
            )
        }
    }
}

@Composable
private fun DisplayEvent(
    nip19: Nip19Bech32.Return,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadNote(nip19.hex, accountViewModel) {
        if (it != null) {
            DisplayNoteLink(it, nip19, accountViewModel, nav)
        } else {
            CreateClickableText(
                clickablePart = remember(nip19) { "@${nip19.hex.toShortenHex()}" },
                suffix = nip19.additionalChars,
                route = remember(nip19) { "Event/${nip19.hex}" },
                nav = nav,
            )
        }
    }
}

@Composable
private fun DisplayNote(
    nip19: Nip19Bech32.Return,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadNote(nip19.hex, accountViewModel = accountViewModel) {
        if (it != null) {
            DisplayNoteLink(it, nip19, accountViewModel, nav)
        } else {
            CreateClickableText(
                clickablePart = remember(nip19) { "@${nip19.hex.toShortenHex()}" },
                suffix = nip19.additionalChars,
                route = remember(nip19) { "Event/${nip19.hex}" },
                nav = nav,
            )
        }
    }
}

@Composable
private fun DisplayNoteLink(
    it: Note,
    nip19: Nip19Bech32.Return,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by it.live().metadata.observeAsState()

    val note = remember(noteState) { noteState?.note } ?: return

    val channelHex = remember(noteState) { note.channelHex() }
    val noteIdDisplayNote = remember(noteState) { "@${note.idDisplayNote()}" }
    val addedCharts = nip19.additionalChars

    if (note.event is ChannelCreateEvent || nip19.kind == ChannelCreateEvent.KIND) {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route = remember(noteState) { "Channel/${nip19.hex}" },
            nav = nav,
        )
    } else if (note.event is PrivateDmEvent || nip19.kind == PrivateDmEvent.KIND) {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route =
                remember(noteState) { (note.author?.pubkeyHex ?: nip19.hex).let { "RoomByAuthor/$it" } },
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
            route = remember(noteState) { "Event/${nip19.hex}" },
            nav = nav,
        )
    }
}

@Composable
private fun DisplayAddress(
    nip19: Nip19Bech32.Return,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var noteBase by remember(nip19) { mutableStateOf(accountViewModel.getNoteIfExists(nip19.hex)) }

    if (noteBase == null) {
        LaunchedEffect(key1 = nip19.hex) {
            accountViewModel.checkGetOrCreateAddressableNote(nip19.hex) { noteBase = it }
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()

        val route = remember(noteState) { "Note/${nip19.hex}" }
        val displayName = remember(noteState) { "@${noteState?.note?.idDisplayNote()}" }

        CreateClickableText(
            clickablePart = displayName,
            suffix = nip19.additionalChars,
            route = route,
            nav = nav,
        )
    }

    if (noteBase == null) {
        Text(
            remember { "@${nip19.hex}${nip19.additionalChars}" },
        )
    }
}

@Composable
private fun DisplayUser(
    nip19: Nip19Bech32.Return,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var userBase by
        remember(nip19) {
            mutableStateOf(
                accountViewModel.getUserIfExists(nip19.hex),
            )
        }

    if (userBase == null) {
        LaunchedEffect(key1 = nip19.hex) {
            accountViewModel.checkGetOrCreateUser(nip19.hex) { userBase = it }
        }
    }

    userBase?.let { RenderUserAsClickableText(it, nip19, nav) }

    if (userBase == null) {
        Text(
            remember { "@${nip19.hex}${nip19.additionalChars}" },
        )
    }
}

@Composable
private fun RenderUserAsClickableText(
    baseUser: User,
    nip19: Nip19Bech32.Return,
    nav: (String) -> Unit,
) {
    val userState by baseUser.live().metadata.observeAsState()
    val route = remember { "User/${baseUser.pubkeyHex}" }

    val userDisplayName by
        remember(userState) { derivedStateOf { userState?.user?.toBestDisplayName() } }

    val userTags by
        remember(userState) {
            derivedStateOf { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }
        }

    userDisplayName?.let {
        CreateClickableTextWithEmoji(
            clickablePart = it,
            maxLines = 1,
            route = route,
            nav = nav,
            tags = userTags,
        )

        nip19.additionalChars.ifBlank { null }?.let {
            Text(text = it, maxLines = 1)
        }
    }
}

@Composable
fun CreateClickableText(
    clickablePart: String,
    suffix: String?,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight? = null,
    route: String,
    nav: (String) -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    val text =
        remember(clickablePart, suffix) {
            val clickablePartStyle =
                SpanStyle(
                    color = overrideColor ?: primaryColor,
                    fontWeight = fontWeight,
                )

            val nonClickablePartStyle =
                SpanStyle(
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
        onClick = { nav(route) },
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
    onEmojiText: @Composable (ImmutableList<Nip30CustomEmoji.Renderable>) -> Unit,
) {
    val mayContainEmoji by remember(text, tags) {
        mutableStateOf(Nip30CustomEmoji.fastMightContainEmoji(text, tags))
    }

    if (mayContainEmoji) {
        var emojiList by
            remember(text, tags) {
                mutableStateOf<ImmutableList<Nip30CustomEmoji.Renderable>?>(null)
            }

        LaunchedEffect(text, tags) {
            val newEmojiList = Nip30CustomEmoji.assembleAnnotatedList(text, tags)
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
    onEmojiText: @Composable (ImmutableList<Nip30CustomEmoji.Renderable>) -> Unit,
) {
    val mayContainEmoji by remember(text, emojis) {
        mutableStateOf(Nip30CustomEmoji.fastMightContainEmoji(text, emojis))
    }

    if (mayContainEmoji) {
        var emojiList by
            remember(text, emojis) {
                mutableStateOf<ImmutableList<Nip30CustomEmoji.Renderable>?>(null)
            }

        LaunchedEffect(text, emojis) {
            val newEmojiList = Nip30CustomEmoji.assembleAnnotatedList(text, emojis)
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
    val textColor =
        color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }

    CustomEmojiChecker(
        text,
        tags,
        onEmojiText = {
            val style =
                LocalTextStyle.current
                    .merge(
                        TextStyle(
                            color = textColor,
                            textAlign = TextAlign.Unspecified,
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                        ),
                    )
                    .toSpanStyle()

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
                        )
                        .toSpanStyle()
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
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    route: String,
    nav: (String) -> Unit,
    tags: ImmutableListOfLists<String>?,
) {
    CustomEmojiChecker(
        text = clickablePart,
        tags = tags,
        onRegularText = {
            CreateClickableText(it, null, maxLines, overrideColor, fontWeight, route, nav)
        },
        onEmojiText = {
            val clickablePartStyle =
                SpanStyle(
                    color = overrideColor ?: MaterialTheme.colorScheme.primary,
                    fontWeight = fontWeight,
                )

            ClickableInLineIconRenderer(
                it,
                maxLines,
                clickablePartStyle,
            ) {
                nav(route)
            }
        },
    )
}

@Composable
fun ClickableInLineIconRenderer(
    wordsInOrder: ImmutableList<Nip30CustomEmoji.Renderable>,
    maxLines: Int = Int.MAX_VALUE,
    style: SpanStyle,
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
                if (value is Nip30CustomEmoji.ImageUrlType) {
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
                                modifier = Modifier.fillMaxSize().padding(1.dp),
                            )
                        },
                    )
                } else {
                    null
                }
            }
            .associate { it.first to it.second }

    val annotatedText =
        buildAnnotatedString {
            wordsInOrder.forEachIndexed { idx, value ->
                withStyle(
                    style,
                ) {
                    if (value is Nip30CustomEmoji.TextType) {
                        append(value.text)
                    } else if (value is Nip30CustomEmoji.ImageUrlType) {
                        appendInlineContent("inlineContent$idx", "[icon]")
                    }
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
    wordsInOrder: ImmutableList<Nip30CustomEmoji.Renderable>,
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
                if (value is Nip30CustomEmoji.ImageUrlType) {
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
                                modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp),
                            )
                        },
                    )
                } else {
                    null
                }
            }
            .associate { it.first to it.second }

    val annotatedText =
        remember {
            buildAnnotatedString {
                wordsInOrder.forEachIndexed { idx, value ->
                    withStyle(
                        style,
                    ) {
                        if (value is Nip30CustomEmoji.TextType) {
                            append(value.text)
                        } else if (value is Nip30CustomEmoji.ImageUrlType) {
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
