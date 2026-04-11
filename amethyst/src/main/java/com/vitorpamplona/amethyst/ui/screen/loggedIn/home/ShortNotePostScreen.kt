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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.disable_poll
import com.vitorpamplona.amethyst.commons.resources.kind_poll
import com.vitorpamplona.amethyst.commons.resources.kind_zap_poll
import com.vitorpamplona.amethyst.commons.resources.lightning_create_and_add_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_invoice
import com.vitorpamplona.amethyst.commons.resources.poll
import com.vitorpamplona.amethyst.commons.resources.post_anonymously
import com.vitorpamplona.amethyst.commons.resources.zapraiser
import com.vitorpamplona.amethyst.ui.actions.StrippingFailureDialog
import com.vitorpamplona.amethyst.ui.actions.mediaServers.FileServerSelectionRow
import com.vitorpamplona.amethyst.ui.actions.uploads.MAX_VOICE_RECORD_SECONDS
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordVoiceButton
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromFiles
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.actions.uploads.TakeVideoButton
import com.vitorpamplona.amethyst.ui.actions.uploads.UploadProgressIndicator
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceAnonymizationSection
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceMessagePreview
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.creators.aihelp.AiWritingHelpPanel
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.ui.note.creators.expiration.ExpirationDateButton
import com.vitorpamplona.amethyst.ui.note.creators.expiration.ExpirationDatePicker
import com.vitorpamplona.amethyst.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.ui.note.creators.location.AddGeoHashButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationAsHash
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.ui.note.creators.notify.Notifying
import com.vitorpamplona.amethyst.ui.note.creators.polls.PollOptionsField
import com.vitorpamplona.amethyst.ui.note.creators.previews.DisplayPreviews
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.AddSecretEmojiButton
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.zappolls.ZapPollField
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ShortNotePostScreen(
    message: String? = null,
    attachment: String? = null,
    baseReplyToId: HexKey? = null,
    quoteId: HexKey? = null,
    forkId: HexKey? = null,
    versionId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: ShortNotePostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val context = LocalContext.current
    val activity = context.getActivity()

    LaunchedEffect(Unit) {
        postViewModel.initWritingAssistant(context)
    }

    LaunchedEffect(postViewModel, accountViewModel) {
        val baseReplyTo = baseReplyToId?.let { accountViewModel.getNoteIfExists(it) }
        val quote = quoteId?.let { accountViewModel.getNoteIfExists(it) }
        val fork = forkId?.let { accountViewModel.getNoteIfExists(it) }
        val version = versionId?.let { accountViewModel.getNoteIfExists(it) }
        val draft = draftId?.let { accountViewModel.getNoteIfExists(it) }
        postViewModel.load(baseReplyTo, quote, fork, version, draft)
        message?.ifBlank { null }?.let {
            postViewModel.message.setTextAndPlaceCursorAtEnd(it)
            postViewModel.onMessageChanged()
        }
        attachment?.ifBlank { null }?.toUri()?.let {
            withContext(Dispatchers.IO) {
                val mediaType = context.contentResolver.getType(it)
                postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
            }
        }
    }

    DisposableEffect(nav, activity) {
        // Microsoft's swift key sends Gifs as new actions
        val consumer =
            Consumer<Intent> { intent ->
                if (intent.action == Intent.ACTION_SEND) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.ifBlank { null }?.let {
                        postViewModel.addToMessage(it)
                    }

                    IntentCompat.getParcelableExtra(activity.intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                        val mediaType = context.contentResolver.getType(it)
                        postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
                    }
                }
            }

        activity.addOnNewIntentListener(consumer)
        onDispose { activity.removeOnNewIntentListener(consumer) }
    }

    NewPostScreenInner(postViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPostScreenInner(
    postViewModel: ShortNotePostViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    WatchAndLoadMyEmojiList(accountViewModel)

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    BackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            postViewModel.cancel()
        }
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                isActive = postViewModel::canPost,
                onPost = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSync()
                        nav.popBack()
                    }
                },
                onCancel = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
                    accountViewModel.launchSigner {
                        postViewModel.sendDraftSync()
                        postViewModel.cancel()
                    }
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
                    .imePadding(),
        ) {
            NewPostScreenBody(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
private fun NewPostScreenBody(
    postViewModel: ShortNotePostViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        ObserveInboxRelayListAndDisplayIfNotFound(accountViewModel, nav)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Size10dp,
                        end = Size10dp,
                    ).weight(1f),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState, reverseScrolling = true),
            ) {
                postViewModel.originalNote?.let {
                    Row {
                        NoteCompose(
                            baseNote = it,
                            modifier = MaterialTheme.colorScheme.replyModifier,
                            isQuotedNote = true,
                            unPackReply = ReplyRenderType.NONE,
                            makeItShort = true,
                            quotesLeft = 1,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        Spacer(modifier = StdVertSpacer)
                    }
                }

                Row {
                    Notifying(postViewModel.pTags?.toImmutableList(), accountViewModel) {
                        postViewModel.removeFromReplyList(it)
                    }
                }

                // Only show text input if no voice message is being posted
                if (postViewModel.voiceMetadata == null && postViewModel.voiceRecording == null) {
                    Row(
                        modifier = Modifier.padding(vertical = Size10dp),
                    ) {
                        if (postViewModel.wantsAnonymousPost) {
                            IconButton(
                                modifier = Size35Modifier,
                                onClick = { postViewModel.wantsAnonymousPost = false },
                            ) {
                                Icon(
                                    painter = painterRes(resourceId = R.drawable.incognito, 1),
                                    contentDescription = stringResource(Res.string.post_anonymously),
                                    modifier = Size30Modifier,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier.clickable {
                                        postViewModel.wantsAnonymousPost = true
                                    },
                            ) {
                                BaseUserPicture(
                                    accountViewModel.userProfile(),
                                    Size35dp,
                                    accountViewModel = accountViewModel,
                                )
                            }
                        }
                        MessageField(
                            R.string.what_s_on_your_mind,
                            postViewModel,
                            onContentReceived = { uri, mimeType ->
                                postViewModel.selectImage(
                                    persistentListOf(
                                        SelectedMedia(uri, mimeType),
                                    ),
                                )
                            },
                        )
                    }
                }

                if (postViewModel.wantsPoll || postViewModel.wantsZapPoll) {
                    Column(
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = postViewModel.wantsPoll,
                                onClick = {
                                    postViewModel.wantsPoll = true
                                    postViewModel.wantsZapPoll = false
                                },
                                label = { Text(stringResource(Res.string.kind_poll)) },
                            )
                            FilterChip(
                                selected = postViewModel.wantsZapPoll,
                                onClick = {
                                    postViewModel.wantsZapPoll = true
                                    postViewModel.wantsPoll = false
                                },
                                label = { Text(stringResource(Res.string.kind_zap_poll)) },
                            )
                        }
                        if (postViewModel.wantsPoll) {
                            Row(verticalAlignment = CenterVertically) {
                                PollOptionsField(postViewModel)
                            }
                        } else {
                            Row(verticalAlignment = CenterVertically) {
                                ZapPollField(postViewModel)
                            }
                        }
                    }
                }

                DisplayPreviews(postViewModel.urlPreviews, accountViewModel, nav)

                if (postViewModel.wantsToMarkAsSensitive) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ContentSensitivityExplainer(
                            description = postViewModel.contentWarningDescription,
                            onDescriptionChange = { postViewModel.contentWarningDescription = it },
                        )
                    }
                }

                if (postViewModel.wantsExpirationDate) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ExpirationDatePicker(postViewModel)
                    }
                }

                if (postViewModel.wantsToAddGeoHash) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        LocationAsHash(postViewModel) {
                            SettingsRow(
                                R.string.geohash_exclusive,
                                R.string.geohash_exclusive_explainer,
                            ) {
                                Switch(postViewModel.wantsExclusiveGeoPost, onCheckedChange = { postViewModel.wantsExclusiveGeoPost = it })
                            }
                        }
                    }
                }

                if (postViewModel.wantsForwardZapTo) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ForwardZapTo(postViewModel, accountViewModel)
                    }
                }

                postViewModel.multiOrchestrator?.let {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        val context = LocalContext.current
                        ImageVideoDescription(
                            it,
                            accountViewModel.account.settings.defaultFileServer,
                            isUploading = postViewModel.mediaUploadTracker.isUploading,
                            onAdd = { alt, server, sensitiveContent, mediaQuality, useH265, stripMetadata, convertGifToMp4 ->
                                postViewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, context, useH265, stripMetadata, convertGifToMp4)
                                accountViewModel.account.settings.changeDefaultFileServer(server)
                            },
                            onDelete = postViewModel::deleteMediaToUpload,
                            onCancel = { postViewModel.multiOrchestrator = null },
                            accountViewModel = accountViewModel,
                        )
                    }
                }

                // Show preview for both uploaded messages (voiceMetadata) and pending recordings
                (postViewModel.voiceMetadata ?: postViewModel.getVoicePreviewMetadata())?.let { metadata ->
                    val fileServersState =
                        accountViewModel.account.blossomServers.hostNameFlow
                            .collectAsState()
                    val fileServers = fileServersState.value

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        // Display voice preview or uploading progress
                        postViewModel.voiceOrchestrator?.let { orchestrator ->
                            UploadProgressIndicator(orchestrator)
                        } ?: run {
                            val displayMetadata =
                                metadata.copy(
                                    waveform = postViewModel.activeWaveform ?: metadata.waveform,
                                )
                            VoiceMessagePreview(
                                voiceMetadata = displayMetadata,
                                localFile = postViewModel.activeFile,
                                onReRecord = { recording -> postViewModel.selectVoiceRecording(recording) },
                                isUploading = postViewModel.isUploadingVoice,
                                onRemove = { postViewModel.removeVoiceMessage() },
                            )

                            // Voice anonymization section (only show when not uploading and voice is pending)
                            if (postViewModel.voiceRecording != null) {
                                VoiceAnonymizationSection(
                                    selectedPreset = postViewModel.selectedPreset,
                                    processingPreset = postViewModel.processingPreset,
                                    onPresetSelected = { postViewModel.selectPreset(it) },
                                )
                            }
                        }

                        FileServerSelectionRow(
                            fileServers = fileServers,
                            selectedServer = postViewModel.voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer,
                            onSelect = { postViewModel.voiceSelectedServer = it },
                        )
                    }
                }

                if (postViewModel.wantsInvoice) {
                    postViewModel.lnAddress()?.let { lud16 ->
                        Row(
                            verticalAlignment = CenterVertically,
                            modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                        ) {
                            InvoiceRequest(
                                lud16,
                                accountViewModel.account.userProfile(),
                                accountViewModel,
                                stringResource(Res.string.lightning_invoice),
                                stringResource(Res.string.lightning_create_and_add_invoice),
                                onNewInvoice = {
                                    postViewModel.insertAtCursor(it)
                                    postViewModel.wantsInvoice = false
                                },
                                onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                            )
                        }
                    }
                }

                if (postViewModel.wantsSecretEmoji) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            SecretEmojiRequest {
                                postViewModel.insertAtCursor(it)
                                postViewModel.wantsSecretEmoji = false
                            }
                        }
                    }
                }

                if (postViewModel.wantsZapRaiser && postViewModel.hasLnAddress()) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ZapRaiserRequest(
                            stringResource(Res.string.zapraiser),
                            postViewModel,
                        )
                    }
                }
            }
        }

        postViewModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                postViewModel::autocompleteWithUser,
                accountViewModel,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        AiWritingHelpPanel(
            isVisible = postViewModel.showAiPanel,
            readyResults = postViewModel.aiResults,
            selectedResult = postViewModel.aiSelectedResult,
            onToneSelected = postViewModel::selectAiResult,
            onApply = postViewModel::applyAiResult,
            onDismiss = postViewModel::dismissAiResult,
        )

        BottomRowActions(postViewModel)
    }
}

