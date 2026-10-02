package com.mapmate.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun roomUpgrade7To8_preservesLegacyRecordAndValidatesFullSchema() = runBlocking {
        seedOlderDatabase(version = 7)

        withRoomDatabase { database ->
            val record = requireNotNull(database.commuteRecordDao().getRecordById(1))
            assertNull(record.targetArrivalAtEpochMillis)
            assertEquals("routine", record.routineName)
            assertEquals(1000L, record.startedAtEpochMillis)
            assertEquals(2000L, record.arrivedAtEpochMillis)
            assertEquals(-5, record.arrivalDeltaMinutes)
            assertEquals(8, database.openHelper.writableDatabase.version)
        }
    }

    @Test
    fun roomUpgrade2To8_handlesColumnsAlreadyCreatedByIntermediateMigrations() {
        seedOlderDatabase(version = 2)

        withRoomDatabase { database ->
            assertEquals(8, database.openHelper.writableDatabase.version)
        }
    }

    @Test
    fun roomUpgrade5To8_handlesStatisticsAlreadyCreatedByMigration5To6() {
        seedOlderDatabase(version = 5)

        withRoomDatabase { database ->
            assertEquals(8, database.openHelper.writableDatabase.version)
        }
    }

    @Test
    fun roomUpgrade6To8_addsMissingStatisticsWithZeroDefaults() {
        seedOlderDatabase(version = 6)

        withRoomDatabase { database ->
            database.openHelper.writableDatabase.query(
                "SELECT averageActualDurationMinutes, minActualDurationMinutes, maxActualDurationMinutes " +
                    "FROM segment_time_adjustments WHERE id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
            }
        }
    }

    @Test
    fun roomUpgrade7To8_preservesTargetEpochAlreadyCreatedByEarlierMigration() = runBlocking {
        seedOlderDatabase(version = 7, targetEpochAlreadyPresent = true)

        withRoomDatabase { database ->
            assertEquals(123456L, database.commuteRecordDao().getRecordById(1)?.targetArrivalAtEpochMillis)
        }
    }

    @Test
    fun roomUpgradeOtherLegacyVersions_preservesRoutineAndValidatesEveryTable() = runBlocking {
        for (version in listOf(1, 3, 4)) {
            context.deleteDatabase(databaseName)
            seedOlderDatabase(version)
            withRoomDatabase { database ->
                val routine = requireNotNull(database.routineDao().getRoutineById(10))
                assertEquals("legacy", routine.name)
                assertEquals("destination", routine.destinationName)
                assertEquals("09:00", routine.targetArrivalTime)
                assertEquals(3, routine.personalBufferMinutes)
                assertEquals(8, database.openHelper.writableDatabase.version)
                if (version == 1) {
                    assertEquals("출발지 미설정", routine.originName)
                    assertNull(routine.originLatitude)
                } else {
                    assertNull(database.commuteRecordDao().getRecordById(1)?.targetArrivalAtEpochMillis)
                    assertEquals("routine", database.commuteRecordDao().getRecordById(1)?.routineName)
                }
            }
        }
    }

    private fun openRoomDatabase(): MapMateDatabase = Room.databaseBuilder(
        context,
        MapMateDatabase::class.java,
        databaseName,
    ).addMigrations(*MapMateDatabase.MIGRATIONS).build()

    private inline fun <T> withRoomDatabase(block: (MapMateDatabase) -> T): T {
        val database = openRoomDatabase()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun seedOlderDatabase(version: Int, targetEpochAlreadyPresent: Boolean = false) {
        // Reuse unchanged tables from Room's current schema, then restore the older table set.
        withRoomDatabase {
            it.openHelper.writableDatabase.execSQL(
                "INSERT INTO routines VALUES (10, 'legacy', 'origin', 'address', 37.5, 127.0, " +
                    "'destination', 'destination address', 37.6, 127.1, '09:00', 'MONDAY', 'TRANSIT', 3, 5, 1000)",
            )
        }
        createOpenHelper(version = 8).use { helper ->
            val db = helper.writableDatabase
            when (version) {
                1, 2, 3, 4 -> {
                    db.execSQL("DROP TABLE route_segments")
                    db.execSQL("DROP TABLE segment_time_adjustments")
                    db.execSQL("DROP TABLE route_estimate_cache")
                    if (version <= 3) db.execSQL("DROP TABLE route_realtime_snapshots")
                    db.execSQL("DROP TABLE commute_records")
                    if (version >= 3) {
                        createVersion7CommuteRecordsTable(db)
                        insertVersion7CommuteRecord(db)
                    }
                    if (version == 1) {
                        db.execSQL("DROP TABLE routines")
                        createVersion1RoutineTable(db)
                    }
                }
                5 -> {
                    db.execSQL("DROP TABLE route_segments")
                    db.execSQL("DROP TABLE segment_time_adjustments")
                    db.execSQL("DROP TABLE commute_records")
                    createVersion7CommuteRecordsTable(db)
                }
                6 -> {
                    db.execSQL("DROP TABLE commute_records")
                    createVersion7CommuteRecordsTable(db)
                    db.execSQL("DROP TABLE segment_time_adjustments")
                    createVersion6AdjustmentsTable(db)
                }
                7 -> {
                    db.execSQL("DROP TABLE commute_records")
                    createVersion7CommuteRecordsTable(db)
                    insertVersion7CommuteRecord(db)
                    if (targetEpochAlreadyPresent) {
                        db.execSQL("ALTER TABLE commute_records ADD COLUMN targetArrivalAtEpochMillis INTEGER")
                        db.execSQL("UPDATE commute_records SET targetArrivalAtEpochMillis = 123456 WHERE id = 1")
                    }
                }
                else -> error("Unsupported fixture version: $version")
            }
            db.version = version
        }
    }

    private fun createVersion1RoutineTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE routines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                destinationName TEXT NOT NULL,
                destinationAddress TEXT NOT NULL,
                destinationLatitude REAL,
                destinationLongitude REAL,
                targetArrivalTime TEXT NOT NULL,
                repeatDays TEXT NOT NULL,
                transportMode TEXT NOT NULL,
                personalBufferMinutes INTEGER NOT NULL,
                safetyMarginMinutes INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO routines VALUES " +
                "(10, 'legacy', 'destination', 'address', 37.6, 127.1, '09:00', 'MONDAY', 'TRANSIT', 3, 5, 1000)",
        )
    }

    private fun createVersion6AdjustmentsTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE segment_time_adjustments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routineId INTEGER NOT NULL,
                segmentType TEXT NOT NULL,
                routeName TEXT,
                startName TEXT,
                endName TEXT,
                averageDelayMinutes INTEGER NOT NULL,
                sampleCount INTEGER NOT NULL,
                confidence REAL NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_segment_time_adjustments_routineId ON segment_time_adjustments(routineId)")
        db.execSQL(
            "CREATE UNIQUE INDEX index_segment_time_adjustments_routineId_segmentType_routeName_startName_endName " +
                "ON segment_time_adjustments(routineId, segmentType, routeName, startName, endName)",
        )
        db.execSQL(
            "INSERT INTO segment_time_adjustments VALUES " +
                "(1, 10, 'BUS_RIDE', '753', 'origin', 'destination', 2, 4, 0.8, 2000)",
        )
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
        db.execSQL("CREATE INDEX index_commute_records_routineId ON commute_records(routineId)")
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
