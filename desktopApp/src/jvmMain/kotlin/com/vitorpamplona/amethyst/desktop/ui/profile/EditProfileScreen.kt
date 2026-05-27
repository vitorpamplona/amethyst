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
package com.vitorpamplona.amethyst.desktop.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.profile.EditProfileFields
import com.vitorpamplona.amethyst.commons.profile.ProfileBroadcastBanner
import com.vitorpamplona.amethyst.commons.profile.ProfileBroadcastStatus
import com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator
import com.vitorpamplona.amethyst.desktop.DesktopPreferences
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopFilePicker
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip05DnsIdentifiers.OkHttpNip05Fetcher
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed class Nip05Status {
    data object Idle : Nip05Status()

    data object Checking : Nip05Status()

    data object Verified : Nip05Status()

    data object NotVerified : Nip05Status()

    data class Failed(
        val message: String,
    ) : Nip05Status()
}

/**
 * Wiring composable — connects [EditProfileFields] to signing, broadcast, and upload infra.
 */
@OptIn(FlowPreview::class)
@Composable
fun EditProfileDialog(
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    latestMetadata: MetadataEvent?,
    latestIdentities: ExternalIdentitiesEvent?,
    onDismiss: () -> Unit,
) {
    val fields = remember { EditProfileFields() }
    LaunchedEffect(Unit) { fields.loadFrom(latestMetadata, latestIdentities) }

    val scope = rememberCoroutineScope()
    var broadcastStatus by remember { mutableStateOf<ProfileBroadcastStatus>(ProfileBroadcastStatus.Idle) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingBanner by remember { mutableStateOf(false) }
    var showUnsavedWarning by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // NIP-05 verification
    var nip05Status by remember { mutableStateOf<Nip05Status>(Nip05Status.Idle) }
    val nip05Value by fields.nip05.collectAsState()
    LaunchedEffect(Unit) {
        fields.nip05
            .debounce(500)
            .mapNotNull { value -> if (value.isBlank()) null else value }
            .collectLatest { value ->
                val nip05Id =
                    Nip05Id.parse(value) ?: run {
                        nip05Status = Nip05Status.Idle
                        return@collectLatest
                    }
                nip05Status = Nip05Status.Checking
                try {
                    val client = Nip05Client(OkHttpNip05Fetcher { OkHttpClient() })
                    val verified = client.verify(nip05Id, account.pubKeyHex)
                    nip05Status =
                        if (verified) Nip05Status.Verified else Nip05Status.NotVerified
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    nip05Status = Nip05Status.Failed(e.message ?: "Verification failed")
                }
            }
    }

    // Reset NIP-05 status when field is blank
    LaunchedEffect(nip05Value) {
        if (nip05Value.isBlank()) nip05Status = Nip05Status.Idle
    }

    val orchestrator = remember { UploadOrchestrator() }
    val serverBaseUrl = DesktopPreferences.preferredBlossomServer

    fun pickAndUpload(
        onUrl: (String) -> Unit,
        setUploading: (Boolean) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val files = DesktopFilePicker.pickMediaFiles()
            val file = files.firstOrNull() ?: return@launch
            setUploading(true)
            try {
                val result = orchestrator.upload(file, null, serverBaseUrl, account.signer)
                result.blossom.url?.let { onUrl(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // upload failed — user sees no URL update
            } finally {
                setUploading(false)
            }
        }
    }

    fun save() {
        if (isSaving) return
        isSaving = true
        scope.launch {
            try {
                val connectedRelays = relayManager.connectedRelays.value
                if (connectedRelays.isEmpty()) {
                    broadcastStatus = ProfileBroadcastStatus.Failed("profile", "No connected relays")
                    return@launch
                }
                broadcastStatus = ProfileBroadcastStatus.Broadcasting("profile", 0, connectedRelays.size)

                // Sign metadata event
                val metadataTemplate =
                    if (latestMetadata != null) {
                        MetadataEvent.updateFromPast(
                            latest = latestMetadata,
                            name = fields.name.value,
                            displayName = fields.displayName.value,
                            picture = fields.picture.value,
                            banner = fields.banner.value,
                            website = fields.website.value,
                            pronouns = fields.pronouns.value,
                            about = fields.about.value,
                            nip05 = fields.nip05.value,
                            lnAddress = fields.lnAddress.value,
                            lnURL = fields.lnURL.value,
                        )
                    } else {
                        MetadataEvent.createNew(
                            name = fields.name.value,
                            displayName = fields.displayName.value,
                            picture = fields.picture.value,
                            banner = fields.banner.value,
                            website = fields.website.value,
                            pronouns = fields.pronouns.value,
                            about = fields.about.value,
                            nip05 = fields.nip05.value,
                            lnAddress = fields.lnAddress.value,
                            lnURL = fields.lnURL.value,
                        )
                    }
                val signedMetadata = account.signer.sign(metadataTemplate)

                // Sign identities event
                val identitiesTemplate =
                    if (latestIdentities != null) {
                        ExternalIdentitiesEvent.updateFromPast(
                            latest = latestIdentities,
                            twitter = fields.twitter.value,
                            mastodon = fields.mastodon.value,
                            github = fields.github.value,
                        )
                    } else {
                        ExternalIdentitiesEvent.createNew(
                            twitter = fields.twitter.value,
                            mastodon = fields.mastodon.value,
                            github = fields.github.value,
                        )
                    }
                val signedIdentities = account.signer.sign(identitiesTemplate)

                // Broadcast both
                relayManager.broadcastToAll(signedMetadata)
                relayManager.broadcastToAll(signedIdentities)

                broadcastStatus = ProfileBroadcastStatus.Success("profile", connectedRelays.size)
                delay(3000)
                broadcastStatus = ProfileBroadcastStatus.Idle
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                broadcastStatus = ProfileBroadcastStatus.Failed("profile", e.message ?: "Unknown error")
            } finally {
                isSaving = false
            }
        }
    }

    fun tryDismiss() {
        if (fields.isDirty) {
            showUnsavedWarning = true
        } else {
            onDismiss()
        }
    }

    EditProfileContent(
        fields = fields,
        onSave = ::save,
        onCancel = ::tryDismiss,
        onPickAvatar = {
            pickAndUpload(
                onUrl = { fields.picture.value = it },
                setUploading = { isUploadingAvatar = it },
            )
        },
        onPickBanner = {
            pickAndUpload(
                onUrl = { fields.banner.value = it },
                setUploading = { isUploadingBanner = it },
            )
        },
        isUploadingAvatar = isUploadingAvatar,
        isUploadingBanner = isUploadingBanner,
        isSaving = isSaving,
        nip05Status = nip05Status,
        broadcastStatus = broadcastStatus,
        onBroadcastStatusReset = { broadcastStatus = ProfileBroadcastStatus.Idle },
    )

    if (showUnsavedWarning) {
        AlertDialog(
            onDismissRequest = { showUnsavedWarning = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Discard them?") },
            confirmButton = {
                Button(onClick = {
                    showUnsavedWarning = false
                    onDismiss()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedWarning = false }) { Text("Keep Editing") }
            },
        )
    }
}

/**
 * Pure UI composable — previewable, no infrastructure dependencies.
 */
@Composable
fun EditProfileContent(
    fields: EditProfileFields,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    isUploadingAvatar: Boolean,
    isUploadingBanner: Boolean,
    isSaving: Boolean,
    nip05Status: Nip05Status,
    broadcastStatus: ProfileBroadcastStatus,
    onBroadcastStatusReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMacOS = remember { System.getProperty("os.name").contains("Mac", ignoreCase = true) }
    val focusRequester = remember { FocusRequester() }

    val nameValue by fields.name.collectAsState()
    val displayNameValue by fields.displayName.collectAsState()
    val aboutValue by fields.about.collectAsState()
    val pictureValue by fields.picture.collectAsState()
    val bannerValue by fields.banner.collectAsState()
    val websiteValue by fields.website.collectAsState()
    val pronounsValue by fields.pronouns.collectAsState()
    val nip05Value by fields.nip05.collectAsState()
    val lnAddressValue by fields.lnAddress.collectAsState()
    val lnURLValue by fields.lnURL.collectAsState()
    val twitterValue by fields.twitter.collectAsState()
    val githubValue by fields.github.collectAsState()
    val mastodonValue by fields.mastodon.collectAsState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier =
                modifier
                    .width(600.dp)
                    .padding(16.dp)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                onCancel()
                                true
                            }
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.S &&
                                (if (isMacOS) event.isMetaPressed else event.isCtrlPressed) -> {
                                onSave()
                                true
                            }
                            else -> false
                        }
                    },
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Edit Profile", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCancel) { Text("Cancel") }
                        Button(onClick = onSave, enabled = !isSaving) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Avatar + basic fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar — tappable circle with upload overlay
                    Box(
                        modifier =
                            Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = !isUploadingAvatar) { onPickAvatar() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pictureValue.isNotBlank()) {
                            AsyncImage(
                                model = pictureValue,
                                contentDescription = "Avatar preview",
                                modifier = Modifier.size(120.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        // Overlay: upload icon centered (or spinner while uploading)
                        Box(
                            modifier =
                                Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(
                                            alpha = if (pictureValue.isNotBlank()) 0.5f else 0f,
                                        ),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isUploadingAvatar) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            } else {
                                Icon(
                                    MaterialSymbols.AddPhotoAlternate,
                                    contentDescription = "Upload avatar",
                                    modifier = Modifier.size(32.dp),
                                    tint =
                                        if (pictureValue.isNotBlank()) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = displayNameValue,
                            onValueChange = { fields.displayName.value = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                        OutlinedTextField(
                            value = nameValue,
                            onValueChange = { fields.name.value = it },
                            label = { Text("Name (@)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = pronounsValue,
                            onValueChange = { fields.pronouns.value = it },
                            label = { Text("Pronouns") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Banner
                Text("Banner", style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { onPickBanner() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bannerValue.isNotBlank()) {
                        AsyncImage(
                            model = bannerValue,
                            contentDescription = "Banner preview",
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    if (isUploadingBanner) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (bannerValue.isBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                MaterialSymbols.AddPhotoAlternate,
                                contentDescription = "Upload banner",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Click to upload banner",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "Recommended: landscape image (~3:1 aspect ratio)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                // About
                OutlinedTextField(
                    value = aboutValue,
                    onValueChange = { fields.about.value = it },
                    label = { Text("About") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 5,
                )

                // Picture URL (manual entry)
                OutlinedTextField(
                    value = pictureValue,
                    onValueChange = { fields.picture.value = it },
                    label = { Text("Avatar URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Banner URL (manual entry)
                OutlinedTextField(
                    value = bannerValue,
                    onValueChange = { fields.banner.value = it },
                    label = { Text("Banner URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Website
                OutlinedTextField(
                    value = websiteValue,
                    onValueChange = { fields.website.value = it },
                    label = { Text("Website") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // NIP-05
                OutlinedTextField(
                    value = nip05Value,
                    onValueChange = { fields.nip05.value = it },
                    label = { Text("NIP-05") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        when (nip05Status) {
                            is Nip05Status.Idle -> {}
                            is Nip05Status.Checking ->
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            is Nip05Status.Verified ->
                                Icon(
                                    MaterialSymbols.CheckCircle,
                                    contentDescription = "Verified",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            is Nip05Status.NotVerified ->
                                Icon(
                                    MaterialSymbols.Error,
                                    contentDescription = "Not verified",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            is Nip05Status.Failed ->
                                Icon(
                                    MaterialSymbols.Close,
                                    contentDescription = "Verification failed",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                        }
                    },
                    supportingText =
                        when (nip05Status) {
                            is Nip05Status.NotVerified -> ({ Text("This address doesn't point to your key") })
                            is Nip05Status.Failed -> ({ Text(nip05Status.message) })
                            else -> null
                        },
                )

                // Lightning
                OutlinedTextField(
                    value = lnAddressValue,
                    onValueChange = { fields.lnAddress.value = it },
                    label = { Text("Lightning Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lnURLValue,
                    onValueChange = { fields.lnURL.value = it },
                    label = { Text("LNURL (legacy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Social Proofs — collapsible
                SocialProofsSection(
                    twitter = twitterValue,
                    onTwitterChange = { fields.twitter.value = it },
                    github = githubValue,
                    onGithubChange = { fields.github.value = it },
                    mastodon = mastodonValue,
                    onMastodonChange = { fields.mastodon.value = it },
                    initiallyExpanded = twitterValue.isNotBlank() || githubValue.isNotBlank() || mastodonValue.isNotBlank(),
                )

                // Broadcast status banner
                ProfileBroadcastBanner(
                    status = broadcastStatus,
                    onTap = onBroadcastStatusReset,
                )
            }
        }
    }
}

@Composable
private fun SocialProofsSection(
    twitter: String,
    onTwitterChange: (String) -> Unit,
    github: String,
    onGithubChange: (String) -> Unit,
    mastodon: String,
    onMastodonChange: (String) -> Unit,
    initiallyExpanded: Boolean,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Social Proofs (NIP-39)", style = MaterialTheme.typography.titleSmall)
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = twitter,
                    onValueChange = onTwitterChange,
                    label = { Text("Twitter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Paste proof tweet URL") },
                )
                OutlinedTextField(
                    value = github,
                    onValueChange = onGithubChange,
                    label = { Text("GitHub") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Paste proof gist URL") },
                )
                OutlinedTextField(
                    value = mastodon,
                    onValueChange = onMastodonChange,
                    label = { Text("Mastodon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Paste proof post URL") },
                )
            }
        }
    }
}
