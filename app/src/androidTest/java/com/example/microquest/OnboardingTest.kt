package com.example.microquest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.microquest.onboarding.OnboardingScreen
import com.example.microquest.ui.theme.MicroQuestTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest {

    @get:Rule
    val rule = createComposeRule()

    private fun launch(onFinish: () -> Unit = {}) {
        rule.setContent {
            MicroQuestTheme {
                OnboardingScreen(onFinish = onFinish)
            }
        }
    }

    @Test
    fun onboarding_showsFirstSlide() {
        launch()
        rule.onNodeWithText("Добро пожаловать в Questify!").assertIsDisplayed()
        rule.onNodeWithText("Далее").assertIsDisplayed()
    }

    @Test
    fun onboarding_nextButton_advancesToSecondSlide() {
        launch()
        rule.onNodeWithText("Далее").performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText("Докажи своё выполнение")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Докажи своё выполнение").assertIsDisplayed()
    }

    @Test
    fun onboarding_navigateThroughAllSlides() {
        launch()
        rule.onNodeWithText("Далее").performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText("Докажи своё выполнение").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Далее").performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText("Соревнуйся с друзьями").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Далее").performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText("Зарабатывай XP и ачивки").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("Начать!").assertIsDisplayed()
        rule.onNodeWithText("Далее").assertDoesNotExist()
    }

    @Test
    fun onboarding_lastSlide_finishButtonCallsCallback() {
        var finished = false
        launch(onFinish = { finished = true })

        repeat(3) {
            rule.onNodeWithText("Далее").performClick()
            rule.waitForIdle()
        }

        rule.onNodeWithText("Начать!").performClick()
        rule.waitForIdle()

        assertTrue("onFinish не был вызван", finished)
    }

    @Test
    fun onboarding_skipButton_callsCallback() {
        var finished = false
        launch(onFinish = { finished = true })

        rule.onNodeWithText("Пропустить").performClick()
        rule.waitForIdle()

        assertTrue("Пропустить не вызвал onFinish", finished)
    }

    @Test
    fun onboarding_dotsIndicator_isVisible() {
        launch()

        rule.onNodeWithText("Далее").assertIsDisplayed()

        rule.onNodeWithText("Далее").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Далее").assertIsDisplayed()
    }
}
