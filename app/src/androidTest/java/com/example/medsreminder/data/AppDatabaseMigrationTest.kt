package com.example.medsreminder.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun cleanBefore() {
        context.deleteDatabase(MIGRATION_DB)
        context.deleteDatabase(FRESH_DB)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(MIGRATION_DB)
        context.deleteDatabase(FRESH_DB)
    }

    @Test
    fun migrateV1ToV2BackfillsEveryDayAndPreservesIdsAndOccurrences() = runBlocking {
        helper.createDatabase(MIGRATION_DB, 1).apply {
            execSQL(
                "INSERT INTO medications (id, name, instructions, enabled) VALUES (41, 'Medicine', 'With food', 1)",
            )
            execSQL(
                "INSERT INTO reminder_times (id, medication_id, minute_of_day) VALUES (91, 41, 480)",
            )
            execSQL(
                "INSERT INTO reminder_times (id, medication_id, minute_of_day) VALUES (92, 41, 1200)",
            )
            execSQL(
                "INSERT INTO alarm_occurrences " +
                    "(id, reminder_time_id, kind, scheduled_at_epoch_millis, status, presented_at_epoch_millis, resolved_at_epoch_millis) " +
                    "VALUES ('base-future', 91, 'BASE', 2000000, 'SCHEDULED', NULL, NULL)",
            )
            execSQL(
                "INSERT INTO alarm_occurrences " +
                    "(id, reminder_time_id, kind, scheduled_at_epoch_millis, status, presented_at_epoch_millis, resolved_at_epoch_millis) " +
                    "VALUES ('snooze-terminal', 91, 'SNOOZE', 1500000, 'TAKEN', 1500100, 1500200)",
            )
            execSQL(
                "INSERT INTO alarm_occurrences " +
                    "(id, reminder_time_id, kind, scheduled_at_epoch_millis, status, presented_at_epoch_millis, resolved_at_epoch_millis) " +
                    "VALUES ('base-ringing', 92, 'BASE', 1600000, 'RINGING', 1600100, NULL)",
            )
            execSQL(
                "INSERT INTO alarm_occurrences " +
                    "(id, reminder_time_id, kind, scheduled_at_epoch_millis, status, presented_at_epoch_millis, resolved_at_epoch_millis) " +
                    "VALUES ('snooze-pending', 92, 'SNOOZE', 2500000, 'SCHEDULED', NULL, NULL)",
            )
            close()
        }

        helper.runMigrationsAndValidate(MIGRATION_DB, 2, true, MIGRATION_1_2).close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, MIGRATION_DB)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            val medication = migrated.medicationDao().getWithTimes(41)
            assertNotNull(medication)
            assertEquals(listOf(91L, 92L), medication?.reminderTimes?.sortedBy { it.id }?.map { it.id })
            assertTrue(medication?.reminderTimes?.all { it.weekdayMask == WeekdayMask.ALL } == true)
            assertEquals(OccurrenceStatus.SCHEDULED, migrated.occurrenceDao().get("base-future")?.status)
            val snooze = migrated.occurrenceDao().get("snooze-terminal")
            assertEquals(OccurrenceKind.SNOOZE, snooze?.kind)
            assertEquals(OccurrenceStatus.TAKEN, snooze?.status)
            assertEquals(1_500_100L, snooze?.presentedAtEpochMillis)
            assertEquals(1_500_200L, snooze?.resolvedAtEpochMillis)
            val ringing = migrated.occurrenceDao().get("base-ringing")
            assertEquals("base-ringing", ringing?.id)
            assertEquals(OccurrenceKind.BASE, ringing?.kind)
            assertEquals(OccurrenceStatus.RINGING, ringing?.status)
            assertEquals(1_600_000L, ringing?.scheduledAtEpochMillis)
            assertEquals(1_600_100L, ringing?.presentedAtEpochMillis)
            assertNull(ringing?.resolvedAtEpochMillis)
            val pendingSnooze = migrated.occurrenceDao().get("snooze-pending")
            assertEquals("snooze-pending", pendingSnooze?.id)
            assertEquals(OccurrenceKind.SNOOZE, pendingSnooze?.kind)
            assertEquals(OccurrenceStatus.SCHEDULED, pendingSnooze?.status)
            assertEquals(2_500_000L, pendingSnooze?.scheduledAtEpochMillis)
            assertNull(pendingSnooze?.presentedAtEpochMillis)
            assertNull(pendingSnooze?.resolvedAtEpochMillis)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun freshV2SchemaUsesSameNonNullEveryDayDefault() {
        val fresh = Room.databaseBuilder(context, AppDatabase::class.java, FRESH_DB).build()
        try {
            val sqlDb = fresh.openHelper.writableDatabase
            sqlDb.execSQL(
                "INSERT INTO medications (id, name, instructions, enabled) VALUES (1, 'Fresh', NULL, 1)",
            )
            sqlDb.execSQL(
                "INSERT INTO reminder_times (id, medication_id, minute_of_day) VALUES (2, 1, 600)",
            )
            sqlDb.query("SELECT weekday_mask FROM reminder_times WHERE id = 2").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(WeekdayMask.ALL, cursor.getInt(0))
            }
            val nullFailure = runCatching {
                sqlDb.execSQL(
                    "INSERT INTO reminder_times (id, medication_id, minute_of_day, weekday_mask) " +
                        "VALUES (3, 1, 660, NULL)",
                )
            }
            assertTrue(nullFailure.isFailure)
        } finally {
            fresh.close()
        }
    }

    companion object {
        private const val MIGRATION_DB = "m2-migration-test"
        private const val FRESH_DB = "m2-fresh-test"
    }
}
