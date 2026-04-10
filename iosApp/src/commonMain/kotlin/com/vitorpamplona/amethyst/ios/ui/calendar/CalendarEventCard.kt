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
package com.vitorpamplona.amethyst.ios.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar

/**
 * Card composable that renders a calendar event (NIP-52, kind 31922/31923).
 */
@Composable
fun CalendarEventCard(
    event: CalendarEventDisplayData,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAuthorClick: ((String) -> Unit)? = null,
    onRsvp: ((String) -> Unit)? = null,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Image banner
            if (event.image != null) {
                AsyncImage(
                    model = event.image,
                    contentDescription = event.title,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Event type badge + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (event.isDateBased) "ALL DAY" else "EVENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Date/time row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =
                        if (event.isDateBased) {
                            Icons.Default.CalendarMonth
                        } else {
                            Icons.Default.Schedule
                        },
                    contentDescription = "Time",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatEventTime(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Location
            if (event.location != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Summary / description
            val summaryText = event.summary ?: event.description
            if (summaryText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Author + RSVP row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Author
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        if (onAuthorClick != null) {
                            Modifier.clickable { onAuthorClick(event.pubKeyHex) }
                        } else {
                            Modifier
                        },
                ) {
                    UserAvatar(
                        userHex = event.pubKeyHex,
                        pictureUrl = event.profilePictureUrl,
                        size = 24.dp,
                        contentDescription = "Organizer",
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = event.pubKeyDisplay.take(15) + if (event.pubKeyDisplay.length > 15) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Participant/RSVP count
                    val attendees = event.participantCount + event.rsvpCount
                    if (attendees > 0) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Attendees",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$attendees",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    // RSVP button
                    if (onRsvp != null) {
                        Button(
                            onClick = { onRsvp(event.id) },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(
                                text = "RSVP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats the event time for display.
 */
private fun formatEventTime(event: CalendarEventDisplayData): String {
    if (event.isDateBased) {
        val start = event.startDate ?: return "Date TBD"
        val end = event.endDate
        return if (end != null && end != start) "$start → $end" else start
    }

    val start = event.startTimestamp ?: return "Time TBD"
    val startStr = formatTimestamp(start, event.startTzId)
    val end = event.endTimestamp
    return if (end != null) "$startStr → ${formatTimestamp(end, event.endTzId)}" else startStr
}

private val MONTH_NAMES = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * Simple epoch-seconds to human-readable date/time formatter (UTC).
 * Does not handle timezone conversion for simplicity.
 */
private fun formatTimestamp(
    epochSeconds: Long,
    @Suppress("UNUSED_PARAMETER") tzId: String? = null,
): String {
    // Simple UTC breakdown without platform dependencies
    val totalDays = epochSeconds / 86400
    val timeOfDay = epochSeconds % 86400
    val hour = ((timeOfDay / 3600) % 24).toInt()
    val minute = ((timeOfDay % 3600) / 60).toInt()

    // Approximate month/day from days since epoch (1970-01-01)
    var y = 1970
    var remaining = totalDays
    while (true) {
        val daysInYear = if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 366L else 365L
        if (remaining < daysInYear) break
        remaining -= daysInYear
        y++
    }
    val monthDays =
        if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) {
            intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        } else {
            intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        }
    var m = 0
    while (m < 12 && remaining >= monthDays[m]) {
        remaining -= monthDays[m]
        m++
    }
    val day = remaining.toInt() + 1
    val monthName = MONTH_NAMES[m.coerceIn(0, 11)]
    val hh = hour.toString().padStart(2, '0')
    val mm = minute.toString().padStart(2, '0')
    return "$monthName $day, $hh:$mm"
}
