package com.example.medsreminder.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPolicyTest {
    @Test
    fun acceptsDeliveryAtDueTimeAndThroughGraceBoundary() {
        val due = 1_000_000L
        assertTrue(AlarmScheduler.isWithinDeliveryGrace(due, due))
        assertTrue(
            AlarmScheduler.isWithinDeliveryGrace(
                due,
                due + AlarmScheduler.DELIVERY_GRACE_MILLIS,
            ),
        )
    }

    @Test
    fun rejectsMateriallyLateAndEarlyDelivery() {
        val due = 1_000_000L
        assertFalse(
            AlarmScheduler.isWithinDeliveryGrace(
                due,
                due + AlarmScheduler.DELIVERY_GRACE_MILLIS + 1,
            ),
        )
        assertFalse(AlarmScheduler.isWithinDeliveryGrace(due, due - 1_001L))
    }

    @Test
    fun routineReconciliationDoesNotExpireGraceBoundaryInFlightDelivery() {
        val due = 1_000_000L

        assertTrue(
            due > AlarmScheduler.routineExpiryCutoff(
                due + AlarmScheduler.DELIVERY_GRACE_MILLIS,
            ),
        )
        assertTrue(
            due <= AlarmScheduler.routineExpiryCutoff(
                due + AlarmScheduler.DELIVERY_GRACE_MILLIS + 1,
            ),
        )
    }
}
