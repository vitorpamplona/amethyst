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
    const val LIST_EVENTS_NEEDING_MODERATION = "listeventsneedingmoderation"
    const val ALLOW_EVENT = "allowevent"
    const val BAN_EVENT = "banevent"
    const val LIST_BANNED_EVENTS = "listbannedevents"
    const val CHANGE_RELAY_NAME = "changerelayname"
    const val CHANGE_RELAY_DESCRIPTION = "changerelaydescription"
    const val CHANGE_RELAY_ICON = "changerelayicon"
    const val ALLOW_KIND = "allowkind"
    const val DISALLOW_KIND = "disallowkind"
    const val LIST_ALLOWED_KINDS = "listallowedkinds"
    const val BLOCK_IP = "blockip"
    const val UNBLOCK_IP = "unblockip"
    const val LIST_BLOCKED_IPS = "listblockedips"
}
