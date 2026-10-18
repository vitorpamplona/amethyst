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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_add
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_empty
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_hint
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_manage_packs
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_mine
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_remove
import com.vitorpamplona.amethyst.commons.resources.reactions
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPicker
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.PickedEmojis
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.util.firstFullChar
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag

@Stable
class UpdateReactionTypeViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account
    var reactionSet by mutableStateOf(listOf<String>())
    private var loaded = false

    /** What the picker marks as already chosen, so a tap on it removes it instead. */
    val picked by derivedStateOf {
        PickedEmojis(
            unicode = reactionSet.toSet(),
            customLinks = reactionSet.mapNotNullTo(HashSet()) { it.customEmojiUrlOrNull() },
        )
    }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    /** Once per screen: coming back from the emoji pack screens must not drop unsaved edits. */
    fun load() {
        if (loaded) return
        loaded = true
        this.reactionSet = account.settings.syncedSettings.reactions.reactionChoices.value
    }

    /**
     * Adds a Unicode emoji (or anything typed into the picker's search) or removes it when it is
     * already a choice. Typed text keeps only its first character, or an EmojiCoder message whole.
     */
    fun toggle(emoji: String) {
        val value = if (EmojiCoder.isCoded(emoji)) EmojiCoder.cropToFirstMessage(emoji) else emoji.trim().firstFullChar()
        if (value.isBlank()) return
        reactionSet = if (value in reactionSet) reactionSet - value else reactionSet + value
    }

    /** Custom emojis are matched by image, as a saved `:shortcode:url` may use another code. */
    fun toggle(custom: EmojiMedia) {
        reactionSet =
            if (reactionSet.any { it.customEmojiUrlOrNull() == custom.link }) {
                reactionSet.filter { it.customEmojiUrlOrNull() != custom.link }
            } else {
                reactionSet + EmojiUrlTag(custom.code, custom.link).encode()
            }
    }

    fun removeChoice(reaction: String) {
        reactionSet = reactionSet - reaction
    }

    fun sendPost() {
        accountViewModel.changeReactionTypes(reactionSet) {}
    }

    fun hasChanged(): Boolean = reactionSet != account.settings.syncedSettings.reactions.reactionChoices.value
}

@Composable
fun UpdateReactionTypeScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: UpdateReactionTypeViewModel = viewModel()
    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, accountViewModel) {
        postViewModel.load()
    }

    // The picker reads the selected packs from the cache; this fetches them while the screen is up.
    WatchAndLoadMyEmojiList(accountViewModel)

    UpdateReactionTypeScreen(postViewModel, accountViewModel, nav)
}

/**
 * The account's reaction choices on top, removable with a tap, over the full emoji picker: one
 * search across Unicode names and the shortcodes of the selected custom emoji packs, a section per
 * Unicode group and per pack, and every current choice marked so a second tap takes it out.
 */
@Composable
fun UpdateReactionTypeScreen(
    postViewModel: UpdateReactionTypeViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = Res.string.reactions,
                isActive = postViewModel::hasChanged,
                onCancel = { nav.popBack() },
                onPost = {
                    postViewModel.sendPost()
                    nav.popBack()
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            Column(Modifier.fillMaxSize()) {
                CurrentReactionChoices(postViewModel)

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                ) {
                    Text(
                        text = stringRes(Res.string.reaction_choices_add),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { nav.nav(Route.MyEmojiList) }) {
                        Text(stringRes(Res.string.reaction_choices_manage_packs))
                    }
                }

                EmojiPicker(
                    emojiPacks = accountViewModel.account.emoji,
                    onPickUnicode = postViewModel::toggle,
                    onPickCustom = postViewModel::toggle,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    picked = postViewModel.picked,
                    fillHeight = true,
                    showFavorites = false,
                    offerTypedEmoji = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentReactionChoices(postViewModel: UpdateReactionTypeViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringRes(Res.string.reaction_choices_mine, postViewModel.reactionSet.size),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringRes(Res.string.reaction_choices_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        if (postViewModel.reactionSet.isEmpty()) {
            Text(
                text = stringRes(Res.string.reaction_choices_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            // A long list scrolls in place so the picker below keeps most of the screen.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 152.dp)
                        .verticalScroll(rememberScrollState())
                        .animateContentSize(),
            ) {
                postViewModel.reactionSet.forEach { reactionType ->
                    ReactionChoiceChip(reactionType) { postViewModel.removeChoice(reactionType) }
                }
            }
        }
    }
}

@Composable
private fun ReactionChoiceChip(
    reactionType: String,
    onRemove: () -> Unit,
) {
    val remove = stringRes(Res.string.reaction_choices_remove)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = remove, onClick = onRemove)
                .heightIn(min = 40.dp)
                .padding(start = 10.dp, end = 6.dp),
    ) {
        RenderReaction(reactionType)
        Icon(
            symbol = MaterialSymbols.Close,
            contentDescription = remove,
            modifier = Modifier.padding(start = 4.dp).size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
