package com.example.microquest.models

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id           = uuid("id").autoGenerate()
    val username     = varchar("username", 50).uniqueIndex()
    val email        = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val displayName  = varchar("display_name", 100)
    val xp           = integer("xp").default(0)
    val level        = integer("level").default(1)
    val createdAt    = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ServerQuests : Table("server_quests") {
    val id          = uuid("id").autoGenerate()
    val userId      = uuid("user_id").references(Users.id)
    val questId     = integer("quest_id")
    val questText   = text("quest_text")
    val questType   = varchar("quest_type", 20)
    val completedAt = long("completed_at")
    val xpEarned    = integer("xp_earned").default(0)
    val proofText   = text("proof_text").nullable()      // optional description from user
    val status      = varchar("status", 20).default("PENDING") // PENDING | VERIFIED | REJECTED

    override val primaryKey = PrimaryKey(id)
}

// ── Social ────────────────────────────────────────────────────────────────────

/** Friend requests / friendships between users. */
object Friendships : Table("friendships") {
    val id          = uuid("id").autoGenerate()
    val requesterId = uuid("requester_id").references(Users.id)
    val receiverId  = uuid("receiver_id").references(Users.id)
    /** PENDING | ACCEPTED | DECLINED */
    val status      = varchar("status", 20).default("PENDING")
    val createdAt   = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

/** One vote per user per quest: approve = true, reject = false. */
object QuestVotes : Table("quest_votes") {
    val id       = uuid("id").autoGenerate()
    val questId  = uuid("quest_id").references(ServerQuests.id)
    val voterId  = uuid("voter_id").references(Users.id)
    val approve  = bool("approve")

    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex(questId, voterId) } // one vote per user per quest
}
