package com.example.microquest

import androidx.compose.foundation.Canvas
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.microquest.data.CompletedQuest
import com.example.microquest.data.QuestType
import com.example.microquest.ui.theme.MicroQuestTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  Activity
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MicroQuestTheme {
                MicroQuestApp()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Root composable
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicroQuestApp(vm: QuestViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── Camera / Gallery helpers ─────────────────────────────────────────
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) vm.onPhotoPicked(tempPhotoUri) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> vm.onPhotoPicked(uri) }
    // ── Voice recorder ───────────────────────────────────────────────────
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var tempVoiceFile by remember { mutableStateOf<File?>(null) }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File.createTempFile("quest_voice_", ".m4a", context.cacheDir)
            tempVoiceFile = file
            val recorder = android.media.MediaRecorder(context).apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
        }
    }
    // ── Media player для прослушивания голосовых ─────────────────────────
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }

    fun playVoice(uri: Uri) {
        if (playingUri == uri) {
            mediaPlayer?.apply { stop(); release() }
            mediaPlayer = null
            playingUri = null
            return
        }
        mediaPlayer?.apply { stop(); release() }
        mediaPlayer = null
        playingUri = uri

        try {
            val afd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
            val player = android.media.MediaPlayer()
            player.setDataSource(afd.fileDescriptor)
            afd.close()

            player.setOnPreparedListener { mp ->
                mp.start()
            }
            player.setOnCompletionListener { mp ->
                mp.release()
                mediaPlayer = null
                playingUri = null
            }
            player.setOnErrorListener { mp, what, extra ->
                android.util.Log.e("MicroQuest", "MediaPlayer error: what=$what extra=$extra")
                mp.release()
                mediaPlayer = null
                playingUri = null
                true
            }

            player.prepareAsync()   // ← асинхронно, не блокирует UI поток
            mediaPlayer = player

        } catch (e: Exception) {
            android.util.Log.e("MicroQuest", "playVoice crashed: ${e.message}", e)
            mediaPlayer = null
            playingUri = null
        }
    }

