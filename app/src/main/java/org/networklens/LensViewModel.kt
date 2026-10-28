package org.networklens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.networklens.core.*
import org.networklens.data.*

class LensViewModel(app: Application) : AndroidViewModel(app) {
    private val database = LensDatabase.open(app)
    private val dao = database.dao()
    private val prefs = app.getSharedPreferences("privacy", 0)
    val activeEvent = MutableStateFlow<Long?>(null)
    val message = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val remember = MutableStateFlow(prefs.getBoolean("remember", true))
    val retentionDays = MutableStateFlow(prefs.getInt("retention", 30))
    val events = dao.events().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val people = activeEvent.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.people(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { prune() }
    fun report(text: String) { message.value = text }
    private fun work(block: suspend () -> Unit) { viewModelScope.launch {
        busy.value = true
        try { block() } catch (e: Exception) { message.value = e.message ?: "Operation failed" }
        finally { busy.value = false }
    } }
    fun createEvent(name: String, url: String) = work {
        require(name.isNotBlank()) { "Enter an event name" }
        require(url.isBlank() || (Uri.parse(url).scheme == "https" && !Uri.parse(url).host.isNullOrBlank())) { "Use an HTTPS event URL" }
        activeEvent.value = dao.addEvent(Event(name = name.trim(), url = url.trim()))
        message.value = "Event saved. Import an authorized attendee CSV or add attendees manually."
    }
    fun select(id: Long?) { activeEvent.value = id; prune() }
    fun importCsv(uri: Uri) {
        val event = activeEvent.value ?: return
        work {
            val rows = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 2_000_000) { "CSV exceeds 2 MB limit" }
                        output.write(buffer, 0, count)
                    }
                    CsvImport.parse(output.toString("UTF-8"))
                } ?: error("Cannot open CSV")
            }
            val existing = dao.attendees(event).map { listOf(it.name, it.company, it.role, it.category, it.link) }.toSet()
            val fresh = rows.filter { listOf(it.name, it.company, it.role, it.category, it.link) !in existing }
            dao.addAttendees(fresh.map { Attendee(eventId = event, name = it.name, company = it.company, role = it.role, category = it.category, link = it.link) })
            message.value = "Imported ${fresh.size} attendees; ${rows.size - fresh.size} duplicates skipped."
        }
    }
    fun addPerson(name: String, company: String, role: String, category: String) {
        val event = activeEvent.value ?: return
        work {
            require(name.isNotBlank()) { "Enter a name" }
            dao.addAttendees(listOf(Attendee(eventId = event, name = name.trim(), company = company.trim(), role = role.trim(), category = category)))
        }
    }
    fun rememberMatch(id: Long, match: Match?, confirmed: Boolean) {
        if (!remember.value || people.value.none { it.attendee.id == id && it.attendee.eventId == activeEvent.value }) return
        work { dao.record(id, match?.score ?: 0, match?.evidence?.joinToString("\n") ?: "Manually selected from event list", confirmed) }
    }
    fun status(id: Long, status: String) {
        if (!remember.value || people.value.none { it.attendee.id == id && it.attendee.eventId == activeEvent.value }) return
        work { dao.setStatus(id, status) }
    }
    fun deleteEncounter(id: Long) = work { dao.deleteEncounter(id) }
    fun clearHistory() { val id = activeEvent.value ?: return; work { dao.clearHistory(id) } }
    fun deleteEvent() { val id = activeEvent.value ?: return; activeEvent.value = null; work { dao.deleteEvent(id) } }
    fun clearAll() { activeEvent.value = null; work { dao.clearAll() } }
    fun setRemember(enabled: Boolean) { remember.value = enabled; prefs.edit().putBoolean("remember", enabled).apply() }
    fun setRetention(days: Int) { retentionDays.value = days; prefs.edit().putInt("retention", days).apply(); prune() }
    private fun prune() = work { dao.expire(System.currentTimeMillis() - retentionDays.value * 86_400_000L) }
    override fun onCleared() { database.close() }
}
