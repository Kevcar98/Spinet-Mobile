package dev.spinet.download

import java.io.ByteArrayOutputStream

object Mp4Tagger {
    fun tag(input: ByteArray, title: String, artist: String, art: ByteArray?): ByteArray {
        return try {
            val out = tagOrNull(input, title, artist, art)

            if (out != null && out.size > 8 && startsWithFtyp(out)) out else input
        } catch (e: Exception) {
            input
        }
    }

    private fun startsWithFtyp(b: ByteArray): Boolean =
        b.size >= 8 && String(b, 4, 4, Charsets.US_ASCII) == "ftyp"

    private fun tagOrNull(input: ByteArray, title: String, artist: String, art: ByteArray?): ByteArray? {
        var moovStart = -1
        var moovSize = -1
        var mdatStart = -1
        var pos = 0
        while (pos + 8 <= input.size) {
            var size = readU32(input, pos).toLong()
            var headerLen = 8
            if (size == 1L) {
                if (pos + 16 > input.size) return null
                size = readU64(input, pos + 8)
                headerLen = 16
            }
            if (size < headerLen || pos + size > input.size) return null
            when (String(input, pos + 4, 4, Charsets.US_ASCII)) {
                "moov" -> { moovStart = pos; moovSize = size.toInt() }
                "mdat" -> if (mdatStart < 0) mdatStart = pos
            }
            pos += size.toInt()
        }
        if (moovStart < 0 || moovSize < 16) return null

        val moovPayloadStart = moovStart + 8
        val moovPayloadLen = moovSize - 8

        if (directChild(input, moovPayloadStart, moovPayloadLen, "meta") != null) return null

        val oldUdta = directChild(input, moovPayloadStart, moovPayloadLen, "udta")

        val payload = if (oldUdta == null) {
            input.copyOfRange(moovPayloadStart, moovPayloadStart + moovPayloadLen)
        } else {
            val (uStart, uSize) = oldUdta
            val bos = ByteArrayOutputStream(moovPayloadLen - uSize)
            bos.write(input, moovPayloadStart, uStart - moovPayloadStart)
            bos.write(input, uStart + uSize, (moovPayloadStart + moovPayloadLen) - (uStart + uSize))
            bos.toByteArray()
        }

        val udta = buildUdta(title, artist, art)

        val netDelta = udta.size - (oldUdta?.second ?: 0)

        if (mdatStart < 0 || moovStart < mdatStart) {
            patchChunkOffsets(payload, 0, payload.size, netDelta)
        }

        val newMoovSize = 8 + payload.size + udta.size
        val out = ByteArrayOutputStream(input.size + netDelta + 16)
        out.write(input, 0, moovStart)
        out.write(u32(newMoovSize))
        out.write("moov".toByteArray(Charsets.US_ASCII))
        out.write(payload)
        out.write(udta)
        val afterMoov = moovStart + moovSize
        out.write(input, afterMoov, input.size - afterMoov)
        return out.toByteArray()
    }

    private fun buildUdta(title: String, artist: String, art: ByteArray?): ByteArray {
        val items = ByteArrayOutputStream()
        if (title.isNotBlank()) items.write(box("©nam", dataText(title)))
        if (artist.isNotBlank()) items.write(box("©ART", dataText(artist)))
        if (art != null && art.isNotEmpty()) items.write(box("covr", dataCover(art)))
        val ilst = box("ilst", items.toByteArray())

        val metaPayload = ByteArrayOutputStream()
        metaPayload.write(u32(0))
        metaPayload.write(itunesHdlr())
        metaPayload.write(ilst)
        return box("udta", box("meta", metaPayload.toByteArray()))
    }

    private fun dataText(value: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(u32(1))
        body.write(u32(0))
        body.write(value.toByteArray(Charsets.UTF_8))
        return box("data", body.toByteArray())
    }

    private fun dataCover(image: ByteArray): ByteArray {
        val isPng = image.size >= 8 &&
            (image[0].toInt() and 0xFF) == 0x89 && (image[1].toInt() and 0xFF) == 0x50
        val body = ByteArrayOutputStream()
        body.write(u32(if (isPng) 14 else 13))
        body.write(u32(0))
        body.write(image)
        return box("data", body.toByteArray())
    }

    private fun itunesHdlr(): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(u32(0))
        body.write(u32(0))
        body.write("mdir".toByteArray(Charsets.US_ASCII))
        body.write("appl".toByteArray(Charsets.US_ASCII))
        body.write(u32(0)); body.write(u32(0))
        body.write(0)
        return box("hdlr", body.toByteArray())
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(payload.size + 8)
        out.write(u32(payload.size + 8))

        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl", "edts")

    private fun patchChunkOffsets(b: ByteArray, start: Int, len: Int, delta: Int) {
        var pos = start
        val end = start + len
        while (pos + 8 <= end) {
            val size = readU32(b, pos)
            if (size < 8 || pos + size > end) return
            val type = String(b, pos + 4, 4, Charsets.US_ASCII)
            when {
                type == "stco" -> patchStco(b, pos + 8, delta)
                type == "co64" -> patchCo64(b, pos + 8, delta)
                type in CONTAINERS -> patchChunkOffsets(b, pos + 8, size - 8, delta)
            }
            pos += size
        }
    }

    private fun patchStco(b: ByteArray, payloadStart: Int, delta: Int) {
        val count = readU32(b, payloadStart + 4)
        var p = payloadStart + 8
        repeat(count) {
            writeU32(b, p, (readU32(b, p).toLong() + delta).toInt())
            p += 4
        }
    }

    private fun patchCo64(b: ByteArray, payloadStart: Int, delta: Int) {
        val count = readU32(b, payloadStart + 4)
        var p = payloadStart + 8
        repeat(count) {
            writeU64(b, p, readU64(b, p) + delta)
            p += 8
        }
    }

    private fun directChild(b: ByteArray, start: Int, len: Int, target: String): Pair<Int, Int>? {
        var pos = start
        val end = start + len
        while (pos + 8 <= end) {
            val size = readU32(b, pos)
            if (size < 8 || pos + size > end) return null
            if (String(b, pos + 4, 4, Charsets.US_ASCII) == target) return pos to size
            pos += size
        }
        return null
    }

    private fun readU32(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or
            ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    private fun writeU32(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 24).toByte(); b[i + 1] = (v ushr 16).toByte()
        b[i + 2] = (v ushr 8).toByte(); b[i + 3] = v.toByte()
    }

    private fun readU64(b: ByteArray, i: Int): Long {
        var v = 0L
        for (k in 0 until 8) v = (v shl 8) or (b[i + k].toLong() and 0xFF)
        return v
    }

    private fun writeU64(b: ByteArray, i: Int, v: Long) {
        for (k in 0 until 8) b[i + 7 - k] = (v ushr (8 * k)).toByte()
    }

    private fun u32(v: Int): ByteArray = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )
}
