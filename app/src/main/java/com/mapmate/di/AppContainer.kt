package com.mapmate.di

import android.content.Context
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.data.preferences.DataStoreSettingsRepository
import com.mapmate.data.repository.RoomRoutineRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository

class AppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    val routineRepository: RoutineRepository by lazy {
        RoomRoutineRepository(MapMateDatabase.getInstance(applicationContext).routineDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext)
    }
}
