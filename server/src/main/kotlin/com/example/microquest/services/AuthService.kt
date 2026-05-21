package com.example.microquest.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.microquest.config.JwtConfig
import com.example.microquest.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object AuthService {

    fun register(req: RegisterRequest): AuthResponse {
        val username    = req.username.trim()
        val email       = req.email.trim().lowercase()
        val displayName = req.displayName.trim().ifBlank { username }

        require(username.length >= 3) { "Имя пользователя слишком короткое (мин. 3 символа)" }
        require(username.all { it.isLetterOrDigit() || it == '_' }) {
            "Имя пользователя может содержать только буквы, цифры и _"
        }
        require(email.contains("@") && email.contains(".")) { "Некорректный email" }
        require(req.password.length >= 6) { "Пароль слишком короткий (мин. 6 символов)" }

        transaction {
            val byEmail = Users.selectAll().where { Users.email eq email }.firstOrNull()
            require(byEmail == null) { "Email уже занят" }
            val byUser  = Users.selectAll().where { Users.username eq username }.firstOrNull()
            require(byUser == null) { "Имя пользователя уже занято" }
        }

        val hash   = BCrypt.withDefaults().hashToString(12, req.password.toCharArray())
        val userId = UUID.randomUUID()

        transaction {
            Users.insert {
                it[id]              = userId
                it[Users.username]  = username
                it[Users.email]     = email
                it[passwordHash]    = hash
                it[Users.displayName] = displayName
                it[xp]              = 0
                it[level]           = 1
                it[createdAt]       = System.currentTimeMillis() / 1000
            }
        }

        val token = JwtConfig.generateToken(userId.toString())
        val user  = UserService.getUserDto(userId.toString())!!
        return AuthResponse(token, user)
    }

    fun login(req: LoginRequest): AuthResponse {
        val email = req.email.trim().lowercase()

        val row = transaction {
            Users.selectAll().where { Users.email eq email }.firstOrNull()
        } ?: error("Неверный email или пароль")

        val verified = BCrypt.verifyer().verify(req.password.toCharArray(), row[Users.passwordHash]).verified
        require(verified) { "Неверный email или пароль" }

        val userId = row[Users.id].toString()
        val token  = JwtConfig.generateToken(userId)
        val user   = UserService.getUserDto(userId)!!
        return AuthResponse(token, user)
    }
}
