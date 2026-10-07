package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutBatchExporter.Part
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.ActivitySummaryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

class WorkoutBatchExporterTest : TestBase() {
    private val start = Date(1_790_000_000_000L)

    private fun summary(id: Long, raw: ByteArray? = null) = BaseActivitySummary().apply {
        this.id = id
        startTime = start
        endTime = Date(start.time + 30 * 60 * 1000L)
        activityKind = ActivityKind.RUNNING.code
        rawSummaryData = raw
    }

    private fun export(summaries: List<BaseActivitySummary>, parts: Set<Part>, asZip: Boolean) =
        WorkoutBatchExporter.export(getContext(), createDummyGDevice("00:00:00:00:00:01"), summaries, parts, asZip)

    private val base get() = ActivitySummaryUtils.getExportBaseName(getContext(), summary(1))

    private fun assertFit(bytes: ByteArray) {
        assertTrue(bytes.size > 12)
        assertEquals(".FIT", String(bytes, 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun exportBaseNameIsTimestampThenLowercaseKind() {
        assertTrue(base, base.endsWith("-running"))
        assertFalse(base, base.contains(':'))
        assertTrue(base, base.startsWith("20"))
    }

    @Test
    fun zipEntriesAreNamedByStartAndDeduplicated() {
        val result = export(listOf(summary(1), summary(2)), setOf(Part.FIT), asZip = true)

        assertEquals(1, result.files.size)
        assertEquals(2, result.entries)
        assertEquals(0, result.skipped)
        ZipFile(result.files[0]).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(listOf("$base.fit", "$base-2.fit"), names)
            names.forEach { name -> assertFit(zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }) }
        }
    }

    @Test
    fun rawSummaryGoesUnderRawFolderInZip() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val result = export(listOf(summary(1, payload)), setOf(Part.RAW), asZip = true)

        ZipFile(result.files[0]).use { zip ->
            val entry = zip.getEntry("raw/$base-summary.bin")
            assertNotNull(entry)
            assertTrue(payload.contentEquals(zip.getInputStream(entry).use { it.readBytes() }))
        }
    }

    @Test
    fun separateFilesAreFlatAndNamedLikeZipEntries() {
        val payload = byteArrayOf(5, 6, 7)
        val result = export(listOf(summary(1, payload), summary(2)), setOf(Part.FIT, Part.RAW), asZip = false)

        assertEquals(listOf("$base.fit", "$base-summary.bin", "$base-2.fit"), result.files.map { it.name })
        assertEquals(3, result.entries)
        assertFit(result.files[0].readBytes())
        assertTrue(payload.contentEquals(result.files[1].readBytes()))
        assertEquals(start.time / 1000, result.files[0].lastModified() / 1000)
    }

    @Test
    fun eachExportReplacesThePreviousOne() {
        val first = export(listOf(summary(1)), setOf(Part.FIT), asZip = true).files[0]
        val second = export(listOf(summary(1)), setOf(Part.FIT), asZip = false).files

        assertFalse(first.exists())
        assertEquals(listOf(second[0]), second[0].parentFile!!.listFiles()!!.toList())
    }

    @Test
    fun nothingToExportLeavesNoFile() {
        for (asZip in listOf(true, false)) {
            val result = export(listOf(summary(1)), setOf(Part.RAW), asZip)
            assertTrue(result.files.isEmpty())
            assertEquals(0, result.entries)
            assertFalse(File(getContext().cacheDir, "raw/workouts").exists())
        }
    }

    @Test
    fun zipIsNamedByTheDaysItCovers() {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val later = summary(2).apply { startTime = Date(start.time + 3 * 24 * 60 * 60 * 1000L) }

        assertEquals("${day.format(start)}-workouts.zip", WorkoutBatchExporter.zipName(listOf(summary(1))))
        assertEquals(
            "${day.format(start)}_${day.format(later.startTime)}-workouts.zip",
            WorkoutBatchExporter.zipName(listOf(later, summary(1)))
        )
    }

    @Test
    fun zipNameSpansYears() {
        fun at(y: Int, m: Int, d: Int) = summary(0).apply {
            startTime = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.time
        }

        assertEquals(
            "2025-12-30_2026-01-02-workouts.zip",
            WorkoutBatchExporter.zipName(listOf(at(2026, 1, 2), at(2025, 12, 30), at(2026, 1, 1)))
        )
        assertEquals(
            "2024-02-29_2026-01-01-workouts.zip",
            WorkoutBatchExporter.zipName(listOf(at(2026, 1, 1), at(2024, 2, 29)))
        )
    }

    @Test
    fun mimeTypeIsTheNarrowestCoveringAllFiles() {
        fun files(vararg names: String) = names.map { File(it) }

        assertEquals("application/zip", WorkoutBatchExporter.mimeTypeFor(files("a.zip")))
        assertEquals("application/gpx+xml", WorkoutBatchExporter.mimeTypeFor(files("a.gpx", "b.GPX")))
        assertEquals("application/octet-stream", WorkoutBatchExporter.mimeTypeFor(files("a.fit", "b.bin")))
        assertEquals("*/*", WorkoutBatchExporter.mimeTypeFor(files("a.fit", "a.gpx")))
    }
}
