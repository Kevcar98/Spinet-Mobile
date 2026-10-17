package dev.spinet.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import dev.spinet.AppBranding

fun buildJsonClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 5 * 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }

        defaultRequest {
            headers.append(HttpHeaders.UserAgent, AppBranding.USER_AGENT)
            headers.append(HttpHeaders.Accept, "application/json")
        }
    }
