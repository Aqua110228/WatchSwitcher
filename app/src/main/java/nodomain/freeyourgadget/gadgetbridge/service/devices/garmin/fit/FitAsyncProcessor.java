package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit;

import android.content.Context;
import android.os.Handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.PendingFileProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GBZipFile;

public class FitAsyncProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(FitAsyncProcessor.class);
    private static final AtomicLong THREAD_COUNTER = new AtomicLong(0L);

    private final Context context;
    private final GBDevice gbDevice;
    private final Handler handler;

    public FitAsyncProcessor(final Context context, final GBDevice gbDevice) {
        this.context = context;
        this.gbDevice = gbDevice;
        this.handler = new Handler(context.getMainLooper());
    }

    /**
     * Process a list of files asynchronously. Callback is executed on the UI thread.
     */
    public void process(final List<File> files, boolean isReprocessing, final Callback callback) {
        LOG.debug("Starting processor for {} files", files.size());

        new Thread(() -> {
            try {
                final FitImporter fitImporter = new FitImporter(context, gbDevice);
                int i = 0;
                for (final File file : files) {
                    i++;
                    LOG.debug("Processing {}", file);

                    final int finalI = i;
                    FitAsyncProcessor.this.handler.post(() -> callback.onProgress(finalI));

                    try {
                        if (!importFile(file, fitImporter, isReprocessing)) {
                            continue; // do not remove from pending files
                        }
                    } catch (final Exception ex) {
                        LOG.error("Exception while importing {}", file, ex);
                        continue; // do not remove from pending files
                    }

                    try (DBHandler handler = GBApplication.acquireDB()) {
                        final DaoSession session = handler.getDaoSession();

                        final PendingFileProvider pendingFileProvider = new PendingFileProvider(gbDevice, session);

                        pendingFileProvider.removePendingFile(file.getPath());
                    } catch (final Exception e) {
                        LOG.error("Exception while removing pending file {}", file, e);
                    }
                }
            } catch (final Exception e) {
                LOG.error("Failed to parse from storage", e);
            }

            FitAsyncProcessor.this.handler.post(callback::onFinish);
        }, "FitAsyncProcessor_" + THREAD_COUNTER.getAndIncrement()).start();
    }

    private boolean importFile(final File file,
                               final FitImporter fitImporter,
                               final boolean isReprocessing) throws Exception {
        final byte[] header = FileUtils.getHeader(file, 12);
        if (header == null) {
            // error logged upstream
            return false;
        }

        if (GBZipFile.isZipFile(header)) {
            // ZIP file (e.g. ECG)
            boolean fitImported = false;
            try(final ZipFile zipFile = new ZipFile(file)) {
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    final ZipEntry zipEntry = entries.nextElement();
                    if (zipEntry.isDirectory()) {
                        continue;
                    }
                    final byte[] bytes = GBZipFile.readAllBytes(zipFile.getInputStream(zipEntry));
                    if (BLETypeConversions.toUint32(bytes, 8) != FitFile.Header.MAGIC) {
                        LOG.warn("Zip contains non-FIT file {}", zipEntry.getName());
                    } else {
                        LOG.info("Processing from zip file: {}", zipEntry.getName());

                        final File tmpZipContents;
                        try {
                            final File cacheDir = context.getExternalCacheDir();
                            final File inflateDir = new File(cacheDir, "garmin-zip-contents");
                            //noinspection ResultOfMethodCallIgnored
                            inflateDir.mkdirs();
                            tmpZipContents = File.createTempFile("activity-files-import", ".bin", inflateDir);
                            tmpZipContents.deleteOnExit();
                            FileUtils.copyStreamToFile(new ByteArrayInputStream(bytes), tmpZipContents);
                        } catch (final IOException e) {
                            LOG.error("Failed to create temp file for zip file contents", e);
                            return false;
                        }

                        fitImported = importFile(tmpZipContents, fitImporter, isReprocessing);
                    }
                }
            }
            return fitImported;
        }

        // FIT file
        if (BLETypeConversions.toUint32(header, 8) == FitFile.Header.MAGIC) {
            fitImporter.importFile(file, isReprocessing);
            return true;
        }

        LOG.warn("Unknown file type for {}", file);
        return false;
    }

    public interface Callback {
        void onProgress(final int perc);

        void onFinish();
    }
}
