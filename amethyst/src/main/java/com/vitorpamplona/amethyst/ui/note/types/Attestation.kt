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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.AttestationStatus
import com.vitorpamplona.quartz.experimental.attestations.attestation.tags.Validity
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RenderAttestation(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AttestationEvent ?: return

    val validity = remember(noteEvent) { noteEvent.validity() }
    val status = remember(noteEvent) { noteEvent.status() }
    val validFrom = remember(noteEvent) { noteEvent.validFrom() }
    val validTo = remember(noteEvent) { noteEvent.validTo() }
    val content = remember(noteEvent) { noteEvent.content.ifBlank { null } }

    val statusColor = remember(status, validity) { attestationColor(status, validity) }
    val statusIcon = remember(status, validity) { attestationIcon(status, validity) }
    val statusLabel = attestationStatusLabel(status, validity)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .background(statusColor.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = stringRes(R.string.attestation),
                tint = statusColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor,
            )
        }

        if (validFrom != null || validTo != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                validFrom?.let {
                    Text(
                        text = stringRes(R.string.attestation_valid_from, formatTimestamp(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                validTo?.let {
                    Text(
                        text = stringRes(R.string.attestation_valid_to, formatTimestamp(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        content?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (quotesLeft > 0) {
        note.replyTo?.firstOrNull()?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringRes(R.string.attestation_attests_to),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NoteCompose(
                baseNote = it,
                modifier = MaterialTheme.colorScheme.replyModifier,
                isQuotedNote = true,
                unPackReply = ReplyRenderType.NONE,
                makeItShort = true,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun RenderAttestationRequest(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AttestationRequestEvent ?: return
    val content = remember(noteEvent) { noteEvent.content.ifBlank { null } }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                ).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = stringRes(R.string.attestation_request),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringRes(R.string.attestation_request),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        content?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (quotesLeft > 0) {
        note.replyTo?.firstOrNull()?.let {
            Spacer(modifier = Modifier.height(8.dp))
            NoteCompose(
                baseNote = it,
                modifier = MaterialTheme.colorScheme.replyModifier,
                isQuotedNote = true,
                unPackReply = ReplyRenderType.NONE,
                makeItShort = true,
                quotesLeft = quotesLeft - 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderAttestorRecommendation(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AttestorRecommendationEvent ?: return
    val kinds = remember(noteEvent) { noteEvent.kinds() }
    val description = remember(noteEvent) { noteEvent.description() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                ).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Recommend,
                contentDescription = stringRes(R.string.attestor_recommendation),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringRes(R.string.attestor_recommendation),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (kinds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    stringRes(
                        R.string.attestor_recommendation_for_kinds,
                        kinds.joinToString(", "),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        description?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderAttestorProficiency(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? AttestorProficiencyEvent ?: return
    val kinds = remember(noteEvent) { noteEvent.kinds() }
    val description = remember(noteEvent) { noteEvent.description() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp),
                ).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f))
                .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringRes(R.string.attestor_proficiency),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringRes(R.string.attestor_proficiency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (kinds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                kinds.forEach { kind ->
                    Text(
                        text = "Kind $kind",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        description?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun attestationColor(
    status: AttestationStatus?,
    validity: Validity?,
): Color =
    when {
        status == AttestationStatus.REVOKED -> Color(0xFFB71C1C)
        status == AttestationStatus.REJECTED -> Color(0xFFB71C1C)
        validity == Validity.INVALID -> Color(0xFFB71C1C)
        status == AttestationStatus.VERIFIED -> Color(0xFF2E7D32)
        validity == Validity.VALID -> Color(0xFF2E7D32)
        status == AttestationStatus.VERIFYING -> Color(0xFFF57F17)
        status == AttestationStatus.ACCEPTED -> Color(0xFF1565C0)
        else -> Color(0xFF757575)
    }

private fun attestationIcon(
    status: AttestationStatus?,
    validity: Validity?,
): ImageVector =
    when {
        status == AttestationStatus.REVOKED -> Icons.Default.Close
        status == AttestationStatus.REJECTED -> Icons.Default.Close
        validity == Validity.INVALID -> Icons.Default.Close
        status == AttestationStatus.VERIFIED -> Icons.Default.VerifiedUser
        validity == Validity.VALID -> Icons.Default.CheckCircle
        status == AttestationStatus.VERIFYING -> Icons.Default.HourglassTop
        status == AttestationStatus.ACCEPTED -> Icons.Default.CheckCircle
        else -> Icons.Default.VerifiedUser
    }

@Composable
private fun attestationStatusLabel(
    status: AttestationStatus?,
    validity: Validity?,
): String =
    when {
        status == AttestationStatus.REVOKED -> stringRes(R.string.attestation_status_revoked)
        status == AttestationStatus.REJECTED -> stringRes(R.string.attestation_status_rejected)
        validity == Validity.INVALID -> stringRes(R.string.attestation_invalid)
        status == AttestationStatus.VERIFIED -> stringRes(R.string.attestation_status_verified)
        validity == Validity.VALID -> stringRes(R.string.attestation_valid)
        status == AttestationStatus.VERIFYING -> stringRes(R.string.attestation_status_verifying)
        status == AttestationStatus.ACCEPTED -> stringRes(R.string.attestation_status_accepted)
        else -> stringRes(R.string.attestation)
    }

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}
