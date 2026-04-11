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
package com.vitorpamplona.amethyst.ui.theme

// Backward-compatible re-exports — all shape/layout definitions live in commons
import com.vitorpamplona.amethyst.commons.ui.theme.AccountPictureModifier as CommonsAccountPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.AuthorInfoVideoFeed as CommonsAuthorInfoVideoFeed
import com.vitorpamplona.amethyst.commons.ui.theme.BadgePictureModifier as CommonsBadgePictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.BigPadding as CommonsBigPadding
import com.vitorpamplona.amethyst.commons.ui.theme.BottomTopHeight as CommonsBottomTopHeight
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder as CommonsButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonPadding as CommonsButtonPadding
import com.vitorpamplona.amethyst.commons.ui.theme.CashuCardBorders as CommonsCashuCardBorders
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleMaxSizeModifier as CommonsChatBubbleMaxSizeModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMe as CommonsChatBubbleShapeMe
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThem as CommonsChatBubbleShapeThem
import com.vitorpamplona.amethyst.commons.ui.theme.ChatHeadlineBorders as CommonsChatHeadlineBorders
import com.vitorpamplona.amethyst.commons.ui.theme.ChatPaddingInnerQuoteModifier as CommonsChatPaddingInnerQuoteModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatPaddingModifier as CommonsChatPaddingModifier
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness as CommonsDividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzPadding as CommonsDoubleHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer as CommonsDoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertPadding as CommonsDoubleVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer as CommonsDoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder as CommonsEditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier as CommonsEditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier as CommonsEditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding as CommonsFeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.FillWidthQuoteBorderModifier as CommonsFillWidthQuoteBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.FollowPackHeaderModifier as CommonsFollowPackHeaderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.FollowSetImageModifier as CommonsFollowSetImageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.HalfDoubleVertSpacer as CommonsHalfDoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.HalfEndPadding as CommonsHalfEndPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfFeedPadding as CommonsHalfFeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfHorzModifier as CommonsHalfHalfHorzModifier
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfTopPadding as CommonsHalfHalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfVertPadding as CommonsHalfHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzPadding as CommonsHalfHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzSpacer as CommonsHalfHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.HalfPadding as CommonsHalfPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfStartPadding as CommonsHalfStartPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfTopPadding as CommonsHalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfVertPadding as CommonsHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfVertSpacer as CommonsHalfVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.HeaderPictureModifier as CommonsHeaderPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Height100Modifier as CommonsHeight100Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Height24dpFilledModifier as CommonsHeight24dpFilledModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Height24dpModifier as CommonsHeight24dpModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Height25Modifier as CommonsHeight25Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Height4dpFilledModifier as CommonsHeight4dpFilledModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Height4dpModifier as CommonsHeight4dpModifier
import com.vitorpamplona.amethyst.commons.ui.theme.HorzHalfVertPadding as CommonsHorzHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HorzPadding as CommonsHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.IconRowModifier as CommonsIconRowModifier
import com.vitorpamplona.amethyst.commons.ui.theme.IconRowTextModifier as CommonsIconRowTextModifier
import com.vitorpamplona.amethyst.commons.ui.theme.IncognitoIconButtonModifier as CommonsIncognitoIconButtonModifier
import com.vitorpamplona.amethyst.commons.ui.theme.IncognitoIconModifier as CommonsIncognitoIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.LargeRelayIconModifier as CommonsLargeRelayIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.LeftHalfCircleButtonBorder as CommonsLeftHalfCircleButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthPaddingTop5dp as CommonsMaxWidthPaddingTop5dp
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthWithHorzPadding as CommonsMaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.MediumRelayIconModifier as CommonsMediumRelayIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.MinHorzSpacer as CommonsMinHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ModifierWidth3dp as CommonsModifierWidth3dp
import com.vitorpamplona.amethyst.commons.ui.theme.NIP05IconSize as CommonsNIP05IconSize
import com.vitorpamplona.amethyst.commons.ui.theme.NoSoTinyBorders as CommonsNoSoTinyBorders
import com.vitorpamplona.amethyst.commons.ui.theme.NotificationIconModifier as CommonsNotificationIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.NotificationIconModifierSmaller as CommonsNotificationIconModifierSmaller
import com.vitorpamplona.amethyst.commons.ui.theme.PaddingHorizontal12Modifier as CommonsPaddingHorizontal12Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.PinBottomIconSize as CommonsPinBottomIconSize
import com.vitorpamplona.amethyst.commons.ui.theme.PlayIconSize as CommonsPlayIconSize
import com.vitorpamplona.amethyst.commons.ui.theme.PopupUpEffect as CommonsPopupUpEffect
import com.vitorpamplona.amethyst.commons.ui.theme.PostKeyboard as CommonsPostKeyboard
import com.vitorpamplona.amethyst.commons.ui.theme.QuickActionPopupShadow as CommonsQuickActionPopupShadow
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder as CommonsQuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowExpandButton as CommonsReactionRowExpandButton
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeight as CommonsReactionRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeightChat as CommonsReactionRowHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeightChatMaxWidth as CommonsReactionRowHeightChatMaxWidth
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowHeightWithPadding as CommonsReactionRowHeightWithPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowZapraiser as CommonsReactionRowZapraiser
import com.vitorpamplona.amethyst.commons.ui.theme.ReactionRowZapraiserWithPadding as CommonsReactionRowZapraiserWithPadding
import com.vitorpamplona.amethyst.commons.ui.theme.RippleRadius45dp as CommonsRippleRadius45dp
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing as CommonsRowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing10dp as CommonsRowColSpacing10dp
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing5dp as CommonsRowColSpacing5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstModifier as CommonsSettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstWithHorzBorderModifier as CommonsSettingsCategoryFirstWithHorzBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategorySpacingModifier as CommonsSettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategorySpacingWithHorzBorderModifier as CommonsSettingsCategorySpacingWithHorzBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Shapes as CommonsShapes
import com.vitorpamplona.amethyst.commons.ui.theme.ShowMoreRelaysButtonBoxModifer as CommonsShowMoreRelaysButtonBoxModifer
import com.vitorpamplona.amethyst.commons.ui.theme.ShowMoreRelaysButtonIconButtonModifier as CommonsShowMoreRelaysButtonIconButtonModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ShowMoreRelaysButtonIconModifier as CommonsShowMoreRelaysButtonIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleHeaderImage as CommonsSimpleHeaderImage
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImage35Modifier as CommonsSimpleImage35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImage75Modifier as CommonsSimpleImage75Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImageBorder as CommonsSimpleImageBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size0dp as CommonsSize0dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size100dp as CommonsSize100dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size10Modifier as CommonsSize10Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp as CommonsSize10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size110dp as CommonsSize110dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size12dp as CommonsSize12dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size13dp as CommonsSize13dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size14Modifier as CommonsSize14Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size14dp as CommonsSize14dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier as CommonsSize15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size15dp as CommonsSize15dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size165dp as CommonsSize165dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier as CommonsSize16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size16dp as CommonsSize16dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size17Modifier as CommonsSize17Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size17dp as CommonsSize17dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier as CommonsSize18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size18dp as CommonsSize18dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size19Modifier as CommonsSize19Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size19dp as CommonsSize19dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier as CommonsSize20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp as CommonsSize20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size22Modifier as CommonsSize22Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size22ModifierWith4Padding as CommonsSize22ModifierWith4Padding
import com.vitorpamplona.amethyst.commons.ui.theme.Size22dp as CommonsSize22dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size23dp as CommonsSize23dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier as CommonsSize24Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size24dp as CommonsSize24dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size25Modifier as CommonsSize25Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp as CommonsSize25dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size26Modifier as CommonsSize26Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size28Modifier as CommonsSize28Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size2dp as CommonsSize2dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size30Modifier as CommonsSize30Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp as CommonsSize30dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size34dp as CommonsSize34dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier as CommonsSize35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp as CommonsSize35dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size39Modifier as CommonsSize39Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size3dp as CommonsSize3dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size40Modifier as CommonsSize40Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp as CommonsSize40dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier as CommonsSize50Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50ModifierOffset10 as CommonsSize50ModifierOffset10
import com.vitorpamplona.amethyst.commons.ui.theme.Size50dp as CommonsSize50dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier as CommonsSize55Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp as CommonsSize55dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5Modifier as CommonsSize5Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp as CommonsSize5dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size6dp as CommonsSize6dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size75Modifier as CommonsSize75Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size75dp as CommonsSize75dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size8dp as CommonsSize8dp
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder as CommonsSmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallestBorder as CommonsSmallestBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallishBorder as CommonsSmallishBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy10dp as CommonsSpacedBy10dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy2dp as CommonsSpacedBy2dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy3dp as CommonsSpacedBy3dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy55dp as CommonsSpacedBy55dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp as CommonsSpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SquaredQuoteBorderModifier as CommonsSquaredQuoteBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdButtonSizeModifier as CommonsStdButtonSizeModifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdEndPadding as CommonsStdEndPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer as CommonsStdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding as CommonsStdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdStartPadding as CommonsStdStartPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdTopPadding as CommonsStdTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer as CommonsStdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StreamingHeaderModifier as CommonsStreamingHeaderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat as CommonsSuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage as CommonsSuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight as CommonsTabRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.TextStyleBottomNavBar as CommonsTextStyleBottomNavBar
import com.vitorpamplona.amethyst.commons.ui.theme.TinyBorders as CommonsTinyBorders
import com.vitorpamplona.amethyst.commons.ui.theme.UserNameMaxRowHeight as CommonsUserNameMaxRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.UserNameRowHeight as CommonsUserNameRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.VertPadding as CommonsVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.VideoReactionColumnPadding as CommonsVideoReactionColumnPadding
import com.vitorpamplona.amethyst.commons.ui.theme.VoiceHeightModifier as CommonsVoiceHeightModifier
import com.vitorpamplona.amethyst.commons.ui.theme.VolumeBottomIconSize as CommonsVolumeBottomIconSize
import com.vitorpamplona.amethyst.commons.ui.theme.Width16Space as CommonsWidth16Space
import com.vitorpamplona.amethyst.commons.ui.theme.WidthAuthorPictureModifier as CommonsWidthAuthorPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.WidthAuthorPictureModifierWithPadding as CommonsWidthAuthorPictureModifierWithPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ZapPictureCommentModifier as CommonsZapPictureCommentModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ZeroPadding as CommonsZeroPadding
import com.vitorpamplona.amethyst.commons.ui.theme.authorNotePictureForImageHeader as commonsAuthorNotePictureForImageHeader
import com.vitorpamplona.amethyst.commons.ui.theme.bannerModifier as commonsBannerModifier
import com.vitorpamplona.amethyst.commons.ui.theme.boostedNoteModifier as commonsBoostedNoteModifier
import com.vitorpamplona.amethyst.commons.ui.theme.chatAuthorBox as commonsChatAuthorBox
import com.vitorpamplona.amethyst.commons.ui.theme.chatAuthorImage as commonsChatAuthorImage
import com.vitorpamplona.amethyst.commons.ui.theme.defaultTweenDuration as commonsDefaultTweenDuration
import com.vitorpamplona.amethyst.commons.ui.theme.defaultTweenFloatSpec as commonsDefaultTweenFloatSpec
import com.vitorpamplona.amethyst.commons.ui.theme.defaultTweenIntOffsetSpec as commonsDefaultTweenIntOffsetSpec
import com.vitorpamplona.amethyst.commons.ui.theme.drawerSpacing as commonsDrawerSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.emptyLineItemModifier as commonsEmptyLineItemModifier
import com.vitorpamplona.amethyst.commons.ui.theme.hashVerifierMark as commonsHashVerifierMark
import com.vitorpamplona.amethyst.commons.ui.theme.imageHeaderBannerSize as commonsImageHeaderBannerSize
import com.vitorpamplona.amethyst.commons.ui.theme.inlinePlaceholder as commonsInlinePlaceholder
import com.vitorpamplona.amethyst.commons.ui.theme.liveStreamTag as commonsLiveStreamTag
import com.vitorpamplona.amethyst.commons.ui.theme.messageBubbleLimits as commonsMessageBubbleLimits
import com.vitorpamplona.amethyst.commons.ui.theme.messageDetailsModifier as commonsMessageDetailsModifier
import com.vitorpamplona.amethyst.commons.ui.theme.normalWithTopMarginNoteModifier as commonsNormalWithTopMarginNoteModifier
import com.vitorpamplona.amethyst.commons.ui.theme.noteComposeRelayBox as commonsNoteComposeRelayBox
import com.vitorpamplona.amethyst.commons.ui.theme.previewCardImageModifier as commonsPreviewCardImageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.profileContentHeaderModifier as commonsProfileContentHeaderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.reactionBox as commonsReactionBox
import com.vitorpamplona.amethyst.commons.ui.theme.ripple24dp as commonsRipple24dp

