package com.tanu.personal

import android.Manifest
import android.content.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.tanu.personal.data.*
import com.tanu.personal.service.RecordingService
import com.tanu.personal.ui.*
import com.tanu.personal.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.io.File

@AndroidEntryPoint
class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{TanuTheme{TanuApp(intent)}}}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent)}
}

private object Route{const val HOME="home";const val NEW="new";const val RECORDING="recording";const val MEETINGS="meetings";const val ACTIONS="actions";const val PEOPLE="people";const val SETTINGS="settings";const val DETAIL="detail/{id}";fun detail(id:String)="detail/$id"}

@Composable
private fun TanuApp(startIntent:Intent,vm:MainViewModel= hiltViewModel()){
    val nav=rememberNavController();val context=LocalContext.current
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){val f=copyImport(context,uri);vm.importAudio(f,"Imported conversation"){id->nav.navigate(Route.detail(id))}}}
    LaunchedEffect(Unit){
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO).let{if(Build.VERSION.SDK_INT>=33)it+Manifest.permission.POST_NOTIFICATIONS else it})
        val active=RecordingService.activeMeetingId
        if(active!=null){vm.selectMeeting(active);nav.navigate(Route.RECORDING)}
        else {
            val openMeeting=startIntent.getStringExtra("open_meeting_id")
            when {
                !openMeeting.isNullOrBlank() -> {vm.selectMeeting(openMeeting);nav.navigate(Route.detail(openMeeting))}
                startIntent.getBooleanExtra("open_new_meeting",false) -> nav.navigate(Route.NEW)
            }
        }
    }
    Scaffold(bottomBar={val entry by nav.currentBackStackEntryAsState();if(entry?.destination?.route in listOf(Route.HOME,Route.MEETINGS,Route.ACTIONS,Route.PEOPLE,Route.SETTINGS))TanuBottom(nav)}){pad->
        NavHost(navController=nav,startDestination=Route.HOME,modifier=Modifier.padding(pad)){
            composable(Route.HOME){HomeScreen(vm,{nav.navigate(Route.NEW)},{importLauncher.launch(arrayOf("audio/*","video/mp4"))},{nav.navigate(Route.MEETINGS)},{nav.navigate(Route.ACTIONS)})}
            composable(Route.NEW){NewMeetingScreen(vm,{nav.popBackStack()}){id->vm.selectMeeting(id);nav.navigate(Route.RECORDING)}}
            composable(Route.RECORDING){RecordingScreen(vm,{nav.navigate(Route.HOME){popUpTo(Route.HOME){inclusive=false}}},{id->nav.navigate(Route.detail(id)){popUpTo(Route.RECORDING){inclusive=true}}})}
            composable(Route.MEETINGS){MeetingsScreen(vm){id->vm.selectMeeting(id);nav.navigate(Route.detail(id))}}
            composable(Route.ACTIONS){ActionsScreen(vm)}
            composable(Route.PEOPLE){PeopleScreen(vm)}
            composable(Route.SETTINGS){SettingsScreen(vm,onOverlayPermission={context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${context.packageName}")))})}
            composable(Route.DETAIL){b->val id=b.arguments?.getString("id").orEmpty();LaunchedEffect(id){vm.selectMeeting(id)};MeetingDetailScreen(vm,{nav.popBackStack()},{shareText(context,it)},{copyText(context,it)},{emailText(context,it)},{whatsAppText(context,it)},{title,text->sharePdf(context,title,text)})}
        }
    }
}

private fun copyImport(context:Context,uri:Uri):File{val dir=File(context.filesDir,"imports").apply{mkdirs()};val f=File(dir,"import_${System.currentTimeMillis()}");context.contentResolver.openInputStream(uri)!!.use{i->f.outputStream().use{i.copyTo(it)}};return f}
private fun shareText(c:Context,text:String){c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"TANU Meeting Notes");putExtra(Intent.EXTRA_TEXT,text)},"Share TANU MOM"))}
private fun copyText(c:Context,text:String){(c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("TANU MOM",text));Toast.makeText(c,"MOM copied",Toast.LENGTH_SHORT).show()}
private fun emailText(c:Context,text:String){val i=Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:")).apply{putExtra(Intent.EXTRA_SUBJECT,"TANU Minutes of Meeting");putExtra(Intent.EXTRA_TEXT,text)};runCatching{c.startActivity(i)}.onFailure{shareText(c,text)}}
private fun whatsAppText(c:Context,text:String){val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text);setPackage("com.whatsapp")};runCatching{c.startActivity(i)}.onFailure{shareText(c,text)}}
private fun sharePdf(c:Context,title:String,text:String){val uri=PdfExporter.create(c,title,text);c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share TANU PDF"))}

@Composable private fun TanuBottom(nav:NavHostController){NavigationBar(containerColor=Color.White){listOf(Route.HOME to Icons.Default.Home,Route.MEETINGS to Icons.Default.Folder,Route.ACTIONS to Icons.Default.CheckCircle,Route.PEOPLE to Icons.Default.People,Route.SETTINGS to Icons.Default.Settings).forEach{(r,i)->NavigationBarItem(selected=nav.currentDestination?.route==r,onClick={nav.navigate(r){launchSingleTop=true;restoreState=true}},icon={Icon(i,null)},label={Text(r.replaceFirstChar{it.uppercase()})})}}}

@Composable private fun BrandTop(sub:String){Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.tanu_app_icon),null,Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(text="TANU",fontSize=24.sp,fontWeight=FontWeight.Bold,color=TanuInk);Text(text=sub,fontSize=12.sp,color=Color.Gray)};Surface(shape=RoundedCornerShape(30.dp),color=Color(0xFFEFF6FF)){Text(text="AI Conversation Assistant",fontSize=11.sp,color=TanuBlue,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp))}}}