@Composable
private fun BottomRowActions(postViewModel: ShortNotePostViewModel) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
                .height(50.dp),
        verticalAlignment = CenterVertically,
    ) {
        SelectFromGallery(
            isUploading = postViewModel.isUploadingImage,
            enabled = !postViewModel.isUploadingFile,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        SelectFromFiles(
            isUploading = postViewModel.isUploadingFile,
            enabled = !postViewModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        TakePictureButton(
            onPictureTaken = {
                postViewModel.selectImage(it)
            },
        )

        TakeVideoButton(
            onVideoTaken = {
                postViewModel.selectImage(it)
            },
        )

        RecordVoiceButton(
            onVoiceTaken = { recording ->
                postViewModel.selectVoiceRecording(recording)
            },
            maxDurationSeconds = MAX_VOICE_RECORD_SECONDS,
        )

        if (postViewModel.canUsePoll || postViewModel.canUseZapPoll) {
            AddPollButton(postViewModel.wantsPoll || postViewModel.wantsZapPoll) {
                val isActive = postViewModel.wantsPoll || postViewModel.wantsZapPoll
                if (isActive) {
                    postViewModel.wantsPoll = false
                    postViewModel.wantsZapPoll = false
                } else {
                    postViewModel.wantsPoll = true
                }
            }
        }

        ForwardZapToButton(postViewModel.wantsForwardZapTo) {
            postViewModel.wantsForwardZapTo = !postViewModel.wantsForwardZapTo
        }

        if (postViewModel.canAddZapRaiser) {
            AddZapraiserButton(postViewModel.wantsZapRaiser) {
                postViewModel.wantsZapRaiser = !postViewModel.wantsZapRaiser
            }
        }

        MarkAsSensitiveButton(postViewModel.wantsToMarkAsSensitive) {
            postViewModel.toggleMarkAsSensitive()
        }

        ExpirationDateButton(postViewModel.wantsExpirationDate) {
            postViewModel.toggleExpirationDate()
        }

        AddGeoHashButton(postViewModel.wantsToAddGeoHash) {
            postViewModel.wantsToAddGeoHash = !postViewModel.wantsToAddGeoHash
        }

        AddSecretEmojiButton(postViewModel.wantsSecretEmoji) {
            postViewModel.wantsSecretEmoji = !postViewModel.wantsSecretEmoji
        }

        if (postViewModel.canAddInvoice && postViewModel.hasLnAddress()) {
            AddLnInvoiceButton(postViewModel.wantsInvoice) {
                postViewModel.wantsInvoice = !postViewModel.wantsInvoice
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
private fun BottomRowActionsPreview() {
    val model = ShortNotePostViewModel()
    model.canUsePoll = true
    ThemeComparisonColumn {
        BottomRowActions(model)
    }
}

@Composable
private fun AddPollButton(
    isPollActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!isPollActive) {
            Icon(
                imageVector = Icons.Outlined.Poll,
                contentDescription = stringResource(Res.string.poll),
                modifier = Modifier.height(22.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Poll,
                contentDescription = stringResource(Res.string.disable_poll),
                modifier = Modifier.height(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
