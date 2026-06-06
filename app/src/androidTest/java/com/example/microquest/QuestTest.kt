package com.example.microquest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.microquest.data.AppDatabase
import com.example.microquest.data.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun loginAsTestUser() {
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

        rule.waitUntil(15_000) {
            rule.onAllNodes(
                hasText("Выполнено", ignoreCase = true) or
                        hasText("Пропустить", ignoreCase = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun quest_isDisplayedOnMainScreen() {
        rule.onAllNodes(
            hasText("Выполнено", ignoreCase = true) or
                    hasText("Пропустить", ignoreCase = true)
        ).onFirst().assertIsDisplayed()
    }

    @Test
    fun quest_skip_loadsNextQuest() {
        rule.onNodeWithText("Пропустить", ignoreCase = true).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Пропустить", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Пропустить", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun quest_complete_savesInDatabase() {
        val dao = AppDatabase.getInstance(rule.activity.applicationContext).completedQuestDao()
        val countBefore = runBlocking { dao.completedIds().size }

        repeat(15) {
            if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                    .isNotEmpty()
            ) return@repeat
            rule.onNodeWithText("Пропустить", ignoreCase = true).performClick()
            rule.waitForIdle()
        }
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Тест")
        rule.onNodeWithText("Выполнено", ignoreCase = true).performClick()
        rule.waitForIdle()

        rule.waitUntil(10_000) {
            runBlocking { dao.completedIds().size } > countBefore
        }

        val countAfter = runBlocking { dao.completedIds().size }
        assertTrue("Квест не сохранился в БД", countAfter > countBefore)
    }

    @Test
    fun quest_textType_completeWithAnswer() {

        repeat(10) {
            if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                    .isNotEmpty()
            ) return@repeat
            rule.onNodeWithText("Пропустить", ignoreCase = true).performClick()
            rule.waitForIdle()
        }

        if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) {
            rule.onAllNodes(hasSetTextAction()).onFirst()
                .performTextInput("Тестовый ответ")
        }

        rule.onNodeWithText("Выполнено", ignoreCase = true).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Пропустить", ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun timer_startsAndCountsDown() {

        val timerBtn = rule.onAllNodes(
            hasContentDescription("Таймер", ignoreCase = true) or
                    hasContentDescription("Старт", ignoreCase = true)
        ).fetchSemanticsNodes()

        if (timerBtn.isEmpty()) return

        rule.onAllNodes(
            hasContentDescription("Таймер", ignoreCase = true) or
                    hasContentDescription("Старт", ignoreCase = true)
        ).onFirst().performClick()

        Thread.sleep(1_500)
        rule.waitForIdle()

        rule.waitUntil(5_000) {
            rule.onAllNodes(
                hasText("29") or hasText("28") or hasText("27") or hasText("26")
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun profile_showsXpAndLevel() {
        rule.onAllNodes(hasContentDescription("Профиль", ignoreCase = true))
            .onFirst().performClick()
        rule.waitForIdle()

        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("XP", ignoreCase = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText("XP", ignoreCase = true)).onFirst().assertIsDisplayed()
    }

    @Test
    fun profile_achievementsAreShown() {
        rule.onAllNodes(hasContentDescription("Профиль", ignoreCase = true))
            .onFirst().performClick()
        rule.waitForIdle()

        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("Достижения", ignoreCase = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasText("Достижения", ignoreCase = true))
            .onFirst().assertIsDisplayed()
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