@Composable private fun HomeScreen(vm:MainViewModel,onStart:()->Unit,onImport:()->Unit,onMeetings:()->Unit,onActions:()->Unit){
    val meetings by vm.meetings.collectAsStateWithLifecycle();val actions by vm.openActions.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{BrandTop("Every conversation becomes an action.")};item{Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent),modifier=Modifier.background(Brush.linearGradient(listOf(TanuBlue,TanuPurple,TanuPink)),RoundedCornerShape(28.dp))){Column(Modifier.padding(22.dp)){Text(text="What can I do for your meeting today?",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Color.White);Text(text="Record now. TANU transcribes in 20-second rolling chunks so the MOM is almost ready when you stop.",fontSize=13.sp,color=Color.White.copy(.9f),modifier=Modifier.padding(vertical=8.dp));Button(onClick=onStart,colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=TanuBlue)){Icon(Icons.Default.Mic,null);Spacer(Modifier.width(8.dp));Text("Start New Meeting")}}}};item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Quick("Import",Icons.Default.UploadFile,onImport,Modifier.weight(1f));Quick("Meetings",Icons.Default.Description,onMeetings,Modifier.weight(1f));Quick("My Actions",Icons.Default.TaskAlt,onActions,Modifier.weight(1f))}};item{SectionTitle("Recent meetings")};if(meetings.isEmpty())item{EmptyCard("No meetings yet","Start a meeting or import an audio file.")}else items(meetings.take(5)){MeetingRow(it){vm.selectMeeting(it.id);onMeetings()}};item{SectionTitle("Open actions")};if(actions.isEmpty())item{EmptyCard("You are clear","New action items will appear here automatically.")}else items(actions.take(4)){a->Card{Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.RadioButtonUnchecked,null,tint=TanuPurple);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(a.title,maxLines=2,overflow=TextOverflow.Ellipsis);Text(text=a.owner+if(a.dueDate.isBlank())"" else " · ${a.dueDate}",fontSize=11.sp,color=Color.Gray)}}}}}
}

@Composable private fun Quick(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit,modifier:Modifier=Modifier){Card(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=TanuBlue);Spacer(Modifier.height(6.dp));Text(text=label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}}
@Composable private fun SectionTitle(s:String){Text(text=s,fontSize=18.sp,fontWeight=FontWeight.Bold,color=TanuInk)}
@Composable private fun EmptyCard(a:String,b:String){Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Text(text=a,fontWeight=FontWeight.SemiBold);Text(text=b,fontSize=12.sp,color=Color.Gray)}}}

