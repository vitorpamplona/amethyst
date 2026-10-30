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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wotNudge

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_activate
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_activated_body
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_activated_status
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_activated_title
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_dismiss
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_first_action
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_first_body
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_first_title
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_friends_action
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_friends_body
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_friends_copy
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_friends_title_many
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_friends_title_one
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_friends_title_two
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_got_it
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_not_now
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_provider
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_ready_action
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_ready_body
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_ready_title
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_rings_body
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_rings_they_trust
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_rings_title
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_rings_you_follow
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_strip_messages
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_strip_notifications
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_strip_thread
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_strip_thread_action
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_thread_body
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_thread_label
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_thread_others
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_thread_title
import com.vitorpamplona.amethyst.commons.resources.wot_nudge_turn_on
import com.vitorpamplona.amethyst.commons.resources.wot_private_entry_explainer
import com.vitorpamplona.amethyst.commons.resources.wot_private_entry_title
import com.vitorpamplona.amethyst.commons.resources.wot_retry
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_body
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_continue
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_continue_with
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_copy_body
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_copy_title
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_key_body
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_key_title
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_note
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_suggested
import com.vitorpamplona.amethyst.commons.resources.wot_sheet_title
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuItemModifier
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.showCount
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Feed cards that invite the user to turn on web of trust. The pitch is "your people first",
 * never "less spam": every card says what moves up, not what goes away.
 *
 * They are stateless. The caller decides when one shows (no kind 10040 yet, enough follows),
 * draws the faces with the app's avatar and opens the setup sheet ([CircleSetupSheet]).
 */

/** Draws one person's avatar at the size it gets; the card clips it to its shape. */
typealias NudgeFace = @Composable (Dp) -> Unit

/** A reply in [ThreadPreviewCard]'s mock thread. */
class NudgePreviewReply(
    val face: NudgeFace,
    val name: String,
    /** "You follow", "Followed by Alice": why this person is in the circle. */
    val relation: String,
    val text: String,
)

/** Variant 1: the follows' faces and the promise. Needs nothing but the follow list. */
@Composable
fun CircleFirstCard(
    faces: List<NudgeFace>,
    followCount: Int,
    onSetUp: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NudgeSurface(modifier, onDismiss) {
        FacePile(faces.take(5), extra = followCount - faces.take(5).size, size = 40.dp)
        Spacer(Modifier.height(14.dp))
        NudgeTitle(stringRes(Res.string.wot_nudge_first_title))
        NudgeBody(stringRes(Res.string.wot_nudge_first_body))
        NudgeButtons(stringRes(Res.string.wot_nudge_first_action), onSetUp, stringRes(Res.string.wot_nudge_not_now), onNotNow)
    }
}

