package com.example.microquest.routes

import com.example.microquest.models.ErrorResponse
import com.example.microquest.models.SyncQuestRequest
import com.example.microquest.models.UpdateProfileRequest
import com.example.microquest.services.UserService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.profileRoutes() {
    authenticate("auth-jwt") {

        get("/profile") {
            val userId = call.principal<JWTPrincipal>()!!.payload
                .getClaim("userId").asString()
            val user = UserService.getUserDto(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            call.respond(user)
        }

        put("/profile") {
            val userId = call.principal<JWTPrincipal>()!!.payload
                .getClaim("userId").asString()
            val req  = call.receive<UpdateProfileRequest>()
            val user = UserService.updateProfile(userId, req)
            call.respond(user)
        }

        post("/quests/sync") {
            val userId = call.principal<JWTPrincipal>()!!.payload
                .getClaim("userId").asString()
            val req  = call.receive<SyncQuestRequest>()
            val user = UserService.syncQuest(userId, req)
            call.respond(user)
        }
    }
}
