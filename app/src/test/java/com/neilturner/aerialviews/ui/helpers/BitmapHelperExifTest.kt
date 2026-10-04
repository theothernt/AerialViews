package com.neilturner.aerialviews.ui.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/**
 * Exercises the real EXIF read path of [BitmapHelper] against synthetic JPEGs.
 *
 * These assertions used to be impossible: androidx ExifInterface is an android.* class, so the
 * read path could not run in a JVM unit test. metadata-extractor is pure Java, so the parsing
 * can be verified here without Robolectric or a device.
 */
internal class BitmapHelperExifTest {
    @Test
    fun `extracts date, offset, gps, description and orientation from a jpeg exif header`() {
        val jpeg =
            jpegWithExif(
                buildTiff(
                    ifd0 =
                        listOf(
                            entry(0x0112, TYPE_SHORT, inlineValue = 6),
                            entry(0x010E, TYPE_ASCII, heap = ascii("Nice photo")),
                            entry(0x8769, TYPE_LONG),
                            entry(0x8825, TYPE_LONG),
                        ),
                    exifIfd =
                        listOf(
                            entry(0x9003, TYPE_ASCII, heap = ascii("2024:07:04 18:30:00")),
                            entry(0x9011, TYPE_ASCII, heap = ascii("+01:00")),
                        ),
                    gpsIfd = londonGpsIfd(),
                ),
            )

        val exif = BitmapHelper.extractExifMetadataFromHeader(jpeg, jpeg.size)

        assertEquals("2024:07:04 18:30:00", exif.date)
        assertEquals("+01:00", exif.offset)
        assertEquals("Nice photo", exif.description)
        assertEquals(6, exif.orientation)
        assertEquals(51.5, exif.latitude!!, 0.0001)
        assertEquals(-0.12, exif.longitude!!, 0.0001)
    }

    @Test
    fun `falls back to ifd0 datetime when there is no datetime original`() {
        val jpeg =
            jpegWithExif(
                buildTiff(
                    ifd0 = listOf(entry(0x0132, TYPE_ASCII, heap = ascii("2019:01:02 03:04:05"))),
                    exifIfd = emptyList(),
                    gpsIfd = emptyList(),
                ),
            )

        val exif = BitmapHelper.extractExifMetadataFromHeader(jpeg, jpeg.size)

        assertEquals("2019:01:02 03:04:05", exif.date)
        assertNull(exif.offset)
    }

    @Test
    fun `treats a zero zero gps fix as no location`() {
        val jpeg =
            jpegWithExif(
                buildTiff(
                    ifd0 = emptyList(),
                    exifIfd = emptyList(),
                    gpsIfd =
                        listOf(
                            entry(0x0001, TYPE_ASCII, heap = ascii("N")),
                            entry(0x0002, TYPE_RATIONAL, heap = rationals(0 to 1, 0 to 1, 0 to 1)),
                            entry(0x0003, TYPE_ASCII, heap = ascii("E")),
                            entry(0x0004, TYPE_RATIONAL, heap = rationals(0 to 1, 0 to 1, 0 to 1)),
                        ),
                ),
            )

        val exif = BitmapHelper.extractExifMetadataFromHeader(jpeg, jpeg.size)

        assertNull(exif.latitude)
        assertNull(exif.longitude)
    }

    @Test
    fun `returns empty metadata for a jpeg without any exif`() {
        val jpeg = jpegWithoutExif()

        val exif = BitmapHelper.extractExifMetadataFromHeader(jpeg, jpeg.size)

        assertNull(exif.date)
        assertNull(exif.offset)
        assertNull(exif.description)
        assertNull(exif.latitude)
        assertNull(exif.longitude)
        assertEquals(0, exif.orientation)
    }

    @Test
    fun `returns empty metadata for a zero length header`() {
        val exif = BitmapHelper.extractExifMetadataFromHeader(ByteArray(0), 0)

        assertNull(exif.date)
        assertNull(exif.offset)
        assertNull(exif.description)
    }

    @Test
    fun `returns empty metadata rather than throwing for garbage input`() {
        val garbage = ByteArray(2048) { (it * 37 % 251).toByte() }

        val exif = BitmapHelper.extractExifMetadataFromHeader(garbage, garbage.size)

        assertNull(exif.date)
        assertNull(exif.description)
    }

    @Test
    fun `rejects a vendor metadata blob in image description`() {
        val jpeg =
            jpegWithExif(
                buildTiff(
                    ifd0 =
                        listOf(
                            entry(
                                0x010E,
                                TYPE_ASCII,
                                heap =
                                    ascii(
                                        "format: 0; filter: null; filterIntensity: 0; " +
                                            "sceneMode: 13107200; cct_value: 0; AI Scene: (-1, weatherInfo: weather?Cloudy",
                                    ),
                            ),
                        ),
                    exifIfd = emptyList(),
                    gpsIfd = emptyList(),
                ),
            )

        val exif = BitmapHelper.extractExifMetadataFromHeader(jpeg, jpeg.size)

        assertNull(exif.description)
    }
}

private const val TYPE_ASCII = 2
private const val TYPE_SHORT = 3
private const val TYPE_LONG = 4
private const val TYPE_RATIONAL = 5

private data class Entry(
    val tag: Int,
    val type: Int,
    val inlineValue: Int = 0,
    val heap: ByteArray? = null,
)

private fun entry(
    tag: Int,
    type: Int,
    inlineValue: Int = 0,
    heap: ByteArray? = null,
) = Entry(tag = tag, type = type, inlineValue = inlineValue, heap = heap)

