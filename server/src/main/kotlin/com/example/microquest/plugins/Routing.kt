package com.example.microquest.plugins

import com.example.microquest.models.FcmTokenRequest
import com.example.microquest.routes.authRoutes
import com.example.microquest.routes.friendRoutes
import com.example.microquest.routes.mediaRoutes
import com.example.microquest.routes.profileRoutes
import com.example.microquest.services.NotificationService
import com.example.microquest.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun Application.configureRouting() {
    val mediaDir = File(System.getenv("MEDIA_DIR") ?: "/app/media")

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        authRoutes()
        profileRoutes()
        friendRoutes()
        mediaRoutes(mediaDir)

        authenticate("auth-jwt") {
            put("/fcm-token") {
                val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
                val req = call.receive<FcmTokenRequest>()
                NotificationService.saveToken(userId, req.token)
                call.respond(HttpStatusCode.OK, mapOf("status" to "saved"))
            }

            get("/leaderboard") {
                val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
                call.respond(UserService.getLeaderboard(userId))
            }
        }
    }
}
