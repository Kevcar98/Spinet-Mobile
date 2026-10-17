package dev.spinet.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(): HttpClient = buildJsonClient(OkHttp.create())