@Composable private fun NewMeetingScreen(vm:MainViewModel,onBack:()->Unit,onStarted:(String)->Unit){var title by remember{mutableStateOf("")};var people by remember{mutableStateOf("")};var mode by remember{mutableStateOf(vm.settings.defaultMode)};Column(Modifier.fillMaxSize().padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(14.dp)){TopBack("New Meeting",onBack);OutlinedTextField(title,{title=it},label={Text("Meeting title")},modifier=Modifier.fillMaxWidth());OutlinedTextField(people,{people=it},label={Text("Participants / guests")},supportingText={Text("Comma separated. Guests do not need TANU IDs.")},modifier=Modifier.fillMaxWidth());SectionTitle("Processing mode");ModeCard("⚡ Fast","Transcribes 20-second chunks during the meeting. Uses your configured secure server; automatically falls back to on-device if unavailable.",mode==ProcessingMode.FAST){mode=ProcessingMode.FAST};ModeCard("🔒 Private Offline","Everything stays on this phone. Rolling chunks still reduce the wait after Stop, but slower phones may build a backlog.",mode==ProcessingMode.PRIVATE){mode=ProcessingMode.PRIVATE};if(mode==ProcessingMode.FAST&&vm.settings.serverUrl.isBlank())AssistChip(onClick={},label={Text("Fast server not set — offline fallback will be used")},leadingIcon={Icon(Icons.Default.Info,null)});Spacer(Modifier.weight(1f));Button(onClick={vm.settings.defaultMode=mode;vm.startMeeting(title,people,mode,onStarted)},modifier=Modifier.fillMaxWidth().height(56.dp)){Icon(Icons.Default.Mic,null);Spacer(Modifier.width(8.dp));Text("Start Recording")}}
}
@Composable private fun ModeCard(title:String,sub:String,selected:Boolean,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=if(selected)Color(0xFFEEF2FF) else Color.White),border=if(selected)androidx.compose.foundation.BorderStroke(1.dp,TanuPurple) else null){Row(Modifier.padding(16.dp)){RadioButton(selected,onClick=onClick);Spacer(Modifier.width(8.dp));Column{Text(text=title,fontWeight=FontWeight.Bold);Text(text=sub,fontSize=12.sp,color=Color.Gray)}}}}

@Composable private fun RecordingScreen(vm:MainViewModel,onHome:()->Unit,onStopped:(String)->Unit){val meeting by vm.meeting.collectAsStateWithLifecycle();val context=LocalContext.current;var elapsed by remember{mutableLongStateOf(0)};var paused by remember{mutableStateOf(RecordingService.isPaused)}
    DisposableEffect(Unit){(context as? ComponentActivity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{(context as? ComponentActivity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
    LaunchedEffect(Unit){while(true){elapsed=if(RecordingService.startedAt>0)System.currentTimeMillis()-RecordingService.startedAt else 0;delay(500)}}
    Column(Modifier.fillMaxSize().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(text=meeting?.title?:"Meeting",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("● ${formatTime(elapsed)}",color=Color(0xFFEF4444));Spacer(Modifier.height(28.dp));Box(Modifier.size(190.dp).background(Brush.radialGradient(listOf(Color(0x332563EB),Color.Transparent)),CircleShape),contentAlignment=Alignment.Center){Image(painterResource(R.drawable.tanu_app_icon),null,Modifier.size(116.dp).clip(RoundedCornerShape(32.dp)))};Text(text=if(paused)"Paused" else "Recording + transcribing live",fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text(text="The screen is kept awake while this page is open. If you manually lock the phone, the foreground recorder continues.",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(20.dp));LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=TanuPink);Spacer(Modifier.height(20.dp));Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Text(text="20-second rolling pipeline",fontWeight=FontWeight.Bold);Text(text="Noise suppression → speech gate → Fast server or local Whisper → transcript segments",fontSize=12.sp,color=Color.Gray)}};Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){OutlinedButton(onClick={if(paused){vm.resume();paused=false}else{vm.pause();paused=true}},modifier=Modifier.weight(1f)){Icon(if(paused)Icons.Default.PlayArrow else Icons.Default.Pause,null);Text(if(paused)"Resume" else "Pause")};Button(onClick={val id=meeting?.id?:RecordingService.activeMeetingId.orEmpty();vm.stopRecording();if(id.isNotBlank())onStopped(id)},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFEF4444)),modifier=Modifier.weight(1f)){Icon(Icons.Default.Stop,null);Text("Stop")}}}
}
private fun formatTime(ms:Long):String{val s=ms/1000;return "%02d:%02d".format(s/60,s%60)}

