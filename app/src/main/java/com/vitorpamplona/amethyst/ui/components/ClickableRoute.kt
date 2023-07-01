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
import androidx.compose.runtime.Immutable
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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NIP30Parser
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ClickableRoute(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    when (nip19.type) {
        Nip19.Type.USER -> {
            DisplayUser(nip19, nav)
        }
        Nip19.Type.ADDRESS -> {
            DisplayAddress(nip19, nav)
        }
        Nip19.Type.NOTE -> {
            DisplayNote(nip19, nav)
        }
        Nip19.Type.EVENT -> {
            DisplayEvent(nip19, nav)
        }
        else -> {
            Text(
                remember {
                    "@${nip19.hex}${nip19.additionalChars}"
                }
            )
        }
    }
}

@Composable
private fun LoadNote(
    hex: String,
    content: @Composable (Note) -> Unit
) {
    var noteBase by remember(hex) { mutableStateOf(LocalCache.getNoteIfExists(hex)) }

    if (noteBase == null) {
        LaunchedEffect(key1 = hex) {
            launch(Dispatchers.IO) {
                noteBase = LocalCache.checkGetOrCreateNote(hex)
            }
        }
    }

    noteBase?.let {
        content(it)
    }
}

@Composable
private fun DisplayEvent(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    LoadNote(nip19.hex) {
        if (it != null) {
            DisplayNoteLink(it, nip19, nav)
        } else {
            CreateClickableText(
                clickablePart = remember(nip19) { "@${nip19.hex.toShortenHex()}" },
                suffix = nip19.additionalChars,
                route = remember(nip19) { "Event/${nip19.hex}" },
                nav = nav
            )
        }
    }
}

@Composable
private fun DisplayNote(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    LoadNote(nip19.hex) {
        if (it != null) {
            DisplayNoteLink(it, nip19, nav)
        } else {
            CreateClickableText(
                clickablePart = remember(nip19) { "@${nip19.hex.toShortenHex()}" },
                suffix = nip19.additionalChars,
                route = remember(nip19) { "Event/${nip19.hex}" },
                nav = nav
            )
        }
    }
}

@Composable
private fun DisplayNoteLink(
    it: Note,
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    val noteState by it.live().metadata.observeAsState()

    val note = remember(noteState) { noteState?.note } ?: return

    val channelHex = remember(noteState) { note.channelHex() }
    val noteIdDisplayNote = remember(noteState) { "@${note.idDisplayNote()}" }
    val addedCharts = remember { "${nip19.additionalChars}" }

    if (note.event is ChannelCreateEvent || nip19.kind == ChannelCreateEvent.kind) {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route = remember(noteState) { "Channel/${nip19.hex}" },
            nav = nav
        )
    } else if (note.event is PrivateDmEvent || nip19.kind == PrivateDmEvent.kind) {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route = remember(noteState) { "Room/${note.author?.pubkeyHex}" },
            nav = nav
        )
    } else if (channelHex != null) {
        LoadChannel(baseChannelHex = channelHex) { baseChannel ->
            val channelState by baseChannel.live.observeAsState()
            val channelDisplayName by remember(channelState) {
                derivedStateOf {
                    channelState?.channel?.toBestDisplayName() ?: noteIdDisplayNote
                }
            }

            CreateClickableText(
                clickablePart = channelDisplayName,
                suffix = addedCharts,
                route = remember(noteState) { "Channel/${baseChannel.idHex}" },
                nav = nav
            )
        }
    } else {
        CreateClickableText(
            clickablePart = noteIdDisplayNote,
            suffix = addedCharts,
            route = remember(noteState) { "Event/${nip19.hex}" },
            nav = nav
        )
    }
}

@Composable
private fun DisplayAddress(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    var noteBase by remember(nip19) { mutableStateOf(LocalCache.getNoteIfExists(nip19.hex)) }

    if (noteBase == null) {
        LaunchedEffect(key1 = nip19.hex) {
            launch(Dispatchers.IO) {
                noteBase = LocalCache.checkGetOrCreateAddressableNote(nip19.hex)
            }
        }
    }

    noteBase?.let {
        val noteState by it.live().metadata.observeAsState()

        val route = remember(noteState) { "Note/${nip19.hex}" }
        val displayName = remember(noteState) { "@${noteState?.note?.idDisplayNote()}" }
        val addedCharts = remember { "${nip19.additionalChars}" }

        CreateClickableText(
            clickablePart = displayName,
            suffix = addedCharts,
            route = route,
            nav = nav
        )
    }

    if (noteBase == null) {
        Text(
            remember {
                "@${nip19.hex}${nip19.additionalChars}"
            }
        )
    }
}

