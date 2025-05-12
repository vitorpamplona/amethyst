/**
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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.hashtags.Amethyst
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.Following
import com.vitorpamplona.amethyst.commons.icons.Like
import com.vitorpamplona.amethyst.commons.icons.Liked
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Repost
import com.vitorpamplona.amethyst.commons.icons.Reposted
import com.vitorpamplona.amethyst.commons.icons.Search
import com.vitorpamplona.amethyst.commons.icons.Zap
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.MoneroOrange
import com.vitorpamplona.amethyst.ui.theme.Size19Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleButton

@Composable
fun AmethystIcon(iconSize: Dp) {
    Icon(
        imageVector = CustomHashTagIcons.Amethyst,
        contentDescription = stringRes(id = R.string.app_logo),
        modifier = Modifier.size(iconSize),
        tint = Color.Unspecified,
    )
}

@Composable
fun FollowingIcon(iconSize: Dp) {
    FollowingIcon(Modifier.size(iconSize))
}

@Composable
fun FollowingIcon(modifier: Modifier) {
    Icon(
        imageVector = Following,
        contentDescription = stringRes(id = R.string.following),
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
fun ArrowBackIcon(tint: Color = MaterialTheme.colorScheme.grayText) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringRes(R.string.back),
        tint = tint,
    )
}

@Composable
fun MessageIcon(modifier: Modifier) {
    Icon(
        painter = painterRes(R.drawable.ic_dm),
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
        contentDescription = stringRes(id = R.string.accessibility_download_for_offline),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = tint,
    )
}

@Composable
fun HashCheckIcon(iconSize: Dp) {
    Icon(
        painter = painterRes(R.drawable.original),
        contentDescription = stringRes(id = R.string.hash_verification_passed),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = Color.Unspecified,
    )
}

@Composable
fun HashCheckFailedIcon(iconSize: Dp) {
    Icon(
        imageVector = Icons.Default.Report,
        contentDescription = stringRes(id = R.string.hash_verification_failed),
        modifier = remember(iconSize) { Modifier.size(iconSize) },
        tint = Color.Red,
    )
}

@Composable
fun LikedIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Liked,
        contentDescription = stringRes(id = R.string.like_description),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun ChangeReactionIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Icons.Outlined.AddReaction,
        contentDescription = stringRes(id = R.string.change_reaction),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun LikeIcon(
    iconSizeModifier: Modifier,
    grayTint: Color,
) {
    Icon(
        imageVector = Like,
        contentDescription = stringRes(id = R.string.like_description),
        modifier = iconSizeModifier,
        tint = grayTint,
    )
}

@Composable
fun RepostIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Repost,
        contentDescription = stringRes(id = R.string.boost_or_quote_description),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun RepostedIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Reposted,
        contentDescription = stringRes(id = R.string.boost_or_quote_description),
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
        contentDescription = stringRes(R.string.lightning_address),
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
        OutlinedZapIcon(Size20Modifier)
        ZapIcon(Size20Modifier)
        ZappedIcon(Size20Modifier)
        MoneroIcon(Size20Modifier)
        ShareIcon(Size20Modifier, Color.Unspecified)
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
        contentDescription = stringRes(contentDescriptor),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun OutlinedZapIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
    contentDescriptor: Int = R.string.zap_description,
) {
    Icon(
        imageVector = Zap,
        contentDescription = stringRes(contentDescriptor),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun TippedMoneroIcon(
    modifier: Modifier,
    tint: Color = MoneroOrange,
) {
    MoneroIcon(modifier, tint)
}

@Composable
fun MoneroIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = ImageVector.vectorResource(R.drawable.monero),
        modifier = modifier,
        contentDescription = stringRes(R.string.share_or_save),
        tint = tint,
    )
}

@Composable
fun ShareIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Icons.Default.Share,
        modifier = modifier,
        contentDescription = stringRes(R.string.share_or_save),
        tint = tint,
    )
}

@Composable
fun CashuIcon(modifier: Modifier) {
    Icon(
        imageVector = CustomHashTagIcons.Cashu,
        stringRes(R.string.cashu),
        tint = Color.Unspecified,
        modifier = modifier,
    )
}

@Composable
fun CopyIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.Default.ContentCopy,
        stringRes(id = R.string.copy_to_clipboard),
        modifier = modifier,
    )
}

@Composable
fun OpenInNewIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        stringRes(id = R.string.copy_to_clipboard),
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
        contentDescription = stringRes(id = contentDescriptor),
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
        contentDescription = stringRes(id = contentDescriptor),
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
        imageVector = Reply,
        contentDescription = stringRes(id = R.string.reply_description),
        modifier = iconSizeModifier,
        tint = tint,
    )
}

@Composable
fun PollIcon() {
    Icon(
        painter = painterRes(R.drawable.ic_poll),
        contentDescription = stringRes(id = R.string.poll),
        modifier = Size20Modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun RegularPostIcon() {
    Icon(
        painter = painterRes(R.drawable.ic_lists),
        contentDescription = stringRes(id = R.string.disable_poll),
        modifier = Size20Modifier,
        tint = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun CancelIcon() {
    Icon(
        imageVector = Icons.Default.Cancel,
        contentDescription = stringRes(id = R.string.cancel),
        modifier = Size30Modifier,
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
fun CloseIcon() {
    Icon(
        imageVector = Icons.Outlined.Close,
        contentDescription = stringRes(id = R.string.cancel),
        modifier = Size20Modifier,
    )
}

@Composable
fun SearchIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = Search,
        contentDescription = stringRes(id = R.string.search_button),
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
        contentDescription = stringRes(id = R.string.accessibility_play_username),
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
        contentDescription = stringRes(id = R.string.accessibility_pushpin),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun EnablePiP(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
        contentDescription = stringRes(id = R.string.enter_picture_in_picture),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun ClearTextIcon() {
    Icon(
        imageVector = Icons.Default.Clear,
        contentDescription = stringRes(R.string.clear),
    )
}

@Composable
fun LinkIcon(
    modifier: Modifier,
    tint: Color,
) {
    Icon(
        imageVector = Icons.Default.Link,
        contentDescription = stringRes(R.string.website),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun VerticalDotsIcon() {
    Icon(
        imageVector = Icons.Default.MoreVert,
        contentDescription = stringRes(id = R.string.note_options),
        modifier = Size19Modifier,
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
fun NIP05CheckingIcon(modifier: Modifier) {
    Icon(
        imageVector = Icons.Default.Downloading,
        contentDescription = stringRes(id = R.string.nip05_checking),
        modifier = modifier,
        tint = Color.Yellow,
    )
}

@Composable
fun NIP05VerifiedIcon(modifier: Modifier) {
    Icon(
        painter = painterRes(R.drawable.nip_05),
        contentDescription = stringRes(id = R.string.nip05_verified),
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
fun NIP05FailedVerification(modifier: Modifier) {
    Icon(
        imageVector = Icons.Default.Report,
        contentDescription = stringRes(id = R.string.nip05_failed),
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
        contentDescription = stringRes(id = R.string.accessibility_turn_off_sealed_message),
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
        contentDescription = stringRes(id = R.string.accessibility_turn_on_sealed_message),
        modifier = modifier,
        tint = tint,
    )
}
