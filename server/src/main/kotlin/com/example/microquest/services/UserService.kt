package com.example.microquest.services

import com.example.microquest.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object UserService {

    fun getUserDto(userId: String): UserDto? {
        val uuid = runCatching { UUID.fromString(userId) }.getOrNull() ?: return null
        return transaction {
            Users.selectAll().where { Users.id eq uuid }.firstOrNull()
        }?.let { row ->
            val xp    = row[Users.xp]
            val level = row[Users.level]
            val count = transaction {
                ServerQuests.selectAll()
                    .where { ServerQuests.userId eq uuid }
                    .count().toInt()
            }
            UserDto(
                id             = row[Users.id].toString(),
                username       = row[Users.username],
                email          = row[Users.email],
                displayName    = row[Users.displayName],
                xp             = xp,
                level          = level,
                xpToNextLevel  = xpForNextLevel(level) - xp,
                completedCount = count,
                achievements   = AchievementService.getForUser(row[Users.id].toString())
            )
        }
    }

    fun updateProfile(userId: String, req: UpdateProfileRequest): UserDto {
        val uuid = UUID.fromString(userId)
        transaction {
            Users.update({ Users.id eq uuid }) { stmt ->
                req.displayName?.let { stmt[Users.displayName] = it }
            }
        }
        return getUserDto(userId) ?: error("User not found")
    }

    fun syncQuest(userId: String, req: SyncQuestRequest): UserDto {
        val uuid   = UUID.fromString(userId)
        val xpGain = when (req.questType) {
            "ACTION" -> 40; "VOICE" -> 35; "PHOTO" -> 30; else -> 20
        }
        transaction {
            ServerQuests.insert {
                it[ServerQuests.userId]      = uuid
                it[ServerQuests.questId]     = req.questId
                it[ServerQuests.questText]   = req.questText
                it[ServerQuests.questType]   = req.questType
                it[ServerQuests.completedAt] = req.completedAt
                it[ServerQuests.xpEarned]    = xpGain
                it[ServerQuests.proofText]   = req.proofText
                it[ServerQuests.status]      = "PENDING"
            }
            val row   = Users.selectAll().where { Users.id eq uuid }.first()
            val newXp = row[Users.xp] + xpGain
            Users.update({ Users.id eq uuid }) {
                it[Users.xp]    = newXp
                it[Users.level] = calculateLevel(newXp)
            }
        }
        // Check achievements after quest sync
        AchievementService.checkAndAward(userId)
        return getUserDto(userId)!!
    }

    // XP needed to reach the NEXT level from current `level`
    private fun xpForNextLevel(level: Int): Int = level * level * 100

    // Which level does `xp` correspond to?
    // Level 1 = 0..99 XP, Level 2 = 100..399 XP, Level 3 = 400..899 XP, ...
    private fun calculateLevel(xp: Int): Int {
        var lv = 1
        while (xp >= xpForNextLevel(lv)) lv++
        return lv   // lv-1 was a bug: new users got downgraded to level 0 after first quest
    }
}
