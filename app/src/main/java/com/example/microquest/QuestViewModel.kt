package com.example.microquest

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.microquest.data.*
import com.example.microquest.network.ApiClient
import com.example.microquest.network.SyncQuestRequest
import com.example.microquest.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody


data class QuestUiState(
    val currentQuest: Quest? = null,
    val timerSeconds: Int = 30,
    val timerRunning: Boolean = false,
    val completedCount: Int = 0,
    val history: List<CompletedQuest> = emptyList(),
    val allDone: Boolean = false,
    val pendingPhotoUri: Uri? = null,
    val pendingAnswer: String = "",
    val pendingVoiceUri: Uri? = null,
    val playingVoiceUri: Uri? = null,
    val pendingVideoUri: Uri? = null,
    val timedOut: Boolean = false,
    val isUploading: Boolean = false
)


class QuestViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.completedQuestDao()
    private val pendingDao = db.pendingSyncDao()
    private val api get() = ApiClient.get(getApplication<Application>().applicationContext)

    private val _state = MutableStateFlow(QuestUiState())
    val state: StateFlow<QuestUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            dao.countFlow().collect { count ->
                _state.update { it.copy(completedCount = count) }
            }
        }
        viewModelScope.launch {
            dao.historyFlow().collect { history ->
                _state.update { it.copy(history = history) }
            }
        }

        loadNextQuest()
    }


    fun completeCurrentQuest(photoUri: Uri? = null) {
        val quest = _state.value.currentQuest ?: return
        val answer = _state.value.pendingAnswer.trim().takeIf { it.isNotEmpty() }
        val voiceUri = _state.value.pendingVoiceUri
        val videoUri = _state.value.pendingVideoUri
        val mediaUri = photoUri ?: videoUri

        viewModelScope.launch {
            val completedAt = System.currentTimeMillis() / 1000

            var serverMediaUrl: String? = null
            if (mediaUri != null) {
                _state.update { it.copy(isUploading = true) }
                serverMediaUrl = uploadMedia(mediaUri)
                _state.update { it.copy(isUploading = false) }
            }

            dao.insert(
                CompletedQuest(
                    questId = quest.id,
                    questText = quest.text,
                    questType = quest.type.name,
                    completedAt = completedAt,
                    photoUri = photoUri?.toString(),
                    userAnswer = answer,
                    voiceUri = voiceUri?.toString(),
                    videoUri = videoUri?.toString()
                )
            )

            launch {
                val ctx = getApplication<Application>().applicationContext
                try {
                    api.syncQuest(
                        SyncQuestRequest(
                            questId = quest.id,
                            questText = quest.text,
                            questType = quest.type.name,
                            completedAt = completedAt,
                            proofText = answer,
                            mediaUrl = serverMediaUrl
                        )
                    )
                    Log.d("QuestVM", "Quest ${quest.id} synced to server")
                } catch (e: Exception) {
                    Log.w("QuestVM", "Server sync failed, queuing for retry: ${e.message}")
                    pendingDao.insert(
                        PendingSync(
                            questId = quest.id,
                            questText = quest.text,
                            questType = quest.type.name,
                            completedAt = completedAt,
                            proofText = answer,
                            mediaUrl = serverMediaUrl
                        )
                    )
                    SyncWorker.enqueue(ctx)
                }
            }
            loadNextQuest()
        }
    }

    private suspend fun uploadMedia(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val mimeType = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = if (mimeType.contains("video")) "mp4" else "jpg"
                val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "proof.$ext", requestBody)
                api.uploadMedia(part)["url"]
            } catch (e: Exception) {
                Log.w("QuestVM", "Media upload failed: ${e.message}")
                null
            }
        }
    }

    fun skipCurrentQuest() = loadNextQuest()

    fun onPhotoPicked(uri: Uri?) {
        _state.update { it.copy(pendingPhotoUri = uri) }
    }

    fun startTimer() {
        timerJob?.cancel()
        _state.update {
            it.copy(
                timerSeconds = it.currentQuest?.durationSeconds ?: 30,
                timerRunning = true
            )
        }
        timerJob = viewModelScope.launch {
            while (_state.value.timerSeconds > 0) {
                delay(1_000)
                _state.update { it.copy(timerSeconds = it.timerSeconds - 1) }
            }
            _state.update { it.copy(timerRunning = false) }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        _state.update { it.copy(timerRunning = false) }
    }

    fun resetAll() {
        viewModelScope.launch {
            dao.clearAll()
            loadNextQuest()
        }
    }


    private fun loadNextQuest() {
        stopTimer()
        viewModelScope.launch {
            val doneIds = dao.completedIds().toSet()
            val next = QuestProvider.random(doneIds)
            _state.update {
                it.copy(
                    currentQuest = next,
                    allDone = next == null,
                    timerSeconds = next?.durationSeconds ?: 30,
                    pendingPhotoUri = null,
                    pendingAnswer = "",
                    pendingVoiceUri = null,
                    pendingVideoUri = null,
                    timedOut = false
                )
            }
        }
    }

    fun onAnswerChanged(text: String) {
        _state.update { it.copy(pendingAnswer = text) }
    }

    fun onVoicePicked(uri: Uri?) {
        _state.update { it.copy(pendingVoiceUri = uri) }
    }

    fun setPlayingVoice(uri: Uri?) {
        _state.update { it.copy(playingVoiceUri = uri) }
    }

    fun onVideoPicked(uri: Uri?) {
        _state.update { it.copy(pendingVideoUri = uri) }
    }

    fun markTimedOut() {
        _state.update { it.copy(timedOut = true) }
    }
}
