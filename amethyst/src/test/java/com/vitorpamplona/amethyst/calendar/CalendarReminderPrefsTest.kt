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
package com.vitorpamplona.amethyst.calendar

import android.content.Context
import android.content.SharedPreferences
import com.vitorpamplona.amethyst.service.calendar.CalendarReminderPrefs
import com.vitorpamplona.amethyst.service.calendar.CalendarReminderStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the device-level reminder preferences and the per-event "already notified"
 * store. Backed by an in-memory fake [SharedPreferences] so the test runs on the JVM without
 * needing Robolectric.
 */
class CalendarReminderPrefsTest {
    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        ctx = mockk()
        every { ctx.getSharedPreferences(any(), any()) } returns fakePrefs
    }

    @Test
    fun prefs_defaultsMatchPublicConstants() {
        val prefs = CalendarReminderPrefs(ctx)
        // Defaults are the contract callers in AppModules rely on — flipping these without an
        // explicit migration would silently re-enable reminders for users who had turned them
        // off (or vice versa).
        assertEquals(CalendarReminderPrefs.DEFAULT_ENABLED, prefs.isEnabled())
        assertEquals(CalendarReminderPrefs.DEFAULT_LEAD_MINUTES, prefs.leadMinutes())
    }

    @Test
    fun prefs_setEnabled_roundTrips() {
        val prefs = CalendarReminderPrefs(ctx)
        prefs.setEnabled(false)
        assertFalse(prefs.isEnabled())
        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled())
    }

    @Test
    fun prefs_setLeadMinutes_roundTrips() {
        val prefs = CalendarReminderPrefs(ctx)
        prefs.setLeadMinutes(30)
        assertEquals(30, prefs.leadMinutes())
    }

    @Test
    fun store_wasNotified_isFalseByDefault() {
        val store = CalendarReminderStore(ctx)
        assertFalse(store.wasNotified("event-a", 1_000_000L))
    }

    @Test
    fun store_markNotified_makesWasNotifiedTrueForSameStart() {
        val store = CalendarReminderStore(ctx)
        store.markNotified("event-a", 1_000_000L)
        assertTrue(store.wasNotified("event-a", 1_000_000L))
    }

    @Test
    fun store_wasNotified_isFalseWhenStartChanges() {
        // Regression test for the "moved meeting" case: if the author updates the appointment
        // with a new start, the store should not silently swallow the new reminder.
        val store = CalendarReminderStore(ctx)
        store.markNotified("event-a", 1_000_000L)
        assertFalse(store.wasNotified("event-a", 2_000_000L))
    }

    @Test
    fun store_forgetBefore_dropsOldEntries() {
        val store = CalendarReminderStore(ctx)
        store.markNotified("old", 1_000_000L)
        store.markNotified("recent", 5_000_000L)
        store.forgetBefore(3_000_000L)
        assertFalse(store.wasNotified("old", 1_000_000L))
        assertTrue(store.wasNotified("recent", 5_000_000L))
    }
}

/**
 * Bare-bones in-memory implementation of [SharedPreferences] sufficient for the prefs/store
 * round-trip tests. apply() is synchronous here — fine because the production code never relies
 * on apply()'s async semantics.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = data[key] as? String ?: defValue

    override fun getStringSet(
        key: String,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = (data[key] as? Int) ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = (data[key] as? Long) ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = (data[key] as? Float) ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = (data[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private class FakeEditor(
    private val data: MutableMap<String, Any?>,
) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removed = mutableSetOf<String>()
    private var clearAll = false

    override fun putString(
        key: String,
        value: String?,
    ): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putStringSet(
        key: String,
        values: MutableSet<String>?,
    ): SharedPreferences.Editor {
        pending[key] = values
        return this
    }

    override fun putInt(
        key: String,
        value: Int,
    ): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putLong(
        key: String,
        value: Long,
    ): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putFloat(
        key: String,
        value: Float,
    ): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun putBoolean(
        key: String,
        value: Boolean,
    ): SharedPreferences.Editor {
        pending[key] = value
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        removed.add(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        clearAll = true
        return this
    }

    override fun commit(): Boolean {
        apply()
        return true
    }

    override fun apply() {
        if (clearAll) data.clear()
        removed.forEach { data.remove(it) }
        data.putAll(pending)
    }
}
