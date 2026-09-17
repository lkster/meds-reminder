package com.example.medsreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmReadinessStateTest {
    @Test
    fun requiredUnavailabilityWinsOverFullScreenLimitation() {
        assertEquals(AlarmReadinessState.NEEDS_ATTENTION, calculateAlarmReadiness(items(false, true, true, false)))
    }

    @Test
    fun fullScreenOnlyUnavailabilityIsLimited() {
        assertEquals(AlarmReadinessState.LIMITED, calculateAlarmReadiness(items(true, true, true, false)))
    }

    @Test
    fun allCapabilitiesReadyIsReady() {
        assertEquals(AlarmReadinessState.READY, calculateAlarmReadiness(items(true, true, true, true)))
    }

    private fun items(notifications: Boolean, channel: Boolean, exact: Boolean, fullScreen: Boolean) = listOf(
        CapabilityItem("Notifications", notifications, "", "", {}),
        CapabilityItem("Alarm notifications", channel, "", "", {}),
        CapabilityItem("Exact alarms", exact, "", "", {}),
        CapabilityItem("Full-screen alarm", fullScreen, "", "", {}, requiredForReliableDelivery = false),
    )
}
