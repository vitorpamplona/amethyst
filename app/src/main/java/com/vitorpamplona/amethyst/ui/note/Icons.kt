/**
 * Copyright (c) 2023 Vitor Pamplona
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

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.R
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
        painter = painterResource(R.drawable.amethyst),
        null,
        modifier = Modifier.size(iconSize),
        tint = Color.Unspecified,
    )
}

@Composable
fun FollowingIcon(iconSize: Dp) {
    Icon(
        painter = painterResource(R.drawable.following),
        contentDescription = stringResource(id = R.string.following),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = Color.Unspecified,
    )
}

@Composable
fun ArrowBackIcon() {
    Icon(
        imageVector = Icons.Default.ArrowBack,
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
        null,
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
fun LikedIcon(iconSize: Dp) {
    LikedIcon(modifier = remember(iconSize) { Modifier.size(iconSize) })
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
        null,
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
        null,
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

@Composable
fun ZapIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = stringResource(R.string.zaps),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun CashuIcon(modifier: Modifier) {
    Icon(
        painter = painterResource(R.drawable.cashu),
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
        imageVector = Icons.Default.OpenInNew,
        stringResource(id = R.string.copy_to_clipboard),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ExpandLessIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.Default.ExpandLess,
        null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.subtleButton,
    )
}

@Composable
fun ExpandMoreIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.Default.ExpandMore,
        null,
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
        contentDescription = null,
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
        null,
        modifier = Size20Modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun RegularPostIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_lists),
        null,
        modifier = Size20Modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun CancelIcon() {
    Icon(
        imageVector = Icons.Default.Cancel,
        null,
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
        imageVector = Icons.Default.VolumeOff,
        contentDescription = stringResource(id = R.string.muted_button),
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Size30Modifier,
    )
}

@Composable
fun MuteIcon() {
    Icon(
        imageVector = Icons.Default.VolumeUp,
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
        contentDescription = null,
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
        contentDescription = null,
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
        contentDescription = null,
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
fun VerticalDotsIcon() {
    Icon(
        imageVector = Icons.Default.MoreVert,
        null,
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
