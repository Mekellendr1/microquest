package com.example.microquest.plugins

import com.example.microquest.models.FcmTokenRequest
import com.example.microquest.routes.authRoutes
import com.example.microquest.routes.friendRoutes
import com.example.microquest.routes.profileRoutes
import com.example.microquest.services.NotificationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        authRoutes()
        profileRoutes()
        friendRoutes()

        authenticate("auth-jwt") {
            put("/fcm-token") {
                val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
                val req = call.receive<FcmTokenRequest>()
                NotificationService.saveToken(userId, req.token)
                call.respond(HttpStatusCode.OK, mapOf("status" to "saved"))
            }
        }
    }
}
