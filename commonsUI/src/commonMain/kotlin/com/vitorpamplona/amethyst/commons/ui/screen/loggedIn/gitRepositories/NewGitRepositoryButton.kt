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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.create.NewGitRepository
import com.vitorpamplona.amethyst.commons.nip34Git.create.findMyGitRepository
import com.vitorpamplona.amethyst.commons.nip34Git.create.myGraspServers
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_cancel
import com.vitorpamplona.amethyst.commons.resources.git_new_repository
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_create
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc_invalid
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_grasp_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_grasp_servers
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier_taken
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_invalid_server
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_other_clone_urls
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_relays
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_description
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_name
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_topics
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_web_urls
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.grasp.GraspServer
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun NewGitRepositoryButton(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        NewGitRepositoryDialog(accountViewModel, nav) { showDialog = false }
    }

    FloatingActionButton(
        onClick = { showDialog = true },
        modifier = Size55Modifier,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
    ) {
        Icon(
            symbol = MaterialSymbols.Add,
            contentDescription = stringRes(Res.string.git_new_repository),
            modifier = Size26Modifier,
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * Form for a new NIP-34 repository announcement (kind 30617), shaped like `ngit init`'s:
 * the identifier follows the name until edited, and the GRASP servers start as the user's
 * kind:10317 list (ngit's defaults when there is none). [NewGitRepository] turns it into the
 * event. Opens the new repository once it is signed and sent.
 */
@Composable
fun NewGitRepositoryDialog(
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var typedIdentifier by rememberSaveable { mutableStateOf<String?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var graspServers by rememberSaveable { mutableStateOf(GraspServer.DEFAULT_SERVERS.joinToString("\n")) }
    var graspServersEdited by rememberSaveable { mutableStateOf(false) }
    var cloneUrls by rememberSaveable { mutableStateOf("") }
    var webUrls by rememberSaveable { mutableStateOf("") }
    var relays by rememberSaveable { mutableStateOf("") }
    var earliestCommit by rememberSaveable { mutableStateOf("") }
    var topics by rememberSaveable { mutableStateOf("") }

    var takenIdentifier by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(accountViewModel) {
        val mine = withContext(Dispatchers.IO) { accountViewModel.account.myGraspServers() }
        if (mine != null && !graspServersEdited) graspServers = mine.joinToString("\n")
    }

    val identifier = typedIdentifier ?: GitRepositoryEvent.suggestIdentifier(name)
    val graspList = NewGitRepository.splitList(graspServers)
    val relayList = NewGitRepository.splitList(relays)
    val invalidGrasp = graspList.firstOrNull { !NewGitRepository.isValidGraspServer(it) }
    val invalidRelay = relayList.firstOrNull { !NewGitRepository.isValidRelay(it) }
    val commitIsValid = NewGitRepository.isValidCommitId(earliestCommit)
    val identifierTaken = takenIdentifier != null && takenIdentifier == identifier.trim()

    val canCreate =
        !checking &&
            name.isNotBlank() &&
            identifier.isNotBlank() &&
            !identifierTaken &&
            invalidGrasp == null &&
            invalidRelay == null &&
            commitIsValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.git_new_repository)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringRes(Res.string.git_repo_settings_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { typedIdentifier = it },
                    label = { Text(stringRes(Res.string.git_new_repository_identifier)) },
                    singleLine = true,
                    isError = identifierTaken,
                    supportingText = {
                        if (identifierTaken) {
                            Text(stringRes(Res.string.git_new_repository_identifier_taken))
                        } else {
                            Text(stringRes(Res.string.git_new_repository_identifier_help))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SpellCheckedOutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringRes(Res.string.git_repo_settings_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = graspServers,
                    onValueChange = {
                        graspServers = it
                        graspServersEdited = true
                    },
                    label = { Text(stringRes(Res.string.git_new_repository_grasp_servers)) },
                    isError = invalidGrasp != null,
                    supportingText = {
                        if (invalidGrasp != null) {
                            Text(stringRes(Res.string.git_new_repository_invalid_server, invalidGrasp))
                        } else {
                            Text(stringRes(Res.string.git_new_repository_grasp_help))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cloneUrls,
                    onValueChange = { cloneUrls = it },
                    label = { Text(stringRes(Res.string.git_new_repository_other_clone_urls)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = webUrls,
                    onValueChange = { webUrls = it },
                    label = { Text(stringRes(Res.string.git_repo_settings_web_urls)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = relays,
                    onValueChange = { relays = it },
                    label = { Text(stringRes(Res.string.git_new_repository_relays)) },
                    isError = invalidRelay != null,
                    supportingText =
                        invalidRelay?.let {
                            { Text(stringRes(Res.string.git_new_repository_invalid_server, it)) }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = earliestCommit,
                    onValueChange = { earliestCommit = it },
                    label = { Text(stringRes(Res.string.git_new_repository_euc)) },
                    singleLine = true,
                    isError = !commitIsValid,
                    supportingText = {
                        if (commitIsValid) {
                            Text(stringRes(Res.string.git_new_repository_euc_help))
                        } else {
                            Text(stringRes(Res.string.git_new_repository_euc_invalid))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = topics,
                    onValueChange = { topics = it },
                    label = { Text(stringRes(Res.string.git_repo_settings_topics)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    val draft =
                        NewGitRepository.Draft(
                            name = name,
                            identifier = identifier,
                            description = description,
                            graspServers = graspList,
                            cloneUrls = NewGitRepository.splitList(cloneUrls),
                            webUrls = NewGitRepository.splitList(webUrls),
                            relays = relayList,
                            earliestUniqueCommit = earliestCommit,
                            hashtags = NewGitRepository.splitList(topics),
                        )
                    checking = true
                    scope.launch {
                        val existing =
                            withContext(Dispatchers.IO) {
                                withTimeoutOrNull(10_000L) { accountViewModel.account.findMyGitRepository(draft.identifier.trim()) }
                            }
                        checking = false
                        if (existing != null) {
                            takenIdentifier = existing.dTag()
                        } else {
                            createRepository(accountViewModel, nav, draft)
                            onDismiss()
                        }
                    }
                },
            ) {
                Text(stringRes(Res.string.git_new_repository_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_new_issue_cancel)) }
        },
    )
}

private fun createRepository(
    accountViewModel: AccountViewModel,
    nav: INav,
    draft: NewGitRepository.Draft,
) {
    accountViewModel.launchSigner {
        val template = NewGitRepository.build(draft, accountViewModel.account.pubKey)
        val signed = accountViewModel.account.signer.sign(template)
        accountViewModel.account.sendAutomatic(signed)
        withContext(Dispatchers.Main) {
            nav.nav(Route.GitRepository(signed.kind, signed.pubKey, signed.dTag()))
        }
    }
}
