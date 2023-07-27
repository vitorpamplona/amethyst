package com.vitorpamplona.amethyst.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.service.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader

@RunWith(AndroidJUnit4::class)
class LargeDbTest {
    private lateinit var database: AppDatabase
    private lateinit var eventDatabase: EventMapping

    @Before
    fun setupDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        eventDatabase = EventMapping(database.eventDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertDatabaseSample() = runBlocking {
        val fullDBInputStream = getInstrumentation().context.assets.open("nostr_vitor_short.json")

        println("Large JSON of Events: Open")

        val eventArray = Event.gson.fromJson(
            InputStreamReader(fullDBInputStream),
            object : TypeToken<ArrayList<Event>?>() {}.type
        ) as List<Event>

        println("Large JSON of Events: Parsed ${eventArray.size}")

        eventArray.forEach {
            eventDatabase.insert(it)
        }

        println("Large JSON of Events: Inserted")

        val job = async(Dispatchers.IO) {
            val events = eventDatabase.getAll()

            println("Large JSON of Events: Loaded")

            events.forEach {
                assertTrue(it.hasValidSignature() ?: false)
            }

            println("Large JSON of Events: Verified")
        }
        job.cancelAndJoin()
    }

    @Test
    @Ignore
    fun insertDatabaseSampleLarger() = runBlocking {
        val fullDBInputStream = getInstrumentation().context.assets.open("nostr_vitor_all.json")

        println("Large JSON of Events: Open")

        val eventArray = Event.gson.fromJson(
            InputStreamReader(fullDBInputStream),
            object : TypeToken<ArrayList<Event>?>() {}.type
        ) as List<Event>

        println("Large JSON of Events: Parsed ${eventArray.size}")

        eventDatabase.insert(eventArray)

        println("Large JSON of Events: Inserted")

        val job = async(Dispatchers.IO) {
            val events = eventDatabase.getAll()

            println("Large JSON of Events: Loaded")

            events.forEach {
                assertTrue(it.hasValidSignature() ?: false)
            }

            println("Large JSON of Events: Verified")
        }
        job.cancelAndJoin()
    }
}
