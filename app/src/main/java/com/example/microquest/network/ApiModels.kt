package com.example.microquest.network

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val displayName: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String,
    val xp: Int,
    val level: Int,
    val xpToNextLevel: Int,
    val completedCount: Int,
    val achievements: List<AchievementDto> = emptyList()
)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class SyncQuestRequest(
    val questId: Int,
    val questText: String,
    val questType: String,
    val completedAt: Long,
    val proofText: String? = null,
    val mediaUrl: String? = null
)

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

data class UpdateProfileRequest(
    val displayName: String?
)

data class FcmTokenRequest(val token: String)

data class AchievementDto(
    val key: String,
    val name: String,
    val description: String,
    val icon: String,
    val unlockedAt: Long?
)


data class FriendDto(
    val friendshipId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val level: Int,
    val xp: Int,
    val completedCount: Int
)

data class FriendRequestDto(
    val friendshipId: String,
    val from: FriendDto,
    val createdAt: Long
)

data class AddFriendRequest(val username: String)


data class QuestFeedItem(
    val questId: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val questText: String,
    val questType: String,
    val proofText: String?,
    val mediaUrl: String?,
    val completedAt: Long,
    val xpEarned: Int,
    val status: String,
    val approvals: Int,
    val rejections: Int,
    val myVote: Boolean?
)

data class VoteRequest(
    val questId: String,
    val approve: Boolean
)
