package com.vitorpamplona.amethyst.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleDbTest {
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
    fun insertEvent() = runBlocking {
        eventDatabase.insert(SimpleNoteWithTag)

        val job = async(Dispatchers.IO) {
            val result = eventDatabase.get(SimpleNoteWithTag.id)

            assertTrue(result?.hasValidSignature() ?: false)
        }
        job.cancelAndJoin()
    }

    @Test
    fun filterByIds() = runBlocking {
        eventDatabase.insert(SimpleNoteWithTag)
        eventDatabase.insert(SimpleNoteWithRTag)
        eventDatabase.insert(SimpleNote)

        val job = async(Dispatchers.IO) {
            val result = eventDatabase.get(listOf(SimpleNoteWithTag.id, SimpleNoteWithRTag.id))

            result.forEach {
                assertTrue(it.hasValidSignature())

                assertTrue(it.id in setOf(SimpleNoteWithTag.id, SimpleNoteWithRTag.id))
            }
        }
        job.cancelAndJoin()
    }
}
