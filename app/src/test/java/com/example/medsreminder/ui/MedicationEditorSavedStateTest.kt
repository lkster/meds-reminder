package com.example.medsreminder.ui

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.medsreminder.data.MedicationScheduleEdit
import com.example.medsreminder.data.MedicationScheduleEditResult
import com.example.medsreminder.data.WeekdayMask
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Process-restoration tests use a saved Activity Bundle and a separate Activity/ViewModel. */
@RunWith(RobolectricTestRunner::class)
class MedicationEditorSavedStateTest {
    @Test
    fun idleNewDraftRestoresExactlyIntoFreshOwner() {
        val first = activity()
        val draft = EditorDraft(
            id = null,
            name = "Vitamin D",
            instructions = "With breakfast",
            enabled = false,
            times = listOf(
                EditorTime(null, 9 * 60 + 15, WeekdayMask.ALL xor 1),
                EditorTime(44L, 20 * 60, 0),
            ),
        )
        owner(first).apply { openNew(); updateDraft(draft) }

        val restored = owner(activity(capture(first)))

        assertEquals(draft, restored.draft)
        assertEquals(EditorSaveState.Idle, restored.saveState)
    }

    @Test
    fun existingAndInvalidIntermediateDraftsKeepIdentitiesAndOrder() {
        val first = activity()
        val draft = EditorDraft(
            id = 12L,
            name = "", // User-editable invalid state is still saved UI state.
            instructions = "Do not normalize",
            enabled = true,
            times = listOf(EditorTime(7L, 500, 0), EditorTime(null, 500, WeekdayMask.ALL)),
        )
        owner(first).apply { openNew(); updateDraft(draft) }

        val restored = owner(activity(capture(first)))

        assertEquals(draft, restored.draft)
        assertEquals(EditorSaveState.Idle, restored.saveState)
    }

    @Test
    fun cancelAndSaveAcceptanceBothLeaveCapturedStateDraftless() {
        val canceledActivity = activity()
        owner(canceledActivity).apply { openNew(); cancelIdleEditor() }
        assertNull(owner(activity(capture(canceledActivity))).draft)

        val control = ControlledOperations(holdRoom = true)
        val savingActivity = activity()
        owner(savingActivity, control.operations).apply {
            openNew()
            updateDraft(validDraft())
            submit()
        }
        assertTrue(control.roomStarted.await(3, TimeUnit.SECONDS))
        val restored = owner(activity(capture(savingActivity)))
        assertNull(restored.draft)
        assertEquals(EditorSaveState.Idle, restored.saveState)
        control.releaseRoom.countDown()
    }

    @Test
    fun preCommitFailureRepublishesDraftForANewCaptureWithoutFailureState() {
        val control = ControlledOperations(failRoom = true)
        val first = activity()
        val draft = validDraft()
        val live = owner(first, control.operations).apply {
            openNew()
            updateDraft(draft)
            submit()
        }
        eventually { live.saveState is EditorSaveState.RoomFailure }

        val restored = owner(activity(capture(first)))
        assertEquals(draft, restored.draft)
        assertEquals(EditorSaveState.Idle, restored.saveState)
    }

    @Test
    fun latestDraftReplacesEarlierDraftBeforeCapture() {
        val first = activity()
        val earlier = validDraft().copy(name = "Earlier", instructions = "Old")
        val latest = earlier.copy(name = "Latest", enabled = false, times = emptyList())
        owner(first).apply {
            openNew()
            updateDraft(earlier)
            updateDraft(latest)
        }

        val restored = owner(activity(capture(first)))

        assertEquals(latest, restored.draft)
        assertEquals(EditorSaveState.Idle, restored.saveState)
    }

    @Test
    fun postCommitFailureIsNotRestoredButLiveOwnerCanStillRetryCompletion() {
        val control = ControlledOperations(failCompletion = true)
        val first = activity()
        val live = owner(first, control.operations).apply {
            openNew()
            updateDraft(validDraft())
            submit()
        }
        eventually { live.saveState is EditorSaveState.PostCommitFailure }

        val restored = owner(activity(capture(first)))
        assertNull(restored.draft)
        assertEquals(EditorSaveState.Idle, restored.saveState)

        live.retryPostCommitCompletion()
        eventually { live.draft == null && live.saveState == EditorSaveState.Idle }
        assertEquals(1, control.roomCalls)
        assertEquals(2, control.completionCalls)
    }

    @Test
    fun unsupportedSnapshotIsDiscardedWithoutRunningOperations() {
        val first = activity()
        lateinit var sourceHandle: SavedStateHandle
        owner(first, onHandle = { sourceHandle = it })
        MedicationEditorSavedStateTestAccess.seedRawSnapshot(
            sourceHandle,
            Bundle().apply { putInt("version", 999) },
        )

        val control = ControlledOperations()
        val restored = owner(activity(capture(first)), control.operations)

        assertNull(restored.draft)
        assertEquals(EditorSaveState.Idle, restored.saveState)
        assertEquals(0, control.roomCalls)
        assertEquals(0, control.completionCalls)
    }

    private fun activity(restoredState: Bundle? = null): SavedStateTestActivity =
        Robolectric.buildActivity(SavedStateTestActivity::class.java)
            .create(restoredState)
            .start()
            .resume()
            .get()

    private fun capture(activity: SavedStateTestActivity) = Bundle().also(activity::saveState)

    private fun owner(
        activity: ComponentActivity,
        operations: MedicationEditorSaveOperations = noOpOperations,
        onHandle: ((SavedStateHandle) -> Unit)? = null,
    ): MedicationEditorViewModel = ViewModelProvider(
        activity,
        factory(activity.application, operations, onHandle),
    )[
        MedicationEditorViewModel::class.java,
    ]

    private fun factory(
        application: Application,
        operations: MedicationEditorSaveOperations,
        onHandle: ((SavedStateHandle) -> Unit)? = null,
    ) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            onHandle?.invoke(savedStateHandle)
            return MedicationEditorViewModel.forTest(
                application,
                savedStateHandle,
                operations,
            ) as T
        }
    }

    private fun validDraft() = EditorDraft(
        id = null,
        name = "Editor medicine",
        instructions = "Once daily",
        enabled = true,
        times = listOf(EditorTime(null, 8 * 60, WeekdayMask.ALL)),
    )

    private fun eventually(condition: () -> Boolean) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Timed out waiting for editor state", condition())
    }

    private class ControlledOperations(
        private val holdRoom: Boolean = false,
        private val failRoom: Boolean = false,
        private val failCompletion: Boolean = false,
    ) {
        val roomStarted = CountDownLatch(1)
        val releaseRoom = CountDownLatch(1)
        var roomCalls = 0
        var completionCalls = 0
        val operations = MedicationEditorSaveOperations(
            applyRoomEdit = { _: MedicationScheduleEdit ->
                roomCalls++
                roomStarted.countDown()
                if (holdRoom) check(releaseRoom.await(5, TimeUnit.SECONDS))
                if (failRoom) throw IllegalStateException("Room edit failed")
                MedicationScheduleEditResult(1L, emptyList(), false)
            },
            completeAlarms = {
                completionCalls++
                if (failCompletion && completionCalls == 1) {
                    throw IllegalStateException("Alarm completion failed")
                }
            },
        )
    }

    private companion object {
        val noOpOperations = MedicationEditorSaveOperations(
            applyRoomEdit = { MedicationScheduleEditResult(1L, emptyList(), false) },
            completeAlarms = {},
        )
    }
}

class SavedStateTestActivity : ComponentActivity() {
    fun saveState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }
}
