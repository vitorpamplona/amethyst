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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LinkIcon
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.KindChip
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip89AppHandlers.PlatformType
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppMetadata
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun RenderAppDefinition(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AppDefinitionEvent ?: return

    var metadata by remember { mutableStateOf<AppMetadata?>(null) }

    LaunchedEffect(key1 = noteEvent) {
        withContext(Dispatchers.IO) { metadata = noteEvent.appMetaData() }
    }

    metadata?.let { theAppMetadata ->
        Box {
            val clipboardManager = LocalClipboard.current
            val scope = rememberCoroutineScope()
            val uri = LocalUriHandler.current

            if (!theAppMetadata.banner.isNullOrBlank()) {
                var zoomImageDialogOpen by remember { mutableStateOf(false) }
                var bannerSourceBounds by remember { mutableStateOf<Rect?>(null) }

                AsyncImage(
                    model = theAppMetadata.banner,
                    contentDescription = stringRes(id = R.string.profile_image),
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                            .onGloballyPositioned { bannerSourceBounds = it.boundsInWindow() }
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    theAppMetadata.banner?.let {
                                        scope.launch {
                                            clipboardManager.setText(it)
                                        }
                                    }
                                },
                            ),
                )

                if (zoomImageDialogOpen) {
                    ZoomableImageDialog(
                        imageUrl = RichTextParser.parseImageOrVideo(theAppMetadata.banner!!),
                        sourceBounds = bannerSourceBounds,
                        onDismiss = { zoomImageDialogOpen = false },
                        accountViewModel = accountViewModel,
                    )
                }
            } else {
                Image(
                    painter = painterRes(R.drawable.profile_banner, 6),
                    contentDescription = stringRes(id = R.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(125.dp),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 75.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    var zoomImageDialogOpen by remember { mutableStateOf(false) }
                    var pictureSourceBounds by remember { mutableStateOf<Rect?>(null) }
                    Box(Modifier.size(100.dp)) {
                        theAppMetadata.picture?.let { picture ->
                            AsyncImage(
                                model = picture,
                                contentDescription = theAppMetadata.name,
                                contentScale = ContentScale.FillWidth,
                                modifier =
                                    Modifier
                                        .border(
                                            3.dp,
                                            MaterialTheme.colorScheme.background,
                                            CircleShape,
                                        ).clip(shape = CircleShape)
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                        .onGloballyPositioned { pictureSourceBounds = it.boundsInWindow() }
                                        .combinedClickable(
                                            onClick = { zoomImageDialogOpen = true },
                                            onLongClick = {
                                                scope.launch {
                                                    clipboardManager.setText(picture)
                                                }
                                            },
                                        ),
                            )

                            if (zoomImageDialogOpen) {
                                ZoomableImageDialog(
                                    imageUrl = RichTextParser.parseImageOrVideo(theAppMetadata.picture!!),
                                    sourceBounds = pictureSourceBounds,
                                    onDismiss = { zoomImageDialogOpen = false },
                                    accountViewModel = accountViewModel,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        // Cancels the surrounding Column's horizontal padding so
                        // the button's right edge lines up with the banner's.
                        modifier = Modifier.padding(bottom = 3.dp).offset(x = 10.dp),
                    ) {
                        if (accountViewModel.account.isWriteable()) {
                            RecommendAppButton(noteEvent, note, accountViewModel)
                        }
                    }
                }

                val name = remember(theAppMetadata) { theAppMetadata.anyName() }
                name?.let {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 7.dp),
                    ) {
                        CreateTextWithEmoji(
                            text = it,
                            tags =
                                remember(note) {
                                    (note.event?.tags ?: emptyArray()).toImmutableListOfLists()
                                },
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp,
                        )
                    }
                }

                val website = remember(theAppMetadata) { theAppMetadata.website }
                if (!website.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinkIcon(Size16Modifier, MaterialTheme.colorScheme.placeholderText)

                        ClickableTextPrimary(
                            text = website.removePrefix("https://"),
                            onClick = { website.let { runCatching { uri.openUri(it) } } },
                            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
                        )
                    }
                }

                theAppMetadata.about?.let {
                    Row(
                        modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
                    ) {
                        val tags =
                            remember(note) {
                                note.event?.tags?.toImmutableListOfLists() ?: EmptyTagList
                            }
                        val bgColor = MaterialTheme.colorScheme.background
                        val backgroundColor = remember { mutableStateOf(bgColor) }
                        TranslatableRichTextViewer(
                            content = it,
                            canPreview = false,
                            quotesLeft = 1,
                            tags = tags,
                            backgroundColor = backgroundColor,
                            id = note.idHex,
                            callbackUri = note.toNostrUri(),
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }

                val platforms = remember(noteEvent) { noteEvent.platformLinks().map { it.platform }.distinct() }
                if (platforms.isNotEmpty()) {
                    Text(
                        text = stringRes(R.string.app_definition_available_on),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        platforms.forEach { PlatformChip(it) }
                    }
                }

                val supportedKinds = remember(noteEvent) { noteEvent.supportedKinds() }
                if (supportedKinds.isNotEmpty()) {
                    Text(
                        text = stringRes(R.string.app_definition_handles),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 5.dp),
                    ) {
                        val visible = supportedKinds.take(VISIBLE_SUPPORTED_KIND_LIMIT)
                        visible.forEach { KindChip(it) }
                        val overflow = supportedKinds.size - VISIBLE_SUPPORTED_KIND_LIMIT
                        if (overflow > 0) {
                            OverflowChip(overflow)
                        }
                    }
                }
            }
        }
    }
}

