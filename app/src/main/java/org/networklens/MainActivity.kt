package org.networklens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.networklens.camera.CameraScanner
import org.networklens.core.*
import org.networklens.data.*
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7BE4B0), secondary = Color(0xFFE8CC79),
                background = Color(0xFF0B151C), surface = Color(0xFF14232D))) {
                Surface(Modifier.fillMaxSize()) { LensApp() }
            }
        }
    }
}

@Composable
internal fun LensApp(vm: LensViewModel = viewModel()) {
    val events by vm.events.collectAsStateWithLifecycle()
    val active by vm.activeEvent.collectAsStateWithLifecycle()
    val storedPeople by vm.people.collectAsStateWithLifecycle()
    val people = storedPeople.filter { it.attendee.eventId == active }
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable(active) { mutableStateOf("People") }
    var addEvent by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    var selectedId by remember(active) { mutableStateOf<Long?>(null) }
    var observation by remember(active) { mutableStateOf<BadgeObservation?>(null) }
    var scanMatch by remember(active) { mutableStateOf<Match?>(null) }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { if (message.isNotBlank()) { snackbar.showSnackbar(message); vm.report("") } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (active != null) NavigationBar {
            listOf("Scan", "People", "History", "Privacy").forEach { item ->
                NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Text(when(item) { "Scan" -> "◎"; "People" -> "☷"; "History" -> "◷"; else -> "◇" }) }, label = { Text(item) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NETWORK LENS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(events.firstOrNull { it.id == active }?.name ?: "Your next conversation", style = MaterialTheme.typography.titleLarge)
                }
                if (active != null) TextButton(onClick = { vm.select(null) }) { Text("Events") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (active == null) {
                LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("Read a badge. Find a connection.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("Private, on-device OCR and event matching. Import only attendee information you are authorized to use.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { addEvent = true }) { Text("Create event") }
                    }
                    items(events, key = { it.id }) { event ->
                        Card(Modifier.fillMaxWidth().clickable { vm.select(event.id) }) {
                            Column(Modifier.padding(18.dp)) { Text(event.name, style = MaterialTheme.typography.titleMedium); Text("Open saved event", color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    item { Text("No facial identification. No camera footage saved. No account required.", style = MaterialTheme.typography.bodySmall) }
                }
            } else when(tab) {
                "Scan" -> if (selectedId == null && observation == null) ScanScreen(people.map { it.attendee.profile() }, { observation = it }, vm::report)
                "Privacy" -> PrivacyScreen(vm)
                else -> PeopleScreen(people, tab == "History", { selectedId = it; scanMatch = null },
                    { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }, { addPerson = true })
            }
        }
    }
    if (addEvent) EventDialog({ addEvent = false }) { name, url -> vm.createEvent(name, url); addEvent = false }
    if (addPerson) PersonDialog({ addPerson = false }) { name, company, role, category -> vm.addPerson(name, company, role, category); addPerson = false }
    observation?.let { badge ->
        AlertDialog(onDismissRequest = { observation = null }, title = { Text("Check the badge") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(badge.text)
                Text("Scores measure text evidence, not identity probability. Confirm the badge belongs to the person you are speaking with.", style = MaterialTheme.typography.bodySmall)
                if (badge.candidates.isEmpty()) Text("No full-name match. Use People search or add this attendee manually.")
                badge.candidates.forEach { match ->
                    TextButton(onClick = { scanMatch = match; selectedId = match.profile.id; observation = null }) {
                        Text("${match.profile.name}\n${match.profile.company} · ${match.score}% estimated score${if (match.ambiguous) " · ambiguous" else ""}")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { observation = null; tab = "People" }) { Text("Search / correct") } },
            dismissButton = { TextButton(onClick = { observation = null }) { Text("Dismiss") } })
    }
    people.firstOrNull { it.attendee.id == selectedId }?.let { record ->
        ProfileDialog(record, scanMatch, vm, { selectedId = null; scanMatch = null }, {
            vm.deleteEncounter(record.attendee.id); selectedId = null; scanMatch = null; tab = "People"
        })
    }
}

@Composable
private fun ScanScreen(profiles: List<Profile>, tap: (BadgeObservation) -> Unit, error: (String) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    if (!allowed) Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Camera access", style = MaterialTheme.typography.headlineSmall)
        Text("Use the rear camera to read badges. Images stay in memory and are never recorded. You can still search attendees without camera access.")
        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Open app permissions") }
        TextButton(onClick = { allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }) { Text("Check permission again") }
    } else Box(Modifier.fillMaxSize()) {
        CameraScanner(profiles, Modifier.fillMaxSize(), tap, error)
        Text("LIVE · ON DEVICE\nPoint at one badge; tap its label to confirm.\nFaces remain anonymous. Scores are uncalibrated.",
            Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xDD0B151C)).padding(10.dp), style = MaterialTheme.typography.labelMedium)
        if (profiles.isEmpty()) Text("Import attendees in People to enable matching.", Modifier.align(Alignment.BottomCenter).background(Color(0xDD0B151C)).padding(16.dp))
    }
}

@Composable
private fun PeopleScreen(people: List<PersonRecord>, history: Boolean, open: (Long) -> Unit, import: () -> Unit, add: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var status by rememberSaveable { mutableStateOf("all") }
    val encountered = people.filter { it.encounter != null }
    val visible = people.filter { (!history || it.encounter != null) &&
        (category == "all" || it.attendee.category == category) &&
        (status == "all" || it.encounter?.status == status) &&
        "${it.attendee.name} ${it.attendee.company} ${it.attendee.role}".contains(query, ignoreCase = true) }
        .let { if (history) it.sortedByDescending { p -> p.encounter?.lastSeen } else it }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(if (history) "Your connections" else "Event directory", style = MaterialTheme.typography.headlineSmall)
            Text("${encountered.size} saved · ${encountered.count { it.attendee.category == "investor" }} investors · ${encountered.count { it.attendee.category == "startup" }} startup · ${encountered.count { it.attendee.category == "other" }} other", style = MaterialTheme.typography.bodySmall)
            Text("Counts reflect saved badge profiles, not everyone visible to the camera.", style = MaterialTheme.typography.bodySmall)
            if (!history) Row { TextButton(onClick = import) { Text("Import CSV") }; TextButton(onClick = add) { Text("Add attendee") } }
            OutlinedTextField(query, { query = it }, label = { Text("Search name, company, role") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        item { ChoiceRow(listOf("all", "investor", "startup", "other"), category) { category = it } }
        if (history) item { ChoiceRow(listOf("all", "interested", "met", "follow up", "not relevant"), status) { status = it } }
        if (visible.isEmpty()) item { Text(if (history) "No saved encounters yet. Tap a badge match and confirm it." else "No attendees found. CSV columns: name, company, role, category, link. Categories: investor, startup, other.") }
        items(visible, key = { it.attendee.id }) { person ->
            Card(Modifier.fillMaxWidth().clickable { open(person.attendee.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(person.attendee.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(person.attendee.role, person.attendee.company).filter { it.isNotBlank() }.joinToString(" · "))
                    Text("${person.attendee.category} · imported profile", color = categoryColor(person.attendee.category), style = MaterialTheme.typography.labelMedium)
                    person.encounter?.let { e -> Text("${e.status.takeUnless { it == "none" } ?: "Saved"} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.lastSeen))}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(values: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { value -> FilterChip(selected == value, { onSelect(value) }, label = { Text(value) }) } }
}

@Composable
private fun ProfileDialog(person: PersonRecord, match: Match?, vm: LensViewModel, close: () -> Unit, correct: () -> Unit) {
    val p = person.attendee
    val context = LocalContext.current
    val remember by vm.remember.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = close, title = { Text(p.name) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${p.role}\n${p.company}")
            Text("${p.category} · supplied by event import; not independently verified", color = categoryColor(p.category))
            Text(if (match != null) "${match.score}% estimated match score · not calibrated" else "Manual directory selection · no identity score")
            (match?.evidence ?: person.encounter?.evidence?.lines() ?: emptyList()).forEach { Text("• $it") }
            Text(if (person.encounter?.confirmed == true) "Previously confirmed by you in this event. Check the current badge again." else "Confirm against the attendee's badge before saving.")
            if (p.link.startsWith("https://")) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.link))) }.onFailure { vm.report("No browser available") }
            }) { Text("Open supplied professional link") }
            if (remember) {
                ChoiceRow(listOf("interested", "met", "follow up", "not relevant"), person.encounter?.status ?: "none") { vm.status(p.id, it) }
                TextButton(onClick = { vm.deleteEncounter(p.id); close() }) { Text("Delete saved encounter") }
            } else Text("Encounter saving is disabled in Privacy.")
            TextButton(onClick = correct) { Text("Reject / select a different attendee") }
        }
    }, confirmButton = { TextButton(onClick = { if (remember) vm.rememberMatch(p.id, match, true); close() }) { Text(if (remember) "Confirm & save" else "Done") } },
        dismissButton = { TextButton(onClick = close) { Text("Close") } })
}

@Composable
private fun EventDialog(close: () -> Unit, create: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("New event") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(160) }, label = { Text("Event name") })
            OutlinedTextField(url, { url = it.take(2000) }, label = { Text("Event URL (optional)") })
            Text("Luma and other URLs are saved as references. This version does not download guest lists. Import an organizer-provided CSV instead.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { create(name, url) }) { Text("Create") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun PersonDialog(close: () -> Unit, add: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var company by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }; var category by remember { mutableStateOf("other") }
    AlertDialog(onDismissRequest = close, title = { Text("Add attendee") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(160) }, label = { Text("Full name") })
            OutlinedTextField(company, { company = it.take(200) }, label = { Text("Company") })
            OutlinedTextField(role, { role = it.take(200) }, label = { Text("Role") })
            ChoiceRow(listOf("investor", "startup", "other"), category) { category = it }
            Text("Use information provided by the attendee. Leave category as other if uncertain.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { add(name, company, role, category) }) { Text("Add") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable
private fun PrivacyScreen(vm: LensViewModel) {
    val remember by vm.remember.collectAsStateWithLifecycle()
    val retention by vm.retentionDays.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Private by design", style = MaterialTheme.typography.headlineMedium)
        Text("Camera frames are processed on device and discarded. No facial identities, embeddings, recordings, accounts, analytics, or cloud services. Faces are only anonymous outlines.")
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Save confirmed encounters", Modifier.weight(1f)); Switch(remember, vm::setRemember) }
        Text("Disabling saving leaves existing history intact. Clear it below if desired. Repeat visits use attendee IDs and badge text, never faces.")
        Text("Keep encounter history for $retention days")
        ChoiceRow(listOf("7", "30", "90"), retention.toString()) { vm.setRetention(it.toInt()) }
        Text("Expired encounters are removed when you open or switch events. Imported attendees remain until you delete the event.", style = MaterialTheme.typography.bodySmall)
        Text("Data is stored in Android's private app storage, with backups disabled. There is no additional application-level database encryption. Protect your phone with a screen lock.")
        OutlinedButton(onClick = { deleting = "history" }) { Text("Clear this event's encounter history") }
        OutlinedButton(onClick = { deleting = "event" }) { Text("Delete this event and its attendees") }
        OutlinedButton(onClick = { deleting = "all" }) { Text("Delete all local data") }
        Text("Network Lens 0.1.0 · Apache 2.0 source\nML Kit libraries are governed by Google's terms. Imported profiles and category labels are not independently verified.", style = MaterialTheme.typography.bodySmall)
    }
    if (deleting.isNotBlank()) AlertDialog(onDismissRequest = { deleting = "" }, title = { Text("Delete $deleting?") }, text = { Text("This cannot be undone. Camera scanning stops when leaving this screen.") },
        confirmButton = { TextButton(onClick = { when(deleting) { "history" -> vm.clearHistory(); "event" -> vm.deleteEvent(); else -> vm.clearAll() }; deleting = "" }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = "" }) { Text("Cancel") } })
}

private fun categoryColor(category: String) = when(category) { "investor" -> Color(0xFF7BE4B0); "startup" -> Color(0xFFE8CC79); else -> Color(0xFFAFBCC5) }
