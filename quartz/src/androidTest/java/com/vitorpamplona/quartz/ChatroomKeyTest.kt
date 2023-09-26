package com.vitorpamplona.quartz

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.events.ChatroomKey
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatroomKeyTest {
    @Test
    fun testEquals() {
        val k1 = ChatroomKey(persistentSetOf("Key1", "Key2"))
        val k2 = ChatroomKey(persistentSetOf("Key1", "Key2"))

        assertEquals(k1, k2)
        assertEquals(k1.hashCode(), k2.hashCode())
    }
}
