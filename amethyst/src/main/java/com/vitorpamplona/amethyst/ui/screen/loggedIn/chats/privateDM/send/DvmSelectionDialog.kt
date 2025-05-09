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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex

/**
 * Data class representing information about a Distributed Verification Machine (DVM)
 * for use in the UI, particularly for text generation capabilities.
 *
 * This class is populated from NIP90TextGenDVMFeedFilter results.
 */
data class DvmInfo(
    val pubkey: String,
    val name: String?,
    val supportedKinds: Set<Int> = emptySet(),
    val description: String? = null,
    val picture: String? = null,
)

@Composable
fun DvmSelectionDialog(
    dvmList: List<DvmInfo>,
    onDismissRequest: () -> Unit,
    onDvmSelected: (String) -> Unit,
) {
    // Just log the number of DVMs found
    Log.d("DVM_DEBUG", "Found ${dvmList.size} DVMs to display in selection dialog")

    if (dvmList.isEmpty()) {
        // Show loading dialog
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(id = R.string.select_dvm)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Simple CircularProgressIndicator as a replacement for LoadingAnimation
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(id = R.string.loading_dvms),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                // Empty confirm button - required param
            },
            dismissButton = {
                Text(
                    stringResource(id = R.string.cancel),
                    modifier =
                        Modifier
                            .clickable { onDismissRequest() }
                            .padding(10.dp),
                )
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        return
    }

    // Sort DVMs alphabetically by name (with nulls last)
    val sortedDvmList =
        dvmList.sortedWith(
            compareBy<DvmInfo> { it.name == null }
                .thenBy { it.name?.lowercase() ?: "" },
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(id = R.string.select_dvm)) },
        text = {
            LazyColumn {
                items(sortedDvmList) { dvmInfo ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDvmSelected(dvmInfo.pubkey)
                                    onDismissRequest()
                                }.padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // DVM Profile Image or Fallback
                        if (dvmInfo.picture != null) {
                            // Use SubcomposeAsyncImage for proper error handling
                            Box(
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                SubcomposeAsyncImage(
                                    model = dvmInfo.picture,
                                    contentDescription = dvmInfo.name ?: "DVM",
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                ) {
                                    val state by painter.state.collectAsState()
                                    when (state) {
                                        is AsyncImagePainter.State.Loading -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                        is AsyncImagePainter.State.Error -> {
                                            DvmInitialsFallback(dvmInfo.name)
                                        }
                                        is AsyncImagePainter.State.Success -> {
                                            SubcomposeAsyncImageContent()
                                        }
                                        else -> {
                                            DvmInitialsFallback(dvmInfo.name)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Use fallback with initial or 'D' letter
                            Box(
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                DvmInitialsFallback(dvmInfo.name)
                            }
                        }

                        // DVM Info
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            // DVM Name
                            Text(
                                text = dvmInfo.name ?: Hex.decode(dvmInfo.pubkey).toNpub(),
                                fontWeight = FontWeight.Bold,
                            )

                            // DVM Description if available
                            dvmInfo.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Supported kinds
                            val displayKinds =
                                dvmInfo.supportedKinds
                                    .filter { it in 5000..7000 }
                                    .joinToString(", ") { "kind:$it" }

                            if (displayKinds.isNotEmpty()) {
                                Text(
                                    text = "Text Generation DVM ($displayKinds)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Empty confirm button - required param
        },
        dismissButton = {
            Text(stringResource(id = R.string.cancel), modifier = Modifier.clickable { onDismissRequest() }.padding(10.dp))
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.9f),
    )
}

@Composable
private fun DvmInitialsFallback(name: String?) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (name?.firstOrNull() ?: "AI").toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
} 
