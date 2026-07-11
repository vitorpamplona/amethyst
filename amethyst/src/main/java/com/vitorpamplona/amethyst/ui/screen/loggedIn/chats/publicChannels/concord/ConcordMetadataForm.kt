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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * The shared metadata form for creating and editing a Concord community — a large
 * circular icon preview at the top that reflects the icon URL live (tap it to jump
 * to the URL field), then the name, description, and icon-URL fields. Mirrors the
 * NIP-29 `GroupImagePicker` hero + `GroupMetadataFields` layout so the two features
 * feel consistent. Callers own the state and add the surrounding scaffold, relays
 * section (create only), and the create/save action.
 */
@Composable
fun ConcordMetadataFields(
    name: MutableState<String>,
    about: MutableState<String>,
    iconUrl: MutableState<String>,
    robotSeed: String,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val iconFocus = remember { FocusRequester() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConcordIconHero(
            robotSeed = robotSeed,
            iconUrl = iconUrl.value,
            displayName = name.value,
            accountViewModel = accountViewModel,
            onClick = { iconFocus.requestFocus() },
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
        OutlinedTextField(
            value = iconUrl.value,
            onValueChange = { iconUrl.value = it },
            modifier = Modifier.fillMaxWidth().focusRequester(iconFocus),
            singleLine = true,
            label = { Text(stringRes(R.string.concord_create_icon)) },
            placeholder = { Text("https://…/icon.png") },
        )
    }
}

/** The circular community-icon hero: shows the icon URL live over a stable robohash placeholder. */
@Composable
private fun ConcordIconHero(
    robotSeed: String,
    iconUrl: String,
    displayName: String,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val autoPlayGif by accountViewModel.settings.autoPlayVideosFlow.collectAsStateWithLifecycle()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            RobohashFallbackAsyncImage(
                robot = robotSeed,
                model = iconUrl.ifBlank { null },
                contentDescription = displayName.ifBlank { stringRes(R.string.concord_create_title) },
                modifier = Modifier.size(104.dp).clip(CircleShape),
                loadProfilePicture = accountViewModel.settings.showProfilePictures(),
                loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
                autoPlayGif = autoPlayGif,
            )
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
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
