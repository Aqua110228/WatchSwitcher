/*  Copyright (C) 2026 Dany Mestas

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.ActivitySummaryUtils
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports several workouts for sharing from the workout list, either as separate files or packed
 * into one zip. Output goes to `cache/raw/workouts/`, which is emptied at the start of every
 * export so that only the latest export is kept.
 *
 * Files are named `<iso start>-<kind>.<ext>` (see [ActivitySummaryUtils.getExportBaseName]);
 * raw device files add `-summary`, `-details` or `-gps` and sit under `raw/` inside a zip. Two
 * workouts that share a start time and kind get `-2`, `-3`, ... appended. A part a workout does
 * not have (no GPS track, no raw file) is left out silently; a part that fails to build is logged
 * and counted in [Result.skipped].
 *
 * Blocking: call from an IO context.
 */
object WorkoutBatchExporter {
    private val LOG = LoggerFactory.getLogger(WorkoutBatchExporter::class.java)

    enum class Part { FIT, GPX, RAW }

    /** [files] is empty when nothing was written, in which case nothing is left on disk. */
    data class Result(val files: List<File>, val entries: Int, val skipped: Int)

    fun export(
        context: Context,
        gbDevice: GBDevice,
        summaries: List<BaseActivitySummary>,
        parts: Set<Part>,
        asZip: Boolean
    ): Result {
        val outDir = File(context.cacheDir, "raw/workouts")
        outDir.deleteRecursively()
        outDir.mkdirs()

        val sink = if (asZip) ZipSink(File(outDir, zipName(summaries))) else DirSink(outDir)
        val usedNames = HashSet<String>()
        var skipped = 0

        sink.use {
            for (summary in summaries) {
                val base = uniqueBase(ActivitySummaryUtils.getExportBaseName(context, summary), usedNames)
                val time = summary.startTime.time

                fun add(name: String, build: () -> File?) {
                    try {
                        val file = build() ?: return
                        try {
                            sink.put(name, time) { out -> file.inputStream().use { it.copyTo(out) } }
                        } finally {
                            deleteIfCached(context, outDir, file)
                        }
                    } catch (e: Exception) {
                        LOG.warn("Could not export {} for summary {}", name, summary.id, e)
                        skipped++
                    }
                }

                if (Part.FIT in parts) {
                    add("$base.fit") { WorkoutUploader.buildFitFile(context, gbDevice, summary) }
                }
                if (Part.GPX in parts && WorkoutUploader.summaryHasTrack(summary)) {
                    add("$base.gpx") {
                        val provider = gbDevice.deviceCoordinator.getActivityTrackProvider(gbDevice, context)
                        ActivitySummaryUtils.getShareableGpxFile(provider, summary)
                    }
                }
                if (Part.RAW in parts) {
                    summary.rawSummaryData?.let { bytes ->
                        try {
                            sink.put("raw/$base-summary.bin", time) { it.write(bytes) }
                        } catch (e: Exception) {
                            LOG.warn("Could not export the raw summary of {}", summary.id, e)
                            skipped++
                        }
                    }
                    val coordinator = gbDevice.deviceCoordinator
                    coordinator.getWorkoutRawDetailsFile(gbDevice, summary)?.let { file ->
                        add("raw/$base-details${extensionOf(file)}") { file }
                    }
                    coordinator.getWorkoutRawGpsFile(gbDevice, summary)?.let { file ->
                        add("raw/$base-gps${extensionOf(file)}") { file }
                    }
                }
            }
        }

        if (sink.entries == 0) {
            outDir.deleteRecursively()
            return Result(emptyList(), 0, skipped)
        }
        return Result(sink.files, sink.entries, skipped)
    }

    /**
     * `<first day>_<last day>-workouts.zip` over the workouts' local start dates, or
     * `<day>-workouts.zip` when they all started on the same day.
     */
    internal fun zipName(summaries: List<BaseActivitySummary>): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val days = summaries.mapNotNull { it.startTime }.ifEmpty { listOf(Date()) }
        val first = format.format(days.min())
        val last = format.format(days.max())
        val range = if (first == last) first else "${first}_$last"
        return "$range-workouts.zip"
    }

    /**
     * The narrowest MIME type covering [files], so that a share of GPX tracks only reaches apps
     * that import `application/gpx+xml` (OpenTracks registers `SEND_MULTIPLE` for that type only).
     */
    fun mimeTypeFor(files: List<File>): String {
        val extensions = files.map { it.extension.lowercase(Locale.ROOT) }.toSet()
        return when {
            extensions == setOf("zip") -> "application/zip"
            extensions == setOf("gpx") -> "application/gpx+xml"
            "gpx" !in extensions -> "application/octet-stream"
            else -> "*/*"
        }
    }

    private fun uniqueBase(base: String, used: MutableSet<String>): String {
        var candidate = base
        var n = 2
        while (!used.add(candidate)) {
            candidate = "$base-${n++}"
        }
        return candidate
    }

    private fun extensionOf(file: File): String =
        file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ".bin"

    /**
     * Removes a temporary file built for the export. Files outside the cache directory are the
     * device's own stored workout files (an original gpx, a raw details file) and are left alone,
     * as is the export's own output.
     */
    private fun deleteIfCached(context: Context, outDir: File, file: File) {
        val path = file.canonicalPath
        val cacheDir = context.cacheDir.canonicalPath + File.separator
        val outPath = outDir.canonicalPath + File.separator
        if (path.startsWith(cacheDir) && !path.startsWith(outPath)) {
            file.delete()
        }
    }

    private abstract class Sink : Closeable {
        var entries = 0
            protected set

        abstract val files: List<File>

        abstract fun put(name: String, time: Long, write: (OutputStream) -> Unit)
    }

    private class ZipSink(private val zipFile: File) : Sink() {
        private val zip = ZipOutputStream(FileOutputStream(zipFile))

        override val files get() = listOf(zipFile)

        override fun put(name: String, time: Long, write: (OutputStream) -> Unit) {
            zip.putNextEntry(ZipEntry(name).apply { this.time = time })
            write(zip)
            zip.closeEntry()
            entries++
        }

        override fun close() = zip.close()
    }

    /** Writes each entry as its own file; the `raw/` prefix is dropped since names stay unique. */
    private class DirSink(private val dir: File) : Sink() {
        private val written = ArrayList<File>()

        override val files get() = written

        override fun put(name: String, time: Long, write: (OutputStream) -> Unit) {
            val file = File(dir, name.substringAfterLast('/'))
            try {
                FileOutputStream(file).use(write)
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            file.setLastModified(time)
            written.add(file)
            entries++
        }

        override fun close() {}
    }
}
