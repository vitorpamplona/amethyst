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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories.newRepository

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.create.NewGitRepositoryViewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.git_new_repository
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_add_server
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_advanced_summary
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc_invalid
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_hosting_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier_taken
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_invalid_server
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_no_hosting
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_other_clone_urls
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_relays
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_remove_topic
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_section_about
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_section_advanced
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_section_hosting
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_topics
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_topics_placeholder
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_untitled
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_description
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_name
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_web_urls
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

private val PreviewShape = RoundedCornerShape(24.dp)
private val TileShape = RoundedCornerShape(16.dp)
private val InsetShape = RoundedCornerShape(12.dp)
private val Mono = TextStyle(fontFamily = FontFamily.Monospace)

/**
 * Announces a new NIP-34 repository. A live preview of the repository card sits on top and
 * follows the form: name, `npub/identifier` address, description, topics and the
 * `git clone nostr://…` line to copy. Below it, About, Hosting (GRASP servers as chips) and a
 * folded Advanced section for the fields most people never touch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGitRepositoryScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val model: NewGitRepositoryViewModel = rememberViewModel { NewGitRepositoryViewModel() }

    LaunchedEffect(accountViewModel.account) {
        model.init(accountViewModel.account)
    }

    val shortNpub = accountViewModel.userProfile().pubkeyDisplayHex()

    Scaffold(
        topBar = {
            CreatingTopBar(
                titleRes = Res.string.git_new_repository,
                isActive = model::canCreate,
                onCancel = { nav.popBack() },
                onPost = {
                    model.create(accountViewModel) { route ->
                        nav.popBack()
                        nav.nav(route)
                    }
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
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepositoryPreviewCard(model, shortNpub)
                    if (model.isCreating) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(InsetShape))
                    }
                }

                FormSection(Res.string.git_new_repository_section_about) {
                    AboutFields(model, shortNpub)
                }

                FormSection(Res.string.git_new_repository_section_hosting, Res.string.git_new_repository_hosting_help) {
                    HostingFields(model)
                }

                AdvancedSection(model)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live preview
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepositoryPreviewCard(
    model: NewGitRepositoryViewModel,
    shortNpub: String,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PreviewShape)
                .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.26f), colors.secondary.copy(alpha = 0.08f))))
                .border(1.dp, colors.primary.copy(alpha = 0.30f), PreviewShape)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(TileShape)
                        .background(Brush.linearGradient(listOf(colors.primary, colors.secondary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MaterialSymbols.Code, contentDescription = null, modifier = Modifier.size(28.dp), tint = colors.onPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val untitled = model.name.isBlank()
                Text(
                    text = if (untitled) stringRes(Res.string.git_new_repository_untitled) else model.name.trim(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (untitled) colors.grayText else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$shortNpub/${model.identifier.trim().ifEmpty { "…" }}",
                    style = MaterialTheme.typography.labelMedium.merge(Mono),
                    color = colors.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (model.description.isNotBlank()) {
            Text(
                text = model.description.trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (model.topics.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                model.topics.forEach { TopicPill(it) }
            }
        }

        val cloneUrl = model.nostrCloneUrl
        CommandLine(
            command = "git clone $cloneUrl",
            display = "git clone ${cloneUrl.replace(model.npub, shortNpub)}" + if (model.identifier.isBlank()) "…" else "",
        )
    }
}

@Composable
private fun TopicPill(topic: String) {
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * A one-line shell command in a terminal-like inset, with a button that copies it. [display]
 * can shorten what is shown (an npub, say) so the end of the command stays visible; the copy
 * is always the full [command].
 */
