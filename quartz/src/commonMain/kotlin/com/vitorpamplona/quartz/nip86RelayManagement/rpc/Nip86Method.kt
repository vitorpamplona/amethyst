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
package com.vitorpamplona.quartz.nip86RelayManagement.rpc

object Nip86Method {
    const val SUPPORTED_METHODS = "supportedmethods"
    const val BAN_PUBKEY = "banpubkey"
    const val UNBAN_PUBKEY = "unbanpubkey"
    const val LIST_BANNED_PUBKEYS = "listbannedpubkeys"
    const val ALLOW_PUBKEY = "allowpubkey"
    const val UNALLOW_PUBKEY = "unallowpubkey"
    const val LIST_ALLOWED_PUBKEYS = "listallowedpubkeys"
    const val CREATE_ROLE = "createrole"
    const val EDIT_ROLE = "editrole"
    const val DELETE_ROLE = "deleterole"
    const val ASSIGN_ROLE = "assignrole"
    const val UNASSIGN_ROLE = "unassignrole"
    const val LIST_CLAIMS = "listclaims"
    const val CREATE_CLAIM = "createclaim"
    const val DELETE_CLAIM = "deleteclaim"
    const val LIST_EVENTS_NEEDING_MODERATION = "listeventsneedingmoderation"

    /** Adds an event to the relay's allow list (and removes it from the ban list). */
    const val ALLOW_EVENT = "allowevent"

    /** Removes an event from the allow list without banning it. */
    const val UNALLOW_EVENT = "unallowevent"

    /** Bans an event (and removes it from the allow list). */
    const val BAN_EVENT = "banevent"

    /** Removes an event from the ban list without allow-listing it. */
    const val UNBAN_EVENT = "unbanevent"
    const val LIST_BANNED_EVENTS = "listbannedevents"
    const val LIST_ALLOWED_EVENTS = "listallowedevents"
    const val CHANGE_RELAY_NAME = "changerelayname"
    const val CHANGE_RELAY_DESCRIPTION = "changerelaydescription"
    const val CHANGE_RELAY_ICON = "changerelayicon"
    const val ALLOW_KIND = "allowkind"
    const val DISALLOW_KIND = "disallowkind"
    const val LIST_ALLOWED_KINDS = "listallowedkinds"
    const val LIST_DISALLOWED_KINDS = "listdisallowedkinds"
    const val BLOCK_IP = "blockip"
    const val UNBLOCK_IP = "unblockip"
    const val LIST_BLOCKED_IPS = "listblockedips"
}
