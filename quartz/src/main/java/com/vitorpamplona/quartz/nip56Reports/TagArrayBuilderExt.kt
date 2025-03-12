/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip56Reports

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip56Reports.tags.HashSha256Tag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAddressTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedAuthorTag
import com.vitorpamplona.quartz.nip56Reports.tags.ReportedEventTag
import com.vitorpamplona.quartz.nip56Reports.tags.ServerTag

fun TagArrayBuilder<ReportEvent>.event(
    eventId: HexKey,
    reportType: ReportType,
) = addUnique(ReportedEventTag.assemble(eventId, reportType))

fun TagArrayBuilder<ReportEvent>.address(
    address: Address,
    reportType: ReportType,
) = addUnique(ReportedAddressTag.assemble(address, reportType))

fun TagArrayBuilder<ReportEvent>.user(
    pubkey: HexKey,
    reportType: ReportType,
) = addUnique(ReportedAuthorTag.assemble(pubkey, reportType))

fun TagArrayBuilder<ReportEvent>.hash(
    x: String,
    reportType: ReportType,
) = addUnique(HashSha256Tag.assemble(x, reportType))

fun TagArrayBuilder<ReportEvent>.server(url: String) = addUnique(ServerTag.assemble(url))