@Composable private fun MeetingsScreen(vm:MainViewModel,onOpen:(String)->Unit){var q by remember{mutableStateOf("")};val flow=remember(q){vm.searchMeetings(q)};val meetings by flow.collectAsStateWithLifecycle(initialValue=emptyList());LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{BrandTop("Meetings")};item{OutlinedTextField(q,{q=it},leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text("Search meetings, people, transcript, actions, amounts")},modifier=Modifier.fillMaxWidth())};if(meetings.isEmpty())item{EmptyCard("No matches","Search across meetings, people, transcript, MOM, decisions and actions.")}else items(meetings){MeetingRow(it){onOpen(it.id)}}}}
@Composable private fun MeetingRow(m:MeetingEntity,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=when(m.status){MeetingStatus.READY->Color(0xFFE7F8EF);MeetingStatus.FAILED->Color(0xFFFFEAEA);else->Color(0xFFEEF2FF)}){Icon(if(m.status==MeetingStatus.READY)Icons.Default.Check else Icons.Default.GraphicEq,null,tint=TanuBlue,modifier=Modifier.padding(10.dp))};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(text=m.title,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(text="${m.status.replace('_',' ')} · ${m.processingMode}",fontSize=11.sp,color=Color.Gray)};Icon(Icons.Default.ChevronRight,null)}}}

