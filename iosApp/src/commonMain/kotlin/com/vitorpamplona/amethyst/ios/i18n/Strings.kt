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
package com.vitorpamplona.amethyst.ios.i18n

/**
 * Centralized string resources for the iOS app.
 *
 * This provides a foundation for i18n/l10n. When Crowdin or compose-resources
 * string extraction is set up, these can be replaced with proper resource IDs.
 *
 * For now, strings are organized by screen/feature for easy discovery.
 */
object Strings {
    // ── General ──
    const val APP_NAME = "Amethyst"
    const val BACK = "Back"
    const val CANCEL = "Cancel"
    const val CONFIRM = "Confirm"
    const val SAVE = "Save"
    const val DELETE = "Delete"
    const val EDIT = "Edit"
    const val COPY = "Copy"
    const val SHARE = "Share"
    const val LOADING = "Loading..."
    const val ERROR = "Error"
    const val RETRY = "Retry"
    const val DONE = "Done"

    // ── Tabs ──
    const val TAB_FEED = "Feed"
    const val TAB_SEARCH = "Search"
    const val TAB_NOTIFICATIONS = "Notifications"
    const val TAB_MESSAGES = "Messages"
    const val TAB_PROFILE = "Profile"

    // ── Feed ──
    const val FEED_GLOBAL = "Global"
    const val FEED_FOLLOWING = "Following"
    const val FEED_HASHTAGS = "Hashtags"
    const val FEED_TRENDING = "Trending"
    const val FEED_POLLS = "Polls"
    const val FEED_CALENDAR = "Calendar"
    const val FEED_LIVE = "Live"
    const val FEED_MARKETPLACE = "Marketplace"
    const val FEED_EMPTY = "No notes to show"
    const val FEED_LOADING = "Loading feed..."

    // ── Compose ──
    const val COMPOSE_TITLE = "New Note"
    const val COMPOSE_PLACEHOLDER = "What's on your mind?"
    const val COMPOSE_PUBLISH = "Publish"
    const val COMPOSE_REPLY_TO = "Reply to"
    const val COMPOSE_ADD_IMAGE = "Add image"
    const val COMPOSE_DRAFT_SAVED = "Draft saved"

    // ── Note actions ──
    const val ACTION_REPLY = "Reply"
    const val ACTION_REPOST = "Repost"
    const val ACTION_LIKE = "Like"
    const val ACTION_ZAP = "Zap"
    const val ACTION_BOOKMARK = "Bookmark"
    const val ACTION_COPY_NOTE_ID = "Copy note ID"
    const val ACTION_COPY_NOTE_TEXT = "Copy note text"
    const val ACTION_COPY_AUTHOR = "Copy author npub"
    const val ACTION_MUTE_USER = "Mute user"
    const val ACTION_REPORT = "Report"
    const val ACTION_DELETE = "Delete"
    const val ACTION_EDIT = "Edit note"

    // ── NIP-05 ──
    const val NIP05_VERIFIED = "Verified"
    const val NIP05_CHECKING = "Checking..."
    const val NIP05_FAILED = "Verification failed"

    // ── Zaps ──
    const val ZAP_TITLE = "⚡ Zap"
    const val ZAP_CHOOSE_AMOUNT = "Choose amount (sats)"
    const val ZAP_CUSTOM_AMOUNT = "Custom amount"
    const val ZAP_ENTER_SATS = "Enter sats"
    const val ZAP_SENDING = "Sending..."
    const val ZAP_SUCCESS = "Zap sent!"
    const val ZAP_NWC_NOT_CONFIGURED = "Set up Wallet Connect in Settings to send zaps"

    // ── Settings ──
    const val SETTINGS_TITLE = "Settings"
    const val SETTINGS_ACCOUNT = "Account"
    const val SETTINGS_RELAY_MANAGEMENT = "Relay Management"
    const val SETTINGS_BOOKMARKS = "Bookmarks"
    const val SETTINGS_MUTE_LIST = "Mute List"
    const val SETTINGS_HASHTAG_FOLLOW = "Followed Hashtags"
    const val SETTINGS_COMMUNITIES = "Communities"
    const val SETTINGS_RELAY_GROUPS = "Relay Groups"
    const val SETTINGS_RELAY_SETS = "Relay Sets"
    const val SETTINGS_PEOPLE_LISTS = "People Lists"
    const val SETTINGS_APPEARANCE = "Appearance"
    const val SETTINGS_NWC = "Wallet Connect"
    const val SETTINGS_KEY_BACKUP = "Key Backup"
    const val SETTINGS_LOGOUT = "Logout"

    // ── Login ──
    const val LOGIN_TITLE = "Welcome to Amethyst"
    const val LOGIN_NSEC_PLACEHOLDER = "nsec, npub, hex key, or mnemonic"
    const val LOGIN_BUTTON = "Login"
    const val LOGIN_CREATE_ACCOUNT = "Create Account"
    const val LOGIN_SCAN_QR = "Scan QR"
    const val LOGIN_BUNKER = "NIP-46 Bunker"

    // ── Profile ──
    const val PROFILE_EDIT = "Edit Profile"
    const val PROFILE_FOLLOW = "Follow"
    const val PROFILE_UNFOLLOW = "Unfollow"
    const val PROFILE_FOLLOWERS = "Followers"
    const val PROFILE_FOLLOWING = "Following"

    // ── DMs ──
    const val DM_TITLE = "Messages"
    const val DM_NEW_MESSAGE = "New Message"
    const val DM_PLACEHOLDER = "Type a message..."
    const val DM_SEND = "Send"

    // ── Search ──
    const val SEARCH_PLACEHOLDER = "Search users, notes, hashtags..."
    const val SEARCH_PEOPLE = "People"
    const val SEARCH_NOTES = "Notes"

    // ── Errors ──
    const val ERROR_NETWORK = "Network error"
    const val ERROR_NOTE_NOT_FOUND = "Note not found"
    const val ERROR_RELAY_CONNECTION = "Relay connection failed"

    // ── Time ──
    const val TIME_NOW = "now"
    const val TIME_SECONDS_AGO = "s ago"
    const val TIME_MINUTES_AGO = "m ago"
    const val TIME_HOURS_AGO = "h ago"
    const val TIME_DAYS_AGO = "d ago"
    const val TIME_WEEKS_AGO = "w ago"
}
