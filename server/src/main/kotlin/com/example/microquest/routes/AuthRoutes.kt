package com.example.microquest.routes

import com.example.microquest.models.LoginRequest
import com.example.microquest.models.RegisterRequest
import com.example.microquest.services.AuthService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            val res = AuthService.register(req)
            call.respond(HttpStatusCode.Created, res)
        }
        post("/login") {
            val req = call.receive<LoginRequest>()
            val res = AuthService.login(req)
            call.respond(res)
        }
    }
}
