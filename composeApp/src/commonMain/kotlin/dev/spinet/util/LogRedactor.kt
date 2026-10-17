package dev.spinet.util

import dev.spinet.data.nowEpochMillis
import dev.spinet.data.savedServers
import dev.spinet.settings.SettingsRepository
import kotlin.concurrent.Volatile

object LogRedactor {

    private const val HOSTS_REFRESH_MS = 30_000L

    private val settings by lazy { runCatching { SettingsRepository() }.getOrNull() }

    @Volatile private var hosts: List<String> = emptyList()
    @Volatile private var hostsReadAt: Long? = null

    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val query = Regex("""(https?://[^\s"'<>?]+)\?[^\s"'<>)]*""")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val windowsHome = Regex("""(?i)([A-Z]:[\\/]+Users[\\/]+)[^\\/"'\n]+(?=[\\/])""")

    fun redact(line: String): String {
        var out = email.replace(line, "<email>")
        out = query.replace(out) { it.groupValues[1] + "?..." }
        for (host in serverHosts()) {
            out = out.replace(host, "<your-server>", ignoreCase = true)
        }
        out = ipv4.replace(out, "<ip>")
        out = windowsHome.replace(out) { it.groupValues[1] + "<you>" }
        return out
    }

    private fun serverHosts(): List<String> {
        val now = nowEpochMillis()
        val last = hostsReadAt
        if (last == null || now - last > HOSTS_REFRESH_MS) {
            hostsReadAt = now
            hosts = runCatching {
                settings?.savedServers().orEmpty()
                    .map { it.baseUrl.substringAfter("://").substringBefore('/').substringBefore(':') }
                    .filter { it.length > 3 }
                    .distinct()
                    .sortedByDescending { it.length }
            }.getOrDefault(hosts)
        }
        return hosts
    }
}