val Shapes = CommonsShapes

val RippleRadius45dp = CommonsRippleRadius45dp

val BottomTopHeight = CommonsBottomTopHeight
val TabRowHeight = CommonsTabRowHeight

val SmallestBorder = CommonsSmallestBorder
val SmallBorder = CommonsSmallBorder
val SmallishBorder = CommonsSmallishBorder
val QuoteBorder = CommonsQuoteBorder

val ButtonBorder = CommonsButtonBorder
val LeftHalfCircleButtonBorder = CommonsLeftHalfCircleButtonBorder
val EditFieldBorder = CommonsEditFieldBorder

val ChatBubbleShapeMe = CommonsChatBubbleShapeMe
val ChatBubbleShapeThem = CommonsChatBubbleShapeThem

val StdButtonSizeModifier = CommonsStdButtonSizeModifier

val HalfVertSpacer = CommonsHalfVertSpacer

val MinHorzSpacer = CommonsMinHorzSpacer

val HalfHorzSpacer = CommonsHalfHorzSpacer

val StdHorzSpacer = CommonsStdHorzSpacer
val StdVertSpacer = CommonsStdVertSpacer

val DoubleHorzSpacer = CommonsDoubleHorzSpacer
val DoubleVertSpacer = CommonsDoubleVertSpacer

