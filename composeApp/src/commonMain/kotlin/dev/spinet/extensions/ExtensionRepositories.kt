package dev.spinet.extensions

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.spinet.extension.ApiVersion
import dev.spinet.settings.SettingsRepository

@Serializable
data class RepoIndex(
    val schema: Int = 0,
    val name: String = "",
    val website: String = "",
    val extensions: List<RepoExtension> = emptyList()
)

@Serializable
data class RepoExtension(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String = "",
    val api: Int,
    val author: String = "",
    val license: String = "",
    val description: String = "",
    val iconUrl: String = "",
    val capabilities: List<String> = emptyList(),
    val artifacts: Map<String, RepoArtifact> = emptyMap()
)

@Serializable
data class RepoArtifact(val url: String, val size: Long = 0, val sha256: String)

enum class EntryStatus {
    AVAILABLE,

    INSTALLED,

    UPDATE,

    RESTART,

    NEEDS_NEWER_APP,

    NOT_FOR_PLATFORM
}

data class RepoEntryUi(
    val extension: RepoExtension,
    val status: EntryStatus,
    val installedVersion: Int? = null,
    val busy: Boolean = false
)

data class RepoUi(
    val url: String,
    val name: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val entries: List<RepoEntryUi> = emptyList()
)

data class RepositoriesState(
    val repos: List<RepoUi> = emptyList(),

    val message: String? = null
)

