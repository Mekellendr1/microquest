package com.example.microquest.services

import com.example.microquest.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object FriendService {

    // ── Send friend request ───────────────────────────────────────────────────

    fun sendRequest(requesterId: String, targetUsername: String): FriendDto {
        val rId = UUID.fromString(requesterId)

        val targetRow = transaction {
            Users.selectAll().where { Users.username eq targetUsername }.firstOrNull()
        } ?: error("Пользователь «$targetUsername» не найден")

        val tId = targetRow[Users.id]
        require(rId != tId) { "Нельзя добавить себя в друзья" }

        // Check for existing friendship in either direction
        transaction {
            val existing = Friendships.selectAll().where {
                ((Friendships.requesterId eq rId) and (Friendships.receiverId eq tId)) or
                ((Friendships.requesterId eq tId) and (Friendships.receiverId eq rId))
            }.firstOrNull()
            require(existing == null) {
                when (existing?.get(Friendships.status)) {
                    "ACCEPTED" -> "Вы уже друзья"
                    "PENDING"  -> "Запрос уже отправлен"
                    else       -> "Нельзя отправить запрос"
                }
            }
        }

        val friendshipId = UUID.randomUUID()
        transaction {
            Friendships.insert {
                it[id]          = friendshipId
                it[Friendships.requesterId] = rId
                it[Friendships.receiverId]  = tId
                it[status]      = "PENDING"
                it[createdAt]   = System.currentTimeMillis() / 1000
            }
        }

        // Check achievements for requester (first_friend etc.)
        AchievementService.checkAndAward(requesterId)

        // Notify receiver
        val requesterName = transaction {
            Users.selectAll().where { Users.id eq rId }.first()[Users.username]
        }
        NotificationService.notifyFriendRequest(tId.toString(), requesterName)

        val count = transaction {
            ServerQuests.selectAll().where { ServerQuests.userId eq tId }.count().toInt()
        }
        return FriendDto(
            friendshipId  = friendshipId.toString(),
            userId        = tId.toString(),
            username      = targetRow[Users.username],
            displayName   = targetRow[Users.displayName],
            level         = targetRow[Users.level],
            xp            = targetRow[Users.xp],
            completedCount = count
        )
    }

    // ── Respond to request ────────────────────────────────────────────────────

    fun respondToRequest(userId: String, friendshipId: String, accept: Boolean) {
        val uid = UUID.fromString(userId)
        val fid = UUID.fromString(friendshipId)

        var requesterId: String? = null
        transaction {
            val row = Friendships.selectAll()
                .where { (Friendships.id eq fid) and (Friendships.receiverId eq uid) }
                .firstOrNull()
                ?: error("Запрос не найден")
            require(row[Friendships.status] == "PENDING") { "Запрос уже обработан" }

            requesterId = row[Friendships.requesterId].toString()
            Friendships.update({ Friendships.id eq fid }) {
                it[status] = if (accept) "ACCEPTED" else "DECLINED"
            }
        }
        if (accept) {
            // Both sides may unlock friend achievements
            AchievementService.checkAndAward(userId)
            requesterId?.let { AchievementService.checkAndAward(it) }

            // Notify requester
            val acceptorName = transaction {
                Users.selectAll().where { Users.id eq uid }.first()[Users.username]
            }
            requesterId?.let { NotificationService.notifyFriendAccepted(it, acceptorName) }
        }
    }

    // ── Remove friend ─────────────────────────────────────────────────────────

    fun removeFriend(userId: String, friendshipId: String) {
        val uid = UUID.fromString(userId)
        val fid = UUID.fromString(friendshipId)
        transaction {
            val deleted = Friendships.deleteWhere {
                (Friendships.id eq fid) and
                ((Friendships.requesterId eq uid) or (Friendships.receiverId eq uid))
            }
            require(deleted > 0) { "Дружба не найдена" }
        }
    }

    // ── List accepted friends ─────────────────────────────────────────────────

    fun listFriends(userId: String): List<FriendDto> {
        val uid = UUID.fromString(userId)
        return transaction {
            Friendships.selectAll().where {
                ((Friendships.requesterId eq uid) or (Friendships.receiverId eq uid)) and
                (Friendships.status eq "ACCEPTED")
            }.map { row ->
                val friendId = if (row[Friendships.requesterId] == uid)
                    row[Friendships.receiverId] else row[Friendships.requesterId]
                val fid = row[Friendships.id]
                val friendRow = Users.selectAll().where { Users.id eq friendId }.first()
                val count = ServerQuests.selectAll()
                    .where { ServerQuests.userId eq friendId }.count().toInt()
                FriendDto(
                    friendshipId   = fid.toString(),
                    userId         = friendId.toString(),
                    username       = friendRow[Users.username],
                    displayName    = friendRow[Users.displayName],
                    level          = friendRow[Users.level],
                    xp             = friendRow[Users.xp],
                    completedCount = count
                )
            }
        }
    }

    // ── Incoming pending requests ─────────────────────────────────────────────

    fun listIncomingRequests(userId: String): List<FriendRequestDto> {
        val uid = UUID.fromString(userId)
        return transaction {
            Friendships.selectAll().where {
                (Friendships.receiverId eq uid) and (Friendships.status eq "PENDING")
            }.map { row ->
                val requesterId = row[Friendships.requesterId]
                val requesterRow = Users.selectAll().where { Users.id eq requesterId }.first()
                val count = ServerQuests.selectAll()
                    .where { ServerQuests.userId eq requesterId }.count().toInt()
                FriendRequestDto(
                    friendshipId = row[Friendships.id].toString(),
                    from = FriendDto(
                        friendshipId   = row[Friendships.id].toString(),
                        userId         = requesterId.toString(),
                        username       = requesterRow[Users.username],
                        displayName    = requesterRow[Users.displayName],
                        level          = requesterRow[Users.level],
                        xp             = requesterRow[Users.xp],
                        completedCount = count
                    ),
                    createdAt = row[Friendships.createdAt]
                )
            }
        }
    }

    // ── Quest feed (friends' completed quests) ────────────────────────────────

    fun getFeed(userId: String): List<QuestFeedItem> {
        val uid = UUID.fromString(userId)

        // Collect friend IDs
        val friendIds: List<UUID> = transaction {
            Friendships.selectAll().where {
                ((Friendships.requesterId eq uid) or (Friendships.receiverId eq uid)) and
                (Friendships.status eq "ACCEPTED")
            }.map { row ->
                if (row[Friendships.requesterId] == uid)
                    row[Friendships.receiverId] else row[Friendships.requesterId]
            }
        }

        if (friendIds.isEmpty()) return emptyList()

        return transaction {
            ServerQuests.selectAll()
                .where { ServerQuests.userId inList friendIds }
                .orderBy(ServerQuests.completedAt, SortOrder.DESC)
                .limit(50)
                .map { qRow ->
                    val authorId  = qRow[ServerQuests.userId]
                    val questUuid = qRow[ServerQuests.id]
                    val userRow   = Users.selectAll().where { Users.id eq authorId }.first()

                    val votes = QuestVotes.selectAll().where { QuestVotes.questId eq questUuid }.toList()
                    val myVoteRow = votes.firstOrNull { it[QuestVotes.voterId] == uid }

                    QuestFeedItem(
                        questId     = questUuid.toString(),
                        userId      = authorId.toString(),
                        username    = userRow[Users.username],
                        displayName = userRow[Users.displayName],
                        questText   = qRow[ServerQuests.questText],
                        questType   = qRow[ServerQuests.questType],
                        proofText   = qRow[ServerQuests.proofText],
                        completedAt = qRow[ServerQuests.completedAt],
                        xpEarned    = qRow[ServerQuests.xpEarned],
                        status      = qRow[ServerQuests.status],
                        approvals   = votes.count { it[QuestVotes.approve] },
                        rejections  = votes.count { !it[QuestVotes.approve] },
                        myVote      = myVoteRow?.get(QuestVotes.approve)
                    )
                }
        }
    }

    // ── Vote on a quest ───────────────────────────────────────────────────────

    fun vote(userId: String, req: VoteRequest) {
        val uid  = UUID.fromString(userId)
        val quid = UUID.fromString(req.questId)

        transaction {
            val questRow = ServerQuests.selectAll().where { ServerQuests.id eq quid }.firstOrNull()
                ?: error("Квест не найден")

            // Can only vote on friends' quests
            val questOwnerId = questRow[ServerQuests.userId]
            require(questOwnerId != uid) { "Нельзя голосовать за свой квест" }

            val areFriends = Friendships.selectAll().where {
                ((Friendships.requesterId eq uid) and (Friendships.receiverId eq questOwnerId) or
                 (Friendships.requesterId eq questOwnerId) and (Friendships.receiverId eq uid)) and
                (Friendships.status eq "ACCEPTED")
            }.count() > 0
            require(areFriends) { "Можно голосовать только за квесты друзей" }

            // Upsert vote
            val existing = QuestVotes.selectAll()
                .where { (QuestVotes.questId eq quid) and (QuestVotes.voterId eq uid) }
                .firstOrNull()
            if (existing != null) {
                QuestVotes.update({ (QuestVotes.questId eq quid) and (QuestVotes.voterId eq uid) }) {
                    it[approve] = req.approve
                }
            } else {
                QuestVotes.insert {
                    it[QuestVotes.questId] = quid
                    it[QuestVotes.voterId] = uid
                    it[approve] = req.approve
                }
            }

            // Auto-verify: if at least 1 approval → VERIFIED
            val approvals = QuestVotes.selectAll()
                .where { (QuestVotes.questId eq quid) and (QuestVotes.approve eq true) }
                .count()
            if (approvals >= 1) {
                ServerQuests.update({ ServerQuests.id eq quid }) {
                    it[status] = "VERIFIED"
                }
            }
        }
        // Check achievements for voter (first_vote) and quest owner (verified_quest)
        AchievementService.checkAndAward(userId)
        val questOwner = transaction {
            ServerQuests.selectAll().where { ServerQuests.id eq quid }
                .first()[ServerQuests.userId].toString()
        }
        AchievementService.checkAndAward(questOwner)

        // Notify quest owner if just verified
        if (req.approve) {
            val questRow = transaction {
                ServerQuests.selectAll().where { ServerQuests.id eq quid }.first()
            }
            if (questRow[ServerQuests.status] == "VERIFIED") {
                val voterName = transaction {
                    Users.selectAll().where { Users.id eq UUID.fromString(userId) }
                        .first()[Users.username]
                }
                NotificationService.notifyQuestVerified(
                    questOwner,
                    voterName,
                    questRow[ServerQuests.questText].take(40)
                )
            }
        }
    }
}
