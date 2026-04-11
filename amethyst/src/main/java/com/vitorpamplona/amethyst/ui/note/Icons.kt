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
package com.vitorpamplona.amethyst.ui.note

// Re-export all commons icons so existing callers don't need to change their imports.
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.vitorpamplona.amethyst.commons.icons.Zap
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.subtleButton
import com.vitorpamplona.amethyst.commons.ui.note.AmethystIcon as CommonsAmethystIcon
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon as CommonsArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.CancelIcon as CommonsCancelIcon
import com.vitorpamplona.amethyst.commons.ui.note.CashuIcon as CommonsCashuIcon
import com.vitorpamplona.amethyst.commons.ui.note.ChangeReactionIcon as CommonsChangeReactionIcon
import com.vitorpamplona.amethyst.commons.ui.note.ClearTextIcon as CommonsClearTextIcon
import com.vitorpamplona.amethyst.commons.ui.note.CloseIcon as CommonsCloseIcon
import com.vitorpamplona.amethyst.commons.ui.note.CommentIcon as CommonsCommentIcon
import com.vitorpamplona.amethyst.commons.ui.note.CopyIcon as CommonsCopyIcon
import com.vitorpamplona.amethyst.commons.ui.note.DownloadForOfflineIcon as CommonsDownloadForOfflineIcon
import com.vitorpamplona.amethyst.commons.ui.note.EnablePiP as CommonsEnablePiP
import com.vitorpamplona.amethyst.commons.ui.note.FollowingIcon as CommonsFollowingIcon
import com.vitorpamplona.amethyst.commons.ui.note.LightningAddressIcon as CommonsLightningAddressIcon
import com.vitorpamplona.amethyst.commons.ui.note.LikeIcon as CommonsLikeIcon
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon as CommonsLikedIcon
import com.vitorpamplona.amethyst.commons.ui.note.LinkIcon as CommonsLinkIcon
import com.vitorpamplona.amethyst.commons.ui.note.OpenInNewIcon as CommonsOpenInNewIcon
import com.vitorpamplona.amethyst.commons.ui.note.PinIcon as CommonsPinIcon
import com.vitorpamplona.amethyst.commons.ui.note.PlayIcon as CommonsPlayIcon
import com.vitorpamplona.amethyst.commons.ui.note.RepostIcon as CommonsRepostIcon
import com.vitorpamplona.amethyst.commons.ui.note.RepostedIcon as CommonsRepostedIcon
import com.vitorpamplona.amethyst.commons.ui.note.SearchIcon as CommonsSearchIcon
import com.vitorpamplona.amethyst.commons.ui.note.ShareIcon as CommonsShareIcon
import com.vitorpamplona.amethyst.commons.ui.note.VerticalDotsIcon as CommonsVerticalDotsIcon
import com.vitorpamplona.amethyst.commons.ui.note.VoiceReplyIcon as CommonsVoiceReplyIcon
import com.vitorpamplona.amethyst.commons.ui.note.ZappedIcon as CommonsZappedIcon

// ---- Delegated composables (thin wrappers for source compatibility) ----

@Composable
fun AmethystIcon(iconSize: Dp) = CommonsAmethystIcon(iconSize)

@Composable
fun FollowingIcon(modifier: Modifier) = CommonsFollowingIcon(modifier)

@Composable
fun ArrowBackIcon(tint: Color = MaterialTheme.colorScheme.grayText) = CommonsArrowBackIcon(tint)

@Composable
fun DownloadForOfflineIcon(
    iconSize: Dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) = CommonsDownloadForOfflineIcon(iconSize, tint)

@Composable
fun LikedIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) = CommonsLikedIcon(modifier, tint)

@Composable
fun ChangeReactionIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) = CommonsChangeReactionIcon(modifier, tint)

@Composable
fun LikeIcon(
    iconSizeModifier: Modifier,
    grayTint: Color,
) = CommonsLikeIcon(iconSizeModifier, grayTint)

@Composable
fun RepostIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) = CommonsRepostIcon(modifier, tint)

@Composable
fun RepostedIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) = CommonsRepostedIcon(modifier, tint)

@Composable
fun LightningAddressIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) = CommonsLightningAddressIcon(modifier, tint)

@Composable
fun ZappedIcon(iconSize: Dp) = CommonsZappedIcon(iconSize)

@Composable
fun ZappedIcon(modifier: Modifier) = CommonsZappedIcon(modifier)

@Composable
fun ZapIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
    contentDescriptor: Int = com.vitorpamplona.amethyst.R.string.zap_description,
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
    contentDescriptor: Int = com.vitorpamplona.amethyst.R.string.zap_description,
) {
    Icon(
        imageVector = Zap,
        contentDescription = stringRes(contentDescriptor),
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ShareIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) = CommonsShareIcon(modifier, tint)

@Composable
fun CashuIcon(modifier: Modifier) = CommonsCashuIcon(modifier)

@Composable
fun CopyIcon(modifier: Modifier) = CommonsCopyIcon(modifier)

@Composable
fun OpenInNewIcon(modifier: Modifier) = CommonsOpenInNewIcon(modifier)

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
fun VoiceReplyIcon(
    iconSizeModifier: Modifier,
    tint: Color,
) = CommonsVoiceReplyIcon(iconSizeModifier, tint)

@Composable
fun CommentIcon(
    iconSizeModifier: Modifier,
    tint: Color,
) = CommonsCommentIcon(iconSizeModifier, tint)

@Composable
fun CancelIcon() = CommonsCancelIcon()

@Composable
fun CloseIcon() = CommonsCloseIcon()

@Composable
fun SearchIcon(
    modifier: Modifier,
    tint: Color = Color.Unspecified,
) = CommonsSearchIcon(modifier, tint)

@Composable
fun PlayIcon(
    modifier: Modifier,
    tint: Color,
) = CommonsPlayIcon(modifier, tint)

@Composable
fun PinIcon(
    modifier: Modifier,
    tint: Color,
) = CommonsPinIcon(modifier, tint)

@Composable
fun EnablePiP(
    modifier: Modifier,
    tint: Color,
) = CommonsEnablePiP(modifier, tint)

@Composable
fun ClearTextIcon() = CommonsClearTextIcon()

@Composable
fun LinkIcon(
    modifier: Modifier,
    tint: Color,
) = CommonsLinkIcon(modifier, tint)

@Composable
fun VerticalDotsIcon() = CommonsVerticalDotsIcon()

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
        ShareIcon(Size20Modifier, Color.Unspecified)
    }
}
