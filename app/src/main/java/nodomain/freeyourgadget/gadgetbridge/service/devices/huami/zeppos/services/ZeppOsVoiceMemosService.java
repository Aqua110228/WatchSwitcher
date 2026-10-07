/*  Copyright (C) 2025 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services;

import android.content.Intent;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.activities.audiorecordings.AudioRecordingsActivity;
import nodomain.freeyourgadget.gadgetbridge.database.repository.AudioRecordingsRepository;
import nodomain.freeyourgadget.gadgetbridge.entities.AudioRecording;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.AbstractZeppOsService;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ZeppOsSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ZeppOsTransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ftp.ZeppOsWifiFtpSession;
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSession;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperFtpClient;

public class ZeppOsVoiceMemosService extends AbstractZeppOsService {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsVoiceMemosService.class);

    private static final short ENDPOINT = 0x0033;

    private static final byte CMD_LIST_REQUEST = 0x05;
    private static final byte CMD_LIST_RESPONSE = 0x06;
    private static final byte CMD_DOWNLOAD_START_REQUEST = 0x07;
    private static final byte CMD_DOWNLOAD_START_ACK = 0x08;
    private static final byte CMD_DOWNLOAD_FINISH_REQUEST = 0x0a;
    private static final byte CMD_DOWNLOAD_FINISH_ACK = 0x09;

    private static final String FTP_DIR = ZeppOsWifiFtpSession.ROOT_DIR + "/Downloads/VoiceMemo/list";

    private final Map<String, AudioRecording> downloadingRecordings = new HashMap<>();
    private final Queue<String> downloadQueue = new LinkedList<>();
    private boolean downloading = false;
    private final Handler handler = new Handler();

    private WifiFtpSession ftpSession;
    private ExecutorService ftpExecutor;

    public ZeppOsVoiceMemosService(final ZeppOsSupport support) {
        super(support, true);
    }

    @Override
    public short getEndpoint() {
        return ENDPOINT;
    }

    /**
     * Sets the FTP session to download the voice memos. If not set, the downloads use BLE.
     */
    public void setFtpSession(@Nullable final WifiFtpSession ftpSession,
                              @Nullable final ExecutorService ftpExecutor) {
        this.ftpSession = ftpSession;
        this.ftpExecutor = ftpExecutor;
    }

    @Override
    public void dispose() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void handlePayload(final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        switch (buf.get()) {
            case CMD_LIST_RESPONSE:
                final short count = buf.getShort();
                LOG.info("Got list with {} voice memos", count);

                final boolean useFtp = ftpSession != null && ftpExecutor != null;
                final Map<String, AudioRecording> ftpRecordings = new LinkedHashMap<>();

                for (int i = 0; i < count; i++) {
                    final String filename = Objects.requireNonNull(StringUtils.untilNullTerminator(buf));
                    final int size = buf.getInt();
                    final int duration = buf.getInt();
                    final long timestamp = buf.getLong();

                    final AudioRecording existingRecording = AudioRecordingsRepository.getByTimestamp(getSupport().getDevice(), timestamp);

                    LOG.debug(
                        "Voice memo: filename={}, size={}b, duration={}ms, timestamp={}",
                        filename, size, duration, DateTimeUtils.formatIso8601(new Date(timestamp))
                    );

                    if (existingRecording != null) {
                        LOG.debug("Ignoring known local recording {}", filename);
                        continue;
                    }

                    final AudioRecording audioRecording = new AudioRecording();
                    audioRecording.setRecordingId(UUID.randomUUID().toString());
                    audioRecording.setLabel(filename.replace(".opus", ""));
                    audioRecording.setTimestamp(timestamp);
                    audioRecording.setDuration(duration);

                    if (useFtp) {
                        ftpRecordings.put(filename, audioRecording);
                        continue;
                    }

                    downloadingRecordings.put(filename, audioRecording);

                    downloadStart(Objects.requireNonNull(filename));
                }

                if (useFtp && !ftpRecordings.isEmpty()) {
                    ftpExecutor.execute(() -> downloadFtp(ftpRecordings));
                    return;
                }

                if (!downloading) {
                    broadcastDownloadFinished();
                }

                return;
            case CMD_DOWNLOAD_START_ACK:
                LOG.info("Download start ACK, status = {}", payload[1]);
                return;
            case CMD_DOWNLOAD_FINISH_ACK:
                LOG.info("Download finish ACK, status = {}", payload[1]);
                return;
        }

        LOG.warn("Unexpected voice memos byte {}", String.format("0x%02x", payload[0]));
    }

    @Override
    public void initialize(final ZeppOsTransactionBuilder builder) {
        downloadingRecordings.clear();
        downloadQueue.clear();
        downloading = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void requestList() {
        write("get voice memos list", CMD_LIST_REQUEST);
    }

    public void downloadStart(final String filename) {
        LOG.debug("Queuing voice memo download for {}", filename);

        downloadQueue.add(filename);
        if (!downloading) {
            downloading = true;
            downloadNext();
        }
    }

    public void onFileDownloadFinish(final String url, final String filename, final byte[] data) {
        final AudioRecording audioRecording = downloadingRecordings.get(filename);
        if (audioRecording == null) {
            LOG.error("Received file {} for unknown audio recording", filename);
            downloadNext();
            return;
        }

        final File targetFile;
        try {
            targetFile = getTargetFile(filename);
        } catch (final IOException e) {
            LOG.error("Failed create folder to save voice memo", e);
            downloadNext();
            return;
        }

        try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            outputStream.write(data);
        } catch (final IOException e) {
            LOG.error("Failed to save voice memo bytes", e);
            downloadNext();
            return;
        }

        audioRecording.setPath(targetFile.getPath());
        AudioRecordingsRepository.insertOrReplace(getSupport().getDevice(), audioRecording);

        downloadNext();
    }

    private File getTargetFile(final String filename) throws IOException {
        final File exportDirectory = getCoordinator().getWritableExportDirectory(getSupport().getDevice(), true);
        final File voiceMemosDirectory = new File(exportDirectory, "voicememo");
        //noinspection ResultOfMethodCallIgnored
        voiceMemosDirectory.mkdirs();

        return new File(voiceMemosDirectory, FileUtils.makeValidFileName(filename));
    }

    private void downloadFtp(final Map<String, AudioRecording> recordings) {
        LOG.info("Downloading {} voice memos over FTP", recordings.size());
        try {
            final InternetHelperFtpClient client = ftpSession.acquire();
            try {
                for (final Map.Entry<String, AudioRecording> e : recordings.entrySet()) {
                    final String filename = e.getKey();
                    final File targetFile = getTargetFile(filename);
                    LOG.debug("Downloading voice memo {} to {}", filename, targetFile);
                    client.download(
                        FTP_DIR + "/" + filename,
                        ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE),
                        null
                    );

                    final AudioRecording audioRecording = e.getValue();
                    audioRecording.setPath(targetFile.getPath());
                    LOG.debug("Storing voice memo {}", filename);
                    AudioRecordingsRepository.insertOrReplace(getSupport().getDevice(), audioRecording);
                }
            } finally {
                ftpSession.release();
            }
        } catch (final Exception e) {
            LOG.error("Failed to download voice memos", e);
            GB.toast(getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR, e);
        }

        broadcastDownloadFinished();
    }

    private void downloadNext() {
        handler.removeCallbacksAndMessages(null);

        final String filename = downloadQueue.poll();

        if (filename != null) {
            // Timeout after a while so we do not get stuck
            handler.postDelayed(() -> {
                LOG.warn("Timed out waiting for voice memo download, triggering next");
                downloadNext();
            }, 5000L);

            LOG.debug("Will download voice memo {}", filename);
            write(
                "voice memo download " + filename,
                ArrayUtils.addAll(new byte[]{CMD_DOWNLOAD_START_REQUEST}, filename.getBytes())
            );
        } else {
            LOG.debug("Voice memo downloads finished");
            downloading = false;
            write("voice memo download finish", CMD_DOWNLOAD_FINISH_REQUEST);

            broadcastDownloadFinished();
        }
    }

    private void broadcastDownloadFinished() {
        final Intent intent = new Intent(AudioRecordingsActivity.ACTION_FETCH_FINISH);
        intent.setPackage(BuildConfig.APPLICATION_ID);
        LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
    }
}
