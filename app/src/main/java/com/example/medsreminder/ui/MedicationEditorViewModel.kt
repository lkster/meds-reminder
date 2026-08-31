package com.example.medsreminder.ui

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
    private val savedStateHandle: SavedStateHandle,
    private val saveOperations: MedicationEditorSaveOperations,
) : AndroidViewModel(application) {
    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        application,
        savedStateHandle,
        MedicationEditorSaveOperations.production(application),
    )

    // Only editable draft data crosses the process boundary. Save phases and their results remain
    // live-process state because Room is authoritative once Phase A has committed.
    var draft by mutableStateOf(EditorDraftSnapshot.decode(savedStateHandle))
        private set
    var saveState by mutableStateOf<EditorSaveState>(EditorSaveState.Idle)
        private set

    fun openNew() {
        if (draft == null) {
            draft = EditorDraft.new()
            saveDraft(draft!!)
            saveState = EditorSaveState.Idle
        }
    }

    fun openExisting(item: MedicationWithTimes) {
        if (draft == null) {
            draft = EditorDraft.from(item)
            saveDraft(draft!!)
            saveState = EditorSaveState.Idle
        }
    }

    fun updateDraft(nextDraft: EditorDraft) {
        if (!saveState.locksDraft) {
            draft = nextDraft
            saveDraft(nextDraft)
        }
    }

    /** Discards only an idle or pre-commit-failed session; committed work remains retryable. */
    fun cancelIdleEditor() {
        if (!saveState.locksDraft) {
            draft = null
            savedStateHandle.remove<Bundle>(EditorDraftSnapshot.KEY)
            saveState = EditorSaveState.Idle
        }
    }

    fun submit() {
        val submittedDraft = draft ?: return
        if (!saveState.canStartSubmission || !submittedDraft.editorValidation.isValid) return

        // EditorDraft and EditorTime are immutable, but make the submission boundary explicit.
        val snapshot = submittedDraft.copy(times = submittedDraft.times.map { it.copy() })
        // Do this before launching Phase A: a captured state can never replay an uncertain Save.
        savedStateHandle.remove<Bundle>(EditorDraftSnapshot.KEY)
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
                // This is only live saved state until the host performs another capture.
                saveDraft(snapshot)
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

    private fun saveDraft(draft: EditorDraft) {
        savedStateHandle[EditorDraftSnapshot.KEY] = EditorDraftSnapshot.encode(draft)
    }

    companion object {
        internal fun forTest(
            application: Application,
            savedStateHandle: SavedStateHandle,
            operations: MedicationEditorSaveOperations,
        ) = MedicationEditorViewModel(application, savedStateHandle, operations)
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
    var factory: ((Application, SavedStateHandle) -> MedicationEditorViewModel)? = null
}

/** One deliberately narrow, Bundle-compatible editor snapshot. */
private object EditorDraftSnapshot {
    const val KEY = "medication_editor_draft_v1"
    private const val VERSION = 1
    private const val VERSION_KEY = "version"
    private const val ID_PRESENT_KEY = "id_present"
    private const val ID_KEY = "id"
    private const val NAME_KEY = "name"
    private const val INSTRUCTIONS_KEY = "instructions"
    private const val ENABLED_KEY = "enabled"
    private const val REMINDER_COUNT_KEY = "reminder_count"
    private const val REMINDER_ID_PRESENT_KEY = "reminder_id_present"
    private const val REMINDER_IDS_KEY = "reminder_ids"
    private const val MINUTES_KEY = "minutes"
    private const val WEEKDAY_MASKS_KEY = "weekday_masks"

    fun encode(draft: EditorDraft) = Bundle().apply {
        putInt(VERSION_KEY, VERSION)
        putBoolean(ID_PRESENT_KEY, draft.id != null)
        putLong(ID_KEY, draft.id ?: 0L)
        putString(NAME_KEY, draft.name)
        putString(INSTRUCTIONS_KEY, draft.instructions)
        putBoolean(ENABLED_KEY, draft.enabled)
        putInt(REMINDER_COUNT_KEY, draft.times.size)
        putBooleanArray(REMINDER_ID_PRESENT_KEY, draft.times.map { it.id != null }.toBooleanArray())
        putLongArray(REMINDER_IDS_KEY, draft.times.map { it.id ?: 0L }.toLongArray())
        putIntArray(MINUTES_KEY, draft.times.map { it.minuteOfDay }.toIntArray())
        putIntArray(WEEKDAY_MASKS_KEY, draft.times.map { it.weekdayMask }.toIntArray())
    }

    fun decode(handle: SavedStateHandle): EditorDraft? {
        return try {
        val snapshot = handle.get<Any?>(KEY) as? Bundle ?: return null
        if (snapshot.get(VERSION_KEY) !is Int || snapshot.getInt(VERSION_KEY) != VERSION ||
            snapshot.get(ID_PRESENT_KEY) !is Boolean || snapshot.get(ID_KEY) !is Long ||
            snapshot.get(NAME_KEY) !is String || snapshot.get(INSTRUCTIONS_KEY) !is String ||
            snapshot.get(ENABLED_KEY) !is Boolean || snapshot.get(REMINDER_COUNT_KEY) !is Int ||
            snapshot.get(REMINDER_ID_PRESENT_KEY) !is BooleanArray ||
            snapshot.get(REMINDER_IDS_KEY) !is LongArray || snapshot.get(MINUTES_KEY) !is IntArray ||
            snapshot.get(WEEKDAY_MASKS_KEY) !is IntArray
        ) return null

        val count = snapshot.getInt(REMINDER_COUNT_KEY)
        if (count < 0) return null
        val idsPresent = snapshot.getBooleanArray(REMINDER_ID_PRESENT_KEY) ?: return null
        val ids = snapshot.getLongArray(REMINDER_IDS_KEY) ?: return null
        val minutes = snapshot.getIntArray(MINUTES_KEY) ?: return null
        val weekdayMasks = snapshot.getIntArray(WEEKDAY_MASKS_KEY) ?: return null
        if (idsPresent.size != count || ids.size != count || minutes.size != count ||
            weekdayMasks.size != count
        ) return null

        EditorDraft(
            id = if (snapshot.getBoolean(ID_PRESENT_KEY)) snapshot.getLong(ID_KEY) else null,
            name = snapshot.getString(NAME_KEY) ?: return null,
            instructions = snapshot.getString(INSTRUCTIONS_KEY) ?: return null,
            enabled = snapshot.getBoolean(ENABLED_KEY),
            times = List(count) { index ->
                EditorTime(
                    id = if (idsPresent[index]) ids[index] else null,
                    minuteOfDay = minutes[index],
                    weekdayMask = weekdayMasks[index],
                )
            },
        )
        } catch (_: Throwable) {
            null
        }
    }
}

/** Narrow test access for malformed saved-state decoding coverage; not used by production code. */
internal object MedicationEditorSavedStateTestAccess {
    fun seedRawSnapshot(savedStateHandle: SavedStateHandle, snapshot: Bundle) {
        savedStateHandle[EditorDraftSnapshot.KEY] = snapshot
    }
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
