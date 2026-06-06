package com.example.microquest.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val subtitle: String
)

private val pages = listOf(
    OnboardingPage(
        "🚀",
        "Добро пожаловать в Questify!",
        "Каждый день — новый микро-квест. Выполняй, доказывай, прокачивайся."
    ),
    OnboardingPage(
        "📸",
        "Докажи своё выполнение",
        "Сфотографируй результат, запиши голос или видео — выбирай как тебе удобнее."
    ),
    OnboardingPage(
        "👥",
        "Соревнуйся с друзьями",
        "Добавляй друзей, голосуй за их квесты и следи за лидербордом."
    ),
    OnboardingPage(
        "🏆",
        "Зарабатывай XP и ачивки",
        "За каждый квест — опыт и уровень. Собери все 20 достижений!"
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val page = pages[currentPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            AnimatedContent(
                targetState = page.emoji,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "emoji"
            ) { emoji ->
                Text(emoji, fontSize = 96.sp)
            }

            Spacer(Modifier.height(40.dp))

            AnimatedContent(
                targetState = page.title,
                transitionSpec = {
                    slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) togetherWith
                            slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                },
                label = "title"
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = page.subtitle,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(200))
                },
                label = "subtitle"
            ) { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }

            Spacer(Modifier.height(56.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.indices.forEach { i ->
                    val isActive = i == currentPage
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                            .size(width = if (isActive) 24.dp else 8.dp, height = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    if (currentPage < pages.lastIndex) {
                        currentPage++
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (currentPage < pages.lastIndex) "Далее" else "Начать!",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (currentPage < pages.lastIndex) {
                TextButton(onClick = onFinish) {
                    Text(
                        "Пропустить",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
