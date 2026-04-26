package com.example.microquest

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.microquest.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────

data class QuestUiState(
    val currentQuest: Quest? = null,
    val timerSeconds: Int = 30,
    val timerRunning: Boolean = false,
    val completedCount: Int = 0,
    val history: List<CompletedQuest> = emptyList(),
    val allDone: Boolean = false,
    /** Uri of photo taken for the current PHOTO quest (transient) */
    val pendingPhotoUri: Uri? = null,
    val pendingAnswer: String = "",
    val pendingVoiceUri: Uri? = null,
    val playingVoiceUri: Uri? = null,
    val pendingVideoUri: Uri? = null,
    val timedOut: Boolean = false
){

}

// ─────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────

class QuestViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).completedQuestDao()

    // ── Internal mutable state ───────────────────────────────────────────
    private val _state = MutableStateFlow(QuestUiState())
    val state: StateFlow<QuestUiState> = _state.asStateFlow()

    private var timerJob: Job? = null

    // ── Init ─────────────────────────────────────────────────────────────
    init {
        // Collect completed count
        viewModelScope.launch {
            dao.countFlow().collect { count ->
                _state.update { it.copy(completedCount = count) }
            }
        }
        // Collect history
        viewModelScope.launch {
            dao.historyFlow().collect { history ->
                _state.update { it.copy(history = history) }
            }
        }
        // Load first quest
        loadNextQuest()
    }

    // ── Public API ───────────────────────────────────────────────────────

    /** Called when user taps "✅ Выполнено". */
    fun completeCurrentQuest(photoUri: Uri? = null) {
        val quest = _state.value.currentQuest ?: return
        val answer = _state.value.pendingAnswer.trim().takeIf { it.isNotEmpty() }
        val voiceUri = _state.value.pendingVoiceUri
        val videoUri = _state.value.pendingVideoUri
        viewModelScope.launch {
            dao.insert(
                CompletedQuest(
                    questId = quest.id,
                    questText = quest.text,
                    questType = quest.type.name,
                    photoUri = photoUri?.toString(),
                    userAnswer = answer,
                    voiceUri = voiceUri?.toString(),
                    videoUri = videoUri?.toString()
                )
            )
            loadNextQuest()
        }
    }

    /** Called when user taps "⏭ Пропустить". */
    fun skipCurrentQuest() = loadNextQuest()

    /** Store photo URI from camera/gallery before completing. */
    fun onPhotoPicked(uri: Uri?) {
        _state.update { it.copy(pendingPhotoUri = uri) }
    }

    /** Start / restart the countdown timer. */
    fun startTimer() {
        timerJob?.cancel()
        _state.update { it.copy(timerSeconds = it.currentQuest?.durationSeconds ?: 30, timerRunning = true) }
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

    // ── Private ──────────────────────────────────────────────────────────

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
