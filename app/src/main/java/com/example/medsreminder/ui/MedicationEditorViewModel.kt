package com.example.medsreminder.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medsreminder.alarm.AlarmRingingService
import com.example.medsreminder.alarm.AlarmReconciler
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.alarm.ReconciliationMode
import com.example.medsreminder.data.AppDatabase
import com.example.medsreminder.data.MedicationScheduleEdit
import com.example.medsreminder.data.MedicationScheduleEditResult
import com.example.medsreminder.data.MedicationWithTimes
import com.example.medsreminder.data.ReminderScheduleEdit
import com.example.medsreminder.data.applyMedicationScheduleEdit
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one configuration-retained owner for a medication editor session. It intentionally keeps
 * no durable operation journal: process death falls back to Room's authoritative state.
 */
class MedicationEditorViewModel private constructor(
    application: Application,
    private val saveOperations: MedicationEditorSaveOperations,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        MedicationEditorSaveOperations.production(application),
    )

    var draft by mutableStateOf<EditorDraft?>(null)
        private set
    var saveState by mutableStateOf<EditorSaveState>(EditorSaveState.Idle)
        private set

    fun openNew() {
        if (draft == null) {
            draft = EditorDraft.new()
            saveState = EditorSaveState.Idle
        }
    }

    fun openExisting(item: MedicationWithTimes) {
        if (draft == null) {
            draft = EditorDraft.from(item)
            saveState = EditorSaveState.Idle
        }
    }

    fun updateDraft(nextDraft: EditorDraft) {
        if (!saveState.locksDraft) draft = nextDraft
    }

    /** Discards only an idle or pre-commit-failed session; committed work remains retryable. */
    fun cancelIdleEditor() {
        if (!saveState.locksDraft) {
            draft = null
            saveState = EditorSaveState.Idle
        }
    }

    fun submit() {
        val submittedDraft = draft ?: return
        if (!saveState.canStartSubmission || !submittedDraft.editorValidation.isValid) return

        // EditorDraft and EditorTime are immutable, but make the submission boundary explicit.
        val snapshot = submittedDraft.copy(times = submittedDraft.times.map { it.copy() })
        saveState = EditorSaveState.SavingRoom
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    saveOperations.applyRoomEdit(snapshot.toScheduleEdit())
                }
                // This assignment is the explicit durable Room-commit boundary.
                saveState = EditorSaveState.CompletingAlarms(result)
                finishCommittedSave(result)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                saveState = EditorSaveState.RoomFailure(error.displayMessage())
            }
        }
    }

    fun retryPostCommitCompletion() {
        val result = (saveState as? EditorSaveState.PostCommitFailure)?.result ?: return
        saveState = EditorSaveState.CompletingAlarms(result)
        viewModelScope.launch { finishCommittedSave(result) }
    }

    private suspend fun finishCommittedSave(result: MedicationScheduleEditResult) {
        try {
            withContext(Dispatchers.IO) {
                saveOperations.completeAlarms(result)
            }
            draft = null
            saveState = EditorSaveState.Idle
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // Do not repeat Room work: retry receives this exact result.
            saveState = EditorSaveState.PostCommitFailure(result, error.displayMessage())
        }
    }

    private fun EditorDraft.toScheduleEdit() = MedicationScheduleEdit(
        medicationId = id,
        name = name.trim(),
        instructions = instructions.trim().ifBlank { null },
        enabled = enabled,
        reminders = times.map { ReminderScheduleEdit(it.id, it.minuteOfDay, it.weekdayMask) },
    )

    private fun Throwable.displayMessage(): String = message ?: "Unknown error"

    companion object {
        internal fun forTest(
            application: Application,
            operations: MedicationEditorSaveOperations,
        ) = MedicationEditorViewModel(application, operations)
    }
}

/** Narrow editor-save seam: production still uses the existing Room and alarm implementations. */
internal class MedicationEditorSaveOperations(
    val applyRoomEdit: suspend (MedicationScheduleEdit) -> MedicationScheduleEditResult,
    val completeAlarms: suspend (MedicationScheduleEditResult) -> Unit,
) {
    companion object {
        fun production(application: Application): MedicationEditorSaveOperations {
            val database = AppDatabase.get(application)
            val scheduler = AlarmScheduler(application)
            val reconciler = AlarmReconciler(application, database, scheduler)
            return MedicationEditorSaveOperations(
                applyRoomEdit = { edit ->
                    database.applyMedicationScheduleEdit(
                        edit = edit,
                        nowMillis = System.currentTimeMillis(),
                        zoneId = ZoneId.systemDefault(),
                    )
                },
                completeAlarms = { result ->
                    result.obsoleteOccurrenceIds.forEach(scheduler::cancelOccurrence)
                    reconciler.reconcile(ReconciliationMode.ROUTINE)
                    if (result.refreshRinging) {
                        AlarmRingingService.synchronizeWithPersistedQueue(application, database)
                    }
                },
            )
        }
    }
}

/** Test-only Activity factory override; production leaves this null. */
internal object MedicationEditorViewModelTestHook {
    @Volatile
    var factory: ((Application) -> MedicationEditorViewModel)? = null
}

sealed interface EditorSaveState {
    data object Idle : EditorSaveState
    data object SavingRoom : EditorSaveState
    data class RoomFailure(val message: String) : EditorSaveState
    data class CompletingAlarms(val result: MedicationScheduleEditResult) : EditorSaveState
    data class PostCommitFailure(
        val result: MedicationScheduleEditResult,
        val message: String,
    ) : EditorSaveState
}

val EditorSaveState.locksDraft: Boolean
    get() = this is EditorSaveState.SavingRoom ||
        this is EditorSaveState.CompletingAlarms ||
        this is EditorSaveState.PostCommitFailure

val EditorSaveState.canStartSubmission: Boolean
    get() = this is EditorSaveState.Idle || this is EditorSaveState.RoomFailure
