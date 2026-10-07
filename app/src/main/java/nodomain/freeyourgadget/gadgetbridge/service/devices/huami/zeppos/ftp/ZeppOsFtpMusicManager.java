package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ftp;

import android.net.Uri;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceMusicData;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceMusicFile;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceMusicFileDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceMusic;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ZeppOsSupport;
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSession;
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.audio.AudioInfo;
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized;
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperFtpClient;
import nodomain.freeyourgadget.internethelper.aidl.ftp.FtpEntry;

/**
 * The music on a Zepp OS watch, as mp3 and json file pairs. The watch moves uploaded files from the upload
 * directory to the list directory.
 */
public class ZeppOsFtpMusicManager {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsFtpMusicManager.class);

    private static final String UPLOAD_DIR = ZeppOsWifiFtpSession.ROOT_DIR + "/Downloads/Music";
    private static final String LIST_DIR = UPLOAD_DIR + "/list";
    private static final String MP3_EXTENSION = ".mp3";
    private static final String JSON_EXTENSION = ".mp3.json";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final ZeppOsSupport support;
    private final WifiFtpSession session;
    private final ExecutorService executor;

    @GsonSerialized
    private static class MusicJson {
        private String title;
        private String album;
        private String artist;
        private long size;
    }

    public ZeppOsFtpMusicManager(final ZeppOsSupport support,
                                 final WifiFtpSession session,
                                 final ExecutorService executor) {
        this.support = support;
        this.session = session;
        this.executor = executor;
    }

    public void requestList() {
        LOG.debug("Requesting music list");
        executor.execute(() -> {
            final GBDeviceMusicData startEvent = new GBDeviceMusicData();
            startEvent.type = 1;
            startEvent.deleteSupported = false;
            startEvent.uploadMimeTypes = new String[]{"audio/mpeg"};
            support.evaluateGBDeviceEvent(startEvent);

            final List<GBDeviceMusic> list = new ArrayList<>();
            try {
                final InternetHelperFtpClient client = session.acquire();
                try {
                    for (final DeviceMusicFile musicFile : syncList(client)) {
                        assert musicFile.getId() != null;
                        list.add(new GBDeviceMusic(
                            musicFile.getId().intValue(),
                            musicFile.getTitle(),
                            musicFile.getArtist(),
                            musicFile.getHash() + MP3_EXTENSION
                        ));
                    }
                } finally {
                    session.release();
                }
            } catch (final Exception e) {
                LOG.error("Failed to list music", e);
                GB.toast(support.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR, e);
            }

            LOG.debug("Got {} music files", list.size());
            final GBDeviceMusicData listEvent = new GBDeviceMusicData();
            listEvent.type = 2;
            listEvent.list = list;
            support.evaluateGBDeviceEvent(listEvent);

            final GBDeviceMusicData endEvent = new GBDeviceMusicData();
            endEvent.type = 10;
            support.evaluateGBDeviceEvent(endEvent);
        });
    }

    public void upload(final Uri uri, final AudioInfo audioInfo) {
        executor.execute(() -> {
            final ZeppOsFtpInstallProgress progress = new ZeppOsFtpInstallProgress(
                support.getContext(),
                R.string.music_upload_in_progress,
                R.string.music_upload_complete,
                R.string.music_upload_failed
            );
            progress.update(0, 1);

            try {
                final String hash = CheckSums.md5(support.getContext(), uri);

                final MusicJson json = new MusicJson();
                json.title = audioInfo.getTitle();
                json.album = audioInfo.getAlbum() != null ? audioInfo.getAlbum() : "";
                json.artist = audioInfo.getArtist();
                json.size = audioInfo.getFileSize();
                final String jsonStr = GSON.toJson(json);
                final byte[] jsonBytes = jsonStr.getBytes(StandardCharsets.UTF_8);

                LOG.info("Uploading music {} as {} - {}", audioInfo, hash, jsonStr);

                final InternetHelperFtpClient client = session.acquire();
                try {
                    try (InputStream jsonStream = new ByteArrayInputStream(jsonBytes)) {
                        client.uploadStream(jsonStream, UPLOAD_DIR + "/" + hash + JSON_EXTENSION, jsonBytes.length, null);
                    }
                    client.uploadUri(uri, UPLOAD_DIR + "/" + hash + MP3_EXTENSION, audioInfo.getFileSize(), progress::update);
                } finally {
                    session.release();
                }

                store(hash, json);
                LOG.debug("Uploaded music {}", hash);
                progress.finish(true, null);
            } catch (final Exception e) {
                LOG.error("Failed to upload music", e);
                progress.finish(false, e.getLocalizedMessage());
            }
        });
    }

    /**
     * Matches the music cache to the mp3 files on the watch. Downloads the json files that are not in the cache.
     */
    private List<DeviceMusicFile> syncList(final InternetHelperFtpClient client) throws Exception {
        // A song is complete when both its mp3 and its json file are not empty
        final Set<String> mp3Hashes = new HashSet<>();
        final Set<String> jsonHashes = new HashSet<>();
        for (final FtpEntry entry : client.list(LIST_DIR)) {
            final String name = entry.getName();
            if (entry.getType() != FtpEntry.Type.FILE || entry.getSize() <= 0 || name == null) {
                LOG.warn("Ignoring {} in the music list: type={}, size={}", name, entry.getType(), entry.getSize());
                continue;
            }
            if (name.endsWith(MP3_EXTENSION)) {
                mp3Hashes.add(name.substring(0, name.length() - MP3_EXTENSION.length()));
            } else if (name.endsWith(JSON_EXTENSION)) {
                jsonHashes.add(name.substring(0, name.length() - JSON_EXTENSION.length()));
            }
        }
        final Set<String> hashes = new HashSet<>(mp3Hashes);
        hashes.retainAll(jsonHashes);
        final Set<String> incomplete = new HashSet<>(mp3Hashes);
        incomplete.addAll(jsonHashes);
        incomplete.removeAll(hashes);
        if (!incomplete.isEmpty()) {
            LOG.warn("Ignoring songs without both an mp3 and a json file: {}", incomplete);
        }
        LOG.debug("Found {} music files on the watch", hashes.size());

        final Map<String, DeviceMusicFile> cached = new HashMap<>();
        try (DBHandler db = GBApplication.acquireDbReadOnly()) {
            final Device device = DBHelper.findDevice(support.getDevice(), db.getDaoSession());
            if (device != null) {
                for (final DeviceMusicFile musicFile : db.getDaoSession().getDeviceMusicFileDao().queryBuilder()
                    .where(DeviceMusicFileDao.Properties.DeviceId.eq(device.getId()))
                    .list()) {
                    cached.put(musicFile.getHash(), musicFile);
                }
            }
        }

        final Map<String, MusicJson> downloaded = new HashMap<>();
        for (final String hash : hashes) {
            if (cached.containsKey(hash)) {
                continue;
            }
            final byte[] bytes = client.downloadBytes(LIST_DIR + "/" + hash + JSON_EXTENSION);
            LOG.debug("Got music json for {}: {}", hash, new String(bytes, StandardCharsets.UTF_8));
            try {
                final MusicJson json = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), MusicJson.class);
                if (json != null) {
                    downloaded.put(hash, json);
                }
            } catch (final JsonParseException e) {
                LOG.error("Failed to parse music json for {}", hash, e);
            }
        }

        final List<DeviceMusicFile> result = new ArrayList<>();
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            final Device device = DBHelper.getDevice(support.getDevice(), daoSession);
            final DeviceMusicFileDao dao = daoSession.getDeviceMusicFileDao();

            for (final DeviceMusicFile musicFile : cached.values()) {
                if (hashes.contains(musicFile.getHash())) {
                    result.add(musicFile);
                } else {
                    LOG.debug("Removing music {} from the cache", musicFile.getHash());
                    dao.delete(musicFile);
                }
            }

            for (final Map.Entry<String, MusicJson> e : downloaded.entrySet()) {
                final DeviceMusicFile musicFile = createEntity(device, e.getKey(), e.getValue());
                LOG.debug("Storing existing music {} in the cache", musicFile.getHash());
                dao.insertOrReplace(musicFile);
                result.add(musicFile);
            }
        }

        return result;
    }

    private void store(final String hash, final MusicJson json) throws Exception {
        try (DBHandler db = GBApplication.acquireDB()) {
            final DaoSession daoSession = db.getDaoSession();
            final Device device = DBHelper.getDevice(support.getDevice(), daoSession);
            final DeviceMusicFileDao dao = daoSession.getDeviceMusicFileDao();
            final DeviceMusicFile existing = dao.queryBuilder()
                .where(DeviceMusicFileDao.Properties.DeviceId.eq(device.getId()),
                    DeviceMusicFileDao.Properties.Hash.eq(hash))
                .unique();
            final DeviceMusicFile musicFile = createEntity(device, hash, json);
            if (existing != null) {
                musicFile.setId(existing.getId());
            }
            LOG.debug("Storing music {} in the cache", hash);
            dao.insertOrReplace(musicFile);
        }
    }

    private static DeviceMusicFile createEntity(final Device device, final String hash, final MusicJson json) {
        final DeviceMusicFile musicFile = new DeviceMusicFile();
        musicFile.setDevice(device);
        musicFile.setHash(hash);
        musicFile.setTitle(json.title);
        musicFile.setAlbum(json.album);
        musicFile.setArtist(json.artist);
        musicFile.setSize(json.size);
        return musicFile;
    }
}
