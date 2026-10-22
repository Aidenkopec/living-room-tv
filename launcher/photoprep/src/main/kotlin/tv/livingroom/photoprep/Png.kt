package tv.livingroom.photoprep

/** PNG chunks that make Android convert colours when it reads a picture; the layers file must have none. */
val COLOUR_CHUNKS = setOf("gAMA", "iCCP", "cHRM", "sRGB")

/** The chunk names in a PNG file's [bytes], in order (empty if it isn't a PNG). */
fun pngChunkNames(bytes: ByteArray): List<String> {
    val signature = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
    if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(signature)) return emptyList()
    val names = mutableListOf<String>()
    var at = 8
    while (at + 8 <= bytes.size) {
        val length = ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)
        names += String(bytes, at + 4, 4, Charsets.US_ASCII)
        at += 12 + length // Length, name, data, CRC.
    }
    return names
}