val Height100Modifier = CommonsHeight100Modifier

val HalfDoubleVertSpacer = CommonsHalfDoubleVertSpacer

val Size0dp = CommonsSize0dp
val Size2dp = CommonsSize2dp
val Size3dp = CommonsSize3dp
val Size5dp = CommonsSize5dp
val Size6dp = CommonsSize6dp
val Size8dp = CommonsSize8dp
val Size10dp = CommonsSize10dp
val Size12dp = CommonsSize12dp
val Size13dp = CommonsSize13dp
val Size14dp = CommonsSize14dp
val Size15dp = CommonsSize15dp
val Size16dp = CommonsSize16dp
val Size17dp = CommonsSize17dp
val Size18dp = CommonsSize18dp
val Size19dp = CommonsSize19dp
val Size20dp = CommonsSize20dp
val Size22dp = CommonsSize22dp
val Size23dp = CommonsSize23dp
val Size24dp = CommonsSize24dp
val Size25dp = CommonsSize25dp
val Size30dp = CommonsSize30dp
val Size34dp = CommonsSize34dp
val Size35dp = CommonsSize35dp
val Size40dp = CommonsSize40dp
val Size50dp = CommonsSize50dp
val Size55dp = CommonsSize55dp
val Size75dp = CommonsSize75dp
val Size100dp = CommonsSize100dp
val Size110dp = CommonsSize110dp
val Size165dp = CommonsSize165dp

