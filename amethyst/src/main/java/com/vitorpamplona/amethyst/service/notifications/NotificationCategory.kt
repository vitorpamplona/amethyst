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
package com.vitorpamplona.amethyst.service.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Logical grouping of notification channels, shown as a section header in the
 * Android system-settings page (API 26+). Lets the user silence a whole family
 * (e.g. all Social notifications) with one switch, in addition to the per-kind
 * channel switches.
 */
enum class NotifChannelGroup(
    val id: String,
    @param:StringRes val nameRes: Int,
) {
    MESSAGES("com.vitorpamplona.amethyst.group.messages", R.string.app_notification_group_messages),
    SOCIAL("com.vitorpamplona.amethyst.group.social", R.string.app_notification_group_social),
    PAYMENTS("com.vitorpamplona.amethyst.group.payments", R.string.app_notification_group_payments),
    CONTENT("com.vitorpamplona.amethyst.group.content", R.string.app_notification_group_content),
    DEVELOPER("com.vitorpamplona.amethyst.group.developer", R.string.app_notification_group_developer),
    GAMES("com.vitorpamplona.amethyst.group.games", R.string.app_notification_group_games),
}

/**
 * The visual + behavioral identity of one notification kind. Each entry owns:
 *
 *  - the [NotificationChannel] it posts on (importance, name, description) — the
 *    unit Android lets the user silence/customize. Existing channel ids are
 *    reused verbatim so we never orphan a user's per-channel settings.
 *  - the accent [color] (`setColor`) and monochrome status-bar [smallIcon] that
 *    make the kind recognizable in the shade before it's read.
 *  - the [group] it bundles under (`setGroup`) and the [summaryId] of that
 *    group's summary notification.
 *  - the [channelGroup] it sits inside in system settings.
 *  - the [settingsIcon] rendered next to it in the in-app settings screen.
 *
 * This replaces the six ad-hoc `getOrCreate*Channel` helpers with one
 * table-driven definition every renderer reads from.
 */
