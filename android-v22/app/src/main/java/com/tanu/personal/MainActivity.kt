package com.tanu.personal

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanu.personal.data.ActionItemEntity
import com.tanu.personal.data.AiMode
import com.tanu.personal.data.MeetingEntity
import com.tanu.personal.data.MeetingStatus
import com.tanu.personal.data.MomEntity
import com.tanu.personal.service.RecordingService
import com.tanu.personal.ui.MainViewModel
import com.tanu.personal.ui.TanuBlue
import com.tanu.personal.ui.TanuInk
import com.tanu.personal.ui.TanuPink
import com.tanu.personal.ui.TanuPurple
import com.tanu.personal.ui.TanuTheme
import com.tanu.personal.util.PdfExporter
import com.tanu.personal.util.jsonList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TanuTheme { TanuApp(intent) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private object Route {
    const val HOME = "home"
    const val NEW = "new"
    const val RECORDING = "recording"
    const val MEETINGS = "meetings"
    const val ACTIONS = "actions"
    const val PEOPLE = "people"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{id}"
    fun detail(id: String) = "detail/$id"
}

@Composable
private fun TanuApp(startIntent: Intent, vm: MainViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current
    var showRetentionSetup by remember { mutableStateOf(!vm.settings.retentionConfigured) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val file = copyImport(context, uri)
            vm.importAudio(file, "Imported conversation") { id -> nav.navigate(Route.detail(id)) }
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())

        val active = RecordingService.activeMeetingId
        if (active != null) {
            vm.selectMeeting(active)
            nav.navigate(Route.RECORDING)
        } else {
            val openMeeting = startIntent.getStringExtra("open_meeting_id")
            when {
                !openMeeting.isNullOrBlank() -> {
                    vm.selectMeeting(openMeeting)
                    nav.navigate(Route.detail(openMeeting))
                }
                startIntent.getBooleanExtra("open_new_meeting", false) -> nav.navigate(Route.NEW)
            }
        }
    }

    if (showRetentionSetup) {
        RetentionSetupDialog(
            selected = vm.settings.retention,
            onSelect = { value ->
                vm.setRetention(value)
                showRetentionSetup = false
            }
        )
    }

    Scaffold(
        bottomBar = {
            val entry by nav.currentBackStackEntryAsState()
            if (entry?.destination?.route in listOf(Route.HOME, Route.MEETINGS, Route.ACTIONS, Route.PEOPLE, Route.SETTINGS)) {
                TanuBottom(nav)
            }
        }
    ) { padding ->
        NavHost(nav, Route.HOME, Modifier.padding(padding)) {
            composable(Route.HOME) {
                HomeScreen(
                    vm,
                    onStart = { nav.navigate(Route.NEW) },
                    onImport = { importLauncher.launch(arrayOf("audio/*", "video/mp4")) },
                    onOpenMeeting = { id -> vm.selectMeeting(id); nav.navigate(Route.detail(id)) }
                )
            }
            composable(Route.NEW) {
                NewMeetingScreen(vm, onBack = { nav.popBackStack() }) { id ->
                    vm.selectMeeting(id)
                    nav.navigate(Route.RECORDING)
                }
            }
            composable(Route.RECORDING) {
                RecordingScreen(vm) { id ->
                    nav.navigate(Route.detail(id)) { popUpTo(Route.RECORDING) { inclusive = true } }
                }
            }
            composable(Route.MEETINGS) {
                MeetingsScreen(vm) { id -> vm.selectMeeting(id); nav.navigate(Route.detail(id)) }
            }
            composable(Route.ACTIONS) { ActionsScreen(vm) }
            composable(Route.PEOPLE) { PeopleScreen(vm) }
            composable(Route.SETTINGS) {
                SettingsScreen(vm) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            composable(Route.DETAIL) { backStack ->
                val id = backStack.arguments?.getString("id").orEmpty()
                LaunchedEffect(id) { vm.selectMeeting(id) }
                MeetingDetailScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onDeleted = { nav.navigate(Route.HOME) { popUpTo(Route.HOME) { inclusive = false } } },
                    onShare = { shareText(context, it) },
                    onCopy = { copyText(context, it) },
                    onEmail = { emailText(context, it) },
                    onWhatsApp = { whatsAppText(context, it) },
                    onPdf = { title, text -> sharePdf(context, title, text) }
                )
            }
        }
    }
}