val StdEndPadding = CommonsStdEndPadding
val HalfEndPadding = CommonsHalfEndPadding
val HalfStartPadding = CommonsHalfStartPadding
val StdStartPadding = CommonsStdStartPadding
val StdTopPadding = CommonsStdTopPadding
val HalfTopPadding = CommonsHalfTopPadding
val HalfHalfTopPadding = CommonsHalfHalfTopPadding

val HalfHalfVertPadding = CommonsHalfHalfVertPadding
val HalfHalfHorzModifier = CommonsHalfHalfHorzModifier

val HalfPadding = CommonsHalfPadding
val StdPadding = CommonsStdPadding
val BigPadding = CommonsBigPadding

val RowColSpacing = CommonsRowColSpacing
val RowColSpacing5dp = CommonsRowColSpacing5dp
val RowColSpacing10dp = CommonsRowColSpacing10dp

val HalfHorzPadding = CommonsHalfHorzPadding
val HalfVertPadding = CommonsHalfVertPadding

val HorzPadding = CommonsHorzPadding
val VertPadding = CommonsVertPadding

val HorzHalfVertPadding = CommonsHorzHalfVertPadding

val DoubleHorzPadding = CommonsDoubleHorzPadding
val DoubleVertPadding = CommonsDoubleVertPadding

val MaxWidthWithHorzPadding = CommonsMaxWidthWithHorzPadding

