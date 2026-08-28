package com.example.medsreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityCapabilityMappingTest {
    @Test
    fun missingRuntimePermissionRequestsPermissionOnly() {
        var permissionRequests = 0
        var settingsOpens = 0

        val item = buildNotificationCapabilityItem(
            runtimePermissionReady = false,
            appNotificationsEnabled = true,
            onRequestRuntimePermission = { permissionRequests++ },
            onOpenNotificationSettings = { settingsOpens++ },
        )

        assertFalse(item.ready)
        assertEquals("Allow notifications", item.actionLabel)
        assertTrue(item.detail.contains("Notification permission is required"))
        item.onAction()
        assertEquals(1, permissionRequests)
        assertEquals(0, settingsOpens)
    }

    @Test
    fun disabledAppNotificationsOpenSettingsOnly() {
        var permissionRequests = 0
        var settingsOpens = 0

        val item = buildNotificationCapabilityItem(
            runtimePermissionReady = true,
            appNotificationsEnabled = false,
            onRequestRuntimePermission = { permissionRequests++ },
            onOpenNotificationSettings = { settingsOpens++ },
        )

        assertFalse(item.ready)
        assertEquals("Open notification settings", item.actionLabel)
        assertTrue(item.detail.contains("disabled for Meds Reminder"))
        item.onAction()
        assertEquals(0, permissionRequests)
        assertEquals(1, settingsOpens)
    }

    @Test
    fun readyNotificationSubstatesProduceReadyItem() {
        val item = buildNotificationCapabilityItem(true, true, {}, {})

        assertTrue(item.ready)
    }
}
