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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayReqRefusals
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage

/*
 * What a relay's free-text CLOSED, NOTICE or NEG-ERR message means, in one place.
 *
 * Relays word refusals however their authors chose, so every reading here is a match on
 * wording seen from real relays. Each one is pinned by RelayMessagesTest, which holds the
 * strings as they were captured; a new wording goes there first. Keep the matchers narrow: a
 * miss costs a timeout or a retry, while a false match can end a query a relay would answer.
 */

// ---- Subscription and rate limits (AdaptiveRelayLimiter's actuators) ----

/** True when [message] complains about how many subscriptions are open at once. */
internal fun isSubscriptionLimitMessage(message: String): Boolean {
    val t = message.lowercase()
    return SUB_LIMIT_MARKERS.any { it in t }
}

/** True when [message] complains about how fast subscriptions are sent. */
internal fun isRateLimitMessage(message: String): Boolean {
    val t = message.lowercase()
    return RATE_LIMIT_MARKERS.any { it in t }
}

// A cap on how many subscriptions may be OPEN at once. Fix: fewer
// concurrent subs (demote the concurrency cap).
private val SUB_LIMIT_MARKERS =
    listOf(
        "too many concurrent",
        "concurrent req",
        "too many subscription",
        "number of subscriptions",
        "subscriptions exceeds",
        "subscription limit",
        "subscription count",
        "maximum concurrent subscription",
        "max subscription",
        "too many req",
    )

// Too many subscription CHANGES per second. Fix: space the REQs out in
// time (a per-relay min interval), not fewer concurrent subs.
private val RATE_LIMIT_MARKERS =
    listOf(
        "rate-limit",
        "rate limit",
        "ratelimit",
        "too many messages",
        "too many requests",
        "burst exhausted",
        "throttl",
        "slow down",
    )

// ---- A refusal of the REQ's event limit, and throttling ----

/**
 * Reads a `CLOSED` reason as a refusal of the `REQ`'s `limit` (`blocked: limit too high: 5000
 * (max 500)`, `invalid: limitation.max_limit 1000`, `restricted: limit must not exceed 500`)
 * and returns the largest limit worth asking for instead: the relay's stated max when the
 * message names one below [sent], otherwise half of [sent]. Null when the message is not
 * about the event limit or [sent] cannot go any lower. Relays say "limit" about other things
 * too, and halving the event limit fixes none of them: a rate limit is throttling
 * ([isThrottleMessage]), and a cap on open subscriptions ("number of subscriptions exceeds
 * limit") or on filters per REQ is [AdaptiveRelayLimiter]'s and [RelayReqRefusals]'s to handle.
 */
internal fun lowerLimitAfterRefusal(
    message: String?,
    sent: Int,
): Int? {
    if (message == null || sent <= 1) return null
    if (!message.contains("limit", ignoreCase = true)) return null
    if (isThrottleMessage(message)) return null
    if (isSubscriptionLimitMessage(message)) return null
    if (RelayReqRefusals.parseMaxFilters(message) != null || TOO_MANY_FILTERS.containsMatchIn(message)) return null
    val stated = statedLimit(message)
    return if (stated != null && stated in 1 until sent) stated else sent / 2
}

/** The cap a limit refusal states (`max 500`, `exceeds 500`), if it states one. */
private fun statedLimit(message: String): Int? =
    (STATED_MAX.find(message) ?: STATED_CEILING.find(message))
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

/**
 * [lowerLimitAfterRefusal] for a `REQ` of several filters, whose refusal does not say which
 * filter it was about: the limits to re-ask with, or null if [message] is not a refusal of a
 * limit or nothing is left to lower. With a stated max, only the filters above it come down,
 * to it: `[1000, 100]` refused with `(max 500)` is re-asked as `[500, 100]`, not `[500, 50]`.
 * With none stated, or every filter already at or under it, the largest is halved.
 */
internal fun lowerLimitsAfterRefusal(
    message: String?,
    limits: List<Int?>,
): List<Int?>? {
    val largest = limits.filterNotNull().maxOrNull() ?: return null
    if (message == null || lowerLimitAfterRefusal(message, largest) == null) return null
    val stated = statedLimit(message)
    if (stated != null && stated >= 1) {
        val capped = limits.map { limit -> if (limit != null && limit > stated) stated else limit }
        if (capped != limits) return capped
    }
    return limits.map { limit -> if (limit == largest) limit / 2 else limit }
}

/**
 * True when a relay's message says it is throttling this client rather than refusing what was
 * asked: a `rate-limited:` prefix, rate-limit wording ([isRateLimitMessage]), or a spent read
 * budget (`ERROR: read bandwidth budget exhausted (… bytes/min per IP) (retry in 30ms)`).
 * Waiting helps; asking for less does not.
 */
internal fun isThrottleMessage(message: String): Boolean =
    MachineReadablePrefix.parse(message) == MachineReadablePrefix.RATE_LIMITED ||
        isRateLimitMessage(message) ||
        BUDGET_EXHAUSTED.containsMatchIn(message)

private val BUDGET_EXHAUSTED = Regex("budget (?:is )?exhausted|retry in \\d", RegexOption.IGNORE_CASE)

