package org.networklens.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @Test fun eventIsolationRepeatEncounterAndCascade() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LensDatabase::class.java).build()
        try {
            val dao = db.dao()
            val first = dao.addEvent(Event(name = "Fictional One"))
            val second = dao.addEvent(Event(name = "Fictional Two"))
            dao.addAttendees(listOf(Attendee(eventId = first, name = "Alex Example"), Attendee(eventId = second, name = "Alex Example")))
            val a = dao.attendees(first).single(); val b = dao.attendees(second).single()
            assertNotEquals(a.id, b.id)
            dao.record(a.id, 70, "Name", true, 100)
            dao.setStatus(a.id, "follow up")
            dao.record(a.id, 95, "Name + company", true, 200)
            assertEquals(200L, dao.encounter(a.id)?.lastSeen)
            assertEquals("follow up", dao.encounter(a.id)?.status)
            assertNull(dao.encounter(b.id))
            dao.deleteEvent(first)
            assertNull(dao.encounter(a.id)); assertEquals(1, dao.attendees(second).size)
            dao.clearAll(); assertTrue(dao.attendees(second).isEmpty())
        } finally { db.close() }
    }
    @Test fun retentionAndIndividualDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LensDatabase::class.java).build()
        try {
            val dao = db.dao(); val event = dao.addEvent(Event(name = "Fictional"))
            dao.addAttendees(listOf(Attendee(eventId = event, name = "Alex Example")))
            val id = dao.attendees(event).single().id
            dao.record(id, 70, "test", true, 1)
            dao.expire(2); assertNull(dao.encounter(id)); assertEquals(1, dao.attendees(event).size)
            dao.record(id, 70, "test", true, 3); dao.deleteEncounter(id); assertNull(dao.encounter(id))
        } finally { db.close() }
    }
}
