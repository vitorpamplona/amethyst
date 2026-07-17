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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupWarmupSubscription
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.AmethystSwitch
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * Create a new NIP-29 group on [relayUrl]: publishes kind 9007 + 9002 with every
 * metadata field the user sets (name, description, picture, and the four status
 * flags), then opens the new group. The user becomes its first admin.
 */
@Composable
fun RelayGroupCreateScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val viewModel: RelayGroupMetadataViewModel = viewModel(key = "RelayGroupCreate:$relayUrl")
    LaunchedEffect(relay) { viewModel.initCreate(accountViewModel, relay) }

    // A group only works if the relay actually runs NIP-29 (otherwise it stores our 9007/9002 as
    // ordinary events, never emits metadata/roster, and the "group" is a dead hex id). Gate creation
    // on the relay advertising NIP-29 in its NIP-11 `supported_nips`. Tri-state: null = still checking,
    // false = confirmed unsupported (or unreachable), true = advertised.
    val nip29Support by produceState<Boolean?>(initialValue = null, relay) {
        Amethyst.instance.nip11Cache.loadRelayInfo(
            relay = relay,
            onInfo = { info -> value = info.supported_nips?.any { it == "29" } ?: false },
            onError = { _, _, _ -> value = false },
        )
    }

    RelayGroupMetadataScaffold(
        viewModel = viewModel,
        accountViewModel = accountViewModel,
        nav = nav,
        nip29Support = nip29Support,
        onSuccess = { nav.popUpTo(Route.RelayGroup(viewModel.groupId, relay.url), Route.RelayGroupCreate::class) },
    )
}

/**
 * Edit a group's relay-signed metadata (kind 9002, admin only). Pre-fills from the
 * group's current metadata reactively (so a late first load still populates), then
 * saves and returns.
 */
@Composable
fun RelayGroupEditScreen(
    id: String,
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = remember(relayUrl) { RelayUrlNormalizer.normalizeOrNull(relayUrl) } ?: return
    val groupId = remember(id, relay) { GroupId(id, relay) }
    val viewModel: RelayGroupMetadataViewModel = viewModel(key = "RelayGroupEdit:${groupId.toKey()}")

    LoadRelayGroupChannel(groupId, accountViewModel) { channel ->
        // Keep the relay-signed metadata fresh while editing so a late load prefills.
        RelayGroupWarmupSubscription(channel, accountViewModel.dataSources().relayGroupWarmup, accountViewModel)

        val channelState by channel
            .flow()
            .metadata.stateFlow
            .collectAsStateWithLifecycle()
        val liveChannel = channelState.channel as? RelayGroupChannel ?: channel

        LaunchedEffect(liveChannel.groupId) { viewModel.initEdit(accountViewModel, liveChannel) }
        // Re-seed when the metadata event changes, unless the user has already edited.
        LaunchedEffect(liveChannel.event?.id) { viewModel.prefillFrom(liveChannel) }

        RelayGroupMetadataScaffold(
            viewModel = viewModel,
            accountViewModel = accountViewModel,
            nav = nav,
            onSuccess = { nav.popBack() },
        )
    }
}

@Composable
private fun RelayGroupMetadataScaffold(
    viewModel: RelayGroupMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    onSuccess: () -> Unit,
    // Whether the target relay advertises NIP-29. null = still checking, false = confirmed
    // unsupported (block + warn), true = supported. Only meaningful when creating; editing an
    // existing group already implies a working relay, so callers pass true.
    nip29Support: Boolean? = true,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var wantsToPickImage by remember { mutableStateOf(false) }
    if (wantsToPickImage) {
        GallerySelectSingle(
            onImageUri = { media ->
                wantsToPickImage = false
                if (media != null) viewModel.pickMedia(media)
            },
        )
    }

    val onSubmit: () -> Unit = {
        viewModel.submit(
            context = context,
            onSuccess = onSuccess,
            onError = accountViewModel.toastManager::toast,
        )
    }

    Scaffold(
        topBar = {
            if (viewModel.isNewGroup) {
                CreatingTopBar(
                    titleRes = R.string.relay_group_create_title,
                    isActive = { viewModel.canPost && nip29Support == true },
                    onCancel = nav::popBack,
                    onPost = onSubmit,
                )
            } else {
                SavingTopBar(
                    titleRes = R.string.relay_group_edit_title,
                    isActive = viewModel::canPost,
                    onCancel = nav::popBack,
                    onPost = onSubmit,
                )
            }
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
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (nip29Support == false) {
                    NoNip29Warning()
                    Spacer(Modifier.height(16.dp))
                }

                GroupImagePicker(viewModel) { wantsToPickImage = true }

                Spacer(Modifier.height(16.dp))

                GroupMetadataFields(viewModel)

                Spacer(Modifier.height(16.dp))

                ParentGroupSection(viewModel, accountViewModel)
            }
        }
    }
}

