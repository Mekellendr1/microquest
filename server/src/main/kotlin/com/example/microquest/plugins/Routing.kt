package com.example.microquest.plugins

import com.example.microquest.routes.authRoutes
import com.example.microquest.routes.friendRoutes
import com.example.microquest.routes.profileRoutes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        authRoutes()
        profileRoutes()
        friendRoutes()
    }
}
