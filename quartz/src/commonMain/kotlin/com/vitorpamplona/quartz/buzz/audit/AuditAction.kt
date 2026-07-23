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
package com.vitorpamplona.quartz.buzz.audit

/**
 * The action recorded by a Buzz audit entry, mirroring `AuditAction` in
 * `buzz-audit/src/action.rs` (snake_case wire values). Unknown values map to [UNKNOWN] so a
 * future action string round-trips rather than being dropped.
 */
enum class AuditAction(
    val code: String,
) {
    EVENT_CREATED("event_created"),
    EVENT_DELETED("event_deleted"),
    CHANNEL_CREATED("channel_created"),
    CHANNEL_UPDATED("channel_updated"),
    CHANNEL_DELETED("channel_deleted"),
    MEMBER_ADDED("member_added"),
    MEMBER_REMOVED("member_removed"),
    AUTH_SUCCESS("auth_success"),
    AUTH_FAILURE("auth_failure"),
    RATE_LIMIT_EXCEEDED("rate_limit_exceeded"),
    MEDIA_UPLOADED("media_uploaded"),

    /** An unrecognized action string from a future/extended implementation. */
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWire(value: String): AuditAction = entries.firstOrNull { it.code == value } ?: UNKNOWN
    }
}