@Composable
private fun MeetingDetailScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onEmail: (String) -> Unit,
    onWhatsApp: (String) -> Unit,
    onPdf: (String, String) -> Unit
) {
    val m by vm.meeting.collectAsStateWithLifecycle()
    val mom by vm.mom.collectAsStateWithLifecycle()
    val actions by vm.actions.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    val chunkStats by vm.chunkStats.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var summary by remember(mom?.summary) { mutableStateOf(mom?.summary.orEmpty()) }

    LazyColumn(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TopBack(m?.title ?: "Meeting", onBack) }

        if (m?.status != MeetingStatus.READY) {
            item {
                ProcessingCard(
                    m?.status ?: MeetingStatus.TRANSCRIBING,
                    m?.errorMessage,
                    chunkStats.first,
                    chunkStats.second,
                    chunkStats.third
                )
            }
            item {
                Text(
                    text = "Transcript received so far: ${segments.size} segment(s)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        } else {
            item {
                TabRow(selectedTabIndex = tab) {
                    listOf("Summary", "Transcript", "Actions").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
            }

            when (tab) {
                0 -> {
                    item { SectionTitle("Executive Summary") }
                    item {
                        Card {
                            Column(Modifier.padding(16.dp)) {
                                if (editing) {
                                    OutlinedTextField(
                                        summary,
                                        { summary = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 5
                                    )
                                } else {
                                    Text(mom?.summary.orEmpty())
                                }
                                Spacer(Modifier.height(10.dp))
                                TextButton(onClick = {
                                    if (editing) {
                                        m?.id?.let { vm.saveMomSummary(it, summary) }
                                        editing = false
                                    } else {
                                        editing = true
                                    }
                                }) {
                                    Text(if (editing) "Save summary" else "Edit summary")
                                }
                            }
                        }
                    }
                    item { MomList("Key Decisions", mom?.decisionsJson.orEmpty()) }
                    item { MomList("Discussion Points", mom?.discussionPointsJson.orEmpty()) }
                    item { MomList("Follow-up", mom?.followUpsJson.orEmpty()) }
                    item { MomList("Important Numbers / Prices / Dates", mom?.importantNumbersJson.orEmpty()) }
                    item {
                        val text = renderMom(m, mom, actions)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onWhatsApp(text) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Chat, null)
                                    Text("WhatsApp")
                                }
                                OutlinedButton(onClick = { onEmail(text) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Email, null)
                                    Text("Email")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onCopy(text) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.ContentCopy, null)
                                    Text("Copy")
                                }
                                OutlinedButton(
                                    onClick = { onPdf(m?.title ?: "TANU MOM", text) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, null)
                                    Text("PDF")
                                }
                            }
                            TextButton(onClick = { onShare(text) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Share, null)
                                Spacer(Modifier.width(6.dp))
                                Text("More sharing options")
                            }
                        }
                    }
                }

                1 -> items(segments) { segment ->
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text(text = "${formatTime(segment.startMs)} · ${segment.speaker}", fontSize = 11.sp, color = TanuBlue)
                            Text(segment.text)
                        }
                    }
                }

                2 -> items(actions) { action ->
                    Card {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(action.status == "done", { vm.doneAction(action.id, it) })
                            Column(Modifier.weight(1f)) {
                                Text(text = action.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${action.owner}${if (action.dueDate.isBlank()) "" else " · Due ${action.dueDate}"}",
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
}
@Composable private fun ProcessingCard(status:String,error:String?,done:Int,total:Int,failed:Int){
    val progress=if(total>0)(done.toFloat()/total.toFloat()).coerceIn(0f,1f)else 0f
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=if(status==MeetingStatus.FAILED)Color(0xFFFFF1F2) else Color(0xFFF5F3FF))){
        Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
            if(status==MeetingStatus.FAILED)Icon(Icons.Default.Error,contentDescription=null,tint=Color.Red,modifier=Modifier.size(44.dp)) else if(status==MeetingStatus.GENERATING_MOM)CircularProgressIndicator(color=TanuPink) else {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFE2E8F0))){if(total>0)Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Brush.horizontalGradient(listOf(TanuBlue,TanuPurple,TanuPink))))}
                Spacer(Modifier.height(8.dp));Text(text=if(total>0)"$done / $total audio chunks · ${(progress*100).toInt()}%" else "Preparing audio chunks…",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=TanuPurple)
            }
            Spacer(Modifier.height(12.dp));Text(text=status.replace('_',' '),fontSize=20.sp,fontWeight=FontWeight.Bold)
            Text(text=error?:when(status){MeetingStatus.TRANSCRIBING->"Rolling transcription is finishing. Progress moves after every 20-second chunk.";MeetingStatus.GENERATING_MOM->"Transcript is ready. Building structured English MOM and actions.";MeetingStatus.FAILED->"Processing stopped with a clear error instead of waiting forever.";else->"Processing locally."},fontSize=12.sp,color=Color.Gray)
            if(failed>0&&status!=MeetingStatus.FAILED)Text(text="$failed chunk${if(failed==1)"" else "s"} could not be transcribed; TANU will use the available speech.",fontSize=11.sp,color=Color(0xFFB45309),modifier=Modifier.padding(top=8.dp))
        }
    }
}
@Composable private fun MomList(title:String,json:String){val list=jsonList(json);Card{Column(Modifier.padding(16.dp)){Text(text=title,fontWeight=FontWeight.Bold);if(list.isEmpty())Text(text="None detected",fontSize=12.sp,color=Color.Gray) else list.forEach{Text("• $it",modifier=Modifier.padding(top=6.dp))}}}}
private fun renderMom(m:MeetingEntity?,mom:MomEntity?,actions:List<ActionItemEntity>):String=buildString{append("TANU MEETING NOTES\n\n");append(m?.title?:"Meeting").append("\n\nSUMMARY\n").append(mom?.summary.orEmpty()).append("\n\nDECISIONS\n");jsonList(mom?.decisionsJson.orEmpty()).forEach{append("• ").append(it).append('\n')};append("\nACTIONS\n");actions.forEach{append("• ").append(it.title).append(" — ").append(it.owner);if(it.dueDate.isNotBlank())append(" — ").append(it.dueDate);append('\n')}}

@Composable private fun ActionsScreen(vm:MainViewModel){val a by vm.openActions.collectAsStateWithLifecycle();LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{BrandTop("My Actions")};if(a.isEmpty())item{EmptyCard("Nothing pending","Action items extracted from meetings will appear here.")}else items(a){x->Card{Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(false,{vm.doneAction(x.id,true)});Column{Text(text=x.title,fontWeight=FontWeight.SemiBold);Text(text="${x.owner}${if(x.dueDate.isBlank())"" else " · ${x.dueDate}"}",fontSize=11.sp,color=Color.Gray)}}}}}}

