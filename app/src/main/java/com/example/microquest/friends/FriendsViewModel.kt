package com.example.microquest.friends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.microquest.network.ApiClient
import com.example.microquest.network.AddFriendRequest
import com.example.microquest.network.FriendDto
import com.example.microquest.network.FriendRequestDto
import com.example.microquest.network.QuestFeedItem
import com.example.microquest.network.VoteRequest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class FriendsUiState(
    val isLoading: Boolean = false,
    val friends: List<FriendDto> = emptyList(),
    val incomingRequests: List<FriendRequestDto> = emptyList(),
    val feed: List<QuestFeedItem> = emptyList(),
    val error: String? = null,
    val message: String? = null   // success snackbar text
)

class FriendsViewModel(app: Application) : AndroidViewModel(app) {

    private val api = ApiClient.get(app.applicationContext)

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    init { refresh() }

    // ── Load all data ─────────────────────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val friends  = api.getFriends()
                val requests = api.getFriendRequests()
                val feed     = api.getFeed()
                _state.update {
                    it.copy(isLoading = false, friends = friends,
                        incomingRequests = requests, feed = feed)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = parseError(e)) }
            }
        }
    }

    // ── Send friend request ───────────────────────────────────────────────────

    fun addFriend(username: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                api.sendFriendRequest(AddFriendRequest(username.trim()))
                _state.update { it.copy(isLoading = false, message = "Запрос отправлен @$username") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = parseError(e)) }
            }
        }
    }

    // ── Accept / decline incoming request ────────────────────────────────────

    fun acceptRequest(friendshipId: String) {
        viewModelScope.launch {
            try {
                api.acceptFriendRequest(friendshipId)
                _state.update { it.copy(message = "Запрос принят!") }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = parseError(e)) }
            }
        }
    }

    fun declineRequest(friendshipId: String) {
        viewModelScope.launch {
            try {
                api.declineFriendRequest(friendshipId)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = parseError(e)) }
            }
        }
    }

    // ── Remove friend ─────────────────────────────────────────────────────────

    fun removeFriend(friendshipId: String) {
        viewModelScope.launch {
            try {
                api.removeFriend(friendshipId)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = parseError(e)) }
            }
        }
    }

    // ── Vote ──────────────────────────────────────────────────────────────────

    fun vote(questId: String, approve: Boolean) {
        viewModelScope.launch {
            try {
                api.vote(VoteRequest(questId, approve))
                // Update feed locally for instant feedback
                _state.update { s ->
                    s.copy(feed = s.feed.map { item ->
                        if (item.questId == questId) {
                            val wasApproved = item.myVote == true
                            val wasRejected = item.myVote == false
                            item.copy(
                                myVote     = approve,
                                approvals  = item.approvals
                                    - (if (wasApproved) 1 else 0)
                                    + (if (approve) 1 else 0),
                                rejections = item.rejections
                                    - (if (wasRejected) 1 else 0)
                                    + (if (!approve) 1 else 0)
                            )
                        } else item
                    })
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = parseError(e)) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearError()   = _state.update { it.copy(error = null) }

    // ── Error parsing ─────────────────────────────────────────────────────────

    private fun parseError(e: Exception): String = when (e) {
        is HttpException -> runCatching {
            val body = e.response()?.errorBody()?.string() ?: ""
            Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: "Ошибка сервера (${e.code()})"
        }.getOrDefault("Ошибка сервера (${e.code()})")
        is java.net.ConnectException -> "Нет соединения с сервером"
        else -> e.message ?: "Неизвестная ошибка"
    }
}
