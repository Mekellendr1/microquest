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
                it[ServerQuests.mediaUrl]    = req.mediaUrl
                it[ServerQuests.status]      = "PENDING"
            }
            val row   = Users.selectAll().where { Users.id eq uuid }.first()
            val newXp = row[Users.xp] + xpGain
            Users.update({ Users.id eq uuid }) {
                it[Users.xp]    = newXp
                it[Users.level] = calculateLevel(newXp)
            }
        }
        AchievementService.checkAndAward(userId)
        return getUserDto(userId)!!
    }

    fun getLeaderboard(userId: String): List<LeaderboardEntry> {
        val uid = UUID.fromString(userId)
        val friendIds: List<UUID> = transaction {
            Friendships.selectAll().where {
                ((Friendships.requesterId eq uid) or (Friendships.receiverId eq uid)) and
                (Friendships.status eq "ACCEPTED")
            }.map { row ->
                if (row[Friendships.requesterId] == uid) row[Friendships.receiverId]
                else row[Friendships.requesterId]
            }
        }
        val allIds = friendIds + uid
        return transaction {
            Users.selectAll()
                .where { Users.id inList allIds }
                .orderBy(Users.xp, SortOrder.DESC)
                .mapIndexed { index, row ->
                    val count = ServerQuests.selectAll()
                        .where { ServerQuests.userId eq row[Users.id] }.count().toInt()
                    LeaderboardEntry(
                        rank           = index + 1,
                        userId         = row[Users.id].toString(),
                        username       = row[Users.username],
                        displayName    = row[Users.displayName],
                        level          = row[Users.level],
                        xp             = row[Users.xp],
                        completedCount = count,
                        isMe           = row[Users.id] == uid
                    )
                }
        }
    }

    private fun xpForNextLevel(level: Int): Int = level * level * 100

    private fun calculateLevel(xp: Int): Int {
        var lv = 1
        while (xp >= xpForNextLevel(lv)) lv++
        return lv
    }
}
