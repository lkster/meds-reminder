package com.example.medsreminder.alarm

import android.app.PendingIntent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmIdentityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val pendingIntents = mutableListOf<PendingIntent>()

    @After
    fun cleanUp() {
        pendingIntents.forEach(PendingIntent::cancel)
    }

    @Test
    fun fireIdentityIsStableForUuidAndDistinctAcrossOccurrences() {
        val scheduler = AlarmScheduler(context)
        val first = scheduler.firePendingIntent("occurrence-a").track()
        val same = scheduler.firePendingIntent("occurrence-a").track()
        val second = scheduler.firePendingIntent("occurrence-b").track()

        assertEquals(first, same)
        assertNotEquals(first, second)
    }

    @Test
    fun actionIdentityIncludesActionAndOccurrence() {
        val firstTaken = AlarmActionReceiver.pendingIntent(
            context,
            AlarmActionReceiver.ACTION_TAKEN,
            "occurrence-a",
        ).track()
        val firstSkip = AlarmActionReceiver.pendingIntent(
            context,
            AlarmActionReceiver.ACTION_SKIP,
            "occurrence-a",
        ).track()
        val secondTaken = AlarmActionReceiver.pendingIntent(
            context,
            AlarmActionReceiver.ACTION_TAKEN,
            "occurrence-b",
        ).track()

        assertNotEquals(firstTaken, firstSkip)
        assertNotEquals(firstTaken, secondTaken)
    }

    private fun PendingIntent.track(): PendingIntent = also(pendingIntents::add)
}
