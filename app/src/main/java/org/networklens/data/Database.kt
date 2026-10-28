package org.networklens.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.networklens.core.Profile

@Entity(tableName = "events")
data class Event(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val url: String = "")

@Entity(tableName = "attendees", foreignKeys = [ForeignKey(entity = Event::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)], indices = [Index("eventId")])
data class Attendee(@PrimaryKey(autoGenerate = true) val id: Long = 0, val eventId: Long,
    val name: String, val company: String = "", val role: String = "", val category: String = "other", val link: String = "") {
    fun profile() = Profile(id, name, company, role, category)
}

@Entity(tableName = "encounters", foreignKeys = [ForeignKey(entity = Attendee::class, parentColumns = ["id"], childColumns = ["attendeeId"], onDelete = ForeignKey.CASCADE)])
data class Encounter(@PrimaryKey val attendeeId: Long, val lastSeen: Long, val score: Int,
    val evidence: String, val confirmed: Boolean = false, val status: String = "none")

data class PersonRecord(@Embedded val attendee: Attendee, @Relation(parentColumn = "id", entityColumn = "attendeeId") val encounter: Encounter?)

@Dao
interface LensDao {
    @Query("SELECT * FROM events ORDER BY id DESC") fun events(): Flow<List<Event>>
    @Insert suspend fun addEvent(event: Event): Long
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: Long)
    @Insert suspend fun addAttendees(attendees: List<Attendee>)
    @Query("SELECT * FROM attendees WHERE eventId = :eventId ORDER BY name") suspend fun attendees(eventId: Long): List<Attendee>
    @Transaction @Query("SELECT * FROM attendees WHERE eventId = :eventId ORDER BY name") fun people(eventId: Long): Flow<List<PersonRecord>>
    @Query("SELECT * FROM encounters WHERE attendeeId = :id") suspend fun encounter(id: Long): Encounter?
    @Upsert suspend fun saveEncounter(encounter: Encounter)
    @Query("DELETE FROM encounters WHERE attendeeId = :id") suspend fun deleteEncounter(id: Long)
    @Query("DELETE FROM encounters WHERE attendeeId IN (SELECT id FROM attendees WHERE eventId = :eventId)") suspend fun clearHistory(eventId: Long)
    @Query("DELETE FROM encounters WHERE lastSeen < :cutoff") suspend fun expire(cutoff: Long)
    @Query("DELETE FROM events") suspend fun clearAll()
    @Transaction
    suspend fun record(id: Long, score: Int, evidence: String, confirmed: Boolean, now: Long = System.currentTimeMillis()) {
        val previous = encounter(id)
        saveEncounter(Encounter(id, now, score, evidence, confirmed || previous?.confirmed == true, previous?.status ?: "none"))
    }
    @Transaction suspend fun setStatus(id: Long, status: String) {
        val old = encounter(id) ?: Encounter(id, System.currentTimeMillis(), 0, "Manually selected from event list")
        saveEncounter(old.copy(status = status))
    }
}

@Database(entities = [Event::class, Attendee::class, Encounter::class], version = 1, exportSchema = false)
abstract class LensDatabase : RoomDatabase() {
    abstract fun dao(): LensDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context.applicationContext, LensDatabase::class.java, "network-lens.db").build()
    }
}
