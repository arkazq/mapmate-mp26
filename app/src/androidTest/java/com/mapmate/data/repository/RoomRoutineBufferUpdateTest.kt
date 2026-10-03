package com.mapmate.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRoutineBufferUpdateTest {
    private lateinit var database: MapMateDatabase
    private lateinit var repository: RoomRoutineRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MapMateDatabase::class.java).build()
        repository = RoomRoutineRepository(database.routineDao())
    }
    @After fun tearDown() = database.close()

    @Test fun atomicBufferUpdatePreservesOtherFields() = runBlocking {
        val original = saveRoutine()
        assertTrue(repository.updatePersonalBufferMinutes(original, 6))
        assertEquals(original.copy(personalBufferMinutes = 6), repository.observeRoutines().first().single())
    }

    @Test fun staleSnapshotCannotOverwriteConcurrentEdit() = runBlocking {
        val original = saveRoutine()
        val changed = original.copy(name = "Renamed", targetArrivalTime = LocalTime.of(10, 0))
        repository.saveRoutine(changed)
        assertFalse(repository.updatePersonalBufferMinutes(original, 6))
        assertEquals(changed, repository.observeRoutines().first().single())
    }

    @Test fun deletedRoutineIsNotReinserted() = runBlocking {
        val original = saveRoutine()
        repository.deleteRoutine(requireNotNull(original.id))
        assertFalse(repository.updatePersonalBufferMinutes(original, 6))
        assertTrue(repository.observeRoutines().first().isEmpty())
    }

    @Test fun invalidBufferCannotModifyRow() = runBlocking {
        val original = saveRoutine()
        assertTrue(runCatching { repository.updatePersonalBufferMinutes(original, -1) }.isFailure)
        assertEquals(original, repository.observeRoutines().first().single())
    }

    private suspend fun saveRoutine(): Routine {
        val routine = Routine(
            name = "Test commute",
            origin = Destination("Origin", "Origin address", 37.5, 127.0),
            destination = Destination("Destination", "Destination address", 37.6, 127.1),
            targetArrivalTime = LocalTime.of(9, 0), repeatDays = RepeatDay.entries.toSet(),
            transportMode = TransportMode.TRANSIT, personalBufferMinutes = 3, safetyMarginMinutes = 5,
        )
        return routine.copy(id = repository.saveRoutine(routine))
    }
}
