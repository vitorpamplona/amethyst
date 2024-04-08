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
package com.vitorpamplona.amethyst.ui.components.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.ui.MediaRenderer
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.HashtagIcon
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.ui.components.DisplayFullNote
import com.vitorpamplona.amethyst.ui.components.DisplayUser
import com.vitorpamplona.amethyst.ui.components.LoadUrlPreview
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoadedBechLink
import com.vitorpamplona.amethyst.ui.theme.Font17SP
import com.vitorpamplona.amethyst.ui.theme.Size17Modifier
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import kotlinx.coroutines.runBlocking

class MarkdownMediaRenderer(
    val startOfText: String,
    val tags: ImmutableListOfLists<String>?,
    val canPreview: Boolean,
    val quotesLeft: Int,
    val backgroundColor: MutableState<Color>,
    val accountViewModel: AccountViewModel,
    val nav: (String) -> Unit,
) : MediaRenderer {
    val parser = RichTextParser()

    override fun shouldRenderLinkPreview(
        title: String?,
        uri: String,
    ): Boolean {
        return if (canPreview && uri.startsWith("http")) {
            if (title.isNullOrBlank() || title == uri) {
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    override fun renderImage(
        title: String?,
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        if (canPreview) {
            val content =
                parser.parseMediaUrl(
                    fullUrl = uri,
                    eventTags = tags ?: EmptyTagList,
                    description = title?.ifEmpty { null } ?: startOfText,
                ) ?: MediaUrlImage(url = uri, description = title?.ifEmpty { null } ?: startOfText)

            renderInlineFullWidth(richTextStringBuilder) {
                ZoomableContentView(
                    content = content,
                    roundedCorner = true,
                    accountViewModel = accountViewModel,
                )
            }
        } else {
            renderAsCompleteLink(title ?: uri, uri, richTextStringBuilder)
        }
    }

    override fun renderLinkPreview(
        title: String?,
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        val content = parser.parseMediaUrl(uri, eventTags = tags ?: EmptyTagList, startOfText)

        if (canPreview) {
            if (content != null) {
                renderInlineFullWidth(richTextStringBuilder) {
                    ZoomableContentView(
                        content = content,
                        roundedCorner = true,
                        accountViewModel = accountViewModel,
                    )
                }
            } else {
                if (!accountViewModel.settings.showUrlPreview.value) {
                    renderAsCompleteLink(title ?: uri, uri, richTextStringBuilder)
                } else {
                    renderInlineFullWidth(richTextStringBuilder) {
                        LoadUrlPreview(uri, title ?: uri, accountViewModel)
                    }
                }
            }
        } else {
            renderAsCompleteLink(title ?: uri, uri, richTextStringBuilder)
        }
    }

    override fun renderNostrUri(
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        // This should be fast, so it is ok.
        val loadedLink =
            accountViewModel.bechLinkCache.cached(uri)
                ?: runBlocking {
                    accountViewModel.bechLinkCache.update(uri)
                }

        val baseNote = loadedLink?.baseNote

        if (canPreview && quotesLeft > 0 && baseNote != null) {
            renderInlineFullWidth(richTextStringBuilder) {
                Row {
                    DisplayFullNote(
                        note = baseNote,
                        extraChars = loadedLink.nip19.additionalChars?.ifBlank { null },
                        quotesLeft = quotesLeft,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        } else if (loadedLink?.nip19 != null) {
            when (val entity = loadedLink.nip19.entity) {
                is Nip19Bech32.NPub -> renderObservableUser(entity.hex, richTextStringBuilder)
                is Nip19Bech32.NProfile -> renderObservableUser(entity.hex, richTextStringBuilder)
                is Nip19Bech32.Note -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is Nip19Bech32.NEvent -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is Nip19Bech32.NEmbed -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is Nip19Bech32.NAddress -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is Nip19Bech32.NRelay -> renderShortNostrURI(uri, richTextStringBuilder)
                is Nip19Bech32.NSec -> renderShortNostrURI(uri, richTextStringBuilder)
                else -> renderShortNostrURI(uri, richTextStringBuilder)
            }
        } else {
            renderShortNostrURI(uri, richTextStringBuilder)
        }
    }

    override fun renderHashtag(
        tag: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        val tagWithoutHash = tag.removePrefix("#")
        renderAsCompleteLink(tag, "nostr:Hashtag?id=$tagWithoutHash}", richTextStringBuilder)

        val hashtagIcon: HashtagIcon? = checkForHashtagWithIcon(tagWithoutHash)
        if (hashtagIcon != null) {
            renderInline(richTextStringBuilder) {
                Box(Size17Modifier) {
                    Icon(
                        imageVector = hashtagIcon.icon,
                        contentDescription = hashtagIcon.description,
                        tint = Color.Unspecified,
                        modifier = hashtagIcon.modifier,
                    )
                }
            }
        }
    }

    fun renderObservableUser(
        userHex: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        renderInline(richTextStringBuilder) {
            DisplayUser(userHex, null, accountViewModel, nav)
        }
    }

    fun renderObservableShortNoteUri(
        loadedLink: LoadedBechLink,
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        loadedLink.baseNote?.let { renderNoteObserver(it, richTextStringBuilder) }
        renderShortNostrURI(uri, richTextStringBuilder)
    }

    private fun renderNoteObserver(
        baseNote: Note,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        renderInvisible(richTextStringBuilder) {
            // Preloads note if not loaded yet.
            baseNote.live().metadata.observeAsState()
        }
    }

    private fun renderShortNostrURI(
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        val nip19 = "@" + uri.removePrefix("nostr:")

        renderAsCompleteLink(
            title =
                if (nip19.length > 16) {
                    nip19.replaceRange(8, nip19.length - 8, ":")
                } else {
                    nip19
                },
            destination = uri,
            richTextStringBuilder = richTextStringBuilder,
        )
    }

    private fun renderInvisible(
        richTextStringBuilder: RichTextString.Builder,
        innerComposable: @Composable () -> Unit,
    ) {
        richTextStringBuilder.appendInlineContent(
            content =
                InlineContent(
                    initialSize = {
                        IntSize(0.dp.roundToPx(), 0.dp.roundToPx())
                    },
                ) {
                    innerComposable()
                },
        )
    }

    private fun renderInline(
        richTextStringBuilder: RichTextString.Builder,
        innerComposable: @Composable () -> Unit,
    ) {
        richTextStringBuilder.appendInlineContent(
            content =
                InlineContent(
                    initialSize = {
                        IntSize(Font17SP.roundToPx(), Font17SP.roundToPx())
                    },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ) {
                    innerComposable()
                },
        )
    }

    private fun renderInlineFullWidth(
        richTextStringBuilder: RichTextString.Builder,
        innerComposable: @Composable () -> Unit,
    ) {
        richTextStringBuilder.appendInlineContentFullWidth(
            content =
                InlineContent(
                    initialSize = {
                        IntSize(Font17SP.roundToPx(), Font17SP.roundToPx())
                    },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ) {
                    innerComposable()
                },
        )
    }

    private fun renderAsCompleteLink(
        title: String,
        destination: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        richTextStringBuilder.pushFormat(
            RichTextString.Format.Link(destination = destination),
        )
        richTextStringBuilder.append(title)
        richTextStringBuilder.pop()
    }
}
