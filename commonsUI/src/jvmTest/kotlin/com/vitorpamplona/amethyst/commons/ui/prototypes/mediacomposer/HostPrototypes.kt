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
package com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
 * PROTOTYPE ONLY. Two more hosts of the same MediaStudio, to show it is one component with a
 * config per screen, not a composer feature:
 *
 * - Chat: the attach button opens the Studio with a caption field and Send (StudioConfig.chat).
 *   Whatever was typed in the input moves into the caption. Send closes the Studio and the bubble
 *   uploads, then sends.
 * - Profile: tapping the avatar or the banner opens the Studio locked to 1:1 with a circle mask, or
 *   to 3:1 (StudioConfig.avatar / banner). Save uploads right away, as profile pictures do today,
 *   but cropped to what the profile will actually show.
 */

// ---------------------------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------------------------

class ChatBubble(
    val mine: Boolean,
    val text: String,
    val time: String,
    val media: List<MediaDraft> = emptyList(),
)

@Stable
class ChatPrototype(
    private val scope: CoroutineScope,
) {
    val messages = mutableStateListOf<ChatBubble>()
    val input = TextFieldState()
    val caption = TextFieldState()
    var picking by mutableStateOf<SnapshotStateList<MediaDraft>?>(null)
    val config = StudioConfig.chat(caption)

    fun pick(drafts: List<MediaDraft>) {
        config.prepare(drafts)
        picking = mutableStateListOf<MediaDraft>().apply { addAll(drafts) }
        caption.setTextAndPlaceCursorAtEnd(input.text.toString())
        input.clearText()
    }

    fun cancelPick() {
        input.setTextAndPlaceCursorAtEnd(caption.text.toString())
        caption.clearText()
        picking = null
    }

    fun send() {
        val media = picking?.toList() ?: emptyList()
        val text = (if (picking != null) caption.text else input.text).toString()
        if (media.isEmpty() && text.isBlank()) return
        messages += ChatBubble(mine = true, text = text, time = "now", media = media)
        picking = null
        caption.clearText()
        input.clearText()
        media.forEachIndexed { i, d -> scope.launch { simulateUpload(d, i, PrototypeComposer.SERVERS[0]) } }
    }
}

fun sampleChat(scope: CoroutineScope) =
    ChatPrototype(scope).apply {
        messages += ChatBubble(false, "Did you make it to the coast?", "18:02")
        messages += ChatBubble(true, "Yes! Just in time for sunset", "18:40")
        messages += ChatBubble(false, "Pics or it didn't happen 😄", "18:41")
    }

@Composable
fun ChatPrototypeHost() {
    val scope = rememberCoroutineScope()
    val chat = remember { sampleChat(scope) }
    ChatPrototypeScreen(chat)
}

@Composable
fun ChatPrototypeScreen(
    chat: ChatPrototype,
    studioTool: StudioTool? = null,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = "Back") }
                AvatarStandIn(32.dp)
                Spacer(Modifier.width(10.dp))
                Text("Alice", fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            ) {
                chat.messages.forEach { ChatBubbleView(it) }
            }
            ChatInputRow(chat)
        }
        chat.picking?.takeIf { it.isNotEmpty() }?.let { picking ->
            MediaStudio(
                drafts = picking,
                initial = picking.first(),
                config = chat.config,
                onConfirm = chat::send,
                onCancel = chat::cancelPick,
                onRemove = { d ->
                    picking.remove(d)
                    if (picking.isEmpty()) chat.cancelPick()
                },
                initialTool = studioTool,
            )
        }
    }
}

