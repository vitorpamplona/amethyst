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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.newCommunity

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.StrippingFailureDialog
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelect
import com.vitorpamplona.amethyst.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoClickableRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayUrlEditField
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.RelayTag
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCommunityScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val model: NewCommunityModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(accountViewModel.account) {
        model.init(accountViewModel.account)
    }

    StrippingFailureDialog(model.strippingFailureConfirmation)

    var wantsToPickImage by remember { mutableStateOf(false) }

    if (wantsToPickImage) {
        GallerySelect(
            onImageUri = { uris ->
                wantsToPickImage = false
                model.setPickedMedia(
                    if (uris.isNotEmpty()) persistentListOf(uris.first()) else persistentListOf(),
                )
            },
        )
    }

    Scaffold(
        topBar = {
            CreatingTopBar(
                titleRes = R.string.new_community,
                isActive = model::canPost,
                onCancel = {
                    model.reset()
                    nav.popBack()
                },
                onPost = {
                    model.publish(
                        context = context,
                        onSuccess = { nav.popBack() },
                        onError = accountViewModel.toastManager::toast,
                    )
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CommunityImagePicker(
                    model = model,
                    accountViewModel = accountViewModel,
                    onPickImage = { wantsToPickImage = true },
                )

                OutlinedTextField(
                    value = model.name,
                    onValueChange = { model.name = it },
                    label = { Text(stringRes(R.string.new_community_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )

                OutlinedTextField(
                    value = model.description,
                    onValueChange = { model.description = it },
                    label = { Text(stringRes(R.string.new_community_description)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )

                OutlinedTextField(
                    value = model.rules,
                    onValueChange = { model.rules = it },
                    label = { Text(stringRes(R.string.new_community_rules)) },
                    minLines = 2,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )

                HorizontalDivider()

                SectionHeader(R.string.new_community_moderators_section)
                ModeratorsSection(
                    model = model,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                HorizontalDivider()

                SectionHeader(R.string.new_community_relays_section)
                RelaysSection(
                    model = model,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(resourceId: Int) {
    Text(
        text = stringRes(resourceId),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CommunityImagePicker(
    model: NewCommunityModel,
    accountViewModel: AccountViewModel,
    onPickImage: () -> Unit,
) {
    if (model.hasPickedImage()) {
        model.multiOrchestrator?.let {
            Box(modifier = Modifier.clickable(onClick = onPickImage)) {
                ShowImageUploadGallery(
                    list = it,
                    onDelete = { model.setPickedMedia(persistentListOf()) },
                    accountViewModel = accountViewModel,
                )
            }
        }
    } else {
        CommunityImagePlaceholder(onClick = onPickImage)
    }
}

@Composable
private fun CommunityImagePlaceholder(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                ).clickable(onClick = onClick)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringRes(R.string.new_community_pick_cover),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.new_community_pick_cover_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- Moderators --------------------------------------------------------------------------------

@Composable
private fun ModeratorsSection(
    model: NewCommunityModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var search by remember { mutableStateOf("") }

    val userSuggestions =
        remember {
            UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
        }

    DisposableEffect(Unit) {
        onDispose { userSuggestions.reset() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(R.string.new_community_moderators_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Current user is always a moderator (creator)
        SelectedModeratorRow(
            user = accountViewModel.account.userProfile(),
            accountViewModel = accountViewModel,
            nav = nav,
            isOwner = true,
            onRemove = null,
        )

        model.moderators.toList().forEach { user ->
            SelectedModeratorRow(
                user = user,
                accountViewModel = accountViewModel,
                nav = nav,
                isOwner = false,
                onRemove = { model.removeModerator(user) },
            )
        }

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                if (it.length > 1) {
                    userSuggestions.processCurrentWord(it)
                } else {
                    userSuggestions.reset()
                }
            },
            label = { Text(stringRes(R.string.new_community_add_moderator)) },
            placeholder = { Text(stringRes(R.string.new_community_add_moderator_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (search.length > 1) {
            ShowUserSuggestionList(
                userSuggestions = userSuggestions,
                onSelect = { user ->
                    if (user.pubkeyHex != accountViewModel.account.userProfile().pubkeyHex) {
                        model.addModerator(user)
                    }
                    search = ""
                    userSuggestions.reset()
                },
                accountViewModel = accountViewModel,
                modifier = SuggestionListDefaultHeightPage,
            )
        }
    }
}

@Composable
private fun SelectedModeratorRow(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    isOwner: Boolean,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPicture(
            userHex = user.pubkeyHex,
            size = 40.dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        ) {
            Text(
                text = user.toBestDisplayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ModeratorSecondaryLine(user)
        }

        if (isOwner) {
            AssistChip(
                onClick = {},
                label = { Text(stringRes(R.string.new_community_owner)) },
                enabled = false,
                colors =
                    AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        } else if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringRes(R.string.remove),
                )
            }
        }
    }
}

@Composable
private fun ModeratorSecondaryLine(user: User) {
    val nip05StateMetadata by user.nip05State().flow.collectAsStateWithLifecycle()

    val text =
        when (val state = nip05StateMetadata) {
            is Nip05State.Exists -> {
                val name = state.nip05.name
                if (name == "_") state.nip05.domain else "$name@${state.nip05.domain}"
            }

            else -> {
                user.pubkeyDisplayHex()
            }
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// --- Relays ------------------------------------------------------------------------------------

@Composable
private fun RelaysSection(
    model: NewCommunityModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringRes(R.string.new_community_relays_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        model.relays.toList().forEach { entry ->
            val info = remember(entry.url) { relaySetupInfoBuilder(entry.url) }

            Column {
                BasicRelaySetupInfoClickableRow(
                    item = info,
                    loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                    loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                    onClick = {},
                    onDelete = { model.removeRelay(entry) },
                    nip11CachedRetriever = Amethyst.instance.nip11Cache,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                RelayMarkerChips(
                    current = entry.marker,
                    onSelect = { model.setRelayMarker(entry, it) },
                )
            }
        }

        RelayUrlEditField(
            onNewRelay = { model.addRelay(it) },
            modifier = Modifier.fillMaxWidth(),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun RelayMarkerChips(
    current: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 56.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RelayMarkerOption(
            label = stringRes(R.string.new_community_relay_marker_none),
            selected = current == null,
            onClick = { onSelect(null) },
        )
        RelayMarkerOption(
            label = stringRes(R.string.new_community_relay_marker_author),
            selected = current == RelayTag.MARKER_AUTHOR,
            onClick = { onSelect(RelayTag.MARKER_AUTHOR) },
        )
        RelayMarkerOption(
            label = stringRes(R.string.new_community_relay_marker_requests),
            selected = current == RelayTag.MARKER_REQUESTS,
            onClick = { onSelect(RelayTag.MARKER_REQUESTS) },
        )
        RelayMarkerOption(
            label = stringRes(R.string.new_community_relay_marker_approvals),
            selected = current == RelayTag.MARKER_APPROVALS,
            onClick = { onSelect(RelayTag.MARKER_APPROVALS) },
        )
    }
}

@Composable
private fun RelayMarkerOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}