/** Variant 2: a small thread as it looks with web of trust on. */
@Composable
fun ThreadPreviewCard(
    friends: List<NudgePreviewReply>,
    otherFaces: List<NudgeFace>,
    otherCount: Int,
    onSetUp: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NudgeSurface(modifier, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(MaterialSymbols.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                stringRes(Res.string.wot_nudge_thread_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                friends.forEach { reply ->
                    PreviewReplyRow(reply)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FacePile(otherFaces.take(3), extra = 0, size = 22.dp, dimmed = true)
                    Text(
                        pluralStringRes(Res.plurals.wot_nudge_thread_others, otherCount, otherCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        NudgeTitle(stringRes(Res.string.wot_nudge_thread_title))
        NudgeBody(stringRes(Res.string.wot_nudge_thread_body))
        NudgeButtons(stringRes(Res.string.wot_nudge_first_action), onSetUp, stringRes(Res.string.wot_nudge_not_now), onNotNow)
    }
}

@Composable
private fun PreviewReplyRow(reply: NudgePreviewReply) {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape)) { reply.face(32.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(reply.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RelationChip(reply.relation)
            }
            Text(reply.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RelationChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * Variant 3: follows who already published a kind 10040. Social proof, and a way in that is
 * not Brainstorm: start from one of their setups.
 */
@Composable
fun FriendsUseItCard(
    faces: List<NudgeFace>,
    names: List<String>,
    onSetUp: () -> Unit,
    onUseTheirs: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (names.isEmpty()) return
    val title =
        when (names.size) {
            1 -> stringRes(Res.string.wot_nudge_friends_title_one, names[0])
            2 -> stringRes(Res.string.wot_nudge_friends_title_two, names[0], names[1])
            else -> pluralStringRes(Res.plurals.wot_nudge_friends_title_many, names.size - 2, names.size - 2, names[0], names[1])
        }
    NudgeSurface(modifier, onDismiss) {
        FacePile(faces.take(5), extra = names.size - faces.take(5).size, size = 40.dp)
        Spacer(Modifier.height(14.dp))
        NudgeTitle(title)
        NudgeBody(stringRes(Res.string.wot_nudge_friends_body))
        NudgeButtons(stringRes(Res.string.wot_nudge_friends_action), onSetUp, stringRes(Res.string.wot_nudge_friends_copy, names[0]), onUseTheirs)
    }
}

/** A trust provider the rings card offers, as the user sees it. */
class CircleProvider(
    val id: String,
    val name: String,
    /** The provider's picture, filling the modifier it gets. */
    val picture: NudgeFace,
)

/** Where [CircleRingsCard] is: offering, signing up, failed, or done and computing. */
sealed interface CircleActivation {
    data object Offer : CircleActivation

    /** The sign-up is running; [step] says which part ("Signing in…"). */
    data class Working(
        val step: String,
    ) : CircleActivation

    data class Failed(
        val message: String,
    ) : CircleActivation

    /** Signed up. The provider computes the scores in the background. */
    data object Activated : CircleActivation
}

/**
 * Variant 4: you, your follows, the people they trust, as rings. Pick a provider and activate;
 * the card then turns into the acknowledgement that the circle is being computed.
 */
@Composable
fun CircleRingsCard(
    self: NudgeFace,
    faces: List<NudgeFace>,
    followCount: Int,
    extendedCount: Int,
    providers: List<CircleProvider>,
    selected: CircleProvider,
    onSelect: (CircleProvider) -> Unit,
    activation: CircleActivation,
    onActivate: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NudgeSurface(modifier, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CircleRings(self, faces.take(6), Modifier.size(132.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RingCount(showCount(followCount), stringRes(Res.string.wot_nudge_rings_you_follow), strong = true)
                // Zero until the follows' own lists load: better no number than a wrong one.
                if (extendedCount > 0) {
                    RingCount("~" + showCount(extendedCount), stringRes(Res.string.wot_nudge_rings_they_trust), strong = false)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (activation == CircleActivation.Activated) {
            NudgeTitle(stringRes(Res.string.wot_nudge_activated_title))
            NudgeBody(stringRes(Res.string.wot_nudge_activated_body, selected.name))
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringRes(Res.string.wot_nudge_activated_status), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringRes(Res.string.wot_nudge_got_it)) }
            }
        } else {
            val working = activation is CircleActivation.Working
            NudgeTitle(stringRes(Res.string.wot_nudge_rings_title))
            NudgeBody(stringRes(Res.string.wot_nudge_rings_body))
            Spacer(Modifier.height(14.dp))
            CircleProviderPicker(providers, selected, onSelect, enabled = !working)
            when (activation) {
                is CircleActivation.Working -> NudgeStatus(activation.step, MaterialTheme.colorScheme.onSurfaceVariant)
                is CircleActivation.Failed -> NudgeStatus(activation.message, MaterialTheme.colorScheme.error)
                else -> {}
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onNotNow, enabled = !working) { Text(stringRes(Res.string.wot_nudge_not_now)) }
                Button(onClick = onActivate, enabled = !working) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringRes(if (activation is CircleActivation.Failed) Res.string.wot_retry else Res.string.wot_nudge_activate))
                }
            }
        }
    }
}

@Composable
private fun NudgeStatus(
    text: String,
    color: Color,
) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
}

/** The provider, as a row that opens the app's rounded popup to pick another. */
@Composable
internal fun CircleProviderPicker(
    providers: List<CircleProvider>,
    selected: CircleProvider,
    onSelect: (CircleProvider) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProviderPicture(selected, 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringRes(Res.string.wot_nudge_provider), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Icon(MaterialSymbols.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RoundedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            CircleProviderMenu(providers, selected) {
                onSelect(it)
                expanded = false
            }
        }
    }
}

/** The popup's content: one section, a row per provider. */
@Composable
internal fun CircleProviderMenu(
    providers: List<CircleProvider>,
    selected: CircleProvider,
    onSelect: (CircleProvider) -> Unit,
) {
    RoundedMenuSection {
        providers.forEach { provider ->
            DropdownMenuItem(
                modifier = RoundedMenuItemModifier,
                leadingIcon = { ProviderPicture(provider, 32.dp) },
                text = { Text(provider.name, style = MaterialTheme.typography.bodyLarge) },
                trailingIcon =
                    if (provider.id == selected.id) {
                        { Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                onClick = { onSelect(provider) },
            )
        }
    }
}

@Composable
private fun ProviderPicture(
    provider: CircleProvider,
    size: Dp,
) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4))) { provider.picture(size) }
}

@Composable
private fun RingCount(
    count: String,
    label: String,
    strong: Boolean,
) {
    Column {
        Text(
            count,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CircleRings(
    self: NudgeFace,
    faces: List<NudgeFace>,
    modifier: Modifier,
) {
    val inner = MaterialTheme.colorScheme.primary
    val outer = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val outerR = size.minDimension / 2 - 4.dp.toPx()
            val innerR = size.minDimension * 0.29f
            drawCircle(outer, outerR, c, style = Stroke(1.dp.toPx()))
            drawCircle(inner.copy(alpha = 0.5f), innerR, c, style = Stroke(1.5.dp.toPx()))
            // The people they trust: many, small, anonymous.
            repeat(18) { i ->
                val a = 2 * PI * i / 18 + 0.17
                drawCircle(dot, 2.5.dp.toPx(), Offset(c.x + outerR * cos(a).toFloat(), c.y + outerR * sin(a).toFloat()))
            }
        }
        // Your follows, on the inner ring.
        val r = 132.dp * 0.29f
        faces.forEachIndexed { i, face ->
            val a = 2 * PI * i / faces.size - PI / 2
            Box(
                Modifier
                    .offset(x = r * cos(a).toFloat(), y = r * sin(a).toFloat())
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            ) { face(22.dp) }
        }
        Box(
            Modifier.size(34.dp).clip(CircleShape).border(2.dp, inner, CircleShape),
        ) { self(34.dp) }
    }
}

/** Shown in place of a variant once the provider is set: the payoff, and where the settings are. */
@Composable
fun CircleReadyCard(
    faces: List<NudgeFace>,
    circleSize: Int,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NudgeSurface(modifier, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, filled = true)
            }
            FacePile(faces.take(5), extra = 0, size = 28.dp)
        }
        Spacer(Modifier.height(14.dp))
        NudgeTitle(stringRes(Res.string.wot_nudge_ready_title))
        NudgeBody(pluralStringRes(Res.plurals.wot_nudge_ready_body, circleSize, showCount(circleSize)))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenSettings) { Text(stringRes(Res.string.wot_nudge_ready_action)) }
        }
    }
}