@Composable
private fun RetentionSetupDialog(selected: String, onSelect: (String) -> Unit) {
    var value by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Keep meeting audio") },
        text = {
            Column {
                Text("Your MOM and transcript stay until you delete them. Choose when TANU should remove the audio recording.")
                Spacer(Modifier.height(10.dp))
                retentionOptions.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { value = option.first },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == option.first, onClick = { value = option.first })
                        Text(option.second)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelect(value) }) { Text("Continue") }
        }
    )
}

private val retentionOptions = listOf(
    "after_mom" to "Delete audio after MOM (recommended)",
    "1d" to "Keep for 1 day",
    "7d" to "Keep for 7 days",
    "30d" to "Keep for 30 days",
    "manual" to "Keep until I delete it"
)

private fun copyImport(context: Context, uri: Uri): File {
    val dir = File(context.filesDir, "imports").apply { mkdirs() }
    val file = File(dir, "import_${System.currentTimeMillis()}")
    context.contentResolver.openInputStream(uri)!!.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return file
}

private fun shareText(context: Context, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "TANU Meeting Notes")
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share TANU MOM"
        )
    )
}

private fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("TANU MOM", text))
    Toast.makeText(context, "MOM copied", Toast.LENGTH_SHORT).show()
}

private fun emailText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "TANU Minutes of Meeting")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(intent) }.onFailure { shareText(context, text) }
}

private fun whatsAppText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
    }
    runCatching { context.startActivity(intent) }.onFailure { shareText(context, text) }
}

private fun sharePdf(context: Context, title: String, text: String) {
    val uri = PdfExporter.create(context, title, text)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share TANU PDF"
        )
    )
}

