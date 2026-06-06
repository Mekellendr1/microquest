package com.example.microquest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.microquest.data.dataStore
import com.example.microquest.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FriendsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        runBlocking {
            rule.activity.dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("onboarding_done")] = true
            }
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        if (rule.onAllNodesWithText("Войти").fetchSemanticsNodes().isNotEmpty()) {
            registerAndLogin()
        }

        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("Друзья", ignoreCase = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasContentDescription("Друзья", ignoreCase = true))
            .onFirst().performClick()
        rule.waitForIdle()
    }

    @Test
    fun friends_screenLoads() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Друзья", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Друзья", ignoreCase = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun friends_allTabsAreVisible() {
        rule.onNodeWithText("Запросы", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Лента",   ignoreCase = true).assertIsDisplayed()
        rule.onAllNodes(hasText("Топ", ignoreCase = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun friends_addFriendDialog_opensAndCloses() {
        rule.onNodeWithContentDescription("Добавить друга").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Добавить друга").assertIsDisplayed()

        rule.onNodeWithText("Отмена").performClick()
        rule.waitForIdle()

        assertTrue(
            "Диалог не закрылся",
            rule.onAllNodesWithText("Отправить").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun friends_addNonExistentUser_showsError() {
        rule.onNodeWithContentDescription("Добавить друга").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("friends_username_input").performTextInput("nonexistent_xyz_99999")
        rule.onNodeWithText("Отправить").performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodes(
                hasText("не найден",     ignoreCase = true, substring = true) or
                hasText("ошибка",        ignoreCase = true, substring = true) or
                hasText("не существует", ignoreCase = true, substring = true) or
                hasText("не найд",       ignoreCase = true, substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun friends_leaderboardTab_loads() {
        rule.onAllNodes(hasText("Топ", ignoreCase = true)).onFirst().performClick()
        rule.waitForIdle()

        rule.waitUntil(5_000) {
            rule.onAllNodes(
                hasText("XP",      ignoreCase = true, substring = true) or
                hasText("пуст",    ignoreCase = true, substring = true) or
                hasText("друзей",  ignoreCase = true, substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun friends_feedTab_loads() {
        rule.onNodeWithText("Лента", ignoreCase = true).performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodes(
                hasText("пуста",  ignoreCase = true, substring = true) or
                hasText("квест",  ignoreCase = true, substring = true) or
                hasText("👍",     ignoreCase = true, substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun friends_requestsTab_loads() {
        rule.onNodeWithText("Запросы", ignoreCase = true).performClick()
        rule.waitForIdle()

        rule.waitUntil(5_000) {
            rule.onAllNodes(
                hasText("запрос", ignoreCase = true, substring = true) or
                hasText("нет",    ignoreCase = true, substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun friends_refreshButton_works() {
        rule.onNodeWithContentDescription("Обновить", ignoreCase = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Запросы", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun friends_feedVoting_works() {
        val ts       = System.currentTimeMillis()
        val userBName  = "testb_${ts % 100_000}"
        val userBEmail = "testb_${ts % 100_000}@test.com"

        val anonApi = buildApi(null)
        val regB = runBlocking {
            anonApi.register(RegisterRequest(userBName, userBEmail, "password123", "Test B"))
        }
        val apiB = buildApi(regB.token)
        val apiA = ApiClient.get(rule.activity.applicationContext)

        val userAProfile = runBlocking { apiA.getProfile() }

        runBlocking { apiB.sendFriendRequest(AddFriendRequest(userAProfile.username)) }

        val requests = runBlocking { apiA.getFriendRequests() }
        val req = requests.first { it.from.username == userBName }
        runBlocking { apiA.acceptFriendRequest(req.friendshipId) }

        runBlocking {
            apiB.syncQuest(SyncQuestRequest(
                questId     = (ts % 1_000_000).toInt(),
                questText   = "Напиши что-нибудь вдохновляющее",
                questType   = "TEXT",
                completedAt = ts / 1000,
                proofText   = "Жизнь прекрасна! Тест $userBName",
                mediaUrl    = null
            ))
        }

        rule.onNodeWithText("Лента", ignoreCase = true).performClick()
        rule.onNodeWithContentDescription("Обновить", ignoreCase = true).performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText(userBName, ignoreCase = true, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodes(hasText("👍", substring = true)).onFirst().performClick()
        rule.waitForIdle()

        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("👍 1", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText("👍 1", substring = true)).onFirst().assertIsDisplayed()
    }

    private fun buildApi(token: String?): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    token?.let { addHeader("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(req)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun assertTrue(message: String, value: Boolean) {
        org.junit.Assert.assertTrue(message, value)
    }

    private fun registerAndLogin() {
        val ts = System.currentTimeMillis() % 100_000
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Нет аккаунта? Зарегистрируйся")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Нет аккаунта? Зарегистрируйся").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("register_username").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("register_username").performTextInput("demo_$ts")
        rule.onNodeWithTag("register_email").performTextInput("demo_$ts@test.com")
        rule.onNodeWithTag("register_displayname").performTextInput("Demo User")
        rule.onNodeWithTag("register_password").performTextInput("password123")
        rule.onNodeWithText("Создать аккаунт").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Выполнено", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
