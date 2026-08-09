package com.example.medsreminder.alarm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmStoreTest {
    private lateinit var store: AlarmStore

    @Before
    fun setUp() {
        store = AlarmStore(RuntimeEnvironment.getApplication())
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun emptyStoreHasNoScheduledAlarm() {
        assertNull(store.scheduledAtMillis())
    }

    @Test
    fun saveReplacesTheSingleScheduledAlarm() {
        store.save(1_000L)
        store.save(2_000L)

        assertEquals(2_000L, store.scheduledAtMillis())
    }

    @Test
    fun clearRemovesTheScheduledAlarm() {
        store.save(1_000L)
        store.clear()

        assertNull(store.scheduledAtMillis())
    }
}
