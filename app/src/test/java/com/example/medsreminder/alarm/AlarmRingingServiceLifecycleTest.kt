package com.example.medsreminder.alarm

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.medsreminder.data.AlarmOccurrenceEntity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationEntity
import com.example.medsreminder.data.OccurrenceDetails
import com.example.medsreminder.data.OccurrenceKind
import com.example.medsreminder.data.OccurrenceStatus
import com.example.medsreminder.data.ReminderTimeEntity
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AlarmRingingServiceLifecycleTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database = AppDatabase.get(context)
    private var reminderTimeId = 0L

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        database.clearAllTables()
        shadowOf(context).clearStartedServices()
        context.getSystemService(NotificationManager::class.java)
            .cancel(AlarmRingingService.NOTIFICATION_ID)
        context.getSharedPreferences(AlarmPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ShadowMediaPlayer.resetStaticState()
        AlarmPreferences.defaultAlarmUri()?.let(::registerTone)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val medicationId = database.medicationDao().insertMedication(
            MedicationEntity(name = "Medicine", instructions = null, enabled = true),
        )
        reminderTimeId = database.medicationDao().insertTime(
            ReminderTimeEntity(medicationId = medicationId, minuteOfDay = 8 * 60),
        )
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.IO) {
        AlarmRingingService.stopAndRemoveNotification(context)
        database.clearAllTables()
        context.getSharedPreferences(AlarmPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ShadowMediaPlayer.resetStaticState()
    }

    @Test
    fun staleStartIntentStopsWithoutStartingRingingResources() {
        val stale = details("stale", OccurrenceStatus.RINGING, 1_000L)
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, stale),
        ).create().startCommand(0, 1)
        val service = controller.get()

        awaitServiceWork {
            shadowOf(service).isStoppedBySelf
        }

        assertFalse(service.ringingResourcesStarted)
        assertNull(shadowOf(service).lastForegroundNotification)
        assertEquals(
            0,
            context.getSystemService(NotificationManager::class.java).activeNotifications.size,
        )
        controller.destroy()
    }

    @Test
    fun validFreshStartFirstForegroundNotificationIsActionableAndFullScreen() {
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        val notification = shadowOf(service).lastForegroundNotification

        assertNotNull(notification)
        assertEquals("Medicine", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(3, notification.actions.size)
        assertNotNull(notification.fullScreenIntent)
        assertEquals(notification.contentIntent, notification.fullScreenIntent)
        assertEquals(
            "medsreminder://ringing/session",
            shadowOf(notification.fullScreenIntent).savedIntent.data.toString(),
        )
        assertEquals(
            listOf("Taken", "Snooze", "Skip"),
            notification.actions.map { it.title.toString() },
        )
        notification.actions.forEach { action ->
            assertEquals(
                "a",
                shadowOf(action.actionIntent).savedIntent.data?.lastPathSegment,
            )
        }

        controller.destroy()
    }

    @Test
    fun resolvingFinalOccurrenceCleansFallbackWithoutStartingEmptySession() =
        runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            val current = database.occurrenceDao().getDetails("a")!!
            AlarmRingingService.postFallbackNotification(context, current, Throwable("test"))
            database.occurrenceDao().transition(
                "a",
                OccurrenceStatus.RINGING,
                OccurrenceStatus.TAKEN,
                2_000L,
            )
            shadowOf(context).clearStartedServices()

            AlarmRingingService.synchronizeWithPersistedQueue(context, database)

            assertNull(shadowOf(context).nextStartedService)
            assertNotNull(shadowOf(context).nextStoppedService)
            assertEquals(
                0,
                context.getSystemService(NotificationManager::class.java).activeNotifications.size,
            )
        }

    @Test
    fun resolvingAStartsOrRefreshesServiceWithQueuedB() = runBlocking(Dispatchers.IO) {
        insert("a", OccurrenceStatus.RINGING, 1_000L)
        insert("b", OccurrenceStatus.RINGING, 2_000L)
        database.occurrenceDao().markPresented("a", 1_500L)
        database.occurrenceDao().transition(
            "a",
            OccurrenceStatus.RINGING,
            OccurrenceStatus.SKIPPED,
            3_000L,
        )
        shadowOf(context).clearStartedServices()

        AlarmRingingService.synchronizeWithPersistedQueue(context, database)

        val started = shadowOf(context).nextStartedService
        assertNotNull(started)
        assertEquals(
            AlarmRingingService::class.java.name,
            started.component?.className,
        )
        assertEquals("b", started.getStringExtra("occurrence_id"))
        assertEquals("b", database.occurrenceDao().getCurrentRinging()?.occurrenceId)
    }

    @Test
    fun capabilityLossExpiryStopsAndCleansPersistedSession() {
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.ringingResourcesStarted }

        runBlocking(Dispatchers.IO) {
            database.occurrenceDao().expireAllRinging(2_000L)
            AlarmRingingService.synchronizeWithPersistedQueue(context, database)
            assertNull(database.occurrenceDao().getCurrentRinging())
        }
        assertTrue(shadowOf(context).nextStoppedService.component != null)
        controller.destroy()

        assertFalse(service.ringingResourcesStarted)
        assertEquals(
            0,
            context.getSystemService(NotificationManager::class.java).activeNotifications.size,
        )
    }

    @Test
    fun queueAdvanceUpdatesNotificationThenUsesFreshPreferencesAndKeepsWakeLock() {
        val firstUri = Uri.parse("content://media/internal/audio/media/101")
        val secondUri = Uri.parse("content://media/internal/audio/media/202")
        registerTone(firstUri)
        registerTone(secondUri)
        AlarmPreferences.setSoundUri(context, firstUri)
        AlarmPreferences.setVibrationEnabled(context, true)
        val first = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            insert("b", OccurrenceStatus.RINGING, 2_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, first),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.activeOutputOccurrenceId == "a" }
        val firstWakeLock = service.activeWakeLock

        AlarmPreferences.setSoundUri(context, secondUri)
        AlarmPreferences.setVibrationEnabled(context, false)
        runBlocking(Dispatchers.IO) {
            database.occurrenceDao().transition(
                "a",
                OccurrenceStatus.RINGING,
                OccurrenceStatus.TAKEN,
                3_000L,
            )
        }
        service.onStartCommand(AlarmRingingService.startIntent(context, details("b", OccurrenceStatus.RINGING, 2_000L)), 0, 2)

        awaitServiceWork { service.activeOutputOccurrenceId == "b" }

        assertSame(firstWakeLock, service.activeWakeLock)
        assertTrue(service.activeWakeLock?.isHeld == true)
        assertEquals(secondUri, service.activePreferenceSnapshot?.selectedSoundUri)
        assertFalse(service.activePreferenceSnapshot?.vibrationEnabled ?: true)
        assertEquals(secondUri, shadowOf(requireNotNull(service.activeMediaPlayer)).sourceUri)
        val notification = context.getSystemService(NotificationManager::class.java)
            .activeNotifications.single { it.id == AlarmRingingService.NOTIFICATION_ID }
            .notification
        notification.actions.forEach { action ->
            assertEquals("b", shadowOf(action.actionIntent).savedIntent.data?.lastPathSegment)
        }
        controller.destroy()
    }

    @Test
    fun stalePlayerErrorAfterQueueAdvanceCannotMutateNewAudioOwnership() {
        val firstUri = Uri.parse("content://media/internal/audio/media/301")
        val secondUri = Uri.parse("content://media/internal/audio/media/302")
        registerTone(firstUri)
        registerTone(secondUri)
        AlarmPreferences.setSoundUri(context, firstUri)
        val first = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            insert("b", OccurrenceStatus.RINGING, 2_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, first),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.activeOutputOccurrenceId == "a" }
        val oldPlayer = requireNotNull(service.activeMediaPlayer)
        val oldErrorListener = errorListener(oldPlayer)

        AlarmPreferences.setSoundUri(context, secondUri)
        runBlocking(Dispatchers.IO) {
            database.occurrenceDao().transition(
                "a",
                OccurrenceStatus.RINGING,
                OccurrenceStatus.SKIPPED,
                3_000L,
            )
        }
        service.onStartCommand(AlarmRingingService.startIntent(context, details("b", OccurrenceStatus.RINGING, 2_000L)), 0, 2)
        awaitServiceWork { service.activeOutputOccurrenceId == "b" }
        val secondPlayer = requireNotNull(service.activeMediaPlayer)
        val secondFocus = requireNotNull(service.activeAudioFocusRequest)
        val secondSnapshot = service.activePreferenceSnapshot

        oldErrorListener.onError(oldPlayer, 1, 2)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(secondPlayer, service.activeMediaPlayer)
        assertSame(secondFocus, service.activeAudioFocusRequest)
        assertSame(secondSnapshot, service.activePreferenceSnapshot)
        assertEquals("b", service.activeOutputOccurrenceId)
        assertEquals(secondUri, shadowOf(requireNotNull(service.activeMediaPlayer)).sourceUri)
        controller.destroy()
    }

    @Test
    fun currentExplicitPlayerErrorFallsBackOnceThenReleasesFocus() {
        val explicitUri = Uri.parse("content://media/internal/audio/media/401")
        val defaultUri = requireNotNull(AlarmPreferences.defaultAlarmUri())
        registerTone(explicitUri)
        AlarmPreferences.setSoundUri(context, explicitUri)
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.activeOutputOccurrenceId == "a" }
        val explicitPlayer = requireNotNull(service.activeMediaPlayer)
        val focus = requireNotNull(service.activeAudioFocusRequest)

        shadowOf(explicitPlayer).invokeErrorListener(1, 2)
        shadowOf(Looper.getMainLooper()).idle()

        val fallbackPlayer = requireNotNull(service.activeMediaPlayer)
        assertNotSame(explicitPlayer, fallbackPlayer)
        assertEquals(defaultUri, shadowOf(fallbackPlayer).sourceUri)
        assertSame(focus, service.activeAudioFocusRequest)

        shadowOf(fallbackPlayer).invokeErrorListener(3, 4)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(service.activeMediaPlayer)
        assertNull(service.activeAudioFocusRequest)
        assertEquals("a", service.activeOutputOccurrenceId)
        controller.destroy()
    }

    @Test
    fun repeatedStaleErrorFromReplacedExplicitPlayerCannotMutateSamePresentationFallback() {
        val explicitUri = Uri.parse("content://media/internal/audio/media/451")
        val defaultUri = requireNotNull(AlarmPreferences.defaultAlarmUri())
        registerTone(explicitUri)
        AlarmPreferences.setSoundUri(context, explicitUri)
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.activeOutputOccurrenceId == "a" }
        val explicitPlayer = requireNotNull(service.activeMediaPlayer)
        val explicitErrorListener = errorListener(explicitPlayer)
        val focus = requireNotNull(service.activeAudioFocusRequest)
        val snapshot = service.activePreferenceSnapshot

        explicitErrorListener.onError(explicitPlayer, 1, 2)
        shadowOf(Looper.getMainLooper()).idle()
        val fallbackPlayer = requireNotNull(service.activeMediaPlayer)
        assertNotSame(explicitPlayer, fallbackPlayer)
        assertEquals(defaultUri, shadowOf(fallbackPlayer).sourceUri)
        assertSame(focus, service.activeAudioFocusRequest)

        explicitErrorListener.onError(explicitPlayer, 3, 4)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(fallbackPlayer, service.activeMediaPlayer)
        assertSame(focus, service.activeAudioFocusRequest)
        assertSame(snapshot, service.activePreferenceSnapshot)
        assertEquals("a", service.activeOutputOccurrenceId)
        assertEquals(defaultUri, shadowOf(requireNotNull(service.activeMediaPlayer)).sourceUri)
        controller.destroy()
    }

    @Test
    fun ordinaryRefreshOfSameOccurrenceDoesNotReconfigureActiveOutput() {
        val firstUri = Uri.parse("content://media/internal/audio/media/461")
        val secondUri = Uri.parse("content://media/internal/audio/media/462")
        registerTone(firstUri)
        registerTone(secondUri)
        AlarmPreferences.setSoundUri(context, firstUri)
        AlarmPreferences.setVibrationEnabled(context, true)
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.activeOutputOccurrenceId == "a" }
        val player = requireNotNull(service.activeMediaPlayer)
        val focus = requireNotNull(service.activeAudioFocusRequest)
        val snapshot = service.activePreferenceSnapshot
        val wakeLock = service.activeWakeLock
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val firstNotification = notificationManager.activeNotifications
            .single { it.id == AlarmRingingService.NOTIFICATION_ID }
            .notification

        AlarmPreferences.setSoundUri(context, secondUri)
        AlarmPreferences.setVibrationEnabled(context, false)
        service.onStartCommand(AlarmRingingService.startIntent(context, current), 0, 2)
        awaitServiceWork {
            notificationManager.activeNotifications
                .singleOrNull { it.id == AlarmRingingService.NOTIFICATION_ID }
                ?.notification !== firstNotification
        }

        assertSame(player, service.activeMediaPlayer)
        assertSame(focus, service.activeAudioFocusRequest)
        assertSame(snapshot, service.activePreferenceSnapshot)
        assertEquals(firstUri, service.activePreferenceSnapshot?.selectedSoundUri)
        assertTrue(service.activePreferenceSnapshot?.vibrationEnabled == true)
        assertSame(wakeLock, service.activeWakeLock)
        assertTrue(service.activeWakeLock?.isHeld == true)
        assertTrue(service.ringingResourcesStarted)
        assertEquals("a", service.activeOutputOccurrenceId)
        controller.destroy()
    }

    @Test
    fun explicitInitializationFailureReleasesPlayerAndStartsDefaultOnce() {
        val explicitUri = Uri.parse("content://media/internal/audio/media/471")
        val defaultUri = requireNotNull(AlarmPreferences.defaultAlarmUri())
        val createdPlayers = mutableListOf<MediaPlayer>()
        ShadowMediaPlayer.setCreateListener { player, _ -> createdPlayers += player }
        ShadowMediaPlayer.addException(
            DataSource.toDataSource(context, explicitUri),
            IOException("unreadable selected alarm"),
        )
        AlarmPreferences.setSoundUri(context, explicitUri)
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val controller = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        val service = controller.get()
        awaitServiceWork { service.activeOutputOccurrenceId == "a" }

        assertEquals(2, createdPlayers.size)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(createdPlayers.first()).state)
        assertSame(createdPlayers.last(), service.activeMediaPlayer)
        assertEquals(defaultUri, shadowOf(requireNotNull(service.activeMediaPlayer)).sourceUri)
        assertNotNull(service.activeAudioFocusRequest)
        assertEquals("a", service.activeOutputOccurrenceId)
        controller.destroy()
    }

    @Test
    fun recreatedServiceMayCaptureFreshPreferencesForSamePersistedOccurrence() {
        val firstUri = Uri.parse("content://media/internal/audio/media/501")
        val secondUri = Uri.parse("content://media/internal/audio/media/502")
        registerTone(firstUri)
        registerTone(secondUri)
        AlarmPreferences.setSoundUri(context, firstUri)
        val current = runBlocking(Dispatchers.IO) {
            insert("a", OccurrenceStatus.RINGING, 1_000L)
            database.occurrenceDao().getDetails("a")!!
        }
        val firstController = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 1)
        awaitServiceWork { firstController.get().activeOutputOccurrenceId == "a" }
        assertEquals(firstUri, firstController.get().activePreferenceSnapshot?.selectedSoundUri)
        firstController.destroy()

        AlarmPreferences.setSoundUri(context, secondUri)
        AlarmPreferences.setVibrationEnabled(context, false)
        val secondController = Robolectric.buildService(
            AlarmRingingService::class.java,
            AlarmRingingService.startIntent(context, current),
        ).create().startCommand(0, 2)
        awaitServiceWork { secondController.get().activeOutputOccurrenceId == "a" }

        assertEquals(secondUri, secondController.get().activePreferenceSnapshot?.selectedSoundUri)
        assertFalse(secondController.get().activePreferenceSnapshot?.vibrationEnabled ?: true)
        secondController.destroy()
    }

    private suspend fun insert(id: String, status: OccurrenceStatus, scheduledAt: Long) {
        database.occurrenceDao().insert(
            AlarmOccurrenceEntity(
                id = id,
                reminderTimeId = reminderTimeId,
                kind = OccurrenceKind.BASE,
                scheduledAtEpochMillis = scheduledAt,
                status = status,
            ),
        )
    }

    private fun details(id: String, status: OccurrenceStatus, scheduledAt: Long) = OccurrenceDetails(
        occurrenceId = id,
        reminderTimeId = reminderTimeId,
        kind = OccurrenceKind.BASE,
        status = status,
        scheduledAtEpochMillis = scheduledAt,
        presentedAtEpochMillis = null,
        medicationId = 1L,
        medicationName = "Medicine",
        instructions = null,
        minuteOfDay = 8 * 60,
        weekdayMask = 127,
    )

    private fun awaitServiceWork(condition: () -> Boolean) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Service work did not finish", condition())
    }

    private fun registerTone(uri: Uri) {
        ShadowMediaPlayer.addMediaInfo(
            DataSource.toDataSource(context, uri),
            ShadowMediaPlayer.MediaInfo(60_000, 0),
        )
    }

    private fun errorListener(player: MediaPlayer): MediaPlayer.OnErrorListener {
        val shadow = shadowOf(player)
        val field = ShadowMediaPlayer::class.java.getDeclaredField("errorListener")
        field.isAccessible = true
        return requireNotNull(field.get(shadow) as? MediaPlayer.OnErrorListener)
    }
}
