package com.example.microquest.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

object TokenStore {
    private val TOKEN = stringPreferencesKey("jwt_token")
    private val USER_ID = stringPreferencesKey("user_id")
    private val USERNAME = stringPreferencesKey("username")
    private val DISPLAY_NAME = stringPreferencesKey("display_name")

    fun tokenFlow(ctx: Context): Flow<String?> =
        ctx.dataStore.data.map { it[TOKEN] }

    suspend fun isLoggedIn(ctx: Context): Boolean =
        ctx.dataStore.data.map { it[TOKEN] != null }.first()

    suspend fun save(
        ctx: Context,
        token: String,
        userId: String,
        username: String,
        displayName: String
    ) {
        ctx.dataStore.edit { p ->
            p[TOKEN] = token
            p[USER_ID] = userId
            p[USERNAME] = username
            p[DISPLAY_NAME] = displayName
        }
    }

    suspend fun clear(ctx: Context) {
        ctx.dataStore.edit { it.clear() }
    }

    suspend fun clearAuth(ctx: Context) {
        ctx.dataStore.edit { p ->
            p.remove(TOKEN)
            p.remove(USER_ID)
            p.remove(USERNAME)
            p.remove(DISPLAY_NAME)
        }
    }


    private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    fun onboardingDoneFlow(ctx: Context): Flow<Boolean> =
        ctx.dataStore.data.map { it[ONBOARDING_DONE] == true }

    suspend fun markOnboardingDone(ctx: Context) {
        ctx.dataStore.edit { it[ONBOARDING_DONE] = true }
    }
}
