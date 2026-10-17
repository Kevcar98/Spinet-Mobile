package dev.spinet.data

import io.ktor.client.HttpClient

expect fun createPlatformHttpClient(): HttpClient
