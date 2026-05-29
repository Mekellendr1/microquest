package com.example.microquest.services

import com.example.microquest.models.Users
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.util.UUID

object NotificationService {

    private val logger = LoggerFactory.getLogger("NotificationService")
    private var initialized = false

    /** Call once on server startup. */
    fun init(serviceAccountPath: String) {
        if (initialized) return
        try {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(FileInputStream(serviceAccountPath)))
                .build()
            FirebaseApp.initializeApp(options)
            initialized = true
            logger.info("Firebase Admin SDK initialized from $serviceAccountPath")
        } catch (e: Exception) {
            logger.warn("Firebase Admin SDK NOT initialized — push notifications disabled: ${e.message}")
        }
    }

    // ── Save/update FCM token for a user ──────────────────────────────────────

    fun saveToken(userId: String, token: String) {
        val uid = UUID.fromString(userId)
        transaction {
            Users.update({ Users.id eq uid }) { it[Users.fcmToken] = token }
        }
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    fun sendToUser(userId: String, title: String, body: String) {
        if (!initialized) return
        val uid   = UUID.fromString(userId)
        val token = transaction {
            Users.selectAll().where { Users.id eq uid }.firstOrNull()?.get(Users.fcmToken)
        } ?: return

        try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .build()
            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            logger.warn("Failed to send push to $userId: ${e.message}")
        }
    }

    // ── Convenience methods ───────────────────────────────────────────────────

    fun notifyFriendRequest(receiverId: String, requesterName: String) =
        sendToUser(receiverId, "Новый запрос в друзья", "@$requesterName хочет добавить тебя в друзья")

    fun notifyFriendAccepted(requesterId: String, acceptorName: String) =
        sendToUser(requesterId, "Запрос принят!", "@$acceptorName теперь твой друг 🎉")

    fun notifyQuestVerified(questOwnerId: String, verifierName: String, questText: String) =
        sendToUser(questOwnerId, "Квест подтверждён! ✅", "@$verifierName засчитал: «$questText»")

    fun notifyAchievementUnlocked(userId: String, achievementName: String, icon: String) =
        sendToUser(userId, "Новое достижение! $icon", "Ты получил: $achievementName")
}