@Composable
fun CommandLine(
    command: String,
    modifier: Modifier = Modifier,
    display: String = command,
) {
    val clipboard = LocalClipboard.current
    val notice = rememberShortNotice()
    val scope = rememberCoroutineScope()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(InsetShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .padding(start = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$ ",
            style = MaterialTheme.typography.bodySmall.merge(Mono),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Text(
            text = display,
            style = MaterialTheme.typography.bodySmall.merge(Mono),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        IconButton(
            modifier = Modifier.align(Alignment.CenterVertically),
            onClick = {
                scope.launch {
                    clipboard.setText(command)
                    notice.show(loadStringRes(Res.string.copied_to_clipboard))
                }
            },
        ) {
            Icon(
                MaterialSymbols.ContentCopy,
                contentDescription = stringRes(Res.string.copy_to_clipboard),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

@Composable
private fun FormSection(
    title: StringResource,
    subtitle: StringResource? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringRes(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = stringRes(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutFields(
    model: NewGitRepositoryViewModel,
    shortNpub: String,
) {
    OutlinedTextField(
        value = model.name,
        onValueChange = { model.name = it },
        label = { Text(stringRes(Res.string.git_repo_settings_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = model.identifier,
        onValueChange = { model.updateIdentifier(it) },
        label = { Text(stringRes(Res.string.git_new_repository_identifier)) },
        prefix = { Text("$shortNpub/", color = MaterialTheme.colorScheme.grayText) },
        singleLine = true,
        textStyle = LocalMonoBody(),
        isError = model.isIdentifierTaken,
        supportingText = {
            Text(
                stringRes(
                    if (model.isIdentifierTaken) Res.string.git_new_repository_identifier_taken else Res.string.git_new_repository_identifier_help,
                ),
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    SpellCheckedOutlinedTextField(
        value = model.description,
        onValueChange = { model.description = it },
        label = { Text(stringRes(Res.string.git_repo_settings_description)) },
        minLines = 2,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )

    if (model.topics.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            model.topics.forEach { topic ->
                val remove = stringRes(Res.string.git_new_repository_remove_topic, topic)
                InputChip(
                    selected = false,
                    onClick = { model.removeTopic(topic) },
                    label = { Text("#$topic") },
                    trailingIcon = { Icon(MaterialSymbols.Close, contentDescription = remove, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }

    OutlinedTextField(
        value = model.topicDraft,
        onValueChange = { model.updateTopicDraft(it) },
        label = { Text(stringRes(Res.string.git_new_repository_topics)) },
        placeholder = { Text(stringRes(Res.string.git_new_repository_topics_placeholder)) },
        leadingIcon = { Icon(MaterialSymbols.Tag, contentDescription = null, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { model.commitTopicDraft() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostingFields(model: NewGitRepositoryViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        model.graspServers.forEach { choice ->
            FilterChip(
                selected = choice.selected,
                onClick = { model.toggleGraspServer(choice.server) },
                label = { Text(choice.server) },
                leadingIcon = {
                    Icon(
                        if (choice.selected) MaterialSymbols.Check else MaterialSymbols.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }

    val canAdd = model.serverDraft.isNotBlank() && model.isServerDraftValid
    OutlinedTextField(
        value = model.serverDraft,
        onValueChange = { model.serverDraft = it },
        placeholder = { Text(stringRes(Res.string.git_new_repository_add_server)) },
        leadingIcon = { Icon(MaterialSymbols.Dns, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = { model.addServerDraft() }, enabled = canAdd) {
                Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.git_new_repository_add_server))
            }
        },
        singleLine = true,
        isError = !model.isServerDraftValid,
        supportingText =
            if (!model.isServerDraftValid) {
                { Text(stringRes(Res.string.git_new_repository_invalid_server, model.serverDraft.trim())) }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { model.addServerDraft() }),
        modifier = Modifier.fillMaxWidth(),
    )

    AnimatedVisibility(visible = model.selectedGraspServers.isEmpty() && model.cloneUrls.isBlank()) {
        Text(
            text = stringRes(Res.string.git_new_repository_no_hosting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AdvancedSection(model: NewGitRepositoryViewModel) {
    var expanded by rememberSaveable { mutableStateOf(model.hasAdvancedInput) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PreviewShape)
                .border(1.dp, colors.outlineVariant, PreviewShape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(MaterialSymbols.Tune, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringRes(Res.string.git_new_repository_section_advanced),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringRes(Res.string.git_new_repository_advanced_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.grayText,
                )
            }
            Icon(
                if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = null,
                tint = colors.grayText,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = model.earliestCommit,
                    onValueChange = { model.earliestCommit = it },
                    label = { Text(stringRes(Res.string.git_new_repository_euc)) },
                    placeholder = { Text("3c7af0b95a53d7d9…", style = LocalMonoBody()) },
                    textStyle = LocalMonoBody(),
                    singleLine = true,
                    isError = !model.isCommitValid,
                    supportingText = {
                        Text(stringRes(if (model.isCommitValid) Res.string.git_new_repository_euc_help else Res.string.git_new_repository_euc_invalid))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model.cloneUrls,
                    onValueChange = { model.cloneUrls = it },
                    label = { Text(stringRes(Res.string.git_new_repository_other_clone_urls)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model.webUrls,
                    onValueChange = { model.webUrls = it },
                    label = { Text(stringRes(Res.string.git_repo_settings_web_urls)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                val invalidRelay = model.invalidRelay
                OutlinedTextField(
                    value = model.relays,
                    onValueChange = { model.relays = it },
                    label = { Text(stringRes(Res.string.git_new_repository_relays)) },
                    isError = invalidRelay != null,
                    supportingText = invalidRelay?.let { { Text(stringRes(Res.string.git_new_repository_invalid_server, it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LocalMonoBody(): TextStyle = MaterialTheme.typography.bodyLarge.merge(Mono)
