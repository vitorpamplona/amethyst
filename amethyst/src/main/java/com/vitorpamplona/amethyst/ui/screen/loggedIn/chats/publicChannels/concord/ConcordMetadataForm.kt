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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The shared metadata form for creating and editing a Concord community — a large circular icon
 * hero at the top (tap to pick an image, which is AES-256-GCM-encrypted and uploaded to Blossom as
 * a CORD-02 §6 [ImagePointer], see [ConcordImageUploader]), then the name and description fields.
 * Mirrors the NIP-29 `GroupImagePicker` hero + `GroupMetadataFields` layout so the two features feel
 * consistent. Callers own the state and add the surrounding scaffold, relays section (create only),
 * and the create/save action.
 */
@Composable
fun ConcordMetadataFields(
    name: MutableState<String>,
    about: MutableState<String>,
    icon: MutableState<ImagePointer?>,
    robotSeed: String,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    banner: MutableState<ImagePointer?>? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        banner?.let { ConcordBannerHero(banner = it, accountViewModel = accountViewModel) }

        ConcordIconHero(
            robotSeed = robotSeed,
            icon = icon,
            displayName = name.value,
            accountViewModel = accountViewModel,
        )

        OutlinedTextField(
            value = name.value,
            onValueChange = { name.value = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringRes(R.string.concord_create_name)) },
        )
        OutlinedTextField(
            value = about.value,
            onValueChange = { about.value = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
            label = { Text(stringRes(R.string.concord_create_about)) },
        )
    }
}

/**
 * The circular community-icon hero: shows the current (decrypted) icon over a stable robohash
 * placeholder, and on tap opens the photo picker → encrypts + uploads the chosen image and updates
 * [icon] to the resulting encrypted pointer. A spinner covers the hero while the upload is in flight.
 */
@Composable
private fun ConcordIconHero(
    robotSeed: String,
    icon: MutableState<ImagePointer?>,
    displayName: String,
    accountViewModel: AccountViewModel,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    val iconModel = rememberConcordImageModel(icon.value, accountViewModel)

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            uploading = true
            scope.launch {
                try {
                    icon.value = ConcordImageUploader(accountViewModel.account).uploadEncrypted(uri, context)
                } catch (e: Exception) {
                    Toast.makeText(context, stringRes(context, R.string.failed_to_upload_media_no_details), Toast.LENGTH_SHORT).show()
                } finally {
                    uploading = false
                }
            }
        }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !uploading) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            RobohashFallbackAsyncImage(
                robot = robotSeed,
                model = iconModel,
                contentDescription = displayName.ifBlank { stringRes(R.string.concord_create_title) },
                modifier = Modifier.size(104.dp).clip(CircleShape),
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                autoPlayGif = autoPlayGif,
            )
            if (uploading) CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        Text(
            text = stringRes(R.string.concord_create_icon_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !uploading) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * A wide community-banner hero (a 3:1 header image): shows the current decrypted banner, and on tap
 * opens the photo picker → AES-256-GCM-encrypts + uploads the image and updates [banner] to the
 * resulting CORD-02 §6 encrypted pointer. Tapping when a banner is set replaces it; a small remove
 * button clears it. A spinner covers the hero while the upload is in flight.
 */
@Composable
private fun ConcordBannerHero(
    banner: MutableState<ImagePointer?>,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    val bannerModel = rememberConcordImageModel(banner.value, accountViewModel)

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            uploading = true
            scope.launch {
                try {
                    banner.value = ConcordImageUploader(accountViewModel.account).uploadEncrypted(uri, context)
                } catch (e: Exception) {
                    Toast.makeText(context, stringRes(context, R.string.failed_to_upload_media_no_details), Toast.LENGTH_SHORT).show()
                } finally {
                    uploading = false
                }
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !uploading) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        contentAlignment = Alignment.Center,
    ) {
        if (bannerModel != null) {
            AsyncImage(
                model = bannerModel,
                contentDescription = stringRes(R.string.concord_edit_banner_hint),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f),
            )
        }
        if (uploading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        } else if (bannerModel == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolIcon(
                    symbol = MaterialSymbols.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringRes(R.string.concord_edit_banner_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (bannerModel != null && !uploading) {
            IconButton(
                onClick = { banner.value = null },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                SymbolIcon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(R.string.remove),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