enum class NotificationCategory(
    @param:StringRes val channelIdRes: Int,
    @param:StringRes val channelNameRes: Int,
    @param:StringRes val channelDescriptionRes: Int,
    @param:StringRes val summaryTextRes: Int,
    val importance: Int,
    val color: Int,
    @param:DrawableRes val smallIcon: Int,
    val settingsIcon: MaterialSymbol,
    val channelGroup: NotifChannelGroup,
    val group: String,
    val summaryId: Int,
    /** Full-surface color tint — reserved for the highest-signal kinds. */
    val colorized: Boolean = false,
) {
    DIRECT_MESSAGE(
        channelIdRes = R.string.app_notification_dms_channel_id,
        channelNameRes = R.string.app_notification_dms_channel_name,
        channelDescriptionRes = R.string.app_notification_dms_channel_description,
        summaryTextRes = R.string.app_notification_dms_summary,
        importance = NotificationManager.IMPORTANCE_HIGH,
        color = 0xFF2196F3.toInt(), // blue
        smallIcon = R.drawable.ic_notif_message,
        settingsIcon = MaterialSymbols.Mail,
        channelGroup = NotifChannelGroup.MESSAGES,
        group = "com.vitorpamplona.amethyst.DM_NOTIFICATION",
        summaryId = 0x10000,
    ),
    REPLY(
        channelIdRes = R.string.app_notification_replies_channel_id,
        channelNameRes = R.string.app_notification_replies_channel_name,
        channelDescriptionRes = R.string.app_notification_replies_channel_description,
        summaryTextRes = R.string.app_notification_replies_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFF7C4DFF.toInt(), // deep purple
        smallIcon = R.drawable.ic_notif_reply,
        settingsIcon = MaterialSymbols.Chat,
        channelGroup = NotifChannelGroup.SOCIAL,
        // Replies group per-thread; renderers override group + summaryId. This
        // base is only a fallback when no thread root is known.
        group = "com.vitorpamplona.amethyst.REPLY_NOTIFICATION",
        summaryId = 0x50000,
    ),
    MENTION(
        channelIdRes = R.string.app_notification_mentions_channel_id,
        channelNameRes = R.string.app_notification_mentions_channel_name,
        channelDescriptionRes = R.string.app_notification_mentions_channel_description,
        summaryTextRes = R.string.app_notification_mentions_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFF9C27B0.toInt(), // purple
        smallIcon = R.drawable.ic_notif_mention,
        settingsIcon = MaterialSymbols.AlternateEmail,
        channelGroup = NotifChannelGroup.SOCIAL,
        group = "com.vitorpamplona.amethyst.MENTION_NOTIFICATION",
        summaryId = 0x60000,
    ),
    REACTION(
        channelIdRes = R.string.app_notification_reactions_channel_id,
        channelNameRes = R.string.app_notification_reactions_channel_name,
        channelDescriptionRes = R.string.app_notification_reactions_channel_description,
        summaryTextRes = R.string.app_notification_reactions_summary,
        importance = NotificationManager.IMPORTANCE_LOW,
        color = 0xFFE91E63.toInt(), // pink / heart
        smallIcon = R.drawable.ic_notif_reaction,
        settingsIcon = MaterialSymbols.Favorite,
        channelGroup = NotifChannelGroup.SOCIAL,
        group = "com.vitorpamplona.amethyst.REACTION_NOTIFICATION",
        summaryId = 0x40000,
    ),
    REPOST(
        channelIdRes = R.string.app_notification_reposts_channel_id,
        channelNameRes = R.string.app_notification_reposts_channel_name,
        channelDescriptionRes = R.string.app_notification_reposts_channel_description,
        summaryTextRes = R.string.app_notification_reposts_summary,
        importance = NotificationManager.IMPORTANCE_LOW,
        color = 0xFF4CAF50.toInt(), // green
        smallIcon = R.drawable.ic_notif_repost,
        settingsIcon = MaterialSymbols.Sync,
        channelGroup = NotifChannelGroup.SOCIAL,
        group = "com.vitorpamplona.amethyst.REPOST_NOTIFICATION",
        summaryId = 0x70000,
    ),
    ZAP(
        channelIdRes = R.string.app_notification_zaps_channel_id,
        channelNameRes = R.string.app_notification_zaps_channel_name,
        channelDescriptionRes = R.string.app_notification_zaps_channel_description,
        summaryTextRes = R.string.app_notification_zaps_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFFF7931A.toInt(), // bitcoin orange
        smallIcon = R.drawable.ic_notif_zap,
        settingsIcon = MaterialSymbols.Bolt,
        channelGroup = NotifChannelGroup.PAYMENTS,
        group = "com.vitorpamplona.amethyst.ZAP_NOTIFICATION",
        summaryId = 0x20000,
        colorized = true,
    ),
    MEDIA(
        channelIdRes = R.string.app_notification_media_channel_id,
        channelNameRes = R.string.app_notification_media_channel_name,
        channelDescriptionRes = R.string.app_notification_media_channel_description,
        summaryTextRes = R.string.app_notification_media_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFF00BCD4.toInt(), // cyan
        smallIcon = R.drawable.ic_notif_media,
        settingsIcon = MaterialSymbols.Image,
        channelGroup = NotifChannelGroup.CONTENT,
        group = "com.vitorpamplona.amethyst.MEDIA_NOTIFICATION",
        summaryId = 0x80000,
    ),
    ARTICLE(
        channelIdRes = R.string.app_notification_articles_channel_id,
        channelNameRes = R.string.app_notification_articles_channel_name,
        channelDescriptionRes = R.string.app_notification_articles_channel_description,
        summaryTextRes = R.string.app_notification_articles_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFF3F51B5.toInt(), // indigo
        smallIcon = R.drawable.ic_notif_article,
        settingsIcon = MaterialSymbols.Description,
        channelGroup = NotifChannelGroup.CONTENT,
        group = "com.vitorpamplona.amethyst.ARTICLE_NOTIFICATION",
        summaryId = 0x90000,
    ),
    CODE(
        channelIdRes = R.string.app_notification_code_channel_id,
        channelNameRes = R.string.app_notification_code_channel_name,
        channelDescriptionRes = R.string.app_notification_code_channel_description,
        summaryTextRes = R.string.app_notification_code_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFF607D8B.toInt(), // slate
        smallIcon = R.drawable.ic_notif_code,
        settingsIcon = MaterialSymbols.Code,
        channelGroup = NotifChannelGroup.DEVELOPER,
        group = "com.vitorpamplona.amethyst.CODE_NOTIFICATION",
        summaryId = 0xA0000,
    ),
    BADGE(
        channelIdRes = R.string.app_notification_badges_channel_id,
        channelNameRes = R.string.app_notification_badges_channel_name,
        channelDescriptionRes = R.string.app_notification_badges_channel_description,
        summaryTextRes = R.string.app_notification_badges_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFFFFC107.toInt(), // amber / gold
        smallIcon = R.drawable.ic_notif_badge,
        settingsIcon = MaterialSymbols.MilitaryTech,
        channelGroup = NotifChannelGroup.SOCIAL,
        group = "com.vitorpamplona.amethyst.BADGE_NOTIFICATION",
        summaryId = 0xB0000,
    ),
    CHESS(
        channelIdRes = R.string.app_notification_chess_channel_id,
        channelNameRes = R.string.app_notification_chess_channel_name,
        channelDescriptionRes = R.string.app_notification_chess_channel_description,
        summaryTextRes = R.string.app_notification_chess_summary,
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        color = 0xFF795548.toInt(), // brown
        smallIcon = R.drawable.ic_notif_chess,
        settingsIcon = MaterialSymbols.ChessKnight,
        channelGroup = NotifChannelGroup.GAMES,
        group = "com.vitorpamplona.amethyst.CHESS_NOTIFICATION",
        summaryId = 0x30000,
    ),
    ;

    fun channelId(context: Context): String = stringRes(context, channelIdRes)

    /**
     * Idempotently creates this category's channel group and channel. Safe to
     * call before every post — Android no-ops when the channel already exists
     * (it never downgrades a channel the user has customized). Returns the
     * channel id to post on.
     */
    fun ensureChannel(context: Context): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(channelGroup.id, stringRes(context, channelGroup.nameRes)),
        )
        val id = channelId(context)
        val channel =
            NotificationChannel(id, stringRes(context, channelNameRes), importance).apply {
                description = stringRes(context, channelDescriptionRes)
                group = channelGroup.id
            }
        nm.createNotificationChannel(channel)
        return id
    }
}
