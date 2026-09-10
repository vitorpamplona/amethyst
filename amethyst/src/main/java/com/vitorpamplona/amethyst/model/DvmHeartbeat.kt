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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** The cache slot a DVM's heartbeat lives in: the announcement's own address, kind 11998. */
fun LocalCache.dvmHeartbeatOf(appDef: AppDefinitionEvent): DvmHeartbeatEvent? = getAddressableNoteIfExists(Address(DvmHeartbeatEvent.KIND, appDef.pubKey, appDef.dTag()))?.event as? DvmHeartbeatEvent

/** A DVM counts as alive only if its latest heartbeat is at most 420s old. */
fun LocalCache.hasFreshDvmHeartbeat(
    appDef: AppDefinitionEvent,
    now: Long = TimeUtils.now(),
): Boolean = dvmHeartbeatOf(appDef)?.isFreshAt(now) == true