val Size5Modifier = CommonsSize5Modifier
val Size10Modifier = CommonsSize10Modifier
val Size14Modifier = CommonsSize14Modifier
val Size15Modifier = CommonsSize15Modifier
val Size16Modifier = CommonsSize16Modifier
val Size17Modifier = CommonsSize17Modifier
val Size18Modifier = CommonsSize18Modifier
val Size19Modifier = CommonsSize19Modifier
val Size20Modifier = CommonsSize20Modifier
val Size22Modifier = CommonsSize22Modifier
val Size24Modifier = CommonsSize24Modifier
val Size25Modifier = CommonsSize25Modifier
val Size26Modifier = CommonsSize26Modifier
val Size28Modifier = CommonsSize28Modifier
val Size30Modifier = CommonsSize30Modifier
val Size35Modifier = CommonsSize35Modifier
val Size39Modifier = CommonsSize39Modifier
val Size40Modifier = CommonsSize40Modifier
val Size50Modifier = CommonsSize50Modifier
val Size55Modifier = CommonsSize55Modifier
val Size75Modifier = CommonsSize75Modifier

val TinyBorders = CommonsTinyBorders
val NoSoTinyBorders = CommonsNoSoTinyBorders
val ReactionRowZapraiserWithPadding = CommonsReactionRowZapraiserWithPadding
val ReactionRowZapraiser = CommonsReactionRowZapraiser

val ReactionRowExpandButton = CommonsReactionRowExpandButton

val WidthAuthorPictureModifier = CommonsWidthAuthorPictureModifier
val WidthAuthorPictureModifierWithPadding = CommonsWidthAuthorPictureModifierWithPadding

val VideoReactionColumnPadding = CommonsVideoReactionColumnPadding

val DividerThickness = CommonsDividerThickness

val ReactionRowHeight = CommonsReactionRowHeight
val ReactionRowHeightWithPadding = CommonsReactionRowHeightWithPadding
val ReactionRowHeightChat = CommonsReactionRowHeightChat
val ReactionRowHeightChatMaxWidth = CommonsReactionRowHeightChatMaxWidth
val UserNameRowHeight = CommonsUserNameRowHeight
val UserNameMaxRowHeight = CommonsUserNameMaxRowHeight

val Height24dpModifier = CommonsHeight24dpModifier
val Height4dpModifier = CommonsHeight4dpModifier
val Height25Modifier = CommonsHeight25Modifier

val Height24dpFilledModifier = CommonsHeight24dpFilledModifier
val Height4dpFilledModifier = CommonsHeight4dpFilledModifier

val AccountPictureModifier = CommonsAccountPictureModifier
val HeaderPictureModifier = CommonsHeaderPictureModifier

val ShowMoreRelaysButtonIconButtonModifier = CommonsShowMoreRelaysButtonIconButtonModifier
val ShowMoreRelaysButtonIconModifier = CommonsShowMoreRelaysButtonIconModifier
val ShowMoreRelaysButtonBoxModifer = CommonsShowMoreRelaysButtonBoxModifer

val ChatBubbleMaxSizeModifier = CommonsChatBubbleMaxSizeModifier

val ModifierWidth3dp = CommonsModifierWidth3dp

val NotificationIconModifier = CommonsNotificationIconModifier
val NotificationIconModifierSmaller = CommonsNotificationIconModifierSmaller

val ZapPictureCommentModifier = CommonsZapPictureCommentModifier
val ChatHeadlineBorders = CommonsChatHeadlineBorders

val VolumeBottomIconSize = CommonsVolumeBottomIconSize
val PinBottomIconSize = CommonsPinBottomIconSize
val PlayIconSize = CommonsPlayIconSize
val NIP05IconSize = CommonsNIP05IconSize

val CashuCardBorders = CommonsCashuCardBorders

val EditFieldModifier = CommonsEditFieldModifier
val EditFieldTrailingIconModifier = CommonsEditFieldTrailingIconModifier

val ZeroPadding = CommonsZeroPadding
val HalfFeedPadding = CommonsHalfFeedPadding
val FeedPadding = CommonsFeedPadding
val ButtonPadding = CommonsButtonPadding

val ChatPaddingInnerQuoteModifier = CommonsChatPaddingInnerQuoteModifier
val ChatPaddingModifier = CommonsChatPaddingModifier

