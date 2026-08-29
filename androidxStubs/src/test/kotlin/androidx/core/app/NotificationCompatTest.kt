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
package androidx.core.app

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import com.vitorpamplona.amethyst.shared.platform.JvmContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A notification builder that accepts a title and a body and then drops them
 * posts blank notifications while looking, from the calling code, exactly like
 * a working one. These assert the content survives the builder.
 */
class NotificationCompatTest {
    private fun builder() = NotificationCompat.Builder(JvmContext, "chan")

    @Test
    fun contentSurvivesTheBuilder() {
        val notification =
            builder()
                .setContentTitle("Alice")
                .setContentText("sent you a message")
                .setSubText("Direct messages")
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup("dms")
                .setOngoing(true)
                .setAutoCancel(true)
                .build()

        assertEquals("Alice", notification.title)
        assertEquals("sent you a message", notification.text)
        assertEquals("Direct messages", notification.subText)
        assertEquals("chan", notification.channelId)
        assertEquals(NotificationCompat.CATEGORY_MESSAGE, notification.category)
        assertEquals(NotificationCompat.PRIORITY_HIGH, notification.priority)
        assertEquals("dms", notification.group)
        assertTrue(notification.autoCancel)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun clearingOngoingClearsItsFlag() {
        val notification = builder().setOngoing(true).setOngoing(false).build()
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun actionsReachTheNotification() {
        val notification =
            builder()
                .addAction(0, "Reply", null)
                .addAction(NotificationCompat.Action.Builder(0, "Mark read", null).build())
                .build()

        assertEquals(listOf("Reply", "Mark read"), notification.actions.map { it.title })
    }

    @Test
    fun bigTextStyleFillsTheExpandedBody() {
        val notification =
            builder()
                .setContentTitle("short")
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText("the whole note")
                        .setBigContentTitle("long")
                        .setSummaryText("summary"),
                ).build()

        assertEquals("the whole note", notification.bigText)
        assertEquals("long", notification.title)
        assertEquals("summary", notification.subText)
    }

    @Test
    fun inboxStyleFillsTheLines() {
        val notification =
            builder()
                .setStyle(
                    NotificationCompat
                        .InboxStyle()
                        .addLine("one")
                        .addLine("two")
                        .addLine(null),
                ).build()

        assertEquals(listOf<CharSequence>("one", "two"), notification.lines)
    }

    @Test
    fun progressStyleCollapsesSegmentsIntoATotal() {
        val notification =
            builder()
                .setStyle(
                    NotificationCompat
                        .ProgressStyle()
                        .setProgressSegments(List(4) { NotificationCompat.ProgressStyle.Segment(1) })
                        .setProgress(3),
                ).build()

        assertEquals(4, notification.progressMax)
        assertEquals(3, notification.progress)
        assertTrue(!notification.progressIndeterminate)
    }

    @Test
    fun indeterminateProgressIsCarriedThrough() {
        val notification =
            builder().setStyle(NotificationCompat.ProgressStyle().setProgressIndeterminate(true)).build()
        assertTrue(notification.progressIndeterminate)
    }

    @Test
    fun aProgressStyleWithNoSegmentsIsAPercentage() {
        assertEquals(100, NotificationCompat.ProgressStyle().total)
    }

    @Test
    fun remoteInputRoundTripsThroughAnIntent() {
        val input = RemoteInput.Builder("reply_text").setLabel("Reply").build()
        assertEquals("reply_text", input.resultKey)

        val intent = Intent("REPLY")
        assertNull(RemoteInput.getResultsFromIntent(intent), "no results before anything is collected")

        val typed = Bundle().apply { putCharSequence("reply_text", "on my way") }
        RemoteInput.addResultsToIntent(arrayOf(input), intent, typed)

        assertEquals(
            "on my way",
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply_text"),
        )
    }

    @Test
    fun extrasKeepTheirTypeAcrossPutExtras() {
        val source =
            Bundle().apply {
                putString("s", "text")
                putInt("i", 7)
                putBoolean("b", true)
            }
        val intent = Intent("ACT").putExtras(source)

        assertEquals("text", intent.getStringExtra("s"))
        assertEquals(7, intent.getIntExtra("i", -1))
        assertTrue(intent.getBooleanExtra("b", false))
    }

    @Test
    fun anExplicitIntentRemembersItsTarget() {
        val intent = Intent(JvmContext, NotificationCompatTest::class.java)
        assertEquals(NotificationCompatTest::class.java, intent.targetClass)
        assertEquals(NotificationCompatTest::class.java.name, intent.componentClassName)
    }
}
