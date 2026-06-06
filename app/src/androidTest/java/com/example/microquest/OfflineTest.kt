package com.example.microquest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.microquest.data.AppDatabase
import com.example.microquest.data.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineTest {

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
        val needLogin = rule.onAllNodesWithText("Войти").fetchSemanticsNodes().isNotEmpty()
        if (needLogin) registerAndLogin()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Выполнено", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun offline_questCompletedLocally_savedInRoom() {
        val db  = AppDatabase.getInstance(rule.activity.applicationContext)
        val dao = db.completedQuestDao()

        val countBefore = runBlocking { dao.completedIds().size }

        repeat(15) {
            if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) return@repeat
            rule.onNodeWithText("Пропустить", ignoreCase = true).performClick()
            rule.waitForIdle()
        }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Тест")
        rule.onNodeWithText("Выполнено", ignoreCase = true).performClick()
        rule.waitForIdle()

        rule.waitUntil(5_000) {
            runBlocking { dao.completedIds().size } > countBefore
        }

        val countAfter = runBlocking { dao.completedIds().size }
        assertTrue("Квест не сохранился в локальной БД", countAfter > countBefore)
    }

    @Test
    fun offline_syncWorker_enqueued() {

        repeat(15) {
            if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) return@repeat
            rule.onNodeWithText("Пропустить", ignoreCase = true).performClick()
            rule.waitForIdle()
        }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Тест")
        rule.onNodeWithText("Выполнено", ignoreCase = true).performClick()
        rule.waitForIdle()

        val workManager = WorkManager.getInstance(rule.activity.applicationContext)

        val works = workManager.getWorkInfosForUniqueWork("questify_sync").get()

        assertTrue(
            "SyncWorker не попал в очередь WorkManager",
            works.isNotEmpty()
        )
    }

    @Test
    fun pendingSync_emptyAfterSuccessfulSync() {
        val db         = AppDatabase.getInstance(rule.activity.applicationContext)
        val pendingDao = db.pendingSyncDao()

        rule.waitUntil(15_000) {
            runBlocking { pendingDao.getAll().isEmpty() }
        }

        assertEquals(
            "В очереди остались несинкнутые квесты при наличии сети",
            0,
            runBlocking { pendingDao.getAll().size }
        )
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