/** A warning shown when the target relay doesn't advertise NIP-29, blocking creation. */
@Composable
private fun NoNip29Warning() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringRes(R.string.relay_group_relay_no_nip29),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** The tap-to-upload group avatar hero at the top of the form. */
@Composable
private fun GroupImagePicker(
    viewModel: RelayGroupMetadataViewModel,
    onPick: () -> Unit,
) {
    val picked = viewModel.pickedMedia
    val currentUrl = viewModel.picture.value.text
    val model: Any? = picked?.uri ?: currentUrl.ifBlank { null }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = stringRes(R.string.relay_group_field_picture),
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(onClick = onPick),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = MaterialSymbols.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                if (model != null) {
                    stringRes(R.string.relay_group_change_photo)
                } else {
                    stringRes(R.string.relay_group_add_photo)
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPick),
        )
    }
}

@Composable
private fun GroupMetadataFields(viewModel: RelayGroupMetadataViewModel) {
    OutlinedTextField(
        value = viewModel.name.value,
        onValueChange = {
            viewModel.name.value = it
            viewModel.markTouched()
        },
        singleLine = true,
        label = { Text(stringRes(R.string.relay_group_field_name)) },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = viewModel.about.value,
        onValueChange = {
            viewModel.about.value = it
            viewModel.markTouched()
        },
        label = { Text(stringRes(R.string.relay_group_field_about)) },
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    Spacer(Modifier.height(12.dp))
    Text(
        text = stringRes(R.string.relay_group_section_discovery),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringRes(R.string.relay_group_section_discovery_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )

    OutlinedTextField(
        value = viewModel.topics.value,
        onValueChange = {
            viewModel.topics.value = it
            viewModel.markTouched()
        },
        singleLine = true,
        label = { Text(stringRes(R.string.relay_group_field_topics)) },
        placeholder = { Text(stringRes(R.string.relay_group_field_topics_hint)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        value = viewModel.geohash.value,
        onValueChange = {
            viewModel.geohash.value = it
            viewModel.markTouched()
        },
        singleLine = true,
        label = { Text(stringRes(R.string.relay_group_field_geohash)) },
        placeholder = { Text(stringRes(R.string.relay_group_field_geohash_hint)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )

    Spacer(Modifier.height(12.dp))
    Text(
        text = stringRes(R.string.relay_group_section_permissions),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )

    LabeledSwitchRow(
        label = stringRes(R.string.relay_group_flag_private),
        description = stringRes(R.string.relay_group_flag_private_desc),
        checked = viewModel.isPrivate,
    ) {
        viewModel.isPrivate = it
        viewModel.markTouched()
    }
    LabeledSwitchRow(
        label = stringRes(R.string.relay_group_flag_invite_only),
        description = stringRes(R.string.relay_group_flag_invite_only_desc),
        checked = viewModel.isClosed,
    ) {
        viewModel.isClosed = it
        viewModel.markTouched()
    }
    LabeledSwitchRow(
        label = stringRes(R.string.relay_group_flag_restricted),
        description = stringRes(R.string.relay_group_flag_restricted_desc),
        checked = viewModel.isRestricted,
    ) {
        viewModel.isRestricted = it
        viewModel.markTouched()
    }
    LabeledSwitchRow(
        label = stringRes(R.string.relay_group_flag_hidden),
        description = stringRes(R.string.relay_group_flag_hidden_desc),
        checked = viewModel.isHidden,
    ) {
        viewModel.isHidden = it
        viewModel.markTouched()
    }
}

@Composable
private fun LabeledSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AmethystSwitch(checked = checked, onCheckedChange = onChange)
    }
}
