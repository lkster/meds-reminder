package com.example.medsreminder.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.medsreminder.MainActivity
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.WeekdayMask
import com.example.medsreminder.data.applyMedicationListToggle
import com.example.medsreminder.data.applyMedicationScheduleEdit
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the retained owner through MainActivity, with only its Room/alarm boundary controlled. */
@RunWith(AndroidJUnit4::class)
class MedicationEditorLifecycleTest {
    private lateinit var database: AppDatabase
    private lateinit var control: ControlledEditorSave
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        control = ControlledEditorSave(database)
        MedicationEditorViewModelTestHook.factory = { application: Application ->
            MedicationEditorViewModel.forTest(application, control.operations)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        MedicationEditorViewModelTestHook.factory = null
        database.close()
    }

    @Test
    fun idleNewAndExistingDraftsSurviveActivityRecreationWithoutRoomMutation() {
        val newDraft = validDraft().copy(
            name = "Vitamin D",
            instructions = "With breakfast",
            enabled = false,
            times = listOf(EditorTime(null, 9 * 60 + 15, WeekdayMask.ALL xor 1)),
        )
        val initialOwner = owner()
        onOwner { openNew(); updateDraft(newDraft) }

        scenario.recreate()

        val recreatedOwner = owner()
        assertSame(initialOwner, recreatedOwner)
        assertEquals(newDraft, recreatedOwner.draft)
        assertEquals(0, medicationCount())

        val medicationId = runBlocking {
            database.medicationDao().insertMedication(
                com.example.medsreminder.data.MedicationEntity(
                    name = "Existing",
                    instructions = "Take at night",
                    enabled = true,
                ),
            )
        }
        val reminderId = runBlocking {
            database.medicationDao().insertTime(
                com.example.medsreminder.data.ReminderTimeEntity(
                    medicationId = medicationId,
                    minuteOfDay = 21 * 60,
                    weekdayMask = 0b0010101,
                ),
            )
        }
        val persistedExisting = existing(medicationId)
        onOwner { cancelIdleEditor(); openExisting(persistedExisting) }

        scenario.recreate()

        val existingDraft = owner().draft!!
        assertEquals(medicationId, existingDraft.id)
        assertEquals(reminderId, existingDraft.times.single().id)
        assertEquals(21 * 60, existingDraft.times.single().minuteOfDay)
        assertEquals(0b0010101, existingDraft.times.single().weekdayMask)
        assertEquals(1, medicationCount())
    }

    @Test
    fun recreationDuringPhaseAHoldsOneRetainedSubmissionAndCreatesOneMedication() {
        control.holdRoom = true
        onOwner { openNew(); updateDraft(validDraft()); submit() }
        assertTrue(control.roomStarted.await(3, TimeUnit.SECONDS))
        val originalOwner = owner()

        scenario.recreate()

        assertSame(originalOwner, owner())
        assertTrue(owner().saveState is EditorSaveState.SavingRoom)
        onOwner { submit(); submit() }
        assertEquals(1, control.roomCalls.get())

        control.releaseRoom.countDown()
        eventually { owner().draft == null }
        assertEquals(1, control.roomCalls.get())
        assertEquals(1, medicationCount())
    }

    @Test
    fun recreationAfterRoomCommitRetainsResultAndDoesNotCreateAgain() {
        control.holdCompletion = true
        onOwner { openNew(); updateDraft(validDraft()); submit() }
        assertTrue(control.completionStarted.await(3, TimeUnit.SECONDS))
        val committed = owner().saveState as EditorSaveState.CompletingAlarms
        val originalOwner = owner()
        assertEquals(1, medicationCount())

        scenario.recreate()

        assertSame(originalOwner, owner())
        val recreatedState = owner().saveState as EditorSaveState.CompletingAlarms
        assertEquals(committed.result, recreatedState.result)
        onOwner { submit() }
        assertEquals(1, control.roomCalls.get())

        control.releaseCompletion.countDown()
        eventually { owner().draft == null }
        assertEquals(1, medicationCount())
    }

    @Test
    fun preCommitFailureKeepsDraftAndNormalRetryCommitsOnce() {
        control.failRoomCalls = 1
        val draft = validDraft()
        onOwner { openNew(); updateDraft(draft); submit() }

        eventually { owner().saveState is EditorSaveState.RoomFailure }
        assertEquals(draft, owner().draft)
        assertEquals(0, medicationCount())

        onOwner { submit() }
        eventually { owner().draft == null }
        assertEquals(2, control.roomCalls.get())
        assertEquals(1, medicationCount())
    }

    @Test
    fun postCommitFailureRetainsExactResultAndRetriesOnlyCompletion() {
        control.failCompletionCalls = 1
        control.returnedObsoleteIds = listOf("obsolete-a", "obsolete-b")
        onOwner { openNew(); updateDraft(validDraft()); submit() }

        eventually { owner().saveState is EditorSaveState.PostCommitFailure }
        val failed = owner().saveState as EditorSaveState.PostCommitFailure
        assertEquals(control.returnedObsoleteIds, failed.result.obsoleteOccurrenceIds)
        assertEquals(1, medicationCount())
        assertEquals(1, control.roomCalls.get())

        onOwner { retryPostCommitCompletion() }
        eventually { owner().draft == null }
        assertEquals(1, control.roomCalls.get())
        assertEquals(2, control.completionCalls.get())
        assertEquals(control.returnedObsoleteIds, failed.result.obsoleteOccurrenceIds)
        assertEquals(1, medicationCount())
    }

