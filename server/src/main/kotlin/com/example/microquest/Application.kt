package com.example.microquest

import com.example.microquest.plugins.*
import com.example.microquest.services.NotificationService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureDatabase()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting()

    val serviceAccountPath = System.getenv("FIREBASE_SERVICE_ACCOUNT") ?: "/app/service-account.json"
    NotificationService.init(serviceAccountPath)
}
