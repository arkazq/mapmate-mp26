package com.mapmate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RoutineEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MapMateDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

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
                    .addMigrations(MIGRATION_1_2)
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
    }
}
