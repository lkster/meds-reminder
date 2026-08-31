package com.example.medsreminder.data

import androidx.room.withTransaction
import com.example.medsreminder.alarm.AlarmScheduler
import com.example.medsreminder.alarm.NextOccurrenceCalculator
import com.example.medsreminder.alarm.ensureFutureBase
import java.time.Instant
import java.time.ZoneId

data class ReminderScheduleEdit(
    val id: Long?,
    val minuteOfDay: Int,
    val weekdayMask: Int,
)

data class MedicationScheduleEdit(
    val medicationId: Long?,
    val name: String,
    val instructions: String?,
    val enabled: Boolean,
    val reminders: List<ReminderScheduleEdit>,
)

data class MedicationScheduleEditResult(
    val medicationId: Long,
    val obsoleteOccurrenceIds: List<String>,
    val refreshRinging: Boolean,
)

/** The authoritative result of a medication delete transaction. */
sealed interface MedicationDeleteResult {
    data object Stale : MedicationDeleteResult

    data class Deleted(
        val obsoleteOccurrenceIds: List<String>,
        val refreshRinging: Boolean,
    ) : MedicationDeleteResult
}

/**
 * Deletes only the current row identified by [medicationId]. Reminder and occurrence removal is
 * deliberately left to the established foreign-key cascades.
 */
suspend fun AppDatabase.applyMedicationDelete(medicationId: Long): MedicationDeleteResult =
    withTransaction {
        val medication = medicationDao().get(medicationId) ?: return@withTransaction MedicationDeleteResult.Stale
        val occurrenceDao = occurrenceDao()
        val obsoleteOccurrenceIds = occurrenceDao.getMedicationNonterminalIds(medicationId)
        val refreshRinging = occurrenceDao.getCurrentRinging() != null
        medicationDao().deleteMedication(medication)
        MedicationDeleteResult.Deleted(obsoleteOccurrenceIds, refreshRinging)
    }

/**
 * Persists the medication-list Enabled switch without accepting any list snapshot fields.
 * A null result means the medication was deleted before this stale toggle reached Room.
 */
suspend fun AppDatabase.applyMedicationListToggle(
    medicationId: Long,
    enabled: Boolean,
    nowMillis: Long,
    zoneId: ZoneId,
): MedicationScheduleEditResult? = withTransaction {
    val medicationDao = medicationDao()
    val occurrenceDao = occurrenceDao()
    val previous = medicationDao.get(medicationId) ?: return@withTransaction null
    val refreshRinging = occurrenceDao.getCurrentRinging() != null
    val obsoleteIds = mutableListOf<String>()

    if (previous.enabled != enabled) {
        medicationDao.updateMedication(previous.copy(enabled = enabled))
    }

    if (!enabled) {
        obsoleteIds += occurrenceDao.getMedicationNonterminalIds(medicationId)
        occurrenceDao.deleteMedicationNonterminal(medicationId)
    } else {
        medicationDao.getTimes(medicationId).forEach { reminder ->
            ensureFutureBase(
                reminderTimeId = reminder.id,
                minuteOfDay = reminder.minuteOfDay,
                weekdayMask = reminder.weekdayMask,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )
        }
    }

    MedicationScheduleEditResult(
        medicationId = medicationId,
        obsoleteOccurrenceIds = obsoleteIds.distinct(),
        refreshRinging = refreshRinging,
    )
}

