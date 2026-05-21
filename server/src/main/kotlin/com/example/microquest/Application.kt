package com.example.microquest

import com.example.microquest.plugins.*
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
}
