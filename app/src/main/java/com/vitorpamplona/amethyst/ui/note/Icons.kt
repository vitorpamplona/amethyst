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
package com.vitorpamplona.amethyst.ui.note

import Following
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleButton

@Composable
fun AmethystIcon(iconSize: Dp) {
    Icon(
        imageVector = CustomHashTagIcons.Amethyst,
        contentDescription = stringResource(id = R.string.app_logo),
        modifier = Modifier.size(iconSize),
        tint = Color.Unspecified,
    )
}

@Composable
fun FollowingIcon(iconSize: Dp) {
    Icon(
        imageVector = Following,
        contentDescription = stringResource(id = R.string.following),
        modifier = Modifier.size(iconSize),
        tint = Color.Unspecified,
    )
}

@Composable
fun ArrowBackIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.back),
        tint = MaterialTheme.colorScheme.grayText,
    )
}

@Composable
fun MessageIcon(modifier: Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_dm),
        null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun DownloadForOfflineIcon(
    iconSize: Dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        imageVector = Icons.Default.DownloadForOffline,
        contentDescription = stringResource(id = R.string.accessibility_download_for_offline),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = tint,
    )
}

@Composable
fun HashCheckIcon(iconSize: Dp) {
    Icon(
        painter = painterResource(R.drawable.original),
        contentDescription = stringResource(id = R.string.hash_verification_passed),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = Color.Unspecified,
    )
}

@Composable
fun HashCheckFailedIcon(iconSize: Dp) {
    Icon(
        imageVector = Icons.Default.Report,
        contentDescription = stringResource(id = R.string.hash_verification_failed),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = Color.Red,
    )
}

@Composable
fun LikedIcon(modifier: Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_liked),
        null,
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
fun LikeIcon(
    iconSizeModifier: Modifier,
    grayTint: Color,
) {
    Icon(
        painter = painterResource(R.drawable.ic_like),
        contentDescription = stringResource(id = R.string.like_description),
        modifier = iconSizeModifier,
        tint = grayTint,
    )
}

@Composable
fun RepostedIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(R.drawable.ic_retweeted),
        contentDescription = stringResource(id = R.string.boost_or_quote_description),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun LightningAddressIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = stringResource(R.string.lightning_address),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ZappedIcon(iconSize: Dp) {
    ZappedIcon(modifier = remember(iconSize) { Modifier.size(iconSize) })
}

@Composable
fun ZappedIcon(modifier: Modifier) {
    ZapIcon(modifier = modifier, BitcoinOrange)
}

@Preview
@Composable
fun ReactionRowIconPreview() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CommentIcon(Size20Modifier, Color.Unspecified)
        RepostedIcon(Size20Modifier)
        LikeIcon(Size20Modifier, Color.Unspecified)
        ZapIcon(Size20Modifier)
        ZappedIcon(Size20Modifier)
    }
}

@Composable
fun ZapIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
    contentDescriptor: Int = R.string.zap_description,
) {
    Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = stringResource(contentDescriptor),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun CashuIcon(modifier: Modifier) {
    Icon(
        imageVector = CustomHashTagIcons.Cashu,
        "Cashu",
        tint = Color.Unspecified,
        modifier = modifier,
    )
}

@Composable
fun CopyIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Icons.Default.ContentCopy,
        stringResource(id = R.string.copy_to_clipboard),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun OpenInNewIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        stringResource(id = R.string.copy_to_clipboard),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ExpandLessIcon(
    modifier: Modifier,
    contentDescriptor: Int,
) {
    Icon(
        imageVector = Icons.Default.ExpandLess,
        contentDescription = stringResource(id = contentDescriptor),
        modifier = modifier,
        tint = MaterialTheme.colorScheme.subtleButton,
    )
}

@Composable
fun ExpandMoreIcon(
    modifier: Modifier,
    contentDescriptor: Int,
) {
    Icon(
        imageVector = Icons.Default.ExpandMore,
        contentDescription = stringResource(id = contentDescriptor),
        modifier = modifier,
        tint = MaterialTheme.colorScheme.subtleButton,
    )
}

@Composable
fun CommentIcon(
    iconSizeModifier: Modifier,
    tint: Color,
) {
    Icon(
        painter = painterResource(R.drawable.ic_comment),
        contentDescription = stringResource(id = R.string.reply_description),
        modifier = iconSizeModifier,
        tint = tint,
    )
}

@Composable
fun ViewCountIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Icons.Outlined.BarChart,
        null,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun PollIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_poll),
        contentDescription = stringResource(id = R.string.poll),
        modifier = Size20Modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun RegularPostIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_lists),
        contentDescription = stringResource(id = R.string.disable_poll),
        modifier = Size20Modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun CancelIcon() {
    Icon(
        imageVector = Icons.Default.Cancel,
        contentDescription = stringResource(id = R.string.cancel),
        modifier = Size30Modifier,
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
fun CloseIcon() {
    Icon(
        painter = painterResource(id = R.drawable.ic_close),
        contentDescription = stringResource(id = R.string.cancel),
        modifier = Size20Modifier,
    )
}

@Composable
fun MutedIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
        contentDescription = stringResource(id = R.string.muted_button),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Size30Modifier,
    )
}