val profileContentHeaderModifier = commonsProfileContentHeaderModifier
val bannerModifier = commonsBannerModifier
val drawerSpacing = commonsDrawerSpacing

val IconRowTextModifier = CommonsIconRowTextModifier
val IconRowModifier = CommonsIconRowModifier

val Width16Space = CommonsWidth16Space

val emptyLineItemModifier = commonsEmptyLineItemModifier

val imageHeaderBannerSize = commonsImageHeaderBannerSize

val authorNotePictureForImageHeader = commonsAuthorNotePictureForImageHeader

val normalWithTopMarginNoteModifier = commonsNormalWithTopMarginNoteModifier

val boostedNoteModifier = commonsBoostedNoteModifier

val liveStreamTag = commonsLiveStreamTag

val chatAuthorBox = commonsChatAuthorBox
val chatAuthorImage = commonsChatAuthorImage
val AuthorInfoVideoFeed = CommonsAuthorInfoVideoFeed

val messageDetailsModifier = commonsMessageDetailsModifier
val messageBubbleLimits = commonsMessageBubbleLimits

val inlinePlaceholder = commonsInlinePlaceholder

val IncognitoIconModifier = CommonsIncognitoIconModifier
val IncognitoIconButtonModifier = CommonsIncognitoIconButtonModifier

val hashVerifierMark = commonsHashVerifierMark

val noteComposeRelayBox = commonsNoteComposeRelayBox

val previewCardImageModifier = commonsPreviewCardImageModifier

val reactionBox = commonsReactionBox

val ripple24dp = commonsRipple24dp

val defaultTweenDuration = commonsDefaultTweenDuration
val defaultTweenFloatSpec = commonsDefaultTweenFloatSpec
val defaultTweenIntOffsetSpec = commonsDefaultTweenIntOffsetSpec

val StreamingHeaderModifier = CommonsStreamingHeaderModifier

val PostKeyboard = CommonsPostKeyboard

val SettingsCategoryFirstModifier = CommonsSettingsCategoryFirstModifier
val SettingsCategorySpacingModifier = CommonsSettingsCategorySpacingModifier

val SettingsCategoryFirstWithHorzBorderModifier = CommonsSettingsCategoryFirstWithHorzBorderModifier
val SettingsCategorySpacingWithHorzBorderModifier = CommonsSettingsCategorySpacingWithHorzBorderModifier

val SquaredQuoteBorderModifier = CommonsSquaredQuoteBorderModifier
val FillWidthQuoteBorderModifier = CommonsFillWidthQuoteBorderModifier

val MediumRelayIconModifier = CommonsMediumRelayIconModifier

val LargeRelayIconModifier = CommonsLargeRelayIconModifier

val FollowSetImageModifier = CommonsFollowSetImageModifier

val SimpleImage75Modifier = CommonsSimpleImage75Modifier
val SimpleImage35Modifier = CommonsSimpleImage35Modifier

val SimpleImageBorder = CommonsSimpleImageBorder

val SimpleHeaderImage = CommonsSimpleHeaderImage

val BadgePictureModifier = CommonsBadgePictureModifier

val MaxWidthPaddingTop5dp = CommonsMaxWidthPaddingTop5dp

val VoiceHeightModifier = CommonsVoiceHeightModifier

val PaddingHorizontal12Modifier = CommonsPaddingHorizontal12Modifier

val QuickActionPopupShadow = CommonsQuickActionPopupShadow

val SpacedBy2dp = CommonsSpacedBy2dp
val SpacedBy3dp = CommonsSpacedBy3dp
val SpacedBy5dp = CommonsSpacedBy5dp
val SpacedBy10dp = CommonsSpacedBy10dp
val SpacedBy55dp = CommonsSpacedBy55dp

val PopupUpEffect = CommonsPopupUpEffect

val Size50ModifierOffset10 = CommonsSize50ModifierOffset10

val SuggestionListDefaultHeightChat = CommonsSuggestionListDefaultHeightChat
val SuggestionListDefaultHeightPage = CommonsSuggestionListDefaultHeightPage

val FollowPackHeaderModifier = CommonsFollowPackHeaderModifier

val Size22ModifierWith4Padding = CommonsSize22ModifierWith4Padding

val TextStyleBottomNavBar = CommonsTextStyleBottomNavBar
