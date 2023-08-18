package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.events.Event
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class LargeDBSignatureCheck {

    @Test
    fun insertDatabaseSample() = runBlocking {
        val fullDBInputStream = getInstrumentation().context.assets.open("nostr_vitor_short.json")

        val eventArray = Event.mapper.readValue<ArrayList<Event>>(
            InputStreamReader(fullDBInputStream)
        ) as List<Event>

        var counter = 0
        eventArray.forEach {
            assertTrue(it.hasValidSignature())
            counter ++
        }

        assertEquals(eventArray.size, counter)
    }

}