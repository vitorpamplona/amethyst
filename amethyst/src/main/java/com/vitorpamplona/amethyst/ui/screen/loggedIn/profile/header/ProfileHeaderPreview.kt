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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText

// ─── Full Header ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "Full Profile Header")
@Composable
fun ProfileHeaderRedesignPreview() {
    ThemeComparisonColumn {
        ProfileHeaderMockup()
    }
}

// ─── Stats Row ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "Stats Row")
@Composable
fun ProfileStatsRowPreview() {
    ThemeComparisonColumn {
        StatsRowMockup()
    }
}

@Preview(showBackground = true, widthDp = 400, name = "Stats Row - Loading")
@Composable
fun ProfileStatsRowLoadingPreview() {
    ThemeComparisonColumn {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItemMockup(value = "--", label = "Following")
            StatItemMockup(value = "--", label = "Followers")
            StatItemMockup(value = "--", label = "Zaps")
        }
    }
}

// ─── Animated Ring Avatar ──────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 200, name = "Animated Ring Avatar")
@Composable
fun ProfileAvatarRingPreview() {
    ThemeComparisonColumn {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            AvatarWithRingMockup()
        }
    }
}

// ─── Banner with Gradient ──────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "Banner with Gradient")
@Composable
fun BannerWithGradientPreview() {
    ThemeComparisonColumn {
        BannerWithGradientMockup()
    }
}

// ─── Action Buttons ────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "Action Buttons - Other User")
@Composable
fun ProfileActionButtonsOtherUserPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButtonsMockup(isOwnProfile = false, isFollowing = false)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, name = "Action Buttons - Following")
@Composable
fun ProfileActionButtonsFollowingPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButtonsMockup(isOwnProfile = false, isFollowing = true)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, name = "Action Buttons - Own Profile")
@Composable
fun ProfileActionButtonsOwnPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButtonsMockup(isOwnProfile = true, isFollowing = false)
            }
        }
    }
}

// ─── User Info Section ─────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "User Info - Full")
@Composable
fun UserInfoFullPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            UserInfoMockup(
                displayName = "Vitor Pamplona",
                username = "@vitor",
                nip05 = "_@vitorpamplona.com",
                bio = "Building decentralized social networks with Nostr. Creator of Amethyst. Privacy advocate.",
                website = "vitorpamplona.com",
                lnAddress = "vitor@getalby.com",
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400, name = "User Info - Minimal")
@Composable
fun UserInfoMinimalPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            UserInfoMockup(
                displayName = "anon",
                username = null,
                nip05 = null,
                bio = null,
                website = null,
                lnAddress = null,
            )
        }
    }
}

// ─── Collapsible Technical Details ─────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "Technical Details - Collapsed")
@Composable
fun TechnicalDetailsCollapsedPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            TechnicalDetailsMockup(expanded = false)
        }
    }
}

@Preview(showBackground = true, widthDp = 400, name = "Technical Details - Expanded")
@Composable
fun TechnicalDetailsExpandedPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            TechnicalDetailsMockup(expanded = true)
        }
    }
}

// ─── External Identities Row ───────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "External Identities")
@Composable
fun ExternalIdentitiesPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExternalIdentitiesMockup()
        }
    }
}

// ─── NIP-05 Verification ───────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "NIP-05 Verified")
@Composable
fun Nip05VerifiedPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            Nip05MockupRow()
        }
    }
}

// ─── Lightning Address ─────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 400, name = "Lightning Address")
@Composable
fun LightningAddressPreview() {
    ThemeComparisonColumn {
        Surface(color = MaterialTheme.colorScheme.background) {
            LightningAddressMockup()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Mockup implementations
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileHeaderMockup() {
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Surface(color = backgroundColor) {
        Box {
            BannerWithGradientMockup()

            // More options button
            Box(
                modifier =
                    Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                        .size(30.dp)
                        .align(Alignment.TopEnd),
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.placeholderText,
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                )
            }

            // Main content
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvatarWithRingMockup()

                // Action buttons
                Row(
                    modifier = Modifier.padding(top = 8.dp).height(35.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ActionButtonsMockup(isOwnProfile = false, isFollowing = false)
                }

                StatsRowMockup()

                Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                    UserInfoMockup(
                        displayName = "Vitor Pamplona",
                        username = "@vitor",
                        nip05 = "_@vitorpamplona.com",
                        bio =
                            "Building decentralized social networks with Nostr. " +
                                "Creator of Amethyst. Privacy advocate.",
                        website = "vitorpamplona.com",
                        lnAddress = "vitor@getalby.com",
                    )

                    TechnicalDetailsMockup(expanded = false)
                }

                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun BannerWithGradientMockup() {
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            primaryColor.copy(alpha = 0.6f),
                            secondaryColor.copy(alpha = 0.4f),
                        ),
                    ),
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, backgroundColor),
                        ),
                    ),
        )
    }
}