/** 51 deg 30' 00.00" N, 0 deg 07' 12.00" W. */
private fun londonGpsIfd() =
    listOf(
        entry(0x0001, TYPE_ASCII, heap = ascii("N")),
        entry(0x0002, TYPE_RATIONAL, heap = rationals(51 to 1, 30 to 1, 0 to 100)),
        entry(0x0003, TYPE_ASCII, heap = ascii("W")),
        entry(0x0004, TYPE_RATIONAL, heap = rationals(0 to 1, 7 to 1, 1200 to 100)),
    )

/**
 * Builds a big endian TIFF block with IFD0, an Exif SubIFD and a GPS IFD. Long values that do
 * not fit in an IFD entry are placed in a heap directly after their directory.
 */
private fun buildTiff(
    ifd0: List<Entry>,
    exifIfd: List<Entry>,
    gpsIfd: List<Entry>,
): ByteArray {
    // EXIF requires IFD entries to be ordered by ascending tag.
    val ifd0Entries = ifd0.sortedBy { it.tag }
    val exifEntries = exifIfd.sortedBy { it.tag }
    val gpsEntries = gpsIfd.sortedBy { it.tag }

    fun directorySize(entries: List<Entry>) = 2 + 12 * entries.size + 4

    fun heapSize(entries: List<Entry>) = entries.sumOf { it.spilledSize() }

    val ifd0Offset = 8
    val ifd0HeapOffset = ifd0Offset + directorySize(ifd0Entries)
    val exifIfdOffset = ifd0HeapOffset + heapSize(ifd0Entries)
    val exifIfdHeapOffset = exifIfdOffset + directorySize(exifEntries)
    val gpsIfdOffset = exifIfdHeapOffset + heapSize(exifEntries)
    val gpsIfdHeapOffset = gpsIfdOffset + directorySize(gpsEntries)

    // The SubIFD and GPS IFD offsets are themselves IFD0 entries, so patch them in now that
    // the layout is known.
    val resolvedIfd0 =
        ifd0Entries.map {
            when (it.tag) {
                0x8769 -> it.copy(inlineValue = exifIfdOffset)
                0x8825 -> it.copy(inlineValue = gpsIfdOffset)
                else -> it
            }
        }

    val out = ByteArrayOutputStream()
    out.write("MM".toByteArray(Charsets.US_ASCII))
    out.write(u16(0x002A))
    out.write(u32(ifd0Offset))

    writeDirectory(out, resolvedIfd0, ifd0HeapOffset)
    writeHeap(out, ifd0Entries)
    writeDirectory(out, exifEntries, exifIfdHeapOffset)
    writeHeap(out, exifEntries)
    writeDirectory(out, gpsEntries, gpsIfdHeapOffset)
    writeHeap(out, gpsEntries)

    return out.toByteArray()
}

private fun writeDirectory(
    out: ByteArrayOutputStream,
    entries: List<Entry>,
    heapOffset: Int,
) {
    out.write(u16(entries.size))
    var nextHeapOffset = heapOffset
    for (entry in entries) {
        out.write(u16(entry.tag))
        out.write(u16(entry.type))
        out.write(u32(entry.componentCount()))
        val inline = entry.heap?.takeIf { it.size <= INLINE_VALUE_BYTES }
        when {
            entry.heap == null -> {
                if (entry.type == TYPE_SHORT) {
                    // A single SHORT is left aligned in the four byte value field.
                    out.write(u16(entry.inlineValue))
                    out.write(u16(0))
                } else {
                    out.write(u32(entry.inlineValue))
                }
            }

            inline != null -> {
                // Values of four bytes or fewer live inline in the entry itself.
                out.write(inline)
                repeat(INLINE_VALUE_BYTES - inline.size) { out.write(0) }
            }

            else -> {
                out.write(u32(nextHeapOffset))
                nextHeapOffset += entry.spilledSize()
            }
        }
    }
    out.write(u32(0)) // no next IFD
}

private const val INLINE_VALUE_BYTES = 4

/** Bytes this entry contributes to the heap after its directory. */
private fun Entry.spilledSize(): Int = heap?.takeIf { it.size > INLINE_VALUE_BYTES }?.size ?: 0

/** The TIFF entry count is components, not bytes: characters for ASCII, rationals for RATIONAL. */
private fun Entry.componentCount(): Int {
    val heap = heap ?: return 1
    return when (type) {
        TYPE_ASCII -> heap.size
        TYPE_RATIONAL -> heap.size / 8
        else -> 1
    }
}

private fun writeHeap(
    out: ByteArrayOutputStream,
    entries: List<Entry>,
) = entries.forEach { entry -> entry.heap?.takeIf { it.size > INLINE_VALUE_BYTES }?.let(out::write) }

private fun ascii(value: String) = "$value\u0000".toByteArray(Charsets.US_ASCII)

private fun rationals(vararg values: Pair<Int, Int>): ByteArray {
    val out = ByteArrayOutputStream()
    values.forEach { (numerator, denominator) ->
        out.write(u32(numerator))
        out.write(u32(denominator))
    }
    return out.toByteArray()
}

private fun u16(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())

private fun u32(value: Int) =
    byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )

private fun jpegWithExif(tiff: ByteArray): ByteArray {
    val app1Payload = "Exif".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0) + tiff
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI
    out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte())) // APP1
    out.write(u16(app1Payload.size + 2))
    out.write(app1Payload)
    appendEndOfImage(out)
    return out.toByteArray()
}

private fun jpegWithoutExif(): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // SOI
    appendEndOfImage(out)
    return out.toByteArray()
}

private fun appendEndOfImage(out: ByteArrayOutputStream) {
    out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte())) // SOS - ends metadata processing
    out.write(u16(2))
    out.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) // EOI
}
