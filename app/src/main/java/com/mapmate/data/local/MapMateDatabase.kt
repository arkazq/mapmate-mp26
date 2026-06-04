package com.mapmate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RoutineEntity::class],
    version = 1,
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
                ).build().also { instance = it }
            }
        }
    }
}
