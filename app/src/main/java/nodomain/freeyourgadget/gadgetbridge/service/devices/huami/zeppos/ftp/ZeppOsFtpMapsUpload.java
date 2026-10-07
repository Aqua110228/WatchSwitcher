package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ftp;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.operations.ZeppOsMapsFile;
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSession;
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperFtpClient;

/**
 * Uploads the tiles of a map zip to the map directory of the FTP server.
 */
public class ZeppOsFtpMapsUpload implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsFtpMapsUpload.class);

    private static final String MAPS_DIR = ZeppOsWifiFtpSession.ROOT_DIR + "/map/res/scl";

    private final Context context;
    private final WifiFtpSession session;
    private final ZeppOsMapsFile mapsFile;

    public ZeppOsFtpMapsUpload(final Context context, final WifiFtpSession session, final ZeppOsMapsFile mapsFile) {
        this.context = context;
        this.session = session;
        this.mapsFile = mapsFile;
    }

    @Override
    public void run() {
        final ZeppOsFtpInstallProgress progress = new ZeppOsFtpInstallProgress(
                context,
                R.string.map_upload_in_progress,
                R.string.map_upload_complete,
                R.string.map_upload_failed
        );
        progress.update(0, 1);

        try {
            final InternetHelperFtpClient client = session.acquire();
            try {
                upload(client, progress);
            } finally {
                session.release();
            }
            progress.finish(true, null);
        } catch (final Exception e) {
            LOG.error("Failed to upload map", e);
            progress.finish(false, e.getLocalizedMessage());
        }
    }

    private void upload(final InternetHelperFtpClient client, final ZeppOsFtpInstallProgress progress) throws IOException {
        final long total = mapsFile.getUncompressedSize();
        final Set<String> createdDirs = new HashSet<>();
        final long start = System.currentTimeMillis();
        int tiles = 0;
        long done = 0;
        LOG.info("Uploading map {} with {} bytes", mapsFile.getUri(), total);

        try (InputStream is = context.getContentResolver().openInputStream(mapsFile.getUri());
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }

                final String remotePath = MAPS_DIR + "/" + zipEntry.getName();
                final String remoteDir = remotePath.substring(0, remotePath.lastIndexOf('/'));
                if (createdDirs.add(remoteDir)) {
                    client.mkdirs(remoteDir);
                }

                LOG.debug("Uploading map tile {}", remotePath);
                final long offset = done;
                client.uploadStream(zis, remotePath, zipEntry.getSize(), (bytes, ignored) -> progress.update(offset + bytes, total));
                done += Math.max(zipEntry.getSize(), 0);
                tiles++;
                progress.update(done, total);
            }
        }
        LOG.info("Uploaded {} map tiles in {} ms", tiles, System.currentTimeMillis() - start);
    }
}
