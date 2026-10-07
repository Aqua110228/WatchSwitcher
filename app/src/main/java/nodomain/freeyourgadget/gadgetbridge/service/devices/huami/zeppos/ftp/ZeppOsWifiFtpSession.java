package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.ftp;

import android.content.Context;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services.ZeppOsFtpServerService;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.services.ZeppOsWifiService;
import nodomain.freeyourgadget.gadgetbridge.service.ftp.WifiFtpSession;
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperFtpClient;
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperWifiConnection;

/**
 * An FTP connection to a Zepp OS watch, over the Wi-Fi hotspot started by the watch.
 */
public class ZeppOsWifiFtpSession extends WifiFtpSession implements ZeppOsWifiService.Callback, ZeppOsFtpServerService.Callback {
    private static final Logger LOG = LoggerFactory.getLogger(ZeppOsWifiFtpSession.class);

    public static final String ROOT_DIR = "/mnt/data";
    private static final long HOTSPOT_TIMEOUT_MS = 30_000;
    private static final long WIFI_TIMEOUT_MS = 90_000;
    private static final long FTP_SERVER_TIMEOUT_MS = 30_000;
    private static final String PASSWORD_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ZeppOsWifiService wifiService;
    private final ZeppOsFtpServerService ftpServerService;

    private InternetHelperWifiConnection wifiConnection;

    private volatile boolean active = false;
    private volatile CountDownLatch hotspotLatch;
    private volatile CountDownLatch ftpServerLatch;
    private volatile String ftpAddress;
    private volatile String ftpUsername;

    public ZeppOsWifiFtpSession(final Context context,
                                final GBDevice device,
                                final ZeppOsWifiService wifiService,
                                final ZeppOsFtpServerService ftpServerService) {
        super(context, device);
        this.wifiService = wifiService;
        this.ftpServerService = ftpServerService;
    }

    @NonNull
    @Override
    public String getRootDir() {
        return ROOT_DIR;
    }

    @NonNull
    @Override
    protected InternetHelperFtpClient open() throws Exception {
        final String ssid = String.format(Locale.ROOT, "GB-%04d", new SecureRandom().nextInt(10000));
        final String password = randomPassword();

        wifiConnection = InternetHelperWifiConnection.open(this::onWifiLost);

        active = true;
        wifiService.setCallback(this);
        ftpServerService.setCallback(this);

        setState(State.STARTING_HOTSPOT);
        hotspotLatch = new CountDownLatch(1);
        wifiService.startWifiHotspot(ssid, password);
        if (!hotspotLatch.await(HOTSPOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            LOG.error("Timeout after {} ms waiting for the hotspot to start for {}", HOTSPOT_TIMEOUT_MS, ssid);
            throw new IOException(getContext().getString(R.string.wifi_ftp_error_hotspot));
        }

        setState(State.CONNECTING_WIFI);
        wifiConnection.connect(ssid, password, WIFI_TIMEOUT_MS);

        setState(State.STARTING_FTP_SERVER);
        ftpAddress = null;
        ftpServerLatch = new CountDownLatch(1);
        ftpServerService.startFtpServer(ROOT_DIR);
        if (!ftpServerLatch.await(FTP_SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS) || ftpAddress == null) {
            LOG.error("Timeout after {} ms waiting for the FTP server to start", FTP_SERVER_TIMEOUT_MS);
            throw new IOException(getContext().getString(R.string.wifi_ftp_error_ftp_server));
        }

        String host = ftpAddress;
        int port = 21;
        if (host.startsWith("ftp://")) {
            host = host.substring("ftp://".length());
        }
        final int portSeparator = host.lastIndexOf(':');
        if (portSeparator > 0) {
            port = Integer.parseInt(host.substring(portSeparator + 1).replaceAll("/.*", ""));
            host = host.substring(0, portSeparator);
        }

        LOG.info("Connecting to FTP server at {}:{} as {}, from address {}", host, port, ftpUsername, ftpAddress);
        return InternetHelperFtpClient.connect(
            host,
            port,
            wifiConnection.getNetworkRequestId(),
            ftpUsername,
            ""
        );
    }

    /**
     * Stops the FTP server and the hotspot. The callbacks stay set until the watch confirms the stop.
     */
    @Override
    protected void shutdown() {
        final boolean wasActive = active;
        LOG.debug("Shutting down, active={}, disposed={}", wasActive, getDisposed());
        active = false;
        if (wifiConnection != null) {
            wifiConnection.disconnect();
            wifiConnection = null;
        }
        if (wasActive && !getDisposed()) {
            ftpServerService.stopFtpServer();
            wifiService.stopWifiHotspot();
        }
    }

    private void onWifiLost() {
        LOG.warn("Wi-Fi network lost");
        close();
    }

    @Override
    public void onWifiHotspotStart() {
        LOG.debug("Wi-Fi hotspot started");
        final CountDownLatch latch = hotspotLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void onWifiHotspotStop() {
        if (!active) {
            wifiService.removeCallback();
            return;
        }
        LOG.warn("Wi-Fi hotspot stopped");
        close();
    }

    @Override
    public void onWifiHotspotError(final int errorNumber) {
        if (!active) {
            wifiService.removeCallback();
            return;
        }
        LOG.warn("Wi-Fi hotspot error {}", errorNumber);
        close();
    }

    @Override
    public void onFtpServerStart(final String address, final String username) {
        LOG.debug("FTP server started at {} for {}", address, username);
        ftpAddress = address;
        ftpUsername = username;
        final CountDownLatch latch = ftpServerLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void onFtpServerStop() {
        if (!active) {
            ftpServerService.removeCallback();
            return;
        }
        LOG.warn("FTP server stopped");
        close();
    }

    private static String randomPassword() {
        final SecureRandom random = new SecureRandom();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