@Composable private fun PeopleScreen(vm:MainViewModel){val people by vm.participants.collectAsStateWithLifecycle();var dialog by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().padding(18.dp)){BrandTop("People & Guests");Spacer(Modifier.height(14.dp));Button(onClick={dialog=true}){Icon(Icons.Default.PersonAdd,null);Text("Add participant")};Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(people){p->Card{Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Color(0xFFEEF2FF)){Text(p.name.take(1).uppercase(),color=TanuPurple,fontWeight=FontWeight.Bold,modifier=Modifier.padding(12.dp))};Spacer(Modifier.width(10.dp));Column{Text(text=p.name,fontWeight=FontWeight.SemiBold);Text(text=listOf(p.company,p.phone).filter{it.isNotBlank()}.joinToString(" · "),fontSize=11.sp,color=Color.Gray)}}}}}};if(dialog)AddPersonDialog({dialog=false}){n,c,p->vm.saveParticipant(n,c,p);dialog=false}}
@Composable private fun AddPersonDialog(onClose:()->Unit,onSave:(String,String,String)->Unit){var n by remember{mutableStateOf("")};var c by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onClose,title={Text("Add participant / guest")},text={Column{OutlinedTextField(n,{n=it},label={Text("Name")});OutlinedTextField(c,{c=it},label={Text("Company")});OutlinedTextField(p,{p=it},label={Text("Mobile")})}},confirmButton={TextButton(onClick={if(n.isNotBlank())onSave(n,c,p)}){Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})}

@Composable private fun SettingsScreen(vm:MainViewModel,onOverlayPermission:()->Unit){var server by remember{mutableStateOf(vm.settings.serverUrl)};var token by remember{mutableStateOf("")};var vocab by remember{mutableStateOf(vm.settings.customVocabulary)};var retention by remember{mutableStateOf(vm.settings.retention)};var floating by remember{mutableStateOf(vm.settings.floatingEnabled)};val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{BrandTop("Settings")};item{SectionTitle("Fast processing")};item{Card{Column(Modifier.padding(16.dp)){Text(text="Secure transcription server",fontWeight=FontWeight.Bold);Text(text="Leave the URL blank for 100% on-device fallback. For fastest processing, use the included TANU GPU backend over HTTPS.",fontSize=12.sp,color=Color.Gray);OutlinedTextField(server,{server=it},label={Text("https://your-tanu-server")},modifier=Modifier.fillMaxWidth());OutlinedTextField(token,{token=it},label={Text(if(vm.hasFastServerToken())"Server token (saved securely — enter only to change)" else "Server token")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={vm.saveFastServer(server,token);token=""}){Text("Save fast server")};if(vm.hasFastServerToken())TextButton(onClick={vm.clearFastServerToken();token=""}){Text("Clear token")}}}}};item{SectionTitle("Noise, names & slang")};item{Card{Column(Modifier.padding(16.dp)){Text(text="Vocabulary bias",fontWeight=FontWeight.Bold);Text(text="Add names, brands, abbreviations, Tanglish/Hinglish terms and recurring business words.",fontSize=12.sp,color=Color.Gray);OutlinedTextField(vocab,{vocab=it},modifier=Modifier.fillMaxWidth(),minLines=4);Button(onClick={vm.settings.customVocabulary=vocab}){Text("Save vocabulary")}}}};item{SectionTitle("Audio retention")};item{Card{Column(Modifier.padding(16.dp)){listOf("after_mom" to "Delete audio after MOM (recommended)","1d" to "Keep 1 day","7d" to "Keep 7 days","30d" to "Keep 30 days","manual" to "Keep until I delete").forEach{(v,l)->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(retention==v,{retention=v;vm.setRetention(v)});Text(l)}};Text(text="MOM and transcript are not auto-deleted.",fontSize=12.sp,color=Color.Gray)}}};item{SectionTitle("Floating Assistant")};item{Card{Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(text="TANU bubble",fontWeight=FontWeight.Bold);Text(text="Shows above supported apps. It never bypasses protected call audio.",fontSize=12.sp,color=Color.Gray)};Switch(floating,{on->if(on&&Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(context)){onOverlayPermission()}else{floating=on;vm.toggleFloating(on)}})}}};item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFF0FDFA))){Column(Modifier.padding(16.dp)){Text(text="Recording reliability",fontWeight=FontWeight.Bold);Text(text="Foreground microphone service + partial CPU wake lock. The recording screen also uses KEEP_SCREEN_ON, so automatic display sleep is prevented while TANU is visible.",fontSize=12.sp,color=Color.Gray)}}}}
}

@Composable private fun TopBack(title:String,onBack:()->Unit){Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Text(text=title,fontSize=22.sp,fontWeight=FontWeight.Bold)}}