/** Where a [CircleFirstStrip] shows, which decides what it promises. */
enum class CircleStripPlace { THREAD, NOTIFICATIONS, MESSAGES }

/**
 * Variant 5: a one-line strip at the top of the screen where the benefit shows: a long thread,
 * a busy notifications tab, the messages list.
 */
@Composable
fun CircleFirstStrip(
    place: CircleStripPlace,
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    replyCount: Int = 0,
) {
    val (symbol: MaterialSymbol, text) =
        when (place) {
            CircleStripPlace.THREAD -> MaterialSymbols.Forum to pluralStringRes(Res.plurals.wot_nudge_strip_thread, replyCount, replyCount)
            CircleStripPlace.NOTIFICATIONS -> MaterialSymbols.Notifications to stringRes(Res.string.wot_nudge_strip_notifications)
            CircleStripPlace.MESSAGES -> MaterialSymbols.Mail to stringRes(Res.string.wot_nudge_strip_messages)
        }
    val action = if (place == CircleStripPlace.THREAD) stringRes(Res.string.wot_nudge_strip_thread_action) else stringRes(Res.string.wot_nudge_turn_on)
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(symbol, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = onTurnOn) { Text(action, fontWeight = FontWeight.Bold) }
            IconButton(onClick = onDismiss) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.wot_nudge_dismiss), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The ways in the setup sheet offers. Brainstorm is the suggestion, not the only one. */
