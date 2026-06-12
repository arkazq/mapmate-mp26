package com.mapmate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RoutineEntity::class, CommuteRecordEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class MapMateDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

    abstract fun commuteRecordDao(): CommuteRecordDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