    @Test
    fun repeatedSaveRequestsStartAtMostOnePhaseAOperation() {
        control.holdRoom = true
        onOwner {
            openNew()
            updateDraft(validDraft())
            repeat(12) { submit() }
        }
        assertTrue(control.roomStarted.await(3, TimeUnit.SECONDS))
        assertEquals(1, control.roomCalls.get())

        control.releaseRoom.countDown()
        eventually { owner().draft == null }
        assertEquals(1, medicationCount())
    }

    @Test
    fun idleBackAndCancelCloseButActiveSaveCannotBeAbandoned() {
        onOwner { openNew(); updateDraft(validDraft()) }
        settleUi()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        eventually { owner().draft == null }

        onOwner { openNew(); updateDraft(validDraft()); cancelIdleEditor() }
        assertFalse(owner().draft != null)

        control.holdRoom = true
        onOwner { openNew(); updateDraft(validDraft()); submit() }
        assertTrue(control.roomStarted.await(3, TimeUnit.SECONDS))
        settleUi()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        onOwner { cancelIdleEditor() }
        assertNotNull(owner().draft)
        assertTrue(owner().saveState is EditorSaveState.SavingRoom)

        control.releaseRoom.countDown()
        eventually { owner().draft == null }
    }

    @Test
    fun listToggleDataCanCommitWhileEditorOwnerIsBusy() {
        val medicationId = runBlocking {
            database.medicationDao().insertMedication(
                com.example.medsreminder.data.MedicationEntity(
                    name = "Toggle",
                    instructions = null,
                    enabled = true,
                ),
            )
        }
        val reminderId = runBlocking {
            database.medicationDao().insertTime(
                com.example.medsreminder.data.ReminderTimeEntity(
                    medicationId = medicationId,
                    minuteOfDay = 8 * 60,
                ),
            )
        }
        control.holdRoom = true
        onOwner { openNew(); updateDraft(validDraft()); submit() }
        assertTrue(control.roomStarted.await(3, TimeUnit.SECONDS))

        // This is the same focused persistence edit used by the list path; it never touches editor state.
        runBlocking {
            database.applyMedicationListToggle(
                medicationId = medicationId,
                enabled = false,
                nowMillis = System.currentTimeMillis(),
                zoneId = ZoneId.systemDefault(),
            )
        }
        assertTrue(owner().saveState is EditorSaveState.SavingRoom)
        assertFalse(runBlocking { database.medicationDao().get(medicationId)!!.enabled })

        control.releaseRoom.countDown()
        eventually { owner().draft == null }
    }

    private fun onOwner(action: MedicationEditorViewModel.() -> Unit) {
        val currentOwner = owner()
        scenario.onActivity { currentOwner.action() }
    }

    private fun owner(): MedicationEditorViewModel {
        lateinit var owner: MedicationEditorViewModel
        scenario.onActivity { activity ->
            owner = ViewModelProvider(activity)[MedicationEditorViewModel::class.java]
        }
        return owner
    }

    private fun existing(id: Long): MedicationWithTimes = runBlocking {
        requireNotNull(database.medicationDao().getWithTimes(id))
    }

    private fun medicationCount(): Int = runBlocking { database.medicationDao().observeAll().first().size }

    private fun validDraft() = EditorDraft(
        id = null,
        name = "Editor medicine",
        instructions = "Once daily",
        enabled = true,
        times = listOf(EditorTime(null, 8 * 60, WeekdayMask.ALL)),
    )

    private fun eventually(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("Timed out waiting for editor state", condition())
    }

    private fun settleUi() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private class ControlledEditorSave(private val database: AppDatabase) {
        val roomStarted = CountDownLatch(1)
        val completionStarted = CountDownLatch(1)
        val releaseRoom = CountDownLatch(1)
        val releaseCompletion = CountDownLatch(1)
        val roomCalls = AtomicInteger()
        val completionCalls = AtomicInteger()
        @Volatile var holdRoom = false
        @Volatile var holdCompletion = false
        @Volatile var failRoomCalls = 0
        @Volatile var failCompletionCalls = 0
        @Volatile var returnedObsoleteIds: List<String> = emptyList()

        val operations = MedicationEditorSaveOperations(
            applyRoomEdit = { edit ->
                roomCalls.incrementAndGet()
                roomStarted.countDown()
                if (holdRoom) check(releaseRoom.await(5, TimeUnit.SECONDS))
                if (failRoomCalls > 0) {
                    failRoomCalls--
                    throw IllegalStateException("Room edit failed")
                }
                database.applyMedicationScheduleEdit(
                    edit = edit,
                    nowMillis = System.currentTimeMillis(),
                    zoneId = ZoneId.systemDefault(),
                ).copy(obsoleteOccurrenceIds = returnedObsoleteIds)
            },
            completeAlarms = {
                completionCalls.incrementAndGet()
                completionStarted.countDown()
                if (holdCompletion) check(releaseCompletion.await(5, TimeUnit.SECONDS))
                if (failCompletionCalls > 0) {
                    failCompletionCalls--
                    throw IllegalStateException("Alarm completion failed")
                }
            },
        )
    }
}
