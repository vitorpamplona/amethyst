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
package com.vitorpamplona.quartz.nipXXPushNotifications

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nipXXPushNotifications.deregistration.PushDeregistrationEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.preferences.PushPreferences
import com.vitorpamplona.quartz.nipXXPushNotifications.preferences.PushPreferencesEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.registration.PushRegistrationEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.registration.PushToken
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The three control kinds of divine-push-service's draft (`docs/nip-xx-push-notifications.md`).
 *
 * The payloads are checked as the service would decrypt them, not only through our own decoder,
 * so a field renamed on this side cannot pass by agreeing with itself.
 */
class PushNotificationEventsTest {
    private val user = NostrSignerInternal(KeyPair())
    private val service = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())
    private val app = "co.openvine.app"

    private suspend fun plaintextAtService(event: Event) = service.nip44Decrypt(event.content, event.pubKey)

    @Test
    fun registration() =
        runTest {
            val template = PushRegistrationEvent.build(PushToken("fcm-token", -300), service.pubKey, app, user, expiresAt = 1_800_000_000)
            val event = assertIs<PushRegistrationEvent>(Event.fromJson(user.sign(template).toJson()))

            assertEquals(service.pubKey, event.pushService())
            assertEquals(app, event.app())
            assertEquals(1_800_000_000, event.expiration())
            assertTrue(event.isContentEncoded())
            assertEquals("""{"token":"fcm-token","timezoneOffsetMinutes":-300}""", plaintextAtService(event))

            assertEquals(PushToken("fcm-token", -300), event.decrypt(service))
            assertEquals(PushToken("fcm-token", -300), event.decrypt(user))
        }

    @Test
    fun deregistrationLeavesTheOffsetOut() =
        runTest {
            val event = user.sign(PushDeregistrationEvent.build("fcm-token", service.pubKey, app, user, expiresAt = 1_800_000_000))

            assertEquals(PushDeregistrationEvent.KIND, event.kind)
            assertEquals(1_800_000_000, event.expiration())
            assertEquals("""{"token":"fcm-token"}""", plaintextAtService(event))
            assertEquals(PushToken("fcm-token"), event.decrypt(service))
        }

    @Test
    fun preferences() =
        runTest {
            val prefs = PushPreferences(listOf(1, 3, 7, 16, 34236), campaignsEnabled = false)
            val event = user.sign(PushPreferencesEvent.build(prefs, service.pubKey, app, user))

            assertEquals("""{"kinds":[1,3,7,16,34236],"campaignsEnabled":false}""", plaintextAtService(event))
            assertEquals(prefs, event.decrypt(service))
        }

    @Test
    fun readsWhatTheServiceWrites() =
        runTest {
            // A payload from a newer client: an unknown field, and no campaign flag.
            val content = user.nip44Encrypt("""{"kinds":[],"quietHours":"22-07"}""", service.pubKey)
            val event = user.sign<PushPreferencesEvent>(1_789_000_000, PushPreferencesEvent.KIND, arrayOf(arrayOf("p", service.pubKey), arrayOf("app", app)), content)

            assertEquals(PushPreferences(emptyList(), campaignsEnabled = false), event.decrypt(service))
        }

    @Test
    fun onlyTheTwoEndsCanDecrypt() =
        runTest {
            val event = user.sign(PushRegistrationEvent.build(PushToken("fcm-token"), service.pubKey, app, user))

            assertTrue(event.canDecrypt(user))
            assertTrue(event.canDecrypt(service))
            assertFalse(event.canDecrypt(stranger))
            assertFailsWith<SignerExceptions.UnauthorizedDecryptionException> { event.decrypt(stranger) }
        }
}
