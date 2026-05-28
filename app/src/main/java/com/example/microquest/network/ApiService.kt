package com.example.microquest.network

import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("profile")
    suspend fun getProfile(): UserDto

    @PUT("profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): UserDto

    @POST("quests/sync")
    suspend fun syncQuest(@Body request: SyncQuestRequest): UserDto

    // ── Friends ───────────────────────────────────────────────────────────────

    @GET("friends")
    suspend fun getFriends(): List<FriendDto>

    @POST("friends/request")
    suspend fun sendFriendRequest(@Body request: AddFriendRequest): FriendDto

    @GET("friends/requests")
    suspend fun getFriendRequests(): List<FriendRequestDto>

    @POST("friends/requests/{id}/accept")
    suspend fun acceptFriendRequest(@Path("id") friendshipId: String): Map<String, String>

    @POST("friends/requests/{id}/decline")
    suspend fun declineFriendRequest(@Path("id") friendshipId: String): Map<String, String>

    @DELETE("friends/{id}")
    suspend fun removeFriend(@Path("id") friendshipId: String): Map<String, String>

    // ── Feed / voting ─────────────────────────────────────────────────────────

    @GET("feed")
    suspend fun getFeed(): List<QuestFeedItem>

    @POST("feed/vote")
    suspend fun vote(@Body request: VoteRequest): Map<String, String>

    // ── Push notifications ────────────────────────────────────────────────────

    @PUT("fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Map<String, String>

    // ── Media upload ──────────────────────────────────────────────────────────

    @Multipart
    @POST("upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part): Map<String, String>

    // ── Leaderboard ───────────────────────────────────────────────────────────

    @GET("leaderboard")
    suspend fun getLeaderboard(): List<LeaderboardEntry>
}
