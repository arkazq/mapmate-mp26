package com.mapmate.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapMateDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration7To8_addsNullableTargetArrivalEpochWithoutRewritingExistingRows() {
        val helper = createOpenHelper(version = 7)
        helper.writableDatabase.use { db ->
            createVersion7CommuteRecordsTable(db)
            insertVersion7CommuteRecord(db)
            db.version = 7
        }

        val migratedHelper = createOpenHelper(version = 8)
        migratedHelper.writableDatabase.use { db ->
            MapMateDatabase.MIGRATION_7_8.migrate(db)

            db.query("PRAGMA table_info(commute_records)").use { cursor ->
                var foundColumn = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "targetArrivalAtEpochMillis") {
                        foundColumn = true
                        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                    }
                }
                assertTrue(foundColumn)
            }

            db.query("SELECT targetArrivalAtEpochMillis FROM commute_records WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
    }

    private fun createOpenHelper(version: Int): SupportSQLiteOpenHelper {
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
    }

    private fun createVersion7CommuteRecordsTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE commute_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routineId INTEGER,
                routineName TEXT NOT NULL,
                originName TEXT NOT NULL,
                destinationName TEXT NOT NULL,
                transportMode TEXT NOT NULL,
                targetArrivalTime TEXT NOT NULL,
                recommendedDepartureTime TEXT NOT NULL,
                routeDurationMinutes INTEGER NOT NULL,
                routeSummary TEXT NOT NULL,
                startedAtEpochMillis INTEGER NOT NULL,
                arrivedAtEpochMillis INTEGER NOT NULL,
                arrivalDeltaMinutes INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun insertVersion7CommuteRecord(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO commute_records (
                id,
                routineId,
                routineName,
                originName,
                destinationName,
                transportMode,
                targetArrivalTime,
                recommendedDepartureTime,
                routeDurationMinutes,
                routeSummary,
                startedAtEpochMillis,
                arrivedAtEpochMillis,
                arrivalDeltaMinutes
            ) VALUES (
                1,
                10,
                'routine',
                'origin',
                'destination',
                'TRANSIT',
                '00:30',
                '23:30',
                30,
                'route',
                1000,
                2000,
                -5
            )
            """.trimIndent(),
        )
    }
}