// Освобождаем при уходе с экрана
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.apply { stop(); release() }
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            // Стоп
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            isRecording = false
            tempVoiceFile?.let { file ->
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                vm.onVoicePicked(uri)
            }
        } else {
            // Старт
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val file = File.createTempFile("quest_voice_", ".m4a", context.cacheDir)
                tempVoiceFile = file
                val recorder = android.media.MediaRecorder(context).apply {
                    setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                    setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                isRecording = true
            } else {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File.createTempFile("quest_", ".jpg", context.cacheDir)
            tempPhotoUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            cameraLauncher.launch(tempPhotoUri!!)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val file = File.createTempFile("quest_", ".jpg", context.cacheDir)
            tempPhotoUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            cameraLauncher.launch(tempPhotoUri!!)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Scaffold ─────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "⚡ Micro Quest",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            "×${state.completedCount}",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { vm.resetAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Сбросить всё")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // ── Current quest card ────────────────────────────────────
            item {
                AnimatedContent(
                    targetState = state.currentQuest,
                    transitionSpec = {
                        slideInVertically { -it } + fadeIn() togetherWith
                                slideOutVertically { it } + fadeOut()
                    },
                    label = "quest_transition"
                ) { quest ->
                    if (quest == null && state.allDone) {
                        AllDoneCard(onReset = { vm.resetAll() })
                    } else if (quest != null) {
                        QuestCard(
                            quest = quest,
                            timerSeconds = state.timerSeconds,
                            timerRunning = state.timerRunning,
                            pendingPhotoUri = state.pendingPhotoUri,
                            onStart = { vm.startTimer() },
                            onComplete = { vm.completeCurrentQuest(state.pendingPhotoUri) },
                            onSkip = { vm.skipCurrentQuest() },
                            onLaunchCamera = { launchCamera() },
                            onOpenGallery = { galleryLauncher.launch("image/*") },
                            pendingAnswer = state.pendingAnswer,
                            onAnswerChanged = { vm.onAnswerChanged(it) },
                            pendingVoiceUri = state.pendingVoiceUri,
                            isRecording = isRecording,
                            onToggleRecording = { toggleRecording() },
                        )
                    }
                }
            }

            // ── History header ────────────────────────────────────────

            if (state.history.isNotEmpty()) {
                item {
                    Text(
                        "История (${state.history.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.history, key = { it.rowId }) { item ->
                    HistoryItem(
                        item = item,
                        onPlayVoice = { uri -> playVoice(uri) }
                    )
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
    quest: com.example.microquest.data.Quest,
    timerSeconds: Int,
    timerRunning: Boolean,
    pendingPhotoUri: Uri?,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onLaunchCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    pendingAnswer: String,
    onAnswerChanged: (String) -> Unit,
    pendingVoiceUri: Uri?,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
) {
    val typeColor = when (quest.type) {
        QuestType.ACTION -> MaterialTheme.colorScheme.tertiary
        QuestType.TEXT   -> MaterialTheme.colorScheme.secondary
        QuestType.PHOTO  -> MaterialTheme.colorScheme.primary
        QuestType.VOICE  -> MaterialTheme.colorScheme.error
    }
    val typeIcon = when (quest.type) {
        QuestType.ACTION -> "🏃"
        QuestType.TEXT   -> "✏️"
        QuestType.PHOTO  -> "📷"
        QuestType.VOICE  -> "🎙️"
    }
    val typeLabel = when (quest.type) {
        QuestType.ACTION -> "Действие"
        QuestType.TEXT   -> "Текст"
        QuestType.PHOTO  -> "Фото"
        QuestType.VOICE  -> "Голос"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Type badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = typeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "$typeIcon  $typeLabel",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = typeColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            // Quest text
            Text(
                text = quest.text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )

            // Timer ring
            TimerRing(
                totalSeconds = quest.durationSeconds,
                remainingSeconds = timerSeconds,
                running = timerRunning,
                color = typeColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (quest.type == QuestType.TEXT) {
                OutlinedTextField(
                    value = pendingAnswer,
                    onValueChange = onAnswerChanged,
                    label = { Text("Твой ответ") },
                    placeholder = { Text("Напиши здесь...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            // Start / running hint
            if (!timerRunning) {
                OutlinedButton(
                    onClick = onStart,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("▶ Запустить таймер") }
            }

            // Photo quest extras
            if (quest.type == QuestType.PHOTO) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onLaunchCamera,
                        modifier = Modifier.weight(1f)
                    ) { Text("📷 Камера") }
                    OutlinedButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.weight(1f)
                    ) { Text("🖼 Галерея") }
                }
                if (pendingPhotoUri != null) {
                    Text(
                        "✅ Фото выбрано",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (quest.type == QuestType.VOICE) {
                Button(
                    onClick = onToggleRecording,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        if (isRecording) "⏹ Остановить запись" else "🎙️ Начать запись",
                        color = if (isRecording)
                            MaterialTheme.colorScheme.onError
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (pendingVoiceUri != null) {
                    Text(
                        "✅ Запись сохранена",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) { Text("⏭ Пропустить") }
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    enabled = !timerRunning && timerSeconds == 0 &&
                            (quest.type != QuestType.PHOTO || pendingPhotoUri != null) &&
                            (quest.type != QuestType.TEXT  || pendingAnswer.isNotBlank()) &&
                            (quest.type != QuestType.VOICE || pendingVoiceUri != null)
                ) { Text("✅ Выполнено") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Circular timer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TimerRing(
    totalSeconds: Int,
    remainingSeconds: Int,
    running: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fraction = if (totalSeconds > 0) remainingSeconds / totalSeconds.toFloat() else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800, easing = LinearEasing),
        label = "timer"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(100.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10f, cap = StrokeCap.Round)
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$remainingSeconds",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (remainingSeconds <= 5 && running)
                    MaterialTheme.colorScheme.error else color
            )
            Text("сек", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  All done card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AllDoneCard(onReset: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🏆", fontSize = 64.sp)
            Text(
                "Все квесты пройдены!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                "Ты настоящий герой микро-приключений.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onReset) { Text("🔄 Начать заново") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  History item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HistoryItem(item: CompletedQuest, onPlayVoice: (Uri) -> Unit) {
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale("ru")) }
    val dateStr = remember(item.completedAt) {
        fmt.format(Date(item.completedAt * 1000))
    }
    val icon = when (item.questType) {
        "PHOTO" -> "📷"
        "TEXT"  -> "✏️"
        "VOICE" -> "🎙️"
        else    -> "🏃"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(icon, fontSize = 24.sp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    item.questText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                if (!item.userAnswer.isNullOrBlank()) {
                    Text(
                        "💬 ${item.userAnswer}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                // Кнопка воспроизведения голосовой
                if (!item.voiceUri.isNullOrBlank()) {
                    val uri = Uri.parse(item.voiceUri)
                    OutlinedButton(
                        onClick = { onPlayVoice(uri) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Слушать", fontSize = 12.sp)
                    }
                }
                Text(
                    dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
