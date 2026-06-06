package com.example.microquest.routes

import com.example.microquest.models.AddFriendRequest
import com.example.microquest.models.ErrorResponse
import com.example.microquest.models.VoteRequest
import com.example.microquest.services.FriendService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.friendRoutes() {
    authenticate("auth-jwt") {

        get("/friends") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            call.respond(FriendService.listFriends(userId))
        }

        post("/friends/request") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            val req = call.receive<AddFriendRequest>()
            val friend = FriendService.sendRequest(userId, req.username)
            call.respond(HttpStatusCode.Created, friend)
        }

        get("/friends/requests") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            call.respond(FriendService.listIncomingRequests(userId))
        }

        post("/friends/requests/{id}/accept") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            val friendshipId = call.parameters["id"]!!
            FriendService.respondToRequest(userId, friendshipId, accept = true)
            call.respond(HttpStatusCode.OK, mapOf("status" to "accepted"))
        }

        post("/friends/requests/{id}/decline") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            val friendshipId = call.parameters["id"]!!
            FriendService.respondToRequest(userId, friendshipId, accept = false)
            call.respond(HttpStatusCode.OK, mapOf("status" to "declined"))
        }

        delete("/friends/{id}") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            val friendshipId = call.parameters["id"]!!
            FriendService.removeFriend(userId, friendshipId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "removed"))
        }

        get("/feed") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            call.respond(FriendService.getFeed(userId))
        }

        post("/feed/vote") {
            val userId = call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
            val req = call.receive<VoteRequest>()
            FriendService.vote(userId, req)
            call.respond(HttpStatusCode.OK, mapOf("status" to "voted"))
        }
    }
}
