package com.example.microquest.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String = ""
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String,
    val xp: Int,
    val level: Int,
    val xpToNextLevel: Int,
    val completedCount: Int = 0,
    val achievements: List<AchievementDto> = emptyList()
)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null
)

@Serializable
data class SyncQuestRequest(
    val questId: Int,
    val questText: String,
    val questType: String,
    val completedAt: Long,
    val proofText: String? = null,
    val mediaUrl: String? = null    // uploaded file path from /upload
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val username: String,
    val displayName: String,
    val level: Int,
    val xp: Int,
    val completedCount: Int,
    val isMe: Boolean
)

@Serializable
data class FcmTokenRequest(val token: String)

// ── Achievements ──────────────────────────────────────────────────────────────

@Serializable
data class AchievementDto(
    val key: String,
    val name: String,
    val description: String,
    val icon: String,
    val unlockedAt: Long?   // null = locked
)

// ── Friends ───────────────────────────────────────────────────────────────────

@Serializable
data class FriendDto(
    val friendshipId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val level: Int,
    val xp: Int,
    val completedCount: Int
)

@Serializable
data class FriendRequestDto(
    val friendshipId: String,
    val from: FriendDto,   // requester info
    val createdAt: Long
)

@Serializable
data class AddFriendRequest(val username: String)

@Serializable
data class FriendActionRequest(val friendshipId: String)

// ── Quest feed / voting ───────────────────────────────────────────────────────

@Serializable
data class QuestFeedItem(
    val questId: String,          // server_quests.id (UUID)
    val userId: String,
    val username: String,
    val displayName: String,
    val questText: String,
    val questType: String,
    val proofText: String?,
    val mediaUrl: String?,
    val completedAt: Long,
    val xpEarned: Int,
    val status: String,           // PENDING | VERIFIED | REJECTED
    val approvals: Int,
    val rejections: Int,
    val myVote: Boolean?          // null = not voted, true = approved, false = rejected
)

@Serializable
data class VoteRequest(
    val questId: String,   // server_quests.id (UUID)
    val approve: Boolean
)
