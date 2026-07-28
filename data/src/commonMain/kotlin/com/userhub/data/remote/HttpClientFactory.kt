package com.userhub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun createHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
            }
        )
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                println("HTTP: $message")
            }
        }
        // full request/response logging for easier debugging
        level = LogLevel.ALL
    }
    defaultRequest {
        header("Authorization", "Bearer ${ApiConfig.API_TOKEN}")
        contentType(ContentType.Application.Json)
    }
}