suspend fun AppDatabase.applyMedicationScheduleEdit(
    edit: MedicationScheduleEdit,
    nowMillis: Long,
    zoneId: ZoneId,
): MedicationScheduleEditResult {
    require(edit.name.isNotBlank())
    require(edit.reminders.isNotEmpty())
    require(edit.reminders.map { it.minuteOfDay }.distinct().size == edit.reminders.size)
    edit.reminders.forEach {
        require(it.minuteOfDay in 0..1439)
        WeekdayMask.requireValid(it.weekdayMask)
    }

    return withTransaction {
        val medicationDao = medicationDao()
        val occurrenceDao = occurrenceDao()
        val obsoleteIds = mutableListOf<String>()
        val refreshRinging = occurrenceDao.getCurrentRinging() != null
        val previous = edit.medicationId?.let { medicationDao.get(it) }
        val medicationId = if (previous == null) {
            medicationDao.insertMedication(
                MedicationEntity(
                    name = edit.name,
                    instructions = edit.instructions,
                    enabled = edit.enabled,
                ),
            )
        } else {
            medicationDao.updateMedication(
                previous.copy(
                    name = edit.name,
                    instructions = edit.instructions,
                    enabled = edit.enabled,
                ),
            )
            previous.id
        }

        val existing = medicationDao.getTimes(medicationId).associateBy { it.id }
        val retainedIds = edit.reminders.mapNotNull { it.id }.toSet()
        existing.values.filter { it.id !in retainedIds }.forEach { removed ->
            obsoleteIds += occurrenceDao.getNonterminalIds(removed.id)
            medicationDao.deleteTime(removed)
        }

        edit.reminders.forEach { reminderEdit ->
            val old = reminderEdit.id?.let(existing::get)
            when {
                old == null -> medicationDao.insertTime(
                    ReminderTimeEntity(
                        medicationId = medicationId,
                        minuteOfDay = reminderEdit.minuteOfDay,
                        weekdayMask = reminderEdit.weekdayMask,
                    ),
                )

                old.minuteOfDay != reminderEdit.minuteOfDay -> {
                    obsoleteIds += occurrenceDao.getNonterminalIds(old.id)
                    occurrenceDao.deleteNonterminal(old.id)
                    medicationDao.updateTime(
                        old.copy(
                            minuteOfDay = reminderEdit.minuteOfDay,
                            weekdayMask = reminderEdit.weekdayMask,
                        ),
                    )
                }

                old.weekdayMask != reminderEdit.weekdayMask -> {
                    reconcileWeekdayOnlyEdit(
                        occurrenceDao = occurrenceDao,
                        reminderTime = old,
                        newWeekdayMask = reminderEdit.weekdayMask,
                        nowMillis = nowMillis,
                        zoneId = zoneId,
                        obsoleteIds = obsoleteIds,
                    )
                    medicationDao.updateTime(old.copy(weekdayMask = reminderEdit.weekdayMask))
                }
            }
        }

        if (!edit.enabled) {
            obsoleteIds += occurrenceDao.getMedicationNonterminalIds(medicationId)
            occurrenceDao.deleteMedicationNonterminal(medicationId)
        } else {
            medicationDao.getTimes(medicationId).forEach { reminder ->
                ensureFutureBase(
                    reminderTimeId = reminder.id,
                    minuteOfDay = reminder.minuteOfDay,
                    weekdayMask = reminder.weekdayMask,
                    nowMillis = nowMillis,
                    zoneId = zoneId,
                )
            }
        }

        MedicationScheduleEditResult(
            medicationId = medicationId,
            obsoleteOccurrenceIds = obsoleteIds.distinct(),
            refreshRinging = refreshRinging,
        )
    }
}

private suspend fun reconcileWeekdayOnlyEdit(
    occurrenceDao: OccurrenceDao,
    reminderTime: ReminderTimeEntity,
    newWeekdayMask: Int,
    nowMillis: Long,
    zoneId: ZoneId,
    obsoleteIds: MutableList<String>,
) {
    val expectedFutureMillis = NextOccurrenceCalculator.next(
        reminderTime.minuteOfDay,
        newWeekdayMask,
        Instant.ofEpochMilli(nowMillis),
        zoneId,
    ).toEpochMilli()
    var keptCanonicalFuture = false

    occurrenceDao.getScheduledBases(reminderTime.id).forEach { occurrence ->
        val isRecentlyDue = occurrence.scheduledAtEpochMillis <= nowMillis &&
            AlarmScheduler.isWithinDeliveryGrace(occurrence.scheduledAtEpochMillis, nowMillis)
        val dueDayStillSelected = isRecentlyDue && WeekdayMask.contains(
            newWeekdayMask,
            Instant.ofEpochMilli(occurrence.scheduledAtEpochMillis).atZone(zoneId).dayOfWeek,
        )
        val isCanonicalFuture = !keptCanonicalFuture &&
            occurrence.scheduledAtEpochMillis > nowMillis &&
            occurrence.scheduledAtEpochMillis == expectedFutureMillis

        if (dueDayStillSelected || isCanonicalFuture) {
            if (isCanonicalFuture) keptCanonicalFuture = true
        } else if (occurrenceDao.deleteScheduledBase(occurrence.id) == 1) {
            obsoleteIds += occurrence.id
        }
    }
}
