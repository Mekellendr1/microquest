package com.example.microquest.plugins

import com.example.microquest.models.Friendships
import com.example.microquest.models.QuestVotes
import com.example.microquest.models.ServerQuests
import com.example.microquest.models.Users
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    val url      = environment.config.property("database.url").getString()
    val user     = environment.config.property("database.user").getString()
    val password = environment.config.property("database.password").getString()

    val hikari = HikariConfig().apply {
        jdbcUrl         = url
        username        = user
        this.password   = password
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        isAutoCommit    = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    Database.connect(HikariDataSource(hikari))

    transaction {
        SchemaUtils.createMissingTablesAndColumns(Users, ServerQuests, Friendships, QuestVotes)
    }

    log.info("Database connected and schema up-to-date.")
}
