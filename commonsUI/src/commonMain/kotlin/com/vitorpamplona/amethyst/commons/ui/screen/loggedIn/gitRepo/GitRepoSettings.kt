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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.create.NewGitRepository
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitIconTile
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_cancel
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_remove_topic
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_topics_placeholder
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_clone_urls
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_description
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_name
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_save
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_title
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_topics
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_web_urls
import com.vitorpamplona.amethyst.commons.ui.components.ChipsInputField
import com.vitorpamplona.amethyst.commons.ui.components.FormFieldShape
import com.vitorpamplona.amethyst.commons.ui.components.FormTextField
import com.vitorpamplona.amethyst.commons.ui.components.LabeledField
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.formFieldColors
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * Edit form for a NIP-34 repository announcement (kind 30617). Because the event
 * is addressable, saving republishes it under the same `d` tag — preserving the
 * earliest-unique-commit, relays, maintainers, personal-fork flag and every tag the
 * form does not manage (ngit's role tags, `u`, `blossoms`, …) while letting the owner
 * edit the name, description, web/clone URLs and topics. Fields share the look of the
 * New repository screen.
 */
@Composable
fun GitRepoSettingsDialog(
    event: GitRepositoryEvent,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(event.name() ?: event.dTag()) }
    var description by rememberSaveable { mutableStateOf(event.description() ?: "") }
    var webUrls by rememberSaveable { mutableStateOf(event.webs().joinToString("\n")) }
    var cloneUrls by rememberSaveable { mutableStateOf(event.clones().joinToString("\n")) }
    // personal-fork is a flag that save() writes back on its own, not a topic to edit.
    var topics by rememberSaveable {
        mutableStateOf(
            event
                .hashtags()
                .filter { it != GitRepositoryEvent.PERSONAL_FORK }
                .distinct()
                .joinToString("\n"),
        )
    }
    var topicDraft by rememberSaveable { mutableStateOf("") }
    val topicList = splitLines(topics)

    fun commitTopicDraft() {
        val added = NewGitRepository.splitList(topicDraft).map { it.removePrefix("#").lowercase() }.filter { it.isNotEmpty() && it !in topicList }
        if (added.isNotEmpty()) topics = (topicList + added).joinToString("\n")
        topicDraft = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog's default surfaceContainerHigh is the fields' own fill; one step lower keeps them visible.
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GitIconTile(symbol = MaterialSymbols.Edit, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringRes(Res.string.git_repo_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = event.dTag(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabeledField(Res.string.git_repo_settings_name) {
                    FormTextField(
                        value = name,
                        onValueChange = { name = it },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    )
                }
                LabeledField(Res.string.git_repo_settings_description) {
                    SpellCheckedOutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        minLines = 3,
                        maxLines = 6,
                        shape = FormFieldShape,
                        colors = formFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LabeledField(Res.string.git_repo_settings_topics) {
                    ChipsInputField(
                        items = topicList,
                        draft = topicDraft,
                        onDraftChange = { topicDraft = it },
                        onCommit = { commitTopicDraft() },
                        onRemove = { removed -> topics = topicList.filter { it != removed }.joinToString("\n") },
                        removeDescription = { stringRes(Res.string.git_new_repository_remove_topic, it) },
                        placeholder = stringRes(Res.string.git_new_repository_topics_placeholder),
                        leadingIcon = MaterialSymbols.Tag,
                        chipLabel = { "#$it" },
                    )
                }
                LabeledField(Res.string.git_repo_settings_clone_urls) {
                    FormTextField(
                        value = cloneUrls,
                        onValueChange = { cloneUrls = it },
                        placeholder = "https://",
                        singleLine = false,
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                LabeledField(Res.string.git_repo_settings_web_urls) {
                    FormTextField(
                        value = webUrls,
                        onValueChange = { webUrls = it },
                        placeholder = "https://",
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val allTopics = (topicList + NewGitRepository.splitList(topicDraft).map { it.removePrefix("#").lowercase() }).distinct()
                    saveRepository(accountViewModel, event, name.trim(), description.trim(), webUrls, cloneUrls, allTopics.joinToString("\n"))
                    onDismiss()
                },
            ) {
                Text(stringRes(Res.string.git_repo_settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_new_issue_cancel)) }
        },
    )
}

private fun splitLines(raw: String): List<String> =
    raw
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

private fun saveRepository(
    accountViewModel: AccountViewModel,
    event: GitRepositoryEvent,
    name: String,
    description: String,
    webUrls: String,
    cloneUrls: String,
    topics: String,
) {
    accountViewModel.launchSigner {
        val template =
            GitRepositoryEvent.build(
                name = name,
                description = description.ifBlank { null },
                webUrls = splitLines(webUrls),
                cloneUrls = splitLines(cloneUrls),
                relays = event.relays(),
                maintainers = event.maintainers(),
                hashtags = splitLines(topics).map { it.removePrefix("#") },
                earliestUniqueCommit = event.earliestUniqueCommit(),
                personalFork = event.isPersonalFork(),
                dTag = event.dTag(),
            ) {
                event.unmanagedTags().forEach { add(it) }
            }
        val signed = accountViewModel.account.signer.sign(template)
        accountViewModel.account.sendAutomatic(signed)
    }
}
