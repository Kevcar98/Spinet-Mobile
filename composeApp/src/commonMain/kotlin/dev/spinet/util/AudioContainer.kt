package dev.spinet.util

object AudioContainer {
    fun extensionOf(bytes: ByteArray): String? {
        val start = audioStart(bytes)

        if (matches(bytes, start + 4, "ftyp")) return "m4a"
        if (matches(bytes, start, "fLaC")) return "flac"
        if (matches(bytes, start, "OggS")) return "ogg"
        if (matches(bytes, start, "RIFF") && matches(bytes, start + 8, "WAVE")) return "wav"

        if (start + 1 < bytes.size &&
            (bytes[start].toInt() and 0xFF) == 0xFF &&
            (bytes[start + 1].toInt() and 0xE0) == 0xE0
        ) return "mp3"

        return if (start > 0) "mp3" else null
    }

    private fun audioStart(bytes: ByteArray): Int {
        if (bytes.size < 10 || !matches(bytes, 0, "ID3")) return 0

        fun b(i: Int) = bytes[i].toInt() and 0x7F
        val size = (b(6) shl 21) or (b(7) shl 14) or (b(8) shl 7) or b(9)
        val hasFooter = (bytes[5].toInt() and 0x10) != 0
        val end = 10 + size + (if (hasFooter) 10 else 0)
        return if (end in 0..bytes.size) end else 0
    }

    private fun matches(bytes: ByteArray, at: Int, ascii: String): Boolean {
        if (at < 0 || at + ascii.length > bytes.size) return false
        for (i in ascii.indices) {
            if (bytes[at + i].toInt() and 0xFF != ascii[i].code) return false
        }
        return true
    }
}
