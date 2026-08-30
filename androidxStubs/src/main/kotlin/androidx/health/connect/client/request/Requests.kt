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
package androidx.health.connect.client.request

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.time.TimeRangeFilter
import kotlin.reflect.KClass

/** JVM stand-ins for the Health Connect request types; see HealthConnectClient. */
class ReadRecordsRequest<T : Record>(
    val recordType: KClass<T>,
    val timeRangeFilter: TimeRangeFilter,
    val dataOriginFilter: Set<DataOrigin> = emptySet(),
    val ascendingOrder: Boolean = true,
    val pageSize: Int = 1000,
    val pageToken: String? = null,
)

class AggregateRequest(
    val metrics: Set<AggregateMetric<*>>,
    val timeRangeFilter: TimeRangeFilter,
    val dataOriginFilter: Set<DataOrigin> = emptySet(),
)
