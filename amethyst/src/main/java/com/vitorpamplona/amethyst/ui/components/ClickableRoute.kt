/**
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
package com.vitorpamplona.amethyst.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.njumpLink
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.EmptyTagList
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.ImmutableListOfLists
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNIP19
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
        is NNote -> DisplayEvent(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NEvent -> DisplayEvent(entity.hex, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NEmbed -> LoadAndDisplayEvent(entity.event, nip19.additionalChars, accountViewModel, nav)
        is NAddress -> DisplayAddress(entity, nip19.nip19raw, nip19.additionalChars, accountViewModel, nav)
        is NRelay -> Text(word)
        is NSec -> Text(word)
        else -> Text(word)
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
            note = accountViewModel.noteFromEvent(event)
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
            DisplayNoteLink(it, event.id, additionalChars, accountViewModel, nav)
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
    nip19: String,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(hex, accountViewModel) {
        if (it != null) {
            DisplayNoteLink(it, hex, additionalChars, accountViewModel, nav)
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
    addedCharts: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(it, accountViewModel)
    val noteIdDisplayNote = remember(noteState) { "@${noteState.note.idDisplayNote()}" }

    val route = routeFor(it, accountViewModel.account) ?: Route.EventRedirect(hex)

    CreateClickableText(
        clickablePart = noteIdDisplayNote,
        suffix = addedCharts,
        route = route,
        nav = nav,
    )
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
            noteBase = accountViewModel.getOrCreateAddressableNote(nip19.address())
        }
    }

    noteBase?.let {
        val noteState by observeNote(it, accountViewModel)

        val route = remember(noteState) { Route.Note(nip19.aTag()) }
        val displayName = remember(noteState) { "@${noteState.note.idDisplayNote()}" }

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
fun DisplayUser(
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
            userBase = accountViewModel.checkGetOrCreateUser(userHex)
        }
    }

    userBase?.let { RenderUserAsClickableText(it, additionalChars, accountViewModel, nav) }

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
fun RenderUserAsClickableText(
    baseUser: User,
    additionalChars: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userState by observeUserInfo(baseUser, accountViewModel)

    CreateClickableTextWithEmoji(
        clickablePart = "@" + (userState?.bestName() ?: baseUser.pubkeyDisplayHex()),
        suffix = additionalChars?.ifBlank { null },
        maxLines = 1,
        route = remember(baseUser) { routeFor(baseUser) },
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
    route: Route,
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
    onClick: () -> Unit,
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

            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "clickable",
                        styles = TextLinkStyles(clickablePartStyle),
                    ) {
                        onClick()
                    },
                ) {
                    append(clickablePart)
                }
                if (!suffix.isNullOrBlank()) {
                    val nonClickablePartStyle =
                        SpanStyle(
                            fontSize = fontSize,
                            color = overrideColor ?: onBackgroundColor,
                            fontWeight = fontWeight,
                        )

                    withStyle(nonClickablePartStyle) { append(suffix) }
                }
            }
        }

    Text(
        text = text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
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
        mutableStateOf(CustomEmoji.fastMightContainEmoji(text, tags?.lists))
    }

    if (mayContainEmoji) {
        var emojiList by
            remember(text, tags) {
                mutableStateOf<ImmutableList<CustomEmoji.Renderable>?>(null)
            }

        LaunchedEffect(text, tags) {
            val newEmojiList = CustomEmoji.assembleAnnotatedList(text, tags?.lists)
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
    onClick: () -> Unit,
) {
    CustomEmojiChecker(
        text = clickablePart,
        tags = tags,
        onRegularText = {
            Text(
                text =
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable("me") {
                                onClick()
                            },
                        ) {
                            append(clickablePart)
                        }
                    },
                style = style,
                maxLines = maxLines,
            )
        },
        onEmojiText = {
            ClickableInLineIconRenderer(it, maxLines, style.toSpanStyle(), onClick = onClick)
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
    route: Route,
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
    onClick: () -> Unit,
) {
    val placeholderSize =
        remember(style) {
            if (style.fontSize == TextUnit.Unspecified) 22.sp else style.fontSize.times(1.1f)
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
                withLink(
                    LinkAnnotation.Clickable("link", TextLinkStyles(style)) {
                        onClick()
                    },
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

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        maxLines = maxLines,
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
            if (fontSize == TextUnit.Unspecified) 22.sp else fontSize.times(1.1f)
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
