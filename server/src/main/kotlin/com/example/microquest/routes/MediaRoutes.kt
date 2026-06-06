package com.example.microquest.routes

import com.example.microquest.models.ErrorResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.UUID

fun Route.mediaRoutes(mediaDir: File) {

    authenticate("auth-jwt") {
        post("/upload") {
            val multipart = call.receiveMultipart(formFieldLimit = 52_428_800L) // 50 MB
            var savedPath: String? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val ext = part.originalFileName
                        ?.substringAfterLast('.', "jpg")
                        ?.lowercase()
                        ?.let { if (it in listOf("jpg", "jpeg", "png", "mp4", "mov")) it else "jpg" }
                        ?: "jpg"
                    val fileName = "${UUID.randomUUID()}.$ext"
                    val file = File(mediaDir, fileName)
                    mediaDir.mkdirs()
                    part.streamProvider().use { input ->
                        file.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    savedPath = "/media/$fileName"
                }
                part.dispose()
            }

            if (savedPath != null) {
                call.respond(mapOf("url" to savedPath!!))
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Файл не найден в запросе"))
            }
        }
    }

    get("/media/{filename}") {
        val filename = call.parameters["filename"]
            ?: return@get call.respond(HttpStatusCode.BadRequest)

        if ('/' in filename || '\\' in filename || ".." in filename) {
            call.respond(HttpStatusCode.BadRequest); return@get
        }

        val file = File(mediaDir, filename)
        if (!file.exists()) {
            call.respond(HttpStatusCode.NotFound); return@get
        }

        val contentType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "png"         -> ContentType.Image.PNG
            "mp4"         -> ContentType.Video.MP4
            "mov"         -> ContentType.parse("video/quicktime")
            else          -> ContentType.Application.OctetStream
        }
        call.response.header(HttpHeaders.ContentType, contentType.toString())
        call.respondFile(file)
    }
}
