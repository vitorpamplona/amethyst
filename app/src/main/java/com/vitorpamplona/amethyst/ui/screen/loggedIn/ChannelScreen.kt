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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.ui.actions.NewChannelView
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.actions.ServerOption
import com.vitorpamplona.amethyst.ui.actions.UploadFromGallery
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.ui.screen.NostrChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.equalImmutableLists
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldLeadingIconModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.ui.theme.liveStreamTag
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE
import com.vitorpamplona.quartz.events.Participant
import com.vitorpamplona.quartz.events.findURLs
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChannelScreen(
    channelId: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (channelId == null) return

    LoadChannel(channelId, accountViewModel) {
        PrepareChannelViewModels(
            baseChannel = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
fun PrepareChannelViewModels(
    baseChannel: Channel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val feedViewModel: NostrChannelFeedViewModel =
        viewModel(
            key = baseChannel.idHex + "ChannelFeedViewModel",
            factory =
                NostrChannelFeedViewModel.Factory(
                    baseChannel,
                    accountViewModel.account,
                ),
        )

    val channelScreenModel: NewPostViewModel = viewModel()
    channelScreenModel.accountViewModel = accountViewModel
    channelScreenModel.account = accountViewModel.account

    ChannelScreen(
        channel = baseChannel,
        feedViewModel = feedViewModel,
        newPostModel = channelScreenModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ChannelScreen(
    channel: Channel,
    feedViewModel: NostrChannelFeedViewModel,
    newPostModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val context = LocalContext.current

    NostrChannelDataSource.loadMessagesBetween(accountViewModel.account, channel)

    val lifeCycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            newPostModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) { Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    DisposableEffect(accountViewModel) {
        NostrChannelDataSource.loadMessagesBetween(accountViewModel.account, channel)
        NostrChannelDataSource.start()
        feedViewModel.invalidateData(true)

        onDispose {
            NostrChannelDataSource.clear()
            NostrChannelDataSource.stop()
        }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Channel Start")
                    NostrChannelDataSource.start()
                    feedViewModel.invalidateData(true)
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Channel Stop")

                    NostrChannelDataSource.clear()
                    NostrChannelDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxHeight()) {
        val replyTo = remember { mutableStateOf<Note?>(null) }

        Column(
            modifier =
                remember {
                    Modifier
                        .fillMaxHeight()
                        .padding(vertical = 0.dp)
                        .weight(1f, true)
                },
        ) {
            if (channel is LiveActivitiesChannel) {
                ShowVideoStreaming(channel, accountViewModel)
            }
            RefreshingChatroomFeedView(
                viewModel = feedViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Channel/${channel.idHex}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = { replyTo.value = it },
                onWantsToEditDraft = {
                    newPostModel.load(accountViewModel, null, null, null, null, it)
                },
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        replyTo.value?.let { DisplayReplyingToNote(it, accountViewModel, nav) { replyTo.value = null } }

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                newPostModel.draftTextChanges
                    .receiveAsFlow()
                    .debounce(1000)
                    .collectLatest {
                        innerSendPost(replyTo, channel, newPostModel, accountViewModel, newPostModel.draftTag)
                    }
            }
        }

        // LAST ROW
        EditFieldRow(newPostModel, isPrivate = false, accountViewModel = accountViewModel) {
            scope.launch(Dispatchers.IO) {
                innerSendPost(replyTo, channel, newPostModel, accountViewModel, null)
                newPostModel.message = TextFieldValue("")
                replyTo.value = null
                accountViewModel.deleteDraft(newPostModel.draftTag)
                feedViewModel.sendToTop()
            }
        }
    }
}

private suspend fun innerSendPost(
    replyTo: MutableState<Note?>,
    channel: Channel,
    newPostModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
    draftTag: String?,
) {
    val tagger =
        NewMessageTagger(
            message = newPostModel.message.text,
            pTags = listOfNotNull(replyTo.value?.author),
            eTags = listOfNotNull(replyTo.value),
            channelHex = channel.idHex,
            dao = accountViewModel,
        )
    tagger.run()

    val urls = findURLs(tagger.message)
    val usedAttachments = newPostModel.nip94attachments.filter { it.urls().intersect(urls.toSet()).isNotEmpty() }

    if (channel is PublicChatChannel) {
        accountViewModel.account.sendChannelMessage(
            message = tagger.message,
            toChannel = channel.idHex,
            replyTo = tagger.eTags,
            mentions = tagger.pTags,
            wantsToMarkAsSensitive = false,
            nip94attachments = usedAttachments,
            draftTag = draftTag,
        )
    } else if (channel is LiveActivitiesChannel) {
        accountViewModel.account.sendLiveMessage(
            message = tagger.message,
            toChannel = channel.address,
            replyTo = tagger.eTags,
            mentions = tagger.pTags,
            wantsToMarkAsSensitive = false,
            nip94attachments = usedAttachments,
            draftTag = draftTag,
        )
    }
}

@Composable
fun DisplayReplyingToNote(
    replyingNote: Note?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 10.dp)
            .heightIn(max = 100.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (replyingNote != null) {
            Column(remember { Modifier.weight(1f) }) {
                ChatroomMessageCompose(
                    baseNote = replyingNote,
                    null,
                    innerQuote = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = {},
                    onWantsToEditDraft = {},
                )
            }

            Column(Modifier.padding(end = 10.dp)) {
                IconButton(
                    modifier = Modifier.size(30.dp),
                    onClick = onCancel,
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        null,
                        modifier =
                            Modifier
                                .padding(end = 5.dp)
                                .size(30.dp),
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }
        }
    }
}

@Composable
fun EditFieldRow(
    channelScreenModel: NewPostViewModel,
    isPrivate: Boolean,
    accountViewModel: AccountViewModel,
    onSendNewMessage: () -> Unit,
) {
    Column(
        modifier = EditFieldModifier,
    ) {
        val context = LocalContext.current

        ShowUserSuggestionList(channelScreenModel, accountViewModel)

        MyTextField(
            value = channelScreenModel.message,
            onValueChange = { channelScreenModel.updateMessage(it) },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            shape = EditFieldBorder,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.reply_here),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            trailingIcon = {
                ThinSendButton(
                    isActive =
                        channelScreenModel.message.text.isNotBlank() && !channelScreenModel.isUploadingImage,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    onSendNewMessage()
                }
            },
            leadingIcon = {
                UploadFromGallery(
                    isUploading = channelScreenModel.isUploadingImage,
                    tint = MaterialTheme.colorScheme.placeholderText,
                    modifier = EditFieldLeadingIconModifier,
                ) {
                    channelScreenModel.upload(
                        galleryUri = it,
                        alt = null,
                        sensitiveContent = false,
                        server = ServerOption(accountViewModel.account.defaultFileServer, false),
                        context = context,
                    )
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            visualTransformation = UrlUserTagTransformation(MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    // COPIED FROM TEXT FIELD
    // The only change is the contentPadding below

    val textColor =
        textStyle.color.takeOrElse {
            val focused by interactionSource.collectIsFocusedAsState()

            val targetValue =
                when {
                    !enabled -> MaterialTheme.colorScheme.placeholderText
                    isError -> MaterialTheme.colorScheme.onSurface
                    focused -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface
                }

            rememberUpdatedState(targetValue).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(LocalTextSelectionColors provides LocalTextSelectionColors.current) {
        BasicTextField(
            value = value,
            modifier =
                modifier.defaultMinSize(
                    minWidth = TextFieldDefaults.MinWidth,
                    minHeight = 36.dp,
                ),
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            decorationBox =
                @Composable { innerTextField ->
                    TextFieldDefaults.DecorationBox(
                        value = value.text,
                        visualTransformation = visualTransformation,
                        innerTextField = innerTextField,
                        placeholder = placeholder,
                        label = label,
                        leadingIcon = leadingIcon,
                        trailingIcon = trailingIcon,
                        prefix = prefix,
                        suffix = suffix,
                        supportingText = supportingText,
                        shape = shape,
                        singleLine = singleLine,
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        contentPadding =
                            TextFieldDefaults.contentPaddingWithoutLabel(
                                start = 10.dp,
                                top = 12.dp,
                                end = 10.dp,
                                bottom = 12.dp,
                            ),
                    )
                },
        )
    }
}

@Composable
fun ChannelHeader(
    channelNote: Note,
    showVideo: Boolean,
    sendToChannel: Boolean,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val channelHex by remember { derivedStateOf { channelNote.channelHex() } }
    channelHex?.let {
        ChannelHeader(
            channelHex = it,
            showVideo = showVideo,
            sendToChannel = sendToChannel,
            modifier = modifier,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun ChannelHeader(
    channelHex: String,
    showVideo: Boolean,
    showFlag: Boolean = true,
    sendToChannel: Boolean = false,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadChannel(channelHex, accountViewModel) {
        ChannelHeader(
            it,
            showVideo,
            showFlag,
            sendToChannel,
            modifier,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun ChannelHeader(
    baseChannel: Channel,
    showVideo: Boolean,
    showFlag: Boolean = true,
    sendToChannel: Boolean = false,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (showVideo && baseChannel is LiveActivitiesChannel) {
            ShowVideoStreaming(baseChannel, accountViewModel)
        }

        val expanded = remember { mutableStateOf(false) }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier =
                modifier.clickable {
                    if (sendToChannel) {
                        nav(routeFor(baseChannel))
                    } else {
                        expanded.value = !expanded.value
                    }
                },
        ) {
            ShortChannelHeader(
                baseChannel = baseChannel,
                accountViewModel = accountViewModel,
                nav = nav,
                showFlag = showFlag,
            )

            if (expanded.value) {
                LongChannelHeader(baseChannel = baseChannel, accountViewModel = accountViewModel, nav = nav)
            }
        }
    }
}

@Composable
fun ShowVideoStreaming(
    baseChannel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
) {
    baseChannel.info?.let {
        SensitivityWarning(
            event = it,
            accountViewModel = accountViewModel,
        ) {
            val streamingInfo by
                baseChannel.live
                    .map {
                        val activity = it.channel as? LiveActivitiesChannel
                        activity?.info
                    }
                    .distinctUntilChanged()
                    .observeAsState(baseChannel.info)

            streamingInfo?.let { event ->
                val url = remember(streamingInfo) { event.streaming() }

                url?.let {
                    CrossfadeCheckIfUrlIsOnline(url, accountViewModel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier =
                                remember {
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp, max = 300.dp)
                                },
                        ) {
                            val zoomableUrlVideo =
                                remember(streamingInfo) {
                                    MediaUrlVideo(
                                        url = url,
                                        description = baseChannel.toBestDisplayName(),
                                        artworkUri = event.image(),
                                        authorName = baseChannel.creatorName(),
                                        uri = event.toNostrUri(),
                                    )
                                }

                            ZoomableContentView(
                                content = zoomableUrlVideo,
                                roundedCorner = false,
                                accountViewModel = accountViewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShortChannelHeader(
    baseChannel: Channel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    showFlag: Boolean,
) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel ?: return

    val automaticallyShowProfilePicture =
        remember {
            accountViewModel.settings.showProfilePictures.value
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (channel is LiveActivitiesChannel) {
            channel.creator?.let {
                UserPicture(
                    user = it,
                    size = Size34dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        } else {
            channel.profilePicture()?.let {
                RobohashFallbackAsyncImage(
                    robot = channel.idHex,
                    model = it,
                    contentDescription = stringResource(R.string.profile_image),
                    contentScale = ContentScale.Crop,
                    modifier = HeaderPictureModifier,
                    loadProfilePicture = automaticallyShowProfilePicture,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .height(35.dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = remember(channelState) { channel.toBestDisplayName() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .height(Size35dp)
                    .padding(start = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel is PublicChatChannel) {
                ShortChannelActionOptions(channel, accountViewModel, nav)
            }
            if (channel is LiveActivitiesChannel) {
                LiveChannelActionOptions(channel, showFlag, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun LongChannelHeader(
    baseChannel: Channel,
    lineModifier: Modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val channelState by baseChannel.live.observeAsState()
    val channel = channelState?.channel ?: return

    Row(
        lineModifier,
    ) {
        val summary = remember(channelState) { channel.summary()?.ifBlank { null } }

        Column(
            Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember { mutableStateOf(defaultBackground) }

                val tags =
                    remember(channelState) {
                        if (baseChannel is LiveActivitiesChannel) {
                            baseChannel.info?.tags()?.toImmutableListOfLists() ?: EmptyTagList
                        } else {
                            EmptyTagList
                        }
                    }

                TranslatableRichTextViewer(
                    content = summary ?: stringResource(id = R.string.groups_no_descriptor),
                    canPreview = false,
                    quotesLeft = 1,
                    tags = tags,
                    backgroundColor = background,
                    id = baseChannel.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (baseChannel is LiveActivitiesChannel && baseChannel.info?.hasHashtags() == true) {
                val hashtags =
                    remember(baseChannel.info) {
                        baseChannel.info?.hashtags()?.toImmutableList() ?: persistentListOf()
                    }
                DisplayUncitedHashtags(hashtags, summary ?: "", nav)
            }
        }

        Column {
            if (channel is PublicChatChannel) {
                Row {
                    Spacer(DoubleHorzSpacer)
                    LongChannelActionOptions(channel, accountViewModel, nav)
                }
            }
        }
    }

    LoadNote(baseNoteHex = channel.idHex, accountViewModel) { loadingNote ->
        loadingNote?.let { note ->
            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.owner),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NoteAuthorPicture(note, nav, accountViewModel, Size25dp)
                Spacer(DoubleHorzSpacer)
                NoteUsernameDisplay(note, remember { Modifier.weight(1f) })
            }

            Row(
                lineModifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.created_at),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(75.dp),
                )
                Spacer(DoubleHorzSpacer)
                NormalTimeAgo(note, remember { Modifier.weight(1f) })
                MoreOptionsButton(note, null, accountViewModel, nav)
            }
        }
    }

    var participantUsers by
        remember(baseChannel) {
            mutableStateOf<ImmutableList<Pair<Participant, User>>>(
                persistentListOf(),
            )
        }

    if (channel is LiveActivitiesChannel) {
        LaunchedEffect(key1 = channelState) {
            launch(Dispatchers.IO) {
                val newParticipantUsers =
                    channel.info
                        ?.participants()
                        ?.mapNotNull { part ->
                            LocalCache.checkGetOrCreateUser(part.key)?.let { Pair(part, it) }
                        }
                        ?.toImmutableList()

                if (
                    newParticipantUsers != null && !equalImmutableLists(newParticipantUsers, participantUsers)
                ) {
                    participantUsers = newParticipantUsers
                }
            }
        }

        participantUsers.forEach {
            Row(
                lineModifier.clickable { nav("User/${it.second.pubkeyHex}") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                it.first.role?.let { it1 ->
                    Text(
                        text = it1.capitalize(Locale.ROOT),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(55.dp),
                    )
                }
                Spacer(DoubleHorzSpacer)
                ClickableUserPicture(it.second, Size25dp, accountViewModel)
                Spacer(DoubleHorzSpacer)
                UsernameDisplay(it.second, remember { Modifier.weight(1f) })
            }
        }
    }
}

@Composable
fun NormalTimeAgo(
    baseNote: Note,
    modifier: Modifier,
) {
    val nowStr = stringResource(id = R.string.now)

    val time by
        remember(baseNote) { derivedStateOf { timeAgoShort(baseNote.createdAt() ?: 0, nowStr) } }

    Text(
        text = time,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ShortChannelActionOptions(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadNote(baseNoteHex = channel.idHex, accountViewModel) {
        it?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = RowColSpacing) {
                LikeReaction(
                    baseNote = it,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav,
                )
                ZapReaction(
                    baseNote = it,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                Spacer(modifier = StdHorzSpacer)
            }
        }
    }

    WatchChannelFollows(channel, accountViewModel) { isFollowing ->
        if (!isFollowing) {
            JoinChatButton(accountViewModel, channel, nav)
        }
    }
}

@Composable
private fun WatchChannelFollows(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    content: @Composable (Boolean) -> Unit,
) {
    val isFollowing by
        accountViewModel
            .userProfile()
            .live()
            .follows
            .map { it.user.latestContactList?.isTaggedEvent(channel.idHex) ?: false }
            .distinctUntilChanged()
            .observeAsState(
                accountViewModel.userProfile().latestContactList?.isTaggedEvent(channel.idHex) ?: false,
            )

    content(isFollowing)
}

@Composable
private fun LongChannelActionOptions(
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val isMe by
        remember(accountViewModel) {
            derivedStateOf { channel.creator == accountViewModel.account.userProfile() }
        }

    if (isMe) {
        EditButton(accountViewModel, channel)
    }

    WatchChannelFollows(channel, accountViewModel) { isFollowing ->
        if (isFollowing) {
            LeaveChatButton(accountViewModel, channel, nav)
        }
    }
}

@Composable
private fun LiveChannelActionOptions(
    channel: LiveActivitiesChannel,
    showFlag: Boolean = true,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val isLive by remember(channel) { derivedStateOf { channel.info?.status() == STATUS_LIVE } }

    val note = remember(channel.idHex) { LocalCache.getNoteIfExists(channel.idHex) }

    note?.let {
        if (showFlag && isLive) {
            LiveFlag()
            Spacer(modifier = StdHorzSpacer)
        }

        LikeReaction(
            baseNote = it,
            grayTint = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
            nav,
        )
        Spacer(modifier = StdHorzSpacer)
        ZapReaction(
            baseNote = it,
            grayTint = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun LiveFlag() {
    Text(
        text = stringResource(id = R.string.live_stream_live_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Red)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun EndedFlag() {
    Text(
        text = stringResource(id = R.string.live_stream_ended_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Black)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun OfflineFlag() {
    Text(
        text = stringResource(id = R.string.live_stream_offline_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Black)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun ScheduledFlag(starts: Long?) {
    val startsIn =
        starts?.let {
            SimpleDateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
            ).format(Date(starts * 1000))
        }

    Text(
        text = startsIn ?: stringResource(id = R.string.live_stream_planned_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = liveStreamTag,
    )
}

@Composable
private fun NoteCopyButton(note: Channel) {
    var popupExpanded by remember { mutableStateOf(false) }

    Button(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = { popupExpanded = true },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.placeholderText,
            ),
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Share,
            contentDescription = stringResource(R.string.copies_the_note_id_to_the_clipboard_for_sharing),
        )

        DropdownMenu(
            expanded = popupExpanded,
            onDismissRequest = { popupExpanded = false },
        ) {
            val clipboardManager = LocalClipboardManager.current

            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_channel_id_note_to_the_clipboard)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString("nostr:" + note.idNote()))
                    popupExpanded = false
                },
            )
        }
    }
}

@Composable
private fun EditButton(
    accountViewModel: AccountViewModel,
    channel: PublicChatChannel,
) {
    var wantsToPost by remember { mutableStateOf(false) }

    if (wantsToPost) {
        NewChannelView({ wantsToPost = false }, accountViewModel, channel)
    }

    Button(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = { wantsToPost = true },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.EditNote,
            contentDescription = stringResource(R.string.edits_the_channel_metadata),
        )
    }
}

@Composable
fun JoinChatButton(
    accountViewModel: AccountViewModel,
    channel: Channel,
    nav: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { scope.launch(Dispatchers.IO) { accountViewModel.account.follow(channel) } },
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.join), color = Color.White)
    }
}

@Composable
fun LeaveChatButton(
    accountViewModel: AccountViewModel,
    channel: Channel,
    nav: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { scope.launch(Dispatchers.IO) { accountViewModel.account.unfollow(channel) } },
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.leave), color = Color.White)
    }
}

@Composable
fun JoinCommunityButton(
    accountViewModel: AccountViewModel,
    note: AddressableNote,
    nav: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { scope.launch(Dispatchers.IO) { accountViewModel.account.follow(note) } },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.join), color = Color.White)
    }
}

@Composable
fun LeaveCommunityButton(
    accountViewModel: AccountViewModel,
    note: AddressableNote,
    nav: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { scope.launch(Dispatchers.IO) { accountViewModel.account.unfollow(note) } },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.leave), color = Color.White)
    }
}
