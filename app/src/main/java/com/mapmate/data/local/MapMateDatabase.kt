package com.mapmate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RoutineEntity::class,
        CommuteRecordEntity::class,
        RouteRealtimeSnapshotEntity::class,
        RouteEstimateCacheEntity::class,
        RouteSegmentEntity::class,
        SegmentTimeAdjustmentEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class MapMateDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

    abstract fun commuteRecordDao(): CommuteRecordDao

    abstract fun routeRealtimeSnapshotDao(): RouteRealtimeSnapshotDao

    abstract fun routeEstimateCacheDao(): RouteEstimateCacheDao

    abstract fun routeSegmentDao(): RouteSegmentDao

    abstract fun segmentTimeAdjustmentDao(): SegmentTimeAdjustmentDao

    companion object {
        private const val DATABASE_NAME = "mapmate.db"

        @Volatile
        private var instance: MapMateDatabase? = null

        fun getInstance(context: Context): MapMateDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MapMateDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                    )
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routines ADD COLUMN originName TEXT NOT NULL DEFAULT '출발지 미설정'")
                db.execSQL("ALTER TABLE routines ADD COLUMN originAddress TEXT NOT NULL DEFAULT '기존 루틴에는 출발지가 없습니다.'")
                db.execSQL("ALTER TABLE routines ADD COLUMN originLatitude REAL")
                db.execSQL("ALTER TABLE routines ADD COLUMN originLongitude REAL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS commute_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routineId INTEGER,
                        routineName TEXT NOT NULL,
                        originName TEXT NOT NULL,
                        destinationName TEXT NOT NULL,
                        transportMode TEXT NOT NULL,
                        targetArrivalTime TEXT NOT NULL,
                        targetArrivalAtEpochMillis INTEGER,
                        recommendedDepartureTime TEXT NOT NULL,
                        routeDurationMinutes INTEGER NOT NULL,
                        routeSummary TEXT NOT NULL,
                        startedAtEpochMillis INTEGER NOT NULL,
                        arrivedAtEpochMillis INTEGER NOT NULL,
                        arrivalDeltaMinutes INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commute_records_routineId ON commute_records(routineId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS route_realtime_snapshots (
                        cacheKey TEXT NOT NULL,
                        baseRouteDurationMinutes INTEGER NOT NULL,
                        adjustedRouteDurationMinutes INTEGER NOT NULL,
                        realtimeDelayMinutes INTEGER NOT NULL,
                        providerName TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        capturedAtEpochMillis INTEGER NOT NULL,
                        expiresAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(cacheKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_route_realtime_snapshots_expiresAtEpochMillis
                    ON route_realtime_snapshots(expiresAtEpochMillis)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS route_estimate_cache (
                        cacheKey TEXT NOT NULL,
                        cacheNamespace TEXT NOT NULL,
                        transportMode TEXT NOT NULL,
                        originName TEXT NOT NULL,
                        originAddress TEXT NOT NULL,
                        originLatitude REAL,
                        originLongitude REAL,
                        destinationName TEXT NOT NULL,
                        destinationAddress TEXT NOT NULL,
                        destinationLatitude REAL,
                        destinationLongitude REAL,
                        estimatedMinutes INTEGER NOT NULL,
                        summary TEXT NOT NULL,
                        providerName TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        capturedAtEpochMillis INTEGER NOT NULL,
                        expiresAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(cacheKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_route_estimate_cache_expiresAtEpochMillis
                    ON route_estimate_cache(expiresAtEpochMillis)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS route_segments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        commuteRecordId INTEGER NOT NULL,
                        routineId INTEGER,
                        segmentIndex INTEGER NOT NULL,
                        segmentType TEXT NOT NULL,
                        trafficType INTEGER,
                        routeName TEXT,
                        startName TEXT,
                        endName TEXT,
                        plannedDurationMinutes INTEGER NOT NULL,
                        actualStartedAtEpochMillis INTEGER,
                        actualEndedAtEpochMillis INTEGER,
                        actualDurationMinutes INTEGER,
                        isUserEdited INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_route_segments_commuteRecordId ON route_segments(commuteRecordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_route_segments_routineId ON route_segments(routineId)")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_route_segments_routineId_segmentType_routeName_startName_endName
                    ON route_segments(routineId, segmentType, routeName, startName, endName)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS segment_time_adjustments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routineId INTEGER NOT NULL,
                        segmentType TEXT NOT NULL,
                        routeName TEXT,
                        startName TEXT,
                        endName TEXT,
                        averageDelayMinutes INTEGER NOT NULL,
                        averageActualDurationMinutes INTEGER NOT NULL DEFAULT 0,
                        minActualDurationMinutes INTEGER NOT NULL DEFAULT 0,
                        maxActualDurationMinutes INTEGER NOT NULL DEFAULT 0,
                        sampleCount INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_segment_time_adjustments_routineId ON segment_time_adjustments(routineId)")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_segment_time_adjustments_routineId_segmentType_routeName_startName_endName
                    ON segment_time_adjustments(routineId, segmentType, routeName, startName, endName)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE segment_time_adjustments " +
                        "ADD COLUMN averageActualDurationMinutes INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE segment_time_adjustments " +
                        "ADD COLUMN minActualDurationMinutes INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE segment_time_adjustments " +
                        "ADD COLUMN maxActualDurationMinutes INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commute_records ADD COLUMN targetArrivalAtEpochMillis INTEGER")
            }
        }
    }
}
