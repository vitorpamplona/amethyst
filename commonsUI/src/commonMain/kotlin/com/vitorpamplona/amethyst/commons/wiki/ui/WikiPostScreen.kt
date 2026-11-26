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
package com.vitorpamplona.amethyst.commons.wiki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.content.CachedDocuments
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.article_summary
import com.vitorpamplona.amethyst.commons.resources.markdown_preview
import com.vitorpamplona.amethyst.commons.resources.publish
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.resources.url_slug
import com.vitorpamplona.amethyst.commons.resources.wiki_body_placeholder
import com.vitorpamplona.amethyst.commons.resources.wiki_edit_tab
import com.vitorpamplona.amethyst.commons.resources.wiki_editor_edit
import com.vitorpamplona.amethyst.commons.resources.wiki_editor_fork
import com.vitorpamplona.amethyst.commons.resources.wiki_editor_new
import com.vitorpamplona.amethyst.commons.resources.wiki_preview_empty
import com.vitorpamplona.amethyst.commons.resources.your_article_title
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.content.RenderDocument
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.types.wikilinkResolver
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address

/**
 * The wiki editor: a NIP-54 article's title, slug, summary and Djot body, with a Preview tab that
 * draws the body exactly as readers will see it (the same document renderer, wikilinks included).
 * [topic] fixes the slug of a new article; [base] is the version to edit (the account's own) or to
 * fork (anyone else's).
 */
@Composable
fun WikiPostScreen(
    topic: String?,
    base: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: WikiPostViewModel = rememberViewModel { WikiPostViewModel() }
    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, topic, base) {
        val baseNote = base?.let { Address.parse(it) }?.let { LocalCache.getAddressableNoteIfExists(it) }
        postViewModel.load(topic, baseNote)
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = if (postViewModel.isEdit) Res.string.save else Res.string.publish,
                titleRes =
                    when {
                        postViewModel.isEdit -> Res.string.wiki_editor_edit
                        postViewModel.isFork -> Res.string.wiki_editor_fork
                        else -> Res.string.wiki_editor_new
                    },
                isActive = postViewModel::canPost,
                onPost = {
                    accountViewModel.launchSigner {
                        val address = postViewModel.publish()
                        if (address != null) nav.popBack()
                    }
                },
                onCancel = { nav.popBack() },
            )
        },
    ) { pad ->
        Surface(modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePaddingSafe()) {
            WikiPostBody(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
private fun WikiPostBody(
    postViewModel: WikiPostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        SpellCheckedOutlinedTextField(
            value = postViewModel.title,
            onValueChange = postViewModel::onTitleChanged,
            placeholder = {
                Text(stringRes(Res.string.your_article_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.placeholderText)
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
        )

        OutlinedTextField(
            value = postViewModel.slug,
            onValueChange = postViewModel::onSlugChanged,
            label = { Text(stringRes(Res.string.url_slug)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
            enabled = !postViewModel.slugLocked,
        )

        SpellCheckedOutlinedTextField(
            value = postViewModel.summary,
            onValueChange = { postViewModel.summary = it },
            label = { Text(stringRes(Res.string.article_summary)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            maxLines = 3,
            keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Sentences),
        )

        Spacer(Modifier.height(8.dp))

        SecondaryTabRow(selectedTabIndex = if (postViewModel.showPreview) 1 else 0) {
            Tab(selected = !postViewModel.showPreview, onClick = { postViewModel.showPreview = false }, text = { Text(stringRes(Res.string.wiki_edit_tab)) })
            Tab(selected = postViewModel.showPreview, onClick = { postViewModel.showPreview = true }, text = { Text(stringRes(Res.string.markdown_preview)) })
        }

        if (postViewModel.showPreview) {
            WikiPreview(postViewModel, accountViewModel, nav)
        } else {
            ThinPaddingTextField(
                state = postViewModel.message,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text(stringRes(Res.string.wiki_body_placeholder), color = MaterialTheme.colorScheme.placeholderText) },
                spellCheck = true,
            )
        }
    }
}

/** The body as readers will see it: the article's own Djot document, its emoji and wikilinks resolved. */
@Composable
private fun WikiPreview(
    postViewModel: WikiPostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val text = postViewModel.message.text.toString()
    if (text.isBlank()) {
        Text(stringRes(Res.string.wiki_preview_empty), color = MaterialTheme.colorScheme.placeholderText, modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    val me = accountViewModel.account.signer.pubKey
    val tags by produceState(EmptyTagList, text) {
        value =
            accountViewModel.account.emoji
                .findEmojiTags(text)
                .map { it.toTagArray() }
                .toTypedArray()
                .toImmutableListOfLists()
    }
    val document = remember(text, tags) { CachedDocuments.djot(text, tags, null, me, wikilinkResolver(me)) }
    val state = remember(text, tags) { CachedRichTextParser.parseText(text, tags, null) }
    val backgroundColor = remember { mutableStateOf(Color.Transparent) }
    RenderDocument(document, state, true, 1, backgroundColor, null, accountViewModel, nav, Modifier.padding(vertical = 8.dp))
}
