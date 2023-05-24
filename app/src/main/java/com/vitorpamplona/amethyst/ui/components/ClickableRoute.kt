package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NIP30Parser
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.nip19.Nip19
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ClickableRoute(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    if (nip19.type == Nip19.Type.USER) {
        DisplayUser(nip19, nav)
    } else if (nip19.type == Nip19.Type.ADDRESS) {
        DisplayAddress(nip19, nav)
    } else if (nip19.type == Nip19.Type.NOTE) {
        DisplayNote(nip19, nav)
    } else if (nip19.type == Nip19.Type.EVENT) {
        DisplayEvent(nip19, nav)
    } else {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayEvent(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    var noteBase by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            noteBase = LocalCache.checkGetOrCreateNote(nip19.hex)
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()
        val note = remember(noteState) { noteState?.note } ?: return
        val channel = remember(noteState) { note.channel() }

        if (note.event is ChannelCreateEvent) {
            CreateClickableText(
                clickablePart = "@${note.idDisplayNote()}",
                suffix = "${nip19.additionalChars} ",
                route = "Channel/${nip19.hex}",
                nav = nav
            )
        } else if (note.event is PrivateDmEvent) {
            CreateClickableText(
                clickablePart = "@${note.idDisplayNote()}",
                suffix = "${nip19.additionalChars} ",
                route = "Room/${note.author?.pubkeyHex}",
                nav = nav
            )
        } else if (channel != null) {
            CreateClickableText(
                clickablePart = channel.toBestDisplayName(),
                suffix = "${nip19.additionalChars} ",
                route = "Channel/${channel.idHex}",
                nav = nav
            )
        } else {
            CreateClickableText(
                clickablePart = "@${note.idDisplayNote()}",
                suffix = "${nip19.additionalChars} ",
                route = "Event/${nip19.hex}",
                nav = nav
            )
        }
    }

    if (noteBase == null) {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayNote(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    var noteBase by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            noteBase = LocalCache.checkGetOrCreateNote(nip19.hex)
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()
        val note = remember(noteState) { noteState?.note } ?: return
        val channel = note.channel()

        if (note.event is ChannelCreateEvent) {
            CreateClickableText(
                clickablePart = "@${note.idDisplayNote()}",
                suffix = "${nip19.additionalChars} ",
                route = "Channel/${nip19.hex}",
                nav = nav
            )
        } else if (note.event is PrivateDmEvent) {
            CreateClickableText(
                clickablePart = "@${note.idDisplayNote()}",
                suffix = "${nip19.additionalChars} ",
                route = "Room/${note.author?.pubkeyHex}",
                nav = nav
            )
        } else if (channel != null) {
            CreateClickableText(
                clickablePart = channel.toBestDisplayName(),
                suffix = "${nip19.additionalChars} ",
                route = "Channel/${note.channel()?.idHex}",
                nav = nav
            )
        } else {
            CreateClickableText(
                clickablePart = "@${note.idDisplayNote()}",
                suffix = "${nip19.additionalChars} ",
                route = "Note/${nip19.hex}",
                nav = nav
            )
        }
    }

    if (noteBase == null) {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayAddress(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    var noteBase by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            noteBase = LocalCache.checkGetOrCreateAddressableNote(nip19.hex)
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()
        val note = remember(noteState) { noteState?.note } ?: return

        CreateClickableText(
            clickablePart = "@${note.idDisplayNote()}",
            suffix = "${nip19.additionalChars} ",
            route = "Note/${nip19.hex}",
            nav = nav
        )
    }

    if (noteBase == null) {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
private fun DisplayUser(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    var userBase by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(key1 = nip19.hex) {
        withContext(Dispatchers.IO) {
            userBase = LocalCache.checkGetOrCreateUser(nip19.hex)
        }
    }

    userBase?.let {
        val userState by it.live().metadata.observeAsState()
        val route = remember { "User/${it.pubkeyHex}" }
        val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
        val userTags = remember(userState) { userState?.user?.info?.latestMetadata?.tags }

        if (userDisplayName != null) {
            CreateClickableTextWithEmoji(
                clickablePart = userDisplayName,
                suffix = "${nip19.additionalChars} ",
                tags = userTags,
                route = route,
                nav = nav
            )
        }
    }

    if (userBase == null) {
        Text(
            "@${nip19.hex}${nip19.additionalChars} "
        )
    }
}

@Composable
fun CreateClickableText(
    clickablePart: String,
    suffix: String,
    overrideColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    route: String,
    nav: (String) -> Unit
) {
    ClickableText(
        text = buildAnnotatedString {
            withStyle(
                LocalTextStyle.current.copy(color = overrideColor ?: MaterialTheme.colors.primary, fontWeight = fontWeight).toSpanStyle()
            ) {
                append(clickablePart)
            }
            withStyle(
                LocalTextStyle.current.copy(color = overrideColor ?: MaterialTheme.colors.onBackground, fontWeight = fontWeight).toSpanStyle()
            ) {
                append(suffix)
            }
        },
        onClick = { nav(route) }
    )
}

@Composable
fun CreateTextWithEmoji(
    text: String,
    tags: List<List<String>>?,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    val emojis = remember {
        tags?.filter { it.size > 2 && it[0] == "emoji" }?.associate { ":${it[1]}:" to it[2] } ?: emptyMap()
    }

    CreateTextWithEmoji(text, emojis, color, textAlign, fontWeight, fontSize, maxLines, overflow, modifier)
}

@Composable
fun CreateTextWithEmoji(
    text: String,
    emojis: Map<String, String>,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    val textColor = color.takeOrElse {
        LocalTextStyle.current.color.takeOrElse {
            LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
        }
    }

    if (emojis.isEmpty()) {
        Text(
            text = text,
            color = textColor,
            textAlign = textAlign,
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
    } else {
        val myList = remember {
            assembleAnnotatedList(text, emojis)
        }

        val style = LocalTextStyle.current.merge(
            TextStyle(
                color = textColor,
                textAlign = textAlign,
                fontWeight = fontWeight,
                fontSize = fontSize
            )
        ).toSpanStyle()

        InLineIconRenderer(myList, style, maxLines, overflow, modifier)
    }
}

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    tags: List<List<String>>?,
    style: TextStyle,
    onClick: (Int) -> Unit
) {
    val emojis = remember(tags) {
        tags?.filter { it.size > 2 && it[0] == "emoji" }?.associate { ":${it[1]}:" to it[2] } ?: emptyMap()
    }

    if (emojis.isEmpty()) {
        ClickableText(
            AnnotatedString(clickablePart),
            style = style,
            onClick = onClick
        )
    } else {
        val myList = remember {
            assembleAnnotatedList(clickablePart, emojis)
        }

        ClickableInLineIconRenderer(myList, style.toSpanStyle()) {
            onClick(it)
        }
    }
}

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    suffix: String,
    tags: List<List<String>>?,
    overrideColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    route: String,
    nav: (String) -> Unit
) {
    val emojis = remember(tags) {
        tags?.filter { it.size > 2 && it[0] == "emoji" }?.associate { ":${it[1]}:" to it[2] } ?: emptyMap()
    }

    if (emojis.isEmpty()) {
        CreateClickableText(clickablePart, suffix, overrideColor, fontWeight, route, nav)
    } else {
        val myList = remember {
            assembleAnnotatedList(clickablePart, emojis)
        }

        ClickableInLineIconRenderer(myList, LocalTextStyle.current.copy(color = overrideColor ?: MaterialTheme.colors.primary, fontWeight = fontWeight).toSpanStyle()) {
            nav(route)
        }

        val myList2 = remember {
            assembleAnnotatedList(suffix, emojis)
        }

        InLineIconRenderer(myList2, LocalTextStyle.current.copy(color = overrideColor ?: MaterialTheme.colors.onBackground, fontWeight = fontWeight).toSpanStyle())
    }
}

fun assembleAnnotatedList(text: String, emojis: Map<String, String>): List<Renderable> {
    return NIP30Parser().buildArray(text).map {
        val url = emojis[it]
        if (url != null) {
            ImageUrlType(url)
        } else {
            TextType(it)
        }
    }
}

open class Renderable()
class TextType(val text: String) : Renderable()
class ImageUrlType(val url: String) : Renderable()

@Composable
fun ClickableInLineIconRenderer(wordsInOrder: List<Renderable>, style: SpanStyle, onClick: (Int) -> Unit) {
    val inlineContent = wordsInOrder.mapIndexedNotNull { idx, value ->
        if (value is ImageUrlType) {
            Pair(
                "inlineContent$idx",
                InlineTextContent(
                    Placeholder(
                        width = 17.sp,
                        height = 17.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    AsyncImage(
                        model = value.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(1.dp)
                    )
                }
            )
        } else {
            null
        }
    }.associate { it.first to it.second }

    val annotatedText = buildAnnotatedString {
        wordsInOrder.forEachIndexed { idx, value ->
            withStyle(
                style
            ) {
                if (value is TextType) {
                    append(value.text)
                } else if (value is ImageUrlType) {
                    appendInlineContent("inlineContent$idx", "[icon]")
                }
            }
        }
    }

    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    val pressIndicator = Modifier.pointerInput(onClick) {
        detectTapGestures { pos ->
            layoutResult.value?.let { layoutResult ->
                onClick(layoutResult.getOffsetForPosition(pos))
            }
        }
    }

    BasicText(
        text = annotatedText,
        modifier = pressIndicator,
        inlineContent = inlineContent,
        onTextLayout = {
            layoutResult.value = it
        }
    )
}

@Composable
fun InLineIconRenderer(
    wordsInOrder: List<Renderable>,
    style: SpanStyle,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    val inlineContent = wordsInOrder.mapIndexedNotNull { idx, value ->
        if (value is ImageUrlType) {
            Pair(
                "inlineContent$idx",
                InlineTextContent(
                    Placeholder(
                        width = 17.sp,
                        height = 17.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    AsyncImage(
                        model = value.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(1.dp)
                    )
                }
            )
        } else {
            null
        }
    }.associate { it.first to it.second }

    val annotatedText = buildAnnotatedString {
        wordsInOrder.forEachIndexed { idx, value ->
            withStyle(
                style
            ) {
                if (value is TextType) {
                    append(value.text)
                } else if (value is ImageUrlType) {
                    appendInlineContent("inlineContent$idx", "[icon]")
                }
            }
        }
    }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}
