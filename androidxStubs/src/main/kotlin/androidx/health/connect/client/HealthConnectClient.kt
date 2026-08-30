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
package androidx.health.connect.client

import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import com.vitorpamplona.amethyst.stubs.PlatformGaps

/**
 * JVM stand-ins for androidx.health.connect.client.
 *
 * Health Connect is the Android system service that every health source on the
 * phone — Samsung Health, Google Fit, Fitbit, Garmin, Strava — writes into. It
 * is a phone-side aggregator backed by an OS provider app; there is no desktop
 * counterpart, and there is unlikely to ever be one. A desktop build that
 * wanted workout suggestions would have to go to the sources' own web APIs,
 * which is a different feature with different auth, not a port of this.
 *
 * So the honest answer is the one the platform itself gives on a device with no
 * provider installed: [getSdkStatus] returns [SDK_UNAVAILABLE]. The app already
 * branches on exactly that — `HealthConnectManager.isAvailable` gates every
 * read — so the workout carousel stays hidden and nothing further is called.
 *
 * Everything past that gate throws rather than returning empty. An empty
 * reading would say "you have not worked out", which is a claim about the
 * user's data that this build has no basis to make.
 */
class HealthConnectClient private constructor() {
    val permissionController: PermissionController = PermissionController()

    suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> = unavailable()

    suspend fun aggregate(request: AggregateRequest): AggregationResult = unavailable()

    companion object {
        const val SDK_UNAVAILABLE = 1
        const val SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED = 2
        const val SDK_AVAILABLE = 3

        /**
         * Not "update the provider": there is no provider to update, and the
         * app shows a different, actionable message for each.
         */
        fun getSdkStatus(
            context: Context,
            providerPackageName: String = "",
        ): Int = SDK_UNAVAILABLE

        fun getOrCreate(
            context: Context,
            providerPackageName: String = "",
        ): HealthConnectClient = unavailable()

        internal fun unavailable(): Nothing {
            PlatformGaps.unavailable(
                "HealthConnect",
                "Health Connect is an Android system service backed by an on-device provider app. " +
                    "A desktop build would have to talk to each source's own web API instead, which " +
                    "is a different feature, not a port of this one.",
            )
            throw IllegalStateException("Health Connect is not available on this platform")
        }
    }
}

class PermissionController internal constructor() {
    suspend fun getGrantedPermissions(): Set<String> = HealthConnectClient.unavailable()

    companion object {
        fun createRequestPermissionResultContract(providerPackageName: String = ""): ActivityResultContracts.ActivityResultContract<Set<String>, Set<String>> = HealthConnectClient.unavailable()
    }
}

class ReadRecordsResponse<T : Record> internal constructor(
    val records: List<T>,
    val pageToken: String?,
)
