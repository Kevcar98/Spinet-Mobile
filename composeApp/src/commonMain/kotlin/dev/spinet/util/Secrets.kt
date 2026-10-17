package dev.spinet.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

expect fun encryptSecret(data: ByteArray): ByteArray?

expect fun decryptSecret(data: ByteArray): ByteArray?

expect fun secretScope(): String?

private const val SEALED = "enc1:"

fun isSealed(stored: String): Boolean = stored.isEmpty() || stored.startsWith(SEALED)

@OptIn(ExperimentalEncodingApi::class)
fun sealSecret(plain: String): String {
    if (plain.isEmpty()) return ""
    val sealed = encryptSecret(plain.encodeToByteArray()) ?: return plain
    return SEALED + Base64.encode(sealed)
}

@kotlin.concurrent.Volatile
private var opened: Map<String, String> = emptyMap()

@OptIn(ExperimentalEncodingApi::class)
fun openSecret(stored: String): String {
    if (!stored.startsWith(SEALED)) return stored
    opened[stored]?.let { return it }
    val data = runCatching { Base64.decode(stored.removePrefix(SEALED)) }.getOrNull() ?: return ""
    val plain = decryptSecret(data)?.decodeToString() ?: return ""
    opened = (if (opened.size >= 16) emptyMap() else opened) + (stored to plain)
    return plain
}
