package dev.spinet.download

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

class PiecewiseHttpInputStream(
    private val url: String,
    private val pieceBytes: Long = 8L * 1024 * 1024,
    private val open: (String) -> HttpURLConnection
) : InputStream() {
    var totalLength: Long = -1L
        private set

    private var connection: HttpURLConnection? = null
    private var current: InputStream? = null
    private var position = 0L
    private var pieceRead = 0L
    private var lastPiece = false

    init {
        openPiece()
    }

    private fun openPiece() {
        val c = open(url)
        c.setRequestProperty("Range", "bytes=$position-${position + pieceBytes - 1}")
        val code = c.responseCode
        if (code == 416 && position > 0) {
            c.disconnect()
            lastPiece = true
            current = null
            return
        }
        if (code !in 200..299) {
            c.disconnect()
            throw IOException("HTTP $code")
        }
        if (code == 206) {
            c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?.let { totalLength = it }
        } else {
            lastPiece = true
            if (totalLength < 0) totalLength = c.contentLengthLong
        }
        connection = c
        current = c.inputStream
        pieceRead = 0L
    }

    private fun nextPieceIfDone(): Boolean {
        if (lastPiece) return false
        val finished = pieceRead < pieceBytes || (totalLength in 0..position)
        closePiece()
        if (finished) {
            lastPiece = true
            return false
        }
        openPiece()
        return current != null
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            val input = current ?: return -1
            val n = input.read(b, off, len)
            if (n >= 0) {
                position += n
                pieceRead += n
                return n
            }
            if (!nextPieceIfDone()) return -1
        }
    }

    private fun closePiece() {
        runCatching { current?.close() }
        connection?.disconnect()
        current = null
        connection = null
    }

    override fun close() = closePiece()
}
