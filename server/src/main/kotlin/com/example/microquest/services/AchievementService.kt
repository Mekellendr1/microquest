package com.example.microquest.services

import com.example.microquest.models.AchievementDto
import com.example.microquest.models.Friendships
import com.example.microquest.models.ServerQuests
import com.example.microquest.models.UserAchievements
import com.example.microquest.models.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID


data class AchievementDef(
    val key: String,
    val name: String,
    val description: String,
    val icon: String
)

val ALL_ACHIEVEMENTS = listOf(
    AchievementDef("first_quest", "Первый шаг", "Выполни свой первый квест", "🌱"),

    AchievementDef("quests_5", "На разогреве", "Выполни 5 квестов", "🔥"),
    AchievementDef("quests_10", "В потоке", "Выполни 10 квестов", "⚡"),
    AchievementDef("quests_25", "Ветеран", "Выполни 25 квестов", "💪"),
    AchievementDef("quests_50", "Легенда", "Выполни 50 квестов", "🏆"),
    AchievementDef("quests_100", "Непостижимый", "Выполни 100 квестов", "👑"),

    AchievementDef("photo_5", "Фотограф", "Выполни 5 фото-квестов", "📷"),
    AchievementDef("voice_5", "Голосовик", "Выполни 5 голосовых квестов", "🎙️"),
    AchievementDef("action_5", "Экшн-герой", "Выполни 5 видео-квестов", "🏃"),
    AchievementDef("text_5", "Писатель", "Выполни 5 текстовых квестов", "✍️"),
    AchievementDef("all_types", "Разносторонний", "Выполни все 4 типа квестов", "🎭"),
    AchievementDef("first_friend", "Не один", "Добавь первого друга", "👥"),
    AchievementDef("friends_5", "Своя тусовка", "Заведи 5 друзей", "🎉"),
    AchievementDef("first_vote", "Справедливый", "Проголосуй за квест друга", "⚖️"),
    AchievementDef("verified_quest", "Доказал!", "Получи подтверждение от друга", "✅"),

    AchievementDef("level_5", "Набирающий силу", "Достигни 5 уровня", "⚔️"),
    AchievementDef("level_10", "Мастер", "Достигни 10 уровня", "🧙"),
    AchievementDef("level_20", "Легенда сезона", "Достигни 20 уровня", "🌟"),

    AchievementDef("xp_500", "Опытный", "Набери 500 XP", "💫"),
    AchievementDef("xp_1000", "Бывалый", "Набери 1000 XP", "🌀"),
)

private val achievementMap = ALL_ACHIEVEMENTS.associateBy { it.key }


object AchievementService {

    fun checkAndAward(userId: String): List<String> {
        val uid = UUID.fromString(userId)
        val newlyUnlocked = mutableListOf<String>()

        transaction {
            val unlocked = UserAchievements.selectAll()
                .where { UserAchievements.userId eq uid }
                .map { it[UserAchievements.achievementKey] }
                .toSet()

            val allQuests = ServerQuests.selectAll()
                .where { ServerQuests.userId eq uid }.toList()
            val totalQuests = allQuests.size
            val byType = allQuests.groupBy { it[ServerQuests.questType] }

            val userRow = Users.selectAll().where { Users.id eq uid }.first()
            val level = userRow[Users.level]
            val xp = userRow[Users.xp]

            val friendCount = Friendships.selectAll().where {
                ((Friendships.requesterId eq uid) or (Friendships.receiverId eq uid)) and
                        (Friendships.status eq "ACCEPTED")
            }.count().toInt()

            val hasVoted = org.jetbrains.exposed.sql.SqlExpressionBuilder.run {
                com.example.microquest.models.QuestVotes.selectAll()
                    .where { com.example.microquest.models.QuestVotes.voterId eq uid }
                    .count() > 0
            }

            val hasVerified = allQuests.any {
                it[ServerQuests.status] == "VERIFIED"
            }

            val typesCompleted = byType.keys

            val candidates = mapOf(
                "first_quest" to (totalQuests >= 1),
                "quests_5" to (totalQuests >= 5),
                "quests_10" to (totalQuests >= 10),
                "quests_25" to (totalQuests >= 25),
                "quests_50" to (totalQuests >= 50),
                "quests_100" to (totalQuests >= 100),
                "photo_5" to ((byType["PHOTO"]?.size ?: 0) >= 5),
                "voice_5" to ((byType["VOICE"]?.size ?: 0) >= 5),
                "action_5" to ((byType["ACTION"]?.size ?: 0) >= 5),
                "text_5" to ((byType["TEXT"]?.size ?: 0) >= 5),
                "all_types" to (setOf(
                    "PHOTO",
                    "VOICE",
                    "ACTION",
                    "TEXT"
                ).all { it in typesCompleted }),
                "first_friend" to (friendCount >= 1),
                "friends_5" to (friendCount >= 5),
                "first_vote" to hasVoted,
                "verified_quest" to hasVerified,
                "level_5" to (level >= 5),
                "level_10" to (level >= 10),
                "level_20" to (level >= 20),
                "xp_500" to (xp >= 500),
                "xp_1000" to (xp >= 1000),
            )

            val now = System.currentTimeMillis() / 1000
            candidates.forEach { (key, earned) ->
                if (earned && key !in unlocked) {
                    UserAchievements.insert {
                        it[UserAchievements.userId] = uid
                        it[UserAchievements.achievementKey] = key
                        it[UserAchievements.unlockedAt] = now
                    }
                    newlyUnlocked += key
                }
            }
        }

        // Push notification for each new achievement
        newlyUnlocked.forEach { key ->
            achievementMap[key]?.let { def ->
                NotificationService.notifyAchievementUnlocked(userId, def.name, def.icon)
            }
        }

        return newlyUnlocked
    }

    /** Returns all achievements with unlock status for this user. */
    fun getForUser(userId: String): List<AchievementDto> {
        val uid = UUID.fromString(userId)
        val unlocked: Map<String, Long> = transaction {
            UserAchievements.selectAll()
                .where { UserAchievements.userId eq uid }
                .associate { it[UserAchievements.achievementKey] to it[UserAchievements.unlockedAt] }
        }
        return ALL_ACHIEVEMENTS.map { def ->
            AchievementDto(
                key = def.key,
                name = def.name,
                description = def.description,
                icon = def.icon,
                unlockedAt = unlocked[def.key]
            )
        }
    }
}
