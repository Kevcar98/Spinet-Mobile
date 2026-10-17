package dev.spinet.data

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.util.encodeBase64
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import dev.spinet.model.SourceKind
import dev.spinet.model.SourcePlugin
import dev.spinet.settings.SettingsRepository

data class ServerSignIn(val baseUrl: String, val username: String, val password: String) {
    val isSet: Boolean get() = username.isNotBlank() && password.isNotBlank()
    val header: String get() = "Basic " + "$username:$password".encodeBase64()
}

const val FIRST_SERVER_ID = "server_library"

private val signInJson = Json { ignoreUnknownKeys = true }
private val signInsSerializer = kotlinx.serialization.serializer<Map<String, List<String>>>()

private fun SettingsRepository.storedSignIns(): Map<String, List<String>> =
    runCatching { signInJson.decodeFromString(signInsSerializer, serverSignInsJson) }.getOrDefault(emptyMap())

@Volatile
private var sessionPasswords: Map<String, String> = emptyMap()

fun SettingsRepository.signInFor(id: String): Pair<String, String> {
    val (username, saved) = if (id == FIRST_SERVER_ID) {
        serverUsername to serverPassword
    } else {
        storedSignIns()[id].orEmpty().let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
    }
    return username to if (passwordsOnlyInMemory) sessionPasswords[id].orEmpty() else saved
}

fun SettingsRepository.saveSignIn(id: String, username: String, password: String) {
    val kept = if (passwordsOnlyInMemory) {
        sessionPasswords = sessionPasswords - id + (id to password)
        ""
    } else {
        password
    }
    if (id == FIRST_SERVER_ID) {
        serverUsername = username
        serverPassword = kept
        return
    }
    val rest = storedSignIns() - id
    val all = if (username.isBlank() && kept.isEmpty()) rest else rest + (id to listOf(username, kept))
    serverSignInsJson = signInJson.encodeToString(signInsSerializer, all)
}

fun SettingsRepository.setRememberServerPasswords(remember: Boolean) {
    if (!canChooseToRememberPasswords || remember == rememberServerPasswords) return
    val current = savedServers().associate { it.id to signInFor(it.id) }
    rememberServerPasswords = remember
    if (!remember) removeSavedPasswordCopies()
    current.forEach { (id, signIn) -> saveSignIn(id, signIn.first, signIn.second) }
}

fun SettingsRepository.forgetSavedPasswordsIfNotRemembered() {
    if (!passwordsOnlyInMemory) return
    for (server in savedServers()) {
        val saved = if (server.id == FIRST_SERVER_ID) serverPassword
        else storedSignIns()[server.id].orEmpty().getOrElse(1) { "" }
        if (saved.isEmpty()) continue
        if (sessionPasswords[server.id].isNullOrEmpty()) sessionPasswords = sessionPasswords + (server.id to saved)
        saveSignIn(server.id, signInFor(server.id).first, sessionPasswords[server.id].orEmpty())
    }
    removeSavedPasswordCopies()
}

fun SettingsRepository.serversNeedingPassword(): List<SourcePlugin> =
    if (!passwordsOnlyInMemory) {
        emptyList()
    } else {
        savedServers().filter { server ->
            val (username, password) = signInFor(server.id)
            username.isNotBlank() && password.isEmpty()
        }
    }

fun SettingsRepository.forgetSignIn(id: String) = saveSignIn(id, "", "")

fun SettingsRepository.savedServers(): List<SourcePlugin> =
    runCatching {
        signInJson.parseToJsonElement(sourcePluginsJson).jsonArray.mapNotNull { row ->
            runCatching { signInJson.decodeFromJsonElement(SourcePlugin.serializer(), row) }.getOrNull()
        }
    }.getOrDefault(emptyList()).filter { it.kind == SourceKind.SERVER }

fun SettingsRepository.savedServerSignIns(): List<ServerSignIn> =
    savedServers().map { server ->
        val (username, password) = signInFor(server.id)
        ServerSignIn(server.baseUrl, username, password)
    }

class RefusedSignIn {
    @Volatile var headers: Set<String> = emptySet()

    fun forget() {
        headers = emptySet()
    }
}

fun serverSignInPlugin(
    current: () -> List<ServerSignIn>,
    refused: RefusedSignIn
) = createClientPlugin("ServerSignIn") {
    onRequest { request, _ ->

        val port = request.url.port.takeIf { it != DEFAULT_PORT } ?: request.url.protocol.defaultPort
        val signIn = current().firstOrNull { it.isSet && it.answersFor(request.url.host, port) }
            ?: return@onRequest
        val header = signIn.header
        if (header in refused.headers) return@onRequest
        request.headers[HttpHeaders.Authorization] = header
    }
    onResponse { response ->
        if (response.status == HttpStatusCode.Unauthorized) {
            response.call.request.headers[HttpHeaders.Authorization]
                ?.takeIf { it.startsWith("Basic ") }
                ?.let { refused.headers = refused.headers + it }
        }
    }
}

private fun ServerSignIn.answersFor(host: String, port: Int): Boolean {
    val server = runCatching { Url(baseUrl.withScheme()) }.getOrNull() ?: return false
    return server.host.equals(host, ignoreCase = true) && server.port == port
}

private fun String.withScheme(): String =
    if (contains("://")) this else "http://$this"

enum class ServerAccess {
    OPEN,

    SIGNED_IN,

    NEEDS_SIGN_IN,

    WRONG_SIGN_IN,

    LOCKED_OUT,

    UNREACHABLE
}