@Composable
fun MuteIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
        contentDescription = stringResource(id = R.string.mute_button),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Size30Modifier,
    )
}

@Composable
fun SearchIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(R.drawable.ic_search),
        contentDescription = stringResource(id = R.string.search_button),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun PlayIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = Icons.Outlined.PlayCircle,
        contentDescription = stringResource(id = R.string.accessibility_play_username),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun PinIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = Icons.Default.PushPin,
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun LyricsIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        painter = painterResource(id = R.drawable.lyrics_on),
        contentDescription = stringResource(id = R.string.accessibility_lyrics_on),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun LyricsOffIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        painter = painterResource(id = R.drawable.lyrics_off),
        contentDescription = stringResource(id = R.string.accessibility_lyrics_off),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun ClearTextIcon() {
    Icon(
        imageVector = Icons.Default.Clear,
        contentDescription = stringResource(R.string.clear),
    )
}

@Composable
fun LinkIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = Icons.Default.Link,
        contentDescription = stringResource(R.string.website),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun VerticalDotsIcon(contentDescriptor: Int? = null) {
    Icon(
        imageVector = Icons.Default.MoreVert,
        contentDescription = contentDescriptor?.let { stringResource(id = it) },
        modifier = Size18Modifier,
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
fun NIP05CheckingIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.Default.Downloading,
        contentDescription = stringResource(id = R.string.nip05_checking),
        modifier = modifier,
        tint = Color.Yellow,
    )
}

@Composable
fun NIP05VerifiedIcon(modifier: Modifier) {
    Icon(
        painter = painterResource(R.drawable.nip_05),
        contentDescription = stringResource(id = R.string.nip05_verified),
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
fun NIP05FailedVerification(modifier: Modifier) {
    Icon(
        imageVector = Icons.Default.Report,
        contentDescription = stringResource(id = R.string.nip05_failed),
        modifier = modifier,
        tint = Color.Red,
    )
}

@Composable
fun IncognitoIconOn(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        painter = painterResource(id = R.drawable.incognito),
        contentDescription = stringResource(id = R.string.accessibility_turn_off_sealed_message),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun IncognitoIconOff(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        painter = painterResource(id = R.drawable.incognito_off),
        contentDescription = stringResource(id = R.string.accessibility_turn_on_sealed_message),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun ZapSplitIcon(
    modifier: Modifier = Size20Modifier,
    tint: Color = BitcoinOrange,
) {
    Icon(
        imageVector = ZapSplitVector,
        contentDescription = stringResource(id = R.string.zap_split_title),
        modifier = modifier,
        tint = tint,
    )
}

@Preview
@Composable
fun ZapSplitPreview() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.height(20.dp).width(25.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier.size(20.dp).align(Alignment.CenterStart),
                tint = BitcoinOrange,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowForwardIos,
                contentDescription = stringResource(id = R.string.zaps),
                modifier = Modifier.size(13.dp).align(Alignment.CenterEnd),
                tint = BitcoinOrange,
            )
        }
        ZapSplitIcon(tint = BitcoinOrange)
    }
}

public val ZapSplitVector: ImageVector
    get() {
        if (zapSplit != null) {
            return zapSplit!!
        }
        zapSplit =
            materialIcon(name = "ZapSplit") {
                materialPath {
                    moveTo(7.0f, 21.0f)
                    horizontalLineToRelative(-1.0f)
                    lineToRelative(1.0f, -7.0f)
                    horizontalLineTo(3.5f)
                    curveToRelative(-0.88f, 0.0f, -0.33f, -0.75f, -0.31f, -0.78f)
                    curveTo(4.48f, 10.94f, 6.42f, 7.54f, 9.01f, 3.0f)
                    horizontalLineToRelative(1.0f)
                    lineToRelative(-1.0f, 7.0f)
                    horizontalLineToRelative(3.51f)
                    curveToRelative(0.4f, 0.0f, 0.62f, 0.19f, 0.4f, 0.66f)
                    curveTo(8.97f, 17.55f, 7.0f, 21.0f, 7.0f, 21.0f)
                    close()
                    moveTo(14.59f, 16.59f)
                    lineTo(19.17f, 12.0f)
                    lineTo(14.59f, 7.41f)
                    lineTo(16.0f, 6.0f)
                    lineToRelative(6.0f, 6.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineToRelative(-1.41f, -1.41f)
                    close()
                }
            }
        return zapSplit!!
    }

private var zapSplit: ImageVector? = null