@Composable
private fun DisplayUser(
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    var userBase by remember(nip19) { mutableStateOf(LocalCache.getUserIfExists(nip19.hex)) }

    if (userBase == null) {
        LaunchedEffect(key1 = nip19.hex) {
            launch(Dispatchers.IO) {
                userBase = LocalCache.checkGetOrCreateUser(nip19.hex)
            }
        }
    }

    userBase?.let {
        RenderUserAsClickableText(it, nip19, nav)
    }

    if (userBase == null) {
        Text(
            remember {
                "@${nip19.hex}${nip19.additionalChars}"
            }
        )
    }
}

@Composable
private fun RenderUserAsClickableText(
    baseUser: User,
    nip19: Nip19.Return,
    nav: (String) -> Unit
) {
    val userState by baseUser.live().metadata.observeAsState()
    val route = remember { "User/${baseUser.pubkeyHex}" }

    val userDisplayName by remember(userState) {
        derivedStateOf {
            userState?.user?.toBestDisplayName()
        }
    }

    val userTags by remember(userState) {
        derivedStateOf {
            userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists()
        }
    }

    val addedCharts = remember(nip19) {
        "${nip19.additionalChars}"
    }

    userDisplayName?.let {
        CreateClickableTextWithEmoji(
            clickablePart = it,
            suffix = addedCharts,
            maxLines = 1,
            route = route,
            nav = nav,
            tags = userTags
        )
    }
}

@Composable
fun CreateClickableText(
    clickablePart: String,
    suffix: String?,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    route: String,
    nav: (String) -> Unit
) {
    val currentStyle = LocalTextStyle.current
    val primaryColor = MaterialTheme.colors.primary
    val onBackgroundColor = MaterialTheme.colors.onBackground

    val clickablePartStyle = remember(primaryColor, overrideColor) {
        currentStyle.copy(color = overrideColor ?: primaryColor, fontWeight = fontWeight).toSpanStyle()
    }

    val nonClickablePartStyle = remember(onBackgroundColor, overrideColor) {
        currentStyle.copy(color = overrideColor ?: onBackgroundColor, fontWeight = fontWeight).toSpanStyle()
    }

    val text = remember(clickablePartStyle, nonClickablePartStyle, clickablePart, suffix) {
        buildAnnotatedString {
            withStyle(clickablePartStyle) {
                append(clickablePart)
            }
            if (!suffix.isNullOrBlank()) {
                withStyle(nonClickablePartStyle) {
                    append(suffix)
                }
            }
        }
    }

    ClickableText(
        text = text,
        maxLines = maxLines,
        onClick = { nav(route) }
    )
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
    modifier: Modifier = Modifier
) {
    var emojiList by remember(text) { mutableStateOf<ImmutableList<Renderable>>(persistentListOf()) }

    LaunchedEffect(key1 = text) {
        launch(Dispatchers.Default) {
            val emojis =
                tags?.lists?.filter { it.size > 2 && it[0] == "emoji" }?.associate { ":${it[1]}:" to it[2] } ?: emptyMap()

            if (emojis.isNotEmpty()) {
                val newEmojiList = assembleAnnotatedList(text, emojis)
                if (newEmojiList.isNotEmpty()) {
                    emojiList = newEmojiList.toImmutableList()
                }
            }
        }
    }

    val textColor = color.takeOrElse {
        LocalTextStyle.current.color.takeOrElse {
            LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
        }
    }

    if (emojiList.isEmpty()) {
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
        val style = LocalTextStyle.current.merge(
            TextStyle(
                color = textColor,
                textAlign = textAlign,
                fontWeight = fontWeight,
                fontSize = fontSize
            )
        ).toSpanStyle()

        InLineIconRenderer(emojiList, style, maxLines, overflow, modifier)
    }
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
    modifier: Modifier = Modifier
) {
    var emojiList by remember(text) { mutableStateOf<ImmutableList<Renderable>>(persistentListOf()) }

    if (emojis.isNotEmpty()) {
        LaunchedEffect(key1 = text) {
            launch(Dispatchers.Default) {
                val newEmojiList = assembleAnnotatedList(text, emojis)
                if (newEmojiList.isNotEmpty()) {
                    emojiList = newEmojiList.toImmutableList()
                }
            }
        }
    }

    val textColor = color.takeOrElse {
        LocalTextStyle.current.color.takeOrElse {
            LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
        }
    }

    if (emojiList.isEmpty()) {
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
        val currentStyle = LocalTextStyle.current
        val style = remember(currentStyle) {
            currentStyle.merge(
                TextStyle(
                    color = textColor,
                    textAlign = textAlign,
                    fontWeight = fontWeight,
                    fontSize = fontSize
                )
            ).toSpanStyle()
        }

        InLineIconRenderer(emojiList, style, maxLines, overflow, modifier)
    }
}

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    maxLines: Int = Int.MAX_VALUE,
    tags: ImmutableListOfLists<String>?,
    style: TextStyle,
    onClick: (Int) -> Unit
) {
    var emojiList by remember(clickablePart) { mutableStateOf<ImmutableList<Renderable>>(persistentListOf()) }

    LaunchedEffect(key1 = clickablePart) {
        launch(Dispatchers.Default) {
            val emojis =
                tags?.lists?.filter { it.size > 2 && it[0] == "emoji" }?.associate { ":${it[1]}:" to it[2] } ?: emptyMap()

            if (emojis.isNotEmpty()) {
                val newEmojiList = assembleAnnotatedList(clickablePart, emojis)
                if (newEmojiList.isNotEmpty()) {
                    emojiList = newEmojiList.toImmutableList()
                }
            }
        }
    }

    if (emojiList.isEmpty()) {
        ClickableText(
            text = AnnotatedString(clickablePart),
            style = style,
            maxLines = maxLines,
            onClick = onClick
        )
    } else {
        ClickableInLineIconRenderer(emojiList, maxLines, style.toSpanStyle()) {
            onClick(it)
        }
    }
}