class ExtensionRepositories(
    private val settings: SettingsRepository,
    private val client: HttpClient,

    private val installedVersions: () -> Map<String, Int>,

    private val onExtensionsChanged: () -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val installedThisRun = mutableMapOf<String, Int>()

    @kotlin.concurrent.Volatile
    private var awaitingInstaller = false

    private val _state = MutableStateFlow(RepositoriesState(stored().map { RepoUi(it) }))
    val state: StateFlow<RepositoriesState> = _state.asStateFlow()

    private fun stored(): List<String> = settings.extensionRepositories.lines().filter { it.isNotBlank() }

    fun add(input: String) {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank()) return
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            say("Repositories must use https://.")
            return
        }
        val url = if (trimmed.endsWith(".json", ignoreCase = true)) trimmed else "$trimmed/index.json"
        if (url in stored()) {
            say("That repository is already added.")
            return
        }
        settings.extensionRepositories = (stored() + url).joinToString("\n")
        _state.update { it.copy(repos = it.repos + RepoUi(url), message = null) }
        load(url)
    }

    fun remove(url: String) {
        settings.extensionRepositories = stored().filterNot { it == url }.joinToString("\n")
        _state.update { s -> s.copy(repos = s.repos.filterNot { it.url == url }, message = null) }
    }

    fun loadIfNeeded() = _state.value.repos
        .filter { !it.loading && it.error == null && it.entries.isEmpty() && it.name.isBlank() }
        .forEach { load(it.url) }

    fun refresh() = stored().forEach { load(it) }

    private fun load(url: String) {
        updateRepo(url) { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                val index = fetch(url)
                updateRepo(url) {
                    it.copy(loading = false, name = index.name, entries = index.extensions.map { e -> entryFor(e) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateRepo(url) { it.copy(loading = false, error = e.message ?: e.toString(), entries = emptyList()) }
            }
        }
    }

    private suspend fun fetch(url: String): RepoIndex {
        val index = try {
            json.decodeFromString(RepoIndex.serializer(), client.get(url).bodyAsText())
        } catch (e: Exception) {
            throw IllegalStateException("Couldn't read the repository: ${e.message ?: e}", e)
        }
        if (index.schema != 1) error("Unsupported repository format (schema ${index.schema}).")
        return index
    }

    private fun entryFor(ext: RepoExtension): RepoEntryUi {
        val installed = installedThisRun[ext.id] ?: installedVersions()[ext.id]
        val status = when {
            ext.id in installedThisRun -> EntryStatus.RESTART
            ext.api > ApiVersion.CURRENT -> EntryStatus.NEEDS_NEWER_APP
            artifactFor(ext) == null -> EntryStatus.NOT_FOR_PLATFORM
            installed == null -> EntryStatus.AVAILABLE
            ext.versionCode > installed -> EntryStatus.UPDATE
            else -> EntryStatus.INSTALLED
        }
        return RepoEntryUi(ext, status, installed)
    }

    private fun artifactFor(ext: RepoExtension): RepoArtifact? =
        extensionPlatform?.let { ext.artifacts[it] }

    fun install(repoUrl: String, ext: RepoExtension) {
        val artifact = artifactFor(ext) ?: return
        setBusy(repoUrl, ext.id, true)
        scope.launch {
            val outcome = try {
                checkSameOrigin(repoUrl, artifact.url)
                val bytes: ByteArray = client.get(artifact.url).body()
                if (artifact.size > 0 && bytes.size.toLong() != artifact.size) {
                    error("download was ${bytes.size} bytes, the repository says ${artifact.size}")
                }

                if (!sha256Hex(bytes).equals(artifact.sha256.trim(), ignoreCase = true)) {
                    error("checksum does not match; the file was discarded")
                }
                installExtensionArtifact(ext.id, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InstallOutcome(false, "${ext.name}: ${e.message ?: e}")
            }
            if (outcome.installed && outcome.next == InstallOutcome.Next.RESTART) {
                installedThisRun[ext.id] = ext.versionCode
            }
            afterInstall(outcome)
        }
    }

    private fun checkSameOrigin(indexUrl: String, artifactUrl: String) {
        val index = Url(indexUrl)
        val artifact = Url(artifactUrl)
        if (artifact.protocol.name != "https") error("artifact is not served over https")
        if (!artifact.host.equals(index.host, ignoreCase = true)) {
            error("artifact is on ${artifact.host}, not on the repository's own host ${index.host}")
        }
    }

    private fun setBusy(repoUrl: String, id: String, busy: Boolean) = updateRepo(repoUrl) { r ->
        r.copy(entries = r.entries.map { if (it.extension.id == id) it.copy(busy = busy) else it })
    }

    private fun updateRepo(url: String, change: (RepoUi) -> RepoUi) =
        _state.update { s -> s.copy(repos = s.repos.map { if (it.url == url) change(it) else it }) }

    private fun say(message: String) = _state.update { it.copy(message = message) }

    fun uninstall(origin: String, name: String) {
        scope.launch {
            val outcome = try {
                uninstallExtension(origin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InstallOutcome(false, "Couldn't remove $name: ${e.message ?: e}")
            }
            afterInstall(outcome.copy(message = outcome.message.replace("{name}", name)))
        }
    }

    private val pickedQueue = ArrayDeque<PickedExtensionFile>()
    private val batchFailures = mutableListOf<String>()
    private var batchInstalled = 0
    private var batchHandedOff = 0
    private var batchSize = 0
    private var batchLoadNow = false
    private var askedPermissionOnce = false
    private var lastOutcome: InstallOutcome? = null

    fun installFromFiles(files: List<PickedExtensionFile>) {
        if (files.isEmpty()) return
        if (batchSize > 0) {
            say("Still installing the last files you picked.")
            return
        }
        pickedQueue.addAll(files)
        batchSize = files.size
        installNextPicked()
    }

    private fun installNextPicked() {
        val file = pickedQueue.removeFirstOrNull() ?: return finishBatch()
        val position = batchSize - pickedQueue.size
        say(if (batchSize > 1) "Installing ${file.name} ($position of $batchSize)…" else "Installing ${file.name}…")
        scope.launch {
            val outcome = try {
                installExtensionFile(file.name, file.bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                InstallOutcome(false, "${file.name}: ${e.message ?: e}")
            }
            when {
                outcome.next == InstallOutcome.Next.AFTER_PERMISSION && !askedPermissionOnce -> {
                    askedPermissionOnce = true
                    pickedQueue.addFirst(file)
                    awaitingInstaller = true
                    say(outcome.message)
                    return@launch
                }
                outcome.next == InstallOutcome.Next.AFTER_PERMISSION -> {
                    batchFailures += outcome.message
                    pickedQueue.clear()
                }
                !outcome.installed -> batchFailures += outcome.message
                outcome.next == InstallOutcome.Next.AFTER_INSTALLER -> batchHandedOff++
                else -> batchInstalled++
            }
            lastOutcome = outcome
            if (outcome.installed && outcome.next == InstallOutcome.Next.LOAD_NOW) batchLoadNow = true
            if (outcome.installed && outcome.next == InstallOutcome.Next.AFTER_INSTALLER) {
                awaitingInstaller = true
                say(
                    if (pickedQueue.isEmpty()) outcome.message
                    else "Confirm the install of ${file.name}. The next one follows when you're back."
                )
                return@launch
            }
            installNextPicked()
        }
    }

    private fun finishBatch() {
        if (batchSize == 0) return
        val done = batchInstalled + batchHandedOff
        val message = when {
            batchSize == 1 -> lastOutcome?.takeIf { it.next != InstallOutcome.Next.AFTER_INSTALLER }?.message
            batchHandedOff > 0 && batchFailures.isEmpty() ->
                "Done. Each extension you confirmed is listed above; switch it on there."
            batchFailures.isEmpty() -> "Installed $done extensions. Switch them on above."
            else -> "Installed $done of $batchSize. " + batchFailures.joinToString(" ")
        }
        if (batchLoadNow) onExtensionsChanged()
        batchFailures.clear()
        batchInstalled = 0
        batchHandedOff = 0
        lastOutcome = null
        batchSize = 0
        batchLoadNow = false
        askedPermissionOnce = false
        recomputeEntries()
        message?.let { say(it) }
    }

    private fun afterInstall(outcome: InstallOutcome) {
        when {
            !outcome.installed -> Unit
            outcome.next == InstallOutcome.Next.LOAD_NOW -> onExtensionsChanged()
            outcome.next == InstallOutcome.Next.AFTER_INSTALLER -> awaitingInstaller = true
            else -> Unit
        }
        recomputeEntries()
        say(outcome.message)
    }

    fun onResumed() {
        if (!awaitingInstaller) return
        awaitingInstaller = false
        onExtensionsChanged()
        recomputeEntries()

        if (batchSize > 0) installNextPicked()
    }

    private fun recomputeEntries() = _state.update { s ->
        s.copy(repos = s.repos.map { r -> r.copy(entries = r.entries.map { entryFor(it.extension) }) })
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }
}

data class InstallOutcome(val installed: Boolean, val message: String, val next: Next = Next.NOTHING) {
    enum class Next {
        NOTHING,

        LOAD_NOW,

        AFTER_INSTALLER,

        RESTART,

        AFTER_PERMISSION
    }
}

expect val extensionPlatform: String?

expect fun sha256Hex(bytes: ByteArray): String

expect suspend fun installExtensionArtifact(id: String, bytes: ByteArray): InstallOutcome