@Composable
private fun TanuBottom(nav: NavHostController) {
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = Color.White) {
        val items = listOf(
            Triple(Route.HOME, Icons.Default.Home, "Home"),
            Triple(Route.MEETINGS, Icons.Default.Folder, "Meetings"),
            Triple(Route.ACTIONS, Icons.Default.CheckCircle, "Actions"),
            Triple(Route.PEOPLE, Icons.Default.People, "People"),
            Triple(Route.SETTINGS, Icons.Default.Settings, "Settings")
        )
        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                selected = current == route,
                onClick = { nav.navigate(route) { launchSingleTop = true; restoreState = true } },
                icon = { Icon(icon, null) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun BrandTop(subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.tanu_app_icon),
            null,
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("TANU", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TanuInk)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel, onStart: () -> Unit, onImport: () -> Unit, onOpenMeeting: (String) -> Unit) {
    val meetings by vm.meetings.collectAsStateWithLifecycle()
    val actions by vm.openActions.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BrandTop("AI Conversation Assistant") }
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(TanuBlue, TanuPurple, TanuPink)),
                    RoundedCornerShape(28.dp)
                )
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        "What would you like TANU to remember?",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Start the conversation. TANU will turn it into clear notes and actions.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = .9f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TanuBlue)
                    ) {
                        Icon(Icons.Default.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Meeting")
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("Import Audio")
            }
        }
        item { SectionTitle("Recent meetings") }
        if (meetings.isEmpty()) {
            item { EmptyCard("No meetings yet", "Start a meeting or import an audio recording.") }
        } else {
            items(meetings.take(6)) { meeting -> MeetingRow(meeting) { onOpenMeeting(meeting.id) } }
        }
        if (actions.isNotEmpty()) {
            item { SectionTitle("My open actions") }
            items(actions.take(4)) { action ->
                Card {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RadioButtonUnchecked, null, tint = TanuPurple)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(action.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                action.owner + if (action.dueDate.isBlank()) "" else " · ${action.dueDate}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TanuInk)
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun NewMeetingScreen(vm: MainViewModel, onBack: () -> Unit, onStarted: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(20.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TopBack("New Meeting", onBack)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Meeting title") },
            placeholder = { Text("e.g. Weekly review") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = people,
            onValueChange = { people = it },
            label = { Text("Participants / guests") },
            supportingText = { Text("Separate names with commas. Guests do not need TANU accounts.") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { vm.startMeeting(title, people, onStarted = onStarted) },
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Icon(Icons.Default.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text("Start Meeting")
        }
    }
}

@Composable
private fun RecordingScreen(vm: MainViewModel, onStopped: (String) -> Unit) {
    val meeting by vm.meeting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var elapsed by remember { mutableLongStateOf(0) }
    var paused by remember { mutableStateOf(RecordingService.isPaused) }
    var addPerson by remember { mutableStateOf(false) }
    var person by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        (context as? ComponentActivity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            (context as? ComponentActivity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = if (RecordingService.startedAt > 0) System.currentTimeMillis() - RecordingService.startedAt else 0
            delay(500)
        }
    }

    if (addPerson) {
        AlertDialog(
            onDismissRequest = { addPerson = false },
            title = { Text("Add participant") },
            text = {
                OutlinedTextField(person, { person = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    val current = meeting?.participantsCsv.orEmpty().trim()
                    val updated = listOf(current, person.trim()).filter { it.isNotBlank() }.joinToString(", ")
                    meeting?.let { vm.updateMeta(it.id, it.title, updated) }
                    person = ""
                    addPerson = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addPerson = false }) { Text("Cancel") } }
        )
    }

    Column(
        Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(meeting?.title ?: "Meeting", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("● ${formatTime(elapsed)}", color = Color(0xFFEF4444), fontSize = 18.sp)
        Spacer(Modifier.height(34.dp))
        Box(
            Modifier.size(210.dp).background(
                Brush.radialGradient(listOf(Color(0x332563EB), Color.Transparent)),
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(R.drawable.tanu_app_icon),
                null,
                Modifier.size(122.dp).clip(RoundedCornerShape(34.dp))
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (paused) "Paused" else "TANU is listening…",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TanuInk
        )
        if (!paused) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp), color = TanuPink)
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { addPerson = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Participant")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (paused) { vm.resume(); paused = false } else { vm.pause(); paused = true }
                },
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                Spacer(Modifier.width(6.dp))
                Text(if (paused) "Resume" else "Pause")
            }
            Button(
                onClick = {
                    val id = meeting?.id ?: RecordingService.activeMeetingId.orEmpty()
                    vm.stopRecording()
                    if (id.isNotBlank()) onStopped(id)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.weight(1f).height(54.dp)
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = ms / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}

@Composable
private fun MeetingsScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val flow = remember(query) { vm.searchMeetings(query) }
    val meetings by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BrandTop("Meetings") }
        item {
            OutlinedTextField(
                query,
                { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search meetings, people or actions") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (meetings.isEmpty()) {
            item { EmptyCard("No matches", "Your meetings will appear here.") }
        } else {
            items(meetings) { meeting -> MeetingRow(meeting) { onOpen(meeting.id) } }
        }
    }
}

@Composable
private fun MeetingRow(meeting: MeetingEntity, onClick: () -> Unit) {
    val status = when (meeting.status) {
        MeetingStatus.READY -> "MOM ready"
        MeetingStatus.RECORDING -> "Recording"
        MeetingStatus.FAILED -> "Needs attention"
        else -> "Creating notes…"
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = when (meeting.status) {
                    MeetingStatus.READY -> Color(0xFFE7F8EF)
                    MeetingStatus.FAILED -> Color(0xFFFFEAEA)
                    else -> Color(0xFFEEF2FF)
                }
            ) {
                Icon(
                    if (meeting.status == MeetingStatus.READY) Icons.Default.Check else Icons.Default.AutoAwesome,
                    null,
                    tint = TanuBlue,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(meeting.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, fontSize = 11.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun MeetingDetailScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onEmail: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onPdf: (String, String) -> Unit
) {
    val meeting by vm.meeting.collectAsStateWithLifecycle()
    val mom by vm.mom.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var summary by remember(mom?.summary) { mutableStateOf(mom?.summary.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this meeting?") },
            text = { Text("This deletes the MOM, transcript, actions and any remaining audio from this phone.") },
            confirmButton = {
                Button(
                    onClick = {
                        meeting?.id?.let(vm::deleteMeeting)
                        confirmDelete = false
                        onDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                Text(meeting?.title ?: "Meeting", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFDC2626)) }
            }
        }

        if (meeting?.status != MeetingStatus.READY) {
            item { ProcessingCard(meeting?.status ?: MeetingStatus.TRANSCRIBING, meeting?.errorMessage) }
        } else {
            item {
                TabRow(selectedTabIndex = tab) {
                    listOf("Summary", "Actions", "Transcript").forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                    }
                }
            }
            when (tab) {
                0 -> {
                    item { SectionTitle("Summary") }
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                if (editing) {
                                    OutlinedTextField(summary, { summary = it }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                                } else {
                                    Text(mom?.summary.orEmpty())
                                }
                                TextButton(onClick = {
                                    if (editing) {
                                        meeting?.id?.let { vm.saveMomSummary(it, summary) }
                                        editing = false
                                    } else editing = true
                                }) { Text(if (editing) "Save" else "Edit") }
                            }
                        }
                    }
                    item { MomList("Key Decisions", mom?.decisionsJson.orEmpty()) }
                    item { MomList("Discussion Points", mom?.discussionPointsJson.orEmpty()) }
                    item { MomList("Follow-up", mom?.followUpsJson.orEmpty()) }
                    item { MomList("Important Numbers / Dates", mom?.importantNumbersJson.orEmpty()) }
                    item {
                        val text = renderMom(meeting, mom, actions)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onWhatsApp(text) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Chat, null); Spacer(Modifier.width(5.dp)); Text("WhatsApp")
                            }
                            OutlinedButton(onClick = { onEmail(text) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Email, null); Spacer(Modifier.width(5.dp)); Text("Email")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { onCopy(text) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("Copy")
                            }
                            OutlinedButton(onClick = { onPdf(meeting?.title ?: "TANU MOM", text) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(5.dp)); Text("PDF")
                            }
                        }
                        TextButton(onClick = { onShare(text) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.MoreHoriz, null); Spacer(Modifier.width(5.dp)); Text("More sharing options")
                        }
                    }
                }
                1 -> {
                    if (actions.isEmpty()) item { EmptyCard("No action items", "TANU did not find any assigned actions.") }
                    else items(actions) { action -> ActionRow(vm, action) }
                }
                2 -> {
                    if (segments.isEmpty()) item { EmptyCard("No transcript", "No transcript is available for this meeting.") }
                    else items(segments) { segment ->
                        Card {
                            Column(Modifier.padding(14.dp)) {
                                Text("${formatTime(segment.startMs)} · ${segment.speaker}", fontSize = 11.sp, color = TanuBlue)
                                Text(segment.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingCard(status: String, error: String?) {
    if (status == MeetingStatus.FAILED) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))) {
            Column(Modifier.padding(20.dp)) {
                Text("TANU needs your attention", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBE123C))
                Spacer(Modifier.height(6.dp))
                Text(error ?: "The meeting notes could not be created.")
            }
        }
    } else {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(painterResource(R.drawable.tanu_app_icon), null, Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(color = TanuPurple)
                Spacer(Modifier.height(16.dp))
                Text("Creating your meeting notes…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("You can leave this screen and come back later.", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun MomList(title: String, json: String) {
    val values = jsonList(json)
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(title)
        Card {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value ->
                    Row {
                        Text("•", color = TanuPurple, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(value, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(vm: MainViewModel, action: ActionItemEntity) {
    Card {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(action.status == "done", { vm.doneAction(action.id, it) })
            Column(Modifier.weight(1f)) {
                Text(action.title, fontWeight = FontWeight.SemiBold)
                val detail = listOf(action.owner, action.dueDate).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

private fun renderMom(meeting: MeetingEntity?, mom: MomEntity?, actions: List<ActionItemEntity>): String {
    if (meeting == null || mom == null) return ""
    val b = StringBuilder()
    b.appendLine("TANU — Minutes of Meeting")
    b.appendLine(meeting.title)
    if (meeting.participantsCsv.isNotBlank()) b.appendLine("Participants: ${meeting.participantsCsv}")
    b.appendLine()
    b.appendLine("SUMMARY")
    b.appendLine(mom.summary)

    fun section(title: String, values: List<String>) {
        if (values.isEmpty()) return
        b.appendLine().appendLine(title)
        values.forEach { b.appendLine("• $it") }
    }
    section("KEY DECISIONS", jsonList(mom.decisionsJson))
    section("DISCUSSION POINTS", jsonList(mom.discussionPointsJson))
    section("FOLLOW-UP", jsonList(mom.followUpsJson))
    section("IMPORTANT NUMBERS / DATES", jsonList(mom.importantNumbersJson))

    if (actions.isNotEmpty()) {
        b.appendLine().appendLine("ACTION ITEMS")
        actions.forEach { action ->
            val details = listOf(action.owner, action.dueDate).filter { it.isNotBlank() }.joinToString(" · ")
            b.appendLine("• ${action.title}${if (details.isBlank()) "" else " — $details"}")
        }
    }
    if (mom.nextMeeting.isNotBlank()) b.appendLine().appendLine("NEXT MEETING\n${mom.nextMeeting}")
    return b.toString().trim()
}

@Composable
private fun ActionsScreen(vm: MainViewModel) {
    val actions by vm.openActions.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BrandTop("My Actions") }
        if (actions.isEmpty()) item { EmptyCard("You are clear", "Open action items from your meetings will appear here.") }
        else items(actions) { action -> ActionRow(vm, action) }
    }
}

@Composable
private fun PeopleScreen(vm: MainViewModel) {
    val people by vm.participants.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BrandTop("People") }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Save a participant", fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(company, { company = it }, label = { Text("Company (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                vm.saveParticipant(name.trim(), company.trim(), phone.trim())
                                name = ""; company = ""; phone = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Save")
                    }
                }
            }
        }
        if (people.isEmpty()) item { EmptyCard("No saved people", "Guests can still be typed directly when starting a meeting.") }
        else items(people) { person ->
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(person.name, fontWeight = FontWeight.SemiBold)
                    val detail = listOf(person.company, person.phone).filter { it.isNotBlank() }.joinToString(" · ")
                    if (detail.isNotBlank()) Text(detail, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel, onOverlayPermission: () -> Unit) {
    var aiMode by remember { mutableStateOf(vm.settings.aiMode) }
    var showKeyEntry by remember { mutableStateOf(false) }
    var openAiKey by remember { mutableStateOf("") }
    var hasOpenAi by remember { mutableStateOf(vm.hasOpenAiKey()) }
    var vocabulary by remember { mutableStateOf(vm.settings.customVocabulary) }
    var retention by remember { mutableStateOf(vm.settings.retention) }
    var floating by remember { mutableStateOf(vm.settings.floatingEnabled) }
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BrandTop("Settings") }
        item { SectionTitle("TANU AI") }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiChoice(
                        title = "Auto — recommended",
                        subtitle = if (hasOpenAi) "Uses OpenAI for the final MOM when internet is available, with private on-device fallback." else "Uses TANU's private on-device AI. Connect OpenAI anytime for an optional cloud-quality MOM.",
                        selected = aiMode == AiMode.AUTO
                    ) { aiMode = AiMode.AUTO; vm.setAiMode(AiMode.AUTO) }
                    Divider()
                    AiChoice(
                        title = "Private On-device",
                        subtitle = "Meeting understanding stays on this phone.",
                        selected = aiMode == AiMode.DEVICE
                    ) { aiMode = AiMode.DEVICE; vm.setAiMode(AiMode.DEVICE) }
                    Divider()
                    AiChoice(
                        title = "OpenAI",
                        subtitle = "Sends transcript text, not your meeting audio, for the final MOM. Falls back on-device if unavailable.",
                        selected = aiMode == AiMode.OPENAI
                    ) { aiMode = AiMode.OPENAI; vm.setAiMode(AiMode.OPENAI) }
                    Spacer(Modifier.height(4.dp))
                    if (hasOpenAi) {
                        AssistChip(onClick = { }, label = { Text("OpenAI connected") }, leadingIcon = { Icon(Icons.Default.Check, null) })
                        Row {
                            TextButton(onClick = { showKeyEntry = true }) { Text("Replace key") }
                            TextButton(onClick = {
                                vm.clearOpenAiKey(); hasOpenAi = false; openAiKey = ""; showKeyEntry = false
                            }) { Text("Disconnect") }
                        }
                    } else {
                        OutlinedButton(onClick = { showKeyEntry = !showKeyEntry }) {
                            Text(if (showKeyEntry) "Cancel" else "Connect OpenAI")
                        }
                    }
                    if (showKeyEntry) {
                        OutlinedTextField(
                            openAiKey,
                            { openAiKey = it },
                            label = { Text("OpenAI API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = {
                            if (openAiKey.isNotBlank()) {
                                vm.saveOpenAiKey(openAiKey)
                                openAiKey = ""
                                hasOpenAi = true
                                showKeyEntry = false
                            }
                        }) { Text("Save securely") }
                    }
                }
            }
        }

        item { SectionTitle("Names & words") }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Help TANU recognize your words", fontWeight = FontWeight.Bold)
                    Text("Add names, brands, abbreviations and slang you use often.", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(vocabulary, { vocabulary = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Button(onClick = { vm.settings.customVocabulary = vocabulary }) { Text("Save") }
                }
            }
        }

        item { SectionTitle("Audio") }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    retentionOptions.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                retention = option.first
                                vm.setRetention(option.first)
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = retention == option.first,
                                onClick = { retention = option.first; vm.setRetention(option.first) }
                            )
                            Text(option.second)
                        }
                    }
                    Text("MOM and transcript stay until you delete the meeting.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        item { SectionTitle("Floating Assistant") }
        item {
            Card {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TANU bubble", fontWeight = FontWeight.Bold)
                        Text("Quick access to TANU over supported apps.", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = floating,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) {
                                onOverlayPermission()
                            } else {
                                floating = enabled
                                vm.toggleFloating(enabled)
                            }
                        }
                    )
                }
            }
        }

        item {
            Text(
                "TANU Personal v2.2",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun AiChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun TopBack(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
