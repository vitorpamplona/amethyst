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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * How far ahead of this device's clock an event may be dated and still be read as now: the
 * minutes two devices' clocks drift apart, not a date years out.
 */
const val MAX_CLOCK_SKEW_SECS = 5 * 60L

/**
 * A stored last-read marker as far as it can be trusted. One further ahead of the clock than
 * [MAX_CLOCK_SKEW_SECS] was carried there by an event dated in the future (a reply from 2030 the
 * Notifications tab used to show), and since a marker only moves forward it would mark every real
 * message read, and keep the badge off, until that date. Such a marker reads as [now].
 */
fun trustedLastRead(
    stored: Long,
    now: Long = TimeUtils.now(),
): Long = if (stored > now + MAX_CLOCK_SKEW_SECS) now else stored
