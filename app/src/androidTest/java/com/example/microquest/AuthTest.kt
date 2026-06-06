package com.example.microquest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.microquest.data.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ts = System.currentTimeMillis() % 100_000
    private val testEmail = "test_$ts@demo.com"
    private val testPassword = "password123"
    private val testUsername = "user_$ts"
    private val testDisplay = "Тест Юзер"

    @Before
    fun logout() {
        runBlocking { rule.activity.dataStore.edit { it.clear() } }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        if (rule.onAllNodesWithText("Пропустить").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Пропустить").performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun register_withValidData_opensMainScreen() {
        navigateToRegister()
        fillRegisterForm(testUsername, testEmail, testDisplay, testPassword)
        rule.onNodeWithText("Создать аккаунт").performClick()

        rule.waitUntil(15_000) {
            rule.onAllNodes(
                hasText("Выполнено", ignoreCase = true) or
                        hasText("Пропустить", ignoreCase = true)
            )
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun register_withEmptyFields_showsError() {
        navigateToRegister()
        rule.onNodeWithText("Создать аккаунт").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Заполни все поля")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun login_withWrongPassword_showsError() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Email").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Email").performTextInput("wrong@email.com")
        rule.onNodeWithText("Пароль").performTextInput("wrongpass")
        rule.onNodeWithText("Войти").performClick()

        rule.waitUntil(15_000) {
            rule.onAllNodes(
                hasText("ошибка", ignoreCase = true, substring = true) or
                        hasText("неверн", ignoreCase = true, substring = true) or
                        hasText("не найд", ignoreCase = true, substring = true) or
                        hasText("подключени", ignoreCase = true, substring = true) or
                        hasText("не отвечает", ignoreCase = true, substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun login_withValidCredentials_opensMainScreen() {

        val ts2 = System.currentTimeMillis()
        val email2 = "login_$ts2@demo.com"
        val username2 = "login_$ts2"

        navigateToRegister()
        fillRegisterForm(username2, email2, "Login User", testPassword)
        rule.onNodeWithText("Создать аккаунт").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodes(hasText("Выполнено", ignoreCase = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodes(hasContentDescription("Профиль", ignoreCase = true))
            .onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Выйти").performClick()

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Email").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Email").performTextInput(email2)
        rule.onNodeWithText("Пароль").performTextInput(testPassword)
        rule.onNodeWithText("Войти").performClick()

        rule.waitUntil(15_000) {
            rule.onAllNodes(hasText("Выполнено", ignoreCase = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun navigateToRegister() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Нет аккаунта? Зарегистрируйся")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Нет аккаунта? Зарегистрируйся").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("register_username").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun fillRegisterForm(
        username: String,
        email: String,
        display: String,
        password: String
    ) {
        rule.onNodeWithTag("register_username").performTextInput(username)
        rule.onNodeWithTag("register_email").performTextInput(email)
        rule.onNodeWithTag("register_displayname").performTextInput(display)
        rule.onNodeWithTag("register_password").performTextInput(password)
    }
}