@Composable
private fun StatsRowMockup() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItemMockup(value = "847", label = "Following")
        StatItemMockup(value = "12.3k", label = "Followers")
        StatItemMockup(value = "2.1M", label = "Zaps")
    }
}

@Composable
private fun StatItemMockup(
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.placeholderText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AvatarWithRingMockup() {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val backgroundColor = MaterialTheme.colorScheme.background

    val size = 110.dp

    val infiniteTransition = rememberInfiniteTransition(label = "preview_ring")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "preview_ring_rotation",
    )

    val ringBrush =
        Brush.sweepGradient(
            listOf(
                primaryColor,
                secondaryColor,
                primaryColor.copy(alpha = 0.3f),
                secondaryColor,
                primaryColor,
            ),
        )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size + 8.dp)
                .drawBehind {
                    rotate(rotation) {
                        drawCircle(
                            brush = ringBrush,
                            radius = (size.toPx() + 8.dp.toPx()) / 2f,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .size(size + 4.dp)
                    .clip(CircleShape)
                    .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    primaryColor.copy(alpha = 0.5f),
                                    secondaryColor.copy(alpha = 0.3f),
                                ),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "VP",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsMockup(
    isOwnProfile: Boolean,
    isFollowing: Boolean,
) {
    // Message button
    FilledTonalButton(
        onClick = {},
        modifier = Modifier.padding(horizontal = 3.dp).width(50.dp),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(0.dp),
    ) {
        Icon(Icons.Default.Mail, contentDescription = "Message", modifier = Modifier.size(20.dp))
    }

    if (isOwnProfile) {
        // Edit button
        FilledTonalButton(
            onClick = {},
            modifier = Modifier.padding(horizontal = 3.dp).width(50.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.EditNote, contentDescription = "Edit")
        }
    } else {
        if (isFollowing) {
            // Unfollow button
            FilledTonalButton(
                onClick = {},
                modifier = Modifier.padding(horizontal = 3.dp),
            ) {
                Text("Unfollow")
            }
        } else {
            // Follow button
            FilledTonalButton(
                onClick = {},
                modifier = Modifier.padding(horizontal = 3.dp),
            ) {
                Text("Follow")
            }
        }

        // List button
        TextButton(
            onClick = {},
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(0.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Lists")
        }
    }
}

@Composable
private fun UserInfoMockup(
    displayName: String,
    username: String?,
    nip05: String?,
    bio: String?,
    website: String?,
    lnAddress: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // Display name
        Text(
            text = displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )

        // Username
        if (username != null) {
            Text(
                text = username,
                color = MaterialTheme.colorScheme.placeholderText,
                textAlign = TextAlign.Center,
            )
        }

        // NIP-05
        if (nip05 != null) {
            Nip05MockupRow()
        }

        // Lightning address
        if (lnAddress != null) {
            LightningAddressMockup()
        }

        // Website
        if (website != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.placeholderText,
                    imageVector = Icons.Default.Link,
                    contentDescription = "Website",
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = website,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 5.dp),
                    fontSize = 14.sp,
                )
            }
        }

        // Bio
        if (bio != null) {
            Text(
                text = bio,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 5.dp),
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun TechnicalDetailsMockup(expanded: Boolean) {
    var showDetails by remember { mutableStateOf(expanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Collapsed trigger
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { showDetails = !showDetails }
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "npub1gc...8lxl",
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                fontSize = 12.sp,
            )
            Icon(
                imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Toggle details",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        // Expandable details
        AnimatedVisibility(
            visible = showDetails,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // npub row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "npub1gc...8lxl",
                        color = MaterialTheme.colorScheme.placeholderText,
                        fontSize = 12.sp,
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy npub",
                        modifier = Modifier.padding(start = 5.dp).size(15.dp),
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
                }

                // nprofile row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "nprofi...q8e2p",
                        color = MaterialTheme.colorScheme.placeholderText,
                        fontSize = 12.sp,
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy nprofile",
                        modifier = Modifier.padding(start = 5.dp).size(15.dp),
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
                }

                // Last seen
                Text(
                    text = "Last seen 2 hours ago",
                    color = MaterialTheme.colorScheme.placeholderText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExternalIdentitiesMockup() {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        // Twitter/X
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "X", color = MaterialTheme.colorScheme.background, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "@vitorpamplona",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 5.dp),
                fontSize = 14.sp,
            )
        }

        // GitHub
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "G", color = MaterialTheme.colorScheme.background, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "vitorpamplona",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 5.dp),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun Nip05MockupRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "NIP-05 Verified",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "_@vitorpamplona.com",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun LightningAddressMockup() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "\u26A1",
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "vitor@getalby.com",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
    }
}
