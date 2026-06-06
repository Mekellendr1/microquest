package com.example.microquest.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.microquest.data.TokenStore
import com.example.microquest.network.ApiClient
import com.example.microquest.network.LoginRequest
import com.example.microquest.network.RegisterRequest
import com.example.microquest.network.UserDto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val user: UserDto? = null,
    val isLoggedIn: Boolean = false
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val api get() = ApiClient.get(ctx)

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ApiClient.loadToken(ctx)
            TokenStore.tokenFlow(ctx).collect { token ->
                ApiClient.cachedToken = token
                _state.update { it.copy(isLoggedIn = token != null) }
            }
        }
    }


    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Заполни все поля") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val res = api.login(LoginRequest(email.trim(), password))
                ApiClient.cachedToken = res.token
                TokenStore.save(
                    ctx,
                    res.token,
                    res.user.id,
                    res.user.username,
                    res.user.displayName
                )
                _state.update { it.copy(isLoading = false, user = res.user, isLoggedIn = true) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = parseError(e)) }
            }
        }
    }

    fun register(
        username: String, email: String, password: String, displayName: String,
        onSuccess: () -> Unit
    ) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Заполни все поля") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val res = api.register(
                    RegisterRequest(
                        username = username.trim(),
                        email = email.trim(),
                        password = password,
                        displayName = displayName.trim().ifBlank { username.trim() }
                    )
                )
                ApiClient.cachedToken = res.token
                TokenStore.save(
                    ctx,
                    res.token,
                    res.user.id,
                    res.user.username,
                    res.user.displayName
                )
                _state.update { it.copy(isLoading = false, user = res.user, isLoggedIn = true) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = parseError(e)) }
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            try {
                val user = api.getProfile()
                _state.update { it.copy(user = user) }
            } catch (_: Exception) {
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            ApiClient.cachedToken = null
            TokenStore.clearAuth(ctx)
            _state.update { it.copy(isLoggedIn = false, user = null) }
            onDone()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }


    private fun parseError(e: Exception): String = when (e) {
        is HttpException -> {
            runCatching {
                val body = e.response()?.errorBody()?.string() ?: ""
                Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    ?: "Ошибка сервера (${e.code()})"
            }.getOrDefault("Ошибка сервера (${e.code()})")
        }

        is java.net.ConnectException, is java.net.SocketException ->
            "Нет подключения к серверу"

        is java.net.SocketTimeoutException ->
            "Сервер не отвечает — проверь подключение"

        else -> e.message ?: "Неизвестная ошибка"
    }
}