@Composable
private fun ChatBubbleView(bubble: ChatBubble) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (bubble.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (bubble.mine) colors.primaryContainer else colors.surfaceVariant)
                .padding(4.dp),
        ) {
            if (bubble.media.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    bubble.media.forEach { d ->
                        Box(
                            Modifier
                                .width(if (bubble.media.size == 1) 272.dp else 134.dp)
                                .aspectRatio(if (bubble.media.size == 1) d.aspectRatio.coerceIn(0.8f, 1.8f) else 1f)
                                .clip(RoundedCornerShape(12.dp)),
                        ) {
                            DraftImage(d, Modifier.fillMaxSize())
                            StatusOverlay(d.status, compact = true, onRetry = {}, modifier = Modifier.matchParentSize())
                        }
                    }
                }
            }
            if (bubble.text.isNotBlank()) {
                Text(bubble.text, color = if (bubble.mine) colors.onPrimaryContainer else colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            val sending = bubble.media.any { it.status !is DraftStatus.Uploaded }
            Text(
                if (sending) "Sending…" else bubble.time,
                fontSize = 11.sp,
                color = (if (bubble.mine) colors.onPrimaryContainer else colors.onSurfaceVariant).copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ChatInputRow(chat: ChatPrototype) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { chat.pick(fakePick(1)) }) {
            Icon(MaterialSymbols.AddPhotoAlternate, contentDescription = "Attach", tint = MaterialTheme.colorScheme.primary)
        }
        BasicTextField(
            state = chat.input,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorator = { inner ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (chat.input.text.isEmpty()) Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            },
        )
        IconButton(onClick = chat::send) {
            Icon(MaterialSymbols.AutoMirrored.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Profile: avatar and banner
// ---------------------------------------------------------------------------------------------

enum class ProfileImage { AVATAR, BANNER }

@Stable
class ProfilePrototype(
    private val scope: CoroutineScope,
) {
    var avatar by mutableStateOf<MediaDraft?>(null)
    var banner by mutableStateOf<MediaDraft?>(null)
    var picking by mutableStateOf<MediaDraft?>(null)
    var target by mutableStateOf(ProfileImage.AVATAR)

    fun config() = if (target == ProfileImage.AVATAR) StudioConfig.avatar() else StudioConfig.banner()

    fun pick(
        which: ProfileImage,
        draft: MediaDraft,
    ) {
        target = which
        config().prepare(listOf(draft))
        picking = draft
    }

    fun save() {
        val d = picking ?: return
        if (target == ProfileImage.AVATAR) avatar = d else banner = d
        picking = null
        scope.launch { simulateUpload(d, 0, PrototypeComposer.SERVERS[0]) }
    }
}

@Composable
fun ProfilePrototypeHost() {
    val scope = rememberCoroutineScope()
    val profile = remember { ProfilePrototype(scope) }
    ProfilePrototypeScreen(profile, onPickAvatar = { fakePick(1).first() }, onPickBanner = { MediaDraft(900 + (0..99).random(), DraftKind.IMAGE, sunsetPhoto(seed = 4), 4032, 3024, 3_500_000) })
}

@Composable
fun ProfilePrototypeScreen(
    profile: ProfilePrototype,
    onPickAvatar: () -> MediaDraft = { fakePick(1).first() },
    onPickBanner: () -> MediaDraft = { fakePick(1).first() },
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(MaterialSymbols.Close, contentDescription = "Close") }
                Text("Edit profile", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)))
                        .clickable { profile.pick(ProfileImage.BANNER, onPickBanner()) },
                ) {
                    profile.banner?.let { b ->
                        DraftImage(b, Modifier.fillMaxSize())
                        StatusOverlay(b.status, compact = true, onRetry = {}, modifier = Modifier.matchParentSize())
                    }
                    val b = profile.banner
                    if (b == null || b.status is DraftStatus.Uploaded) CameraBadge(Modifier.align(Alignment.Center))
                }
                Box(
                    Modifier
                        .padding(start = 16.dp)
                        .offset(y = 80.dp)
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(4.dp, colors.background, CircleShape)
                        .clickable { profile.pick(ProfileImage.AVATAR, onPickAvatar()) },
                ) {
                    val a = profile.avatar
                    if (a != null) {
                        DraftImage(a, Modifier.fillMaxSize())
                        StatusOverlay(a.status, compact = true, onRetry = {}, modifier = Modifier.matchParentSize())
                    } else {
                        AvatarStandIn(96.dp)
                    }
                    if (a == null || a.status is DraftStatus.Uploaded) CameraBadge(Modifier.align(Alignment.Center))
                }
            }
            Spacer(Modifier.height(64.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = "Alex", onValueChange = {}, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = "Building Amethyst", onValueChange = {}, label = { Text("About") }, modifier = Modifier.fillMaxWidth())
            }
        }
        profile.picking?.let { d ->
            MediaStudio(
                drafts = listOf(d),
                initial = d,
                config = profile.config(),
                onConfirm = profile::save,
                onCancel = { profile.picking = null },
            )
        }
    }
}

@Composable
private fun CameraBadge(modifier: Modifier) {
    Icon(
        MaterialSymbols.CameraAlt,
        contentDescription = "Change",
        tint = Color.White,
        modifier =
            modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(8.dp),
    )
}