private val TOO_MANY_FILTERS = Regex("(?:too many|number of|max(?:imum)?) filters", RegexOption.IGNORE_CASE)

// "limit must not exceed 500", "limit exceeds 200": the cap without "max".
private val STATED_CEILING = Regex("exceeds?\\D{0,12}?(\\d+)", RegexOption.IGNORE_CASE)

private val STATED_MAX = Regex("max(?:imum)?\\D{0,12}?(\\d+)", RegexOption.IGNORE_CASE)

// ---- NIP-45 COUNT not supported ----

/**
 * A NOTICE that refuses the COUNT verb itself, as relays without NIP-45 word it:
 * `ERROR: bad msg: unknown cmd`, `Unknown message type: COUNT`, `ERROR: bad msg: invalid
 * message: {'message_type': ['Invalid enum value COUNT']}`, `invalid message`. A NOTICE
 * names no subscription, so this stays narrow: a
 * relay that does speak NIP-45 never says any of these to a well-formed COUNT, and a miss
 * only costs the idle window it always cost. A NOTICE that names `COUNT` counts only when it
 * also says the verb is unknown or unsupported: "too many concurrent COUNT requests" is about
 * one query on a relay that counts, and must not end every COUNT open to it.
 */
internal fun isCountRejectionNotice(message: String): Boolean =
    (message.contains("COUNT") && COUNT_UNKNOWN.containsMatchIn(message)) ||
        message.contains("unknown cmd", ignoreCase = true) ||
        message.trim().equals("invalid message", ignoreCase = true)

/** Words that make a NOTICE naming `COUNT` a refusal of the verb, not a remark about one query. */
private val COUNT_UNKNOWN = Regex("unknown|invalid|unsupported|not supported", RegexOption.IGNORE_CASE)

// ---- NIP-77 negentropy not supported, or the set too large ----

/**
 * strfry sends `["NEG-ERR", subId, "blocked: query matches too many records (N > M)"]`
 * (and, older, `"too many query results"`) when a NEG-OPEN matches more than
 * `relay__negentropy__maxSyncEvents`. Match that, plus equivalent "result set too
 * large" wording from other relays, so it triggers the window split rather than
 * aborting.
 *
 * This MUST stay narrow, and specifically must key on the *result-set-size* meaning:
 * only a genuine set-too-large signal may be treated as overflow, because overflow
 * triggers `created_at` window-splitting. Two ways a too-lax matcher goes wrong:
 *  - A hard refusal (negentropy disabled, `auth-required`, a ban) that happens to
 *    contain a matched word would split, re-open, be refused again, and fan out
 *    across the whole `created_at` range instead of failing over to paging.
 *  - A *rate/quota* error — `"too many requests"`, `"too many concurrent
 *    subscriptions"` — is especially dangerous: it does not shrink as the window
 *    shrinks, so every split re-triggers it and the splitter walks toward 1-second
 *    leaves, queueing up to ~2^31 windows (an OOM + relay-hammering storm) before
 *    any window is small enough to give up on. That is why the bare `"too many"` /
 *    `"too large"` substrings were replaced with result-set-qualified phrases:
 *    `"too many requests"` no longer looks like overflow, so it fails over to paging.
 *
 * [reconcileWindows] also caps the total window count as a wording-independent
 * backstop, so a novel overflow-looking-but-not-shrinking error can never storm.
 */
internal fun isOverflow(reason: String): Boolean = NegErrMessage.isOverflow(reason)

/**
 * A relay that advertises NIP-77 but refuses it at runtime signals the refusal with
 * a connection-level `NOTICE` (which carries no subId) rather than a subId-addressed
 * `NEG-ERR`. Observed against public relays that all list NIP-77 in NIP-11:
 *   - strfry with negentropy off: `"ERROR: bad msg: negentropy disabled"`
 *   - purplepag.es (no NEG envelope): `"failed to parse envelope: unknown envelope label"`
 *
 * We only treat a NOTICE as our negentropy rejection when it plausibly refers to the
 * NEG exchange (this matcher) AND it arrives before this session's first valid NEG
 * frame — so an unrelated NOTICE on a healthy relay mid-reconcile can never abort an
 * otherwise-progressing sync. This is only a *fast path*: it is deliberately narrow
 * (a false positive fails the window over to paging), and anything it misses is still
 * caught by the idle watchdog, which — since NOTICE/CLOSED no longer bump the clock —
 * fires once a refusing relay goes silent after its notice. So prefer under-matching
 * here. Both matched phrases are ones a relay that actually speaks NIP-77 would never
 * emit for a well-formed client (quartz only sends valid frames): "negentropy" names
 * the feature; "unknown envelope" is the parse failure of a relay that never
 * implemented the NEG-OPEN envelope. Broad substrings like a bare "envelope" or the
 * echoed command names are excluded — an unrelated parse/rate NOTICE could carry them.
 */
internal fun isNegentropyRejectionNotice(reason: String): Boolean =
    reason.contains("negentropy", ignoreCase = true) ||
        reason.contains("unknown envelope", ignoreCase = true)
