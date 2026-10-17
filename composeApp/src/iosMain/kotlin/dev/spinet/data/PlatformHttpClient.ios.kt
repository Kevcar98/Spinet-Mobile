package dev.spinet.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(): HttpClient = buildJsonClient(Darwin.create())
