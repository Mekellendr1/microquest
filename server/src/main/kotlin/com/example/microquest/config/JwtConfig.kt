package com.example.microquest.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private var secret: String = ""
    private var issuer: String = ""
    private var audience: String = ""

    fun init(secret: String, issuer: String, audience: String) {
        this.secret = secret
        this.issuer = issuer
        this.audience = audience
    }

    val jwtSecret   get() = secret
    val jwtIssuer   get() = issuer
    val jwtAudience get() = audience

    /** Generates a JWT valid for 30 days. */
    fun generateToken(userId: String): String =
        JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1_000))
            .sign(Algorithm.HMAC256(secret))
}