private const val VISIBLE_SUPPORTED_KIND_LIMIT = 12

/** Same shape and metrics as [KindChip] so it lines up with the kind chips. */
@Composable
private fun OverflowChip(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "+$count",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlatformChip(platform: String) {
    val name =
        when (platform) {
            PlatformType.WEB.code -> stringRes(R.string.platform_web)
            PlatformType.ANDROID.code -> stringRes(R.string.platform_android)
            PlatformType.IOS.code -> stringRes(R.string.platform_ios)
            else -> platform
        }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Shows whether the logged-in user publicly recommends this app (NIP-89,
 * kind 31989) and toggles the recommendation on tap.
 */
@Composable
private fun RecommendAppButton(
    noteEvent: AppDefinitionEvent,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val myPubkey = accountViewModel.userProfile().pubkeyHex

    val isRecommended by
        produceState(initialValue = false, key1 = noteEvent) {
            value = withContext(Dispatchers.IO) { accountViewModel.account.isAppRecommended(noteEvent) }
            LocalCache.live.newEventBundles.collect { bundle ->
                val touchesMine = bundle.any { (it.event as? AppRecommendationEvent)?.pubKey == myPubkey }
                if (touchesMine) {
                    value = withContext(Dispatchers.IO) { accountViewModel.account.isAppRecommended(noteEvent) }
                }
            }
        }

    val compactHeight = Modifier.height(32.dp)
    val compactPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

    if (isRecommended) {
        OutlinedButton(
            onClick = {
                accountViewModel.launchSigner {
                    accountViewModel.account.unrecommendApp(noteEvent.address())
                }
            },
            modifier = compactHeight,
            contentPadding = compactPadding,
        ) {
            Text(stringRes(R.string.app_definition_recommended), style = MaterialTheme.typography.labelMedium)
        }
    } else {
        Button(
            enabled = noteEvent.supportedKinds().isNotEmpty(),
            onClick = {
                accountViewModel.launchSigner {
                    accountViewModel.account.recommendApp(noteEvent, note.relayHintUrl())
                }
            },
            modifier = compactHeight,
            contentPadding = compactPadding,
        ) {
            Text(stringRes(R.string.app_definition_recommend), style = MaterialTheme.typography.labelMedium)
        }
    }
}
