package com.example.microquest

import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.microquest.auth.AuthViewModel
import com.example.microquest.auth.LoginScreen
import com.example.microquest.auth.RegisterScreen
import com.example.microquest.data.CompletedQuest
import com.example.microquest.data.QuestType
import com.example.microquest.friends.FriendsScreen
import com.example.microquest.friends.FriendsViewModel
import com.example.microquest.profile.ProfileScreen
import com.example.microquest.ui.theme.MicroQuestTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MicroQuestTheme { AppRoot() } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Navigation root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppRoot() {
    val nav    = rememberNavController()
    val authVm = viewModel<AuthViewModel>()
    val authState by authVm.state.collectAsStateWithLifecycle()

    val startDest = if (authState.isLoggedIn) "main" else "login"

    NavHost(navController = nav, startDestination = startDest) {
        composable("login") {
            LoginScreen(
                vm = authVm,
                onNavigateToRegister = { nav.navigate("register") },
                onLoginSuccess = { nav.navigate("main") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("register") {
            RegisterScreen(
                vm = authVm,
                onBack = { nav.popBackStack() },
                onSuccess = { nav.navigate("main") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("main") {
            MicroQuestApp(
                authVm = authVm,
                onOpenProfile = { nav.navigate("profile") },
                onOpenFriends = { nav.navigate("friends") }
            )
        }
        composable("profile") {
            ProfileScreen(vm = authVm, onBack = { nav.popBackStack() },
                onLogout = { nav.navigate("login") { popUpTo("main") { inclusive = true } } })
        }
        composable("friends") {
            val friendsVm = viewModel<FriendsViewModel>()
            FriendsScreen(vm = friendsVm, onBack = { nav.popBackStack() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main quest screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroQuestApp(
    vm: QuestViewModel = viewModel(),
    authVm: AuthViewModel,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)
    val context = LocalContext.current

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isCameraOpen by remember { mutableStateOf(false) }
    var isVideoCameraOpen by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        isCameraOpen = false
        if (success) vm.onPhotoPicked(tempPhotoUri)
        else if (currentState.timerSeconds == 0 && !currentState.timerRunning) {
            Toast.makeText(context, "⏰ Время вышло! Квест пропущен", Toast.LENGTH_SHORT).show()
            vm.skipCurrentQuest()
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> vm.onPhotoPicked(uri) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File.createTempFile("quest_", ".jpg", context.cacheDir)
            tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(tempPhotoUri!!)
        }
    }

    var tempVideoUri by remember { mutableStateOf<Uri?>(null) }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        isVideoCameraOpen = false
        if (success && !currentState.timedOut) vm.onVideoPicked(tempVideoUri)
        else { Toast.makeText(context, "⏰ Время вышло! Квест пропущен", Toast.LENGTH_SHORT).show(); vm.skipCurrentQuest() }
    }
    val videoGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> vm.onVideoPicked(uri) }

    fun launchVideoCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File.createTempFile("quest_video_", ".mp4", context.cacheDir)
            tempVideoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            isVideoCameraOpen = true; videoLauncher.launch(tempVideoUri!!)
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = File.createTempFile("quest_", ".jpg", context.cacheDir)
            tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            isCameraOpen = true; cameraLauncher.launch(tempPhotoUri!!)
        } else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var tempVoiceFile by remember { mutableStateOf<File?>(null) }

    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File.createTempFile("quest_voice_", ".m4a", context.cacheDir)
            tempVoiceFile = file
            val rec = android.media.MediaRecorder(context).apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath); prepare(); start()
            }
            mediaRecorder = rec; isRecording = true
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            mediaRecorder?.apply { stop(); release() }; mediaRecorder = null; isRecording = false
            tempVoiceFile?.let { vm.onVoicePicked(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)) }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val file = File.createTempFile("quest_voice_", ".m4a", context.cacheDir); tempVoiceFile = file
                val rec = android.media.MediaRecorder(context).apply {
                    setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                    setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath); prepare(); start()
                }
                mediaRecorder = rec; isRecording = true
            } else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    var isPaused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { mediaPlayer?.apply { stop(); release() } } }

    fun stopPlayer() { mediaPlayer?.apply { stop(); release() }; mediaPlayer = null; playingUri = null; isPaused = false }
    fun playVoice(uri: Uri) {
        if (playingUri == uri && mediaPlayer != null) {
            if (isPaused) { mediaPlayer?.start(); isPaused = false } else { mediaPlayer?.pause(); isPaused = true }; return
        }
        stopPlayer(); playingUri = uri
        try {
            val afd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
            val player = android.media.MediaPlayer()
            player.setDataSource(afd.fileDescriptor); afd.close()
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { it.release(); mediaPlayer = null; playingUri = null; isPaused = false }
            player.setOnErrorListener { mp, what, extra ->
                android.util.Log.e("MQ", "MP err what=$what extra=$extra")
                mp.release(); mediaPlayer = null; playingUri = null; isPaused = false; true
            }
            player.prepareAsync(); mediaPlayer = player
        } catch (e: Exception) { android.util.Log.e("MQ", "playVoice: ${e.message}"); mediaPlayer = null; playingUri = null }
    }

    LaunchedEffect(state.currentQuest?.id) { if (state.currentQuest != null) vm.startTimer() }
    LaunchedEffect(state.timerSeconds, state.timerRunning) {
        if (state.timerSeconds == 0 && !state.timerRunning && state.currentQuest != null) {
            val q = state.currentQuest!!
            when (q.type) {
                QuestType.ACTION -> if (state.pendingVideoUri == null) {
                    vm.markTimedOut()
                    if (!isVideoCameraOpen) { Toast.makeText(context, "⏰ Время вышло! Квест пропущен", Toast.LENGTH_SHORT).show(); vm.skipCurrentQuest() }
                }
                QuestType.TEXT  -> if (state.pendingAnswer.isBlank() && !isCameraOpen) { Toast.makeText(context, "⏰ Время вышло! Квест пропущен", Toast.LENGTH_SHORT).show(); vm.skipCurrentQuest() }
                QuestType.PHOTO -> if (!isCameraOpen) { Toast.makeText(context, "⏰ Время вышло! Квест пропущен", Toast.LENGTH_SHORT).show(); vm.skipCurrentQuest() }
                QuestType.VOICE -> if (state.pendingVoiceUri == null) { Toast.makeText(context, "⏰ Время вышло! Квест пропущен", Toast.LENGTH_SHORT).show(); vm.skipCurrentQuest() }
            }
        }
    }

    val authState by authVm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Micro Quest", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
                actions = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("×${state.completedCount}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (authState.isLoggedIn) {
                        IconButton(onClick = onOpenFriends) { Icon(Icons.Default.People, contentDescription = "Друзья") }
                        IconButton(onClick = onOpenProfile) { Icon(Icons.Default.Person, contentDescription = "Профиль") }
                    }
                    IconButton(onClick = { vm.resetAll() }) { Icon(Icons.Default.Refresh, contentDescription = "Сбросить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                AnimatedContent(targetState = state.currentQuest, transitionSpec = {
                    slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
                }, label = "quest_transition") { quest ->
                    if (quest == null && state.allDone) AllDoneCard(onReset = { vm.resetAll() })
                    else if (quest != null) QuestCard(
                        quest = quest, timerSeconds = state.timerSeconds, timerRunning = state.timerRunning,
                        pendingPhotoUri = state.pendingPhotoUri,
                        onComplete = { vm.completeCurrentQuest(state.pendingPhotoUri) },
                        onSkip = { vm.skipCurrentQuest() }, onLaunchCamera = { launchCamera() },
                        onOpenGallery = { galleryLauncher.launch("image/*") },
                        pendingAnswer = state.pendingAnswer, onAnswerChanged = { vm.onAnswerChanged(it) },
                        pendingVoiceUri = state.pendingVoiceUri, isRecording = isRecording,
                        onToggleRecording = { toggleRecording() }, pendingVideoUri = state.pendingVideoUri,
                        onLaunchVideoCamera = { launchVideoCamera() },
                        onOpenVideoGallery = { videoGalleryLauncher.launch("video/*") },
                    )
                }
            }
            if (state.history.isNotEmpty()) {
                item { Text("История (${state.history.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(state.history, key = { it.rowId }) { item ->
                    HistoryItem(item = item, playingUri = playingUri, isPaused = isPaused,
                        onPlayVoice = { uri -> playVoice(uri) }, onStopVoice = { stopPlayer() })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quest card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuestCard(
    quest: com.example.microquest.data.Quest, timerSeconds: Int, timerRunning: Boolean,
    pendingPhotoUri: Uri?, onComplete: () -> Unit, onSkip: () -> Unit,
    onLaunchCamera: () -> Unit, onOpenGallery: () -> Unit,
    pendingAnswer: String, onAnswerChanged: (String) -> Unit,
    pendingVoiceUri: Uri?, isRecording: Boolean, onToggleRecording: () -> Unit,
    pendingVideoUri: Uri?, onLaunchVideoCamera: () -> Unit, onOpenVideoGallery: () -> Unit,
) {
    val typeColor = when (quest.type) {
        QuestType.ACTION -> MaterialTheme.colorScheme.tertiary; QuestType.TEXT -> MaterialTheme.colorScheme.secondary
        QuestType.PHOTO -> MaterialTheme.colorScheme.primary; QuestType.VOICE -> MaterialTheme.colorScheme.error
    }
    val typeIcon = when (quest.type) { QuestType.ACTION -> "🏃"; QuestType.TEXT -> "✏️"; QuestType.PHOTO -> "📷"; QuestType.VOICE -> "🎙️" }
    val typeLabel = when (quest.type) { QuestType.ACTION -> "Действие"; QuestType.TEXT -> "Текст"; QuestType.PHOTO -> "Фото"; QuestType.VOICE -> "Голос" }
    val timerDone = !timerRunning && timerSeconds == 0
    val canComplete = when (quest.type) {
        QuestType.ACTION -> (timerRunning || timerDone) && pendingVideoUri != null
        QuestType.TEXT   -> (timerRunning || timerDone) && pendingAnswer.trim().isNotBlank()
        QuestType.PHOTO  -> (timerRunning || timerDone) && pendingPhotoUri != null
        QuestType.VOICE  -> (timerRunning || timerDone) && pendingVoiceUri != null
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.elevatedCardElevation(6.dp)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(50), color = typeColor.copy(alpha = 0.15f)) {
                Text("$typeIcon  $typeLabel", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = typeColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(text = quest.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
            TimerRing(totalSeconds = quest.durationSeconds, remainingSeconds = timerSeconds, running = timerRunning, color = typeColor, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (quest.type == QuestType.TEXT) {
                OutlinedTextField(value = pendingAnswer, onValueChange = onAnswerChanged, label = { Text("Твой ответ") }, placeholder = { Text("Напиши здесь...") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, shape = RoundedCornerShape(16.dp))
            }
            if (quest.type == QuestType.ACTION) {
                Text("🎬 Сними видео-пруф", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onLaunchVideoCamera, modifier = Modifier.weight(1f)) { Text("🎥 Камера") }
                    OutlinedButton(onClick = onOpenVideoGallery, modifier = Modifier.weight(1f)) { Text("🎞 Галерея") }
                }
                if (pendingVideoUri != null) Text("✅ Видео выбрано", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            if (quest.type == QuestType.PHOTO) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onLaunchCamera, modifier = Modifier.weight(1f)) { Text("📷 Камера") }
                    OutlinedButton(onClick = onOpenGallery, modifier = Modifier.weight(1f)) { Text("🖼 Галерея") }
                }
                if (pendingPhotoUri != null) AsyncImage(model = pendingPhotoUri, contentDescription = "Фото", modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
            }
            if (quest.type == QuestType.VOICE) {
                Button(onClick = onToggleRecording, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer)) {
                    Text(if (isRecording) "⏹ Остановить запись" else "🎙️ Начать запись",
                        color = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer)
                }
                if (pendingVoiceUri != null) Text("✅ Запись сохранена", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("⏭ Пропустить") }
                Button(onClick = onComplete, modifier = Modifier.weight(1f), enabled = canComplete) { Text("✅ Выполнено") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Circular timer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TimerRing(totalSeconds: Int, remainingSeconds: Int, running: Boolean, color: Color, modifier: Modifier = Modifier) {
    val fraction = if (totalSeconds > 0) remainingSeconds / totalSeconds.toFloat() else 0f
    val animatedFraction by animateFloatAsState(targetValue = fraction, animationSpec = tween(800, easing = LinearEasing), label = "timer")
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(100.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10f, cap = StrokeCap.Round)
            drawArc(color = color.copy(alpha = 0.15f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * animatedFraction, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$remainingSeconds", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                color = if (remainingSeconds <= 5 && running) MaterialTheme.colorScheme.error else color)
            Text("сек", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  All done
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AllDoneCard(onReset: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("🏆", fontSize = 64.sp)
            Text("Все квесты пройдены!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("Ты настоящий герой микро-приключений.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onReset) { Text("🔄 Начать заново") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Video player dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VideoPlayerDialog(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(uri) { onDispose { exoPlayer.release() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { androidx.media3.ui.PlayerView(it).apply { player = exoPlayer; useController = true; setFullscreenButtonClickListener { } } }, modifier = Modifier.fillMaxWidth().align(Alignment.Center))
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  History item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HistoryItem(item: CompletedQuest, playingUri: Uri?, isPaused: Boolean, onPlayVoice: (Uri) -> Unit, onStopVoice: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("ru")) }
    val dateStr = remember(item.completedAt) { fmt.format(Date(item.completedAt * 1000)) }
    val icon = when (item.questType) { "PHOTO" -> "📷"; "TEXT" -> "✏️"; "VOICE" -> "🎙️"; else -> "🏃" }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Text(icon, fontSize = 24.sp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.questText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
                    if (!item.userAnswer.isNullOrBlank()) Text("💬 ${item.userAnswer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (!item.photoUri.isNullOrBlank()) AsyncImage(model = Uri.parse(item.photoUri), contentDescription = "Фото", modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
            if (!item.videoUri.isNullOrBlank()) {
                val videoUri = Uri.parse(item.videoUri); var showVideoDialog by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { showVideoDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("▶ Смотреть видео-пруф")
                }
                if (showVideoDialog) VideoPlayerDialog(uri = videoUri, onDismiss = { showVideoDialog = false })
            }
            if (!item.voiceUri.isNullOrBlank()) {
                val uri = Uri.parse(item.voiceUri); val isThisPlaying = playingUri == uri
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onPlayVoice(uri) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) {
                        Icon(imageVector = if (isThisPlaying && !isPaused) Icons.Default.PauseCircle else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(when { isThisPlaying && !isPaused -> "Пауза"; isThisPlaying -> "Продолжить"; else -> "Слушать" }, fontSize = 12.sp)
                    }
                    if (isThisPlaying) {
                        OutlinedButton(onClick = onStopVoice, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(36.dp)) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Стоп", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