enum class CircleSetupOption { GUIDED, COPY, KEY }

/**
 * What the cards open: pick who maps the circle. Content only; the caller hosts it in a
 * ModalBottomSheet. The guided row names whichever provider [KnownTrustProviders] lists first.
 */
@Composable
fun CircleSetupSheet(
    providerName: String,
    selected: CircleSetupOption,
    onSelect: (CircleSetupOption) -> Unit,
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text(stringRes(Res.string.wot_sheet_title), fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringRes(Res.string.wot_sheet_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                SheetOption(
                    providerName,
                    null,
                    badge = stringRes(Res.string.wot_sheet_suggested),
                    selected = selected == CircleSetupOption.GUIDED,
                ) { onSelect(CircleSetupOption.GUIDED) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SheetOption(
                    stringRes(Res.string.wot_sheet_copy_title),
                    stringRes(Res.string.wot_sheet_copy_body),
                    badge = null,
                    selected = selected == CircleSetupOption.COPY,
                ) { onSelect(CircleSetupOption.COPY) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SheetOption(
                    stringRes(Res.string.wot_sheet_key_title),
                    stringRes(Res.string.wot_sheet_key_body),
                    badge = null,
                    selected = selected == CircleSetupOption.KEY,
                ) { onSelect(CircleSetupOption.KEY) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringRes(Res.string.wot_private_entry_title), style = MaterialTheme.typography.bodyMedium)
                Text(stringRes(Res.string.wot_private_entry_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = isPrivate, onCheckedChange = onPrivateChange)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(MaterialSymbols.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(16.dp))
            Text(stringRes(Res.string.wot_sheet_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val cta =
            if (selected == CircleSetupOption.GUIDED) {
                stringRes(Res.string.wot_sheet_continue_with, providerName)
            } else {
                stringRes(Res.string.wot_sheet_continue)
            }
        NudgeButtons(cta, onContinue, stringRes(Res.string.wot_nudge_not_now), onCancel)
    }
}

@Composable
private fun SheetOption(
    title: String,
    body: String?,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (badge != null) RelationChip(badge)
            }
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NudgeSurface(
    modifier: Modifier,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) { content() }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.wot_nudge_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun NudgeTitle(text: String) {
    Text(text, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun NudgeBody(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NudgeButtons(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSecondary) { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Button(onClick = onPrimary) { Text(primary, maxLines = 1) }
    }
}

/** Overlapping avatars, then "+N" for the rest. */
@Composable
private fun FacePile(
    faces: List<NudgeFace>,
    extra: Int,
    size: Dp,
    dimmed: Boolean = false,
) {
    val ring = if (dimmed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh
    Layout(
        content = {
            faces.forEach { face ->
                Box(Modifier.size(size).clip(CircleShape).border(2.dp, ring, CircleShape)) {
                    face(size)
                    if (dimmed) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)))
                }
            }
            if (extra > 0) {
                Box(
                    Modifier
                        .height(size)
                        .clip(RoundedCornerShape(size / 2))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(2.dp, ring, RoundedCornerShape(size / 2))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+" + showCount(extra), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        // Each one tucks 30% of the avatar size under the one before it.
        val step = size.roundToPx() - (size * 0.3f).roundToPx()
        val width = placeables.dropLast(1).size * step + (placeables.lastOrNull()?.width ?: 0)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.place(i * step, (height - p.height) / 2) }
        }
    }
}