@Immutable
data class DoubleEmojiList(
    val part1: ImmutableList<Renderable>,
    val part2: ImmutableList<Renderable>
)

@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    suffix: String?,
    maxLines: Int = Int.MAX_VALUE,
    overrideColor: Color? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    route: String,
    nav: (String) -> Unit,
    tags: ImmutableListOfLists<String>?
) {
    var emojiLists by remember(clickablePart) {
        mutableStateOf<DoubleEmojiList?>(null)
    }

    LaunchedEffect(key1 = clickablePart) {
        launch(Dispatchers.Default) {
            val emojis =
                tags?.lists?.filter { it.size > 2 && it[0] == "emoji" }?.associate { ":${it[1]}:" to it[2] } ?: emptyMap()

            if (emojis.isNotEmpty()) {
                val newEmojiList1 = assembleAnnotatedList(clickablePart, emojis)
                val newEmojiList2 = suffix?.let { assembleAnnotatedList(it, emojis) } ?: emptyList<Renderable>()

                if (newEmojiList1.isNotEmpty() || newEmojiList2.isNotEmpty()) {
                    emojiLists = DoubleEmojiList(newEmojiList1.toImmutableList(), newEmojiList2.toImmutableList())
                }
            }
        }
    }

    if (emojiLists == null) {
        CreateClickableText(clickablePart, suffix, maxLines, overrideColor, fontWeight, route, nav)
    } else {
        ClickableInLineIconRenderer(
            emojiLists!!.part1,
            maxLines,
            LocalTextStyle.current.copy(color = overrideColor ?: MaterialTheme.colors.primary, fontWeight = fontWeight).toSpanStyle()
        ) {
            nav(route)
        }

        InLineIconRenderer(
            emojiLists!!.part2,
            LocalTextStyle.current.copy(color = overrideColor ?: MaterialTheme.colors.onBackground, fontWeight = fontWeight).toSpanStyle(),
            maxLines
        )
    }
}

suspend fun assembleAnnotatedList(text: String, emojis: Map<String, String>): ImmutableList<Renderable> {
    return NIP30Parser().buildArray(text).map {
        val url = emojis[it]
        if (url != null) {
            ImageUrlType(url)
        } else {
            TextType(it)
        }
    }.toImmutableList()
}

@Immutable
open class Renderable()

@Immutable
class TextType(val text: String) : Renderable()

@Immutable
class ImageUrlType(val url: String) : Renderable()

@Composable
fun ClickableInLineIconRenderer(
    wordsInOrder: ImmutableList<Renderable>,
    maxLines: Int = Int.MAX_VALUE,
    style: SpanStyle,
    onClick: (Int) -> Unit
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(1.dp)
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
        maxLines = maxLines,
        onTextLayout = {
            layoutResult.value = it
        }
    )
}

@Composable
fun InLineIconRenderer(
    wordsInOrder: ImmutableList<Renderable>,
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
                        width = 20.sp,
                        height = 20.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    AsyncImage(
                        model = value.url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 1.dp)
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
