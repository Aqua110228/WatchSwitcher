/*  Copyright (C) 2026 Thomas Peoples

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.CobsCoDec;

/**
 * Some Garmin devices (e.g. Edge 520 Plus) only serve files over a Classic Bluetooth RFCOMM
 * link, which the device opens towards the phone after looking up this service UUID. The link
 * carries COBS-framed GFDI, same as the V1 BLE characteristics. Messages received here are
 * handled by {@link GarminSupport} and answered over this link.
 */
public class GarminRfcommListener extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(GarminRfcommListener.class);

    public static final UUID UUID_GARMIN_RFCOMM = UUID.fromString("deab91e4-670f-11e1-9ace-30bc4824019b");

    private final GarminSupport support;
    private volatile boolean running = true;
    private BluetoothServerSocket serverSocket;
    private volatile BluetoothSocket socket;
    private volatile OutputStream out;
    private volatile String classicAddress;

    /**
     * @param classicAddress the device's Classic address if already known, queried once listening
     */
    public GarminRfcommListener(final GarminSupport support, final String classicAddress) {
        super("GarminRfcommListener");
        this.support = support;
        this.classicAddress = classicAddress;
    }

    public boolean isConnected() {
        return out != null;
    }

    /**
     * The device only opens the RFCOMM link on its own after a few minutes, but does so within
     * seconds once the phone contacts it over Classic Bluetooth. An SDP query does that.
     *
     * @param address the device's Classic address, or null to reuse the last known one
     */
    @SuppressLint("MissingPermission")
    public void requestConnection(final String address) {
        if (address != null) {
            classicAddress = address;
        }
        if (isConnected() || classicAddress == null || !BluetoothAdapter.checkBluetoothAddress(classicAddress)) {
            return;
        }
        try {
            LOG.info("Requesting RFCOMM connection via SDP query to {}", classicAddress);
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice(classicAddress).fetchUuidsWithSdp();
        } catch (final SecurityException e) {
            LOG.error("Failed to query {}", classicAddress, e);
        }
    }

    public synchronized void sendMessage(final byte[] message) {
        if (message == null || out == null) {
            return;
        }
        try {
            out.write(CobsCoDec.encode(message));
            out.flush();
        } catch (final IOException e) {
            LOG.warn("RFCOMM write failed", e);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        try {
            serverSocket = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord("Gadgetbridge Garmin", UUID_GARMIN_RFCOMM);
            LOG.info("RFCOMM listening on {}", UUID_GARMIN_RFCOMM);
        } catch (final IOException | SecurityException e) {
            LOG.error("Failed to open RFCOMM server socket", e);
            return;
        }
        // the device looks up our service record when queried, so only query once it exists
        requestConnection(null);

        final byte[] buf = new byte[4096];
        while (running) {
            try {
                socket = serverSocket.accept();
            } catch (final IOException e) {
                // server socket closed or Bluetooth off, do not spin
                if (running) {
                    LOG.warn("RFCOMM accept failed, stopping listener", e);
                }
                return;
            }
            try {
                LOG.info("RFCOMM connection from {}", socket.getRemoteDevice().getAddress());
                final InputStream in = socket.getInputStream();
                out = socket.getOutputStream();
                // device reports max packet 0x4000 over RFCOMM, file chunks use all of it
                final CobsCoDec cobsCoDec = new CobsCoDec(0x10000);
                int n;
                while ((n = in.read(buf)) > 0) {
                    // feed byte by byte: one read can hold several frames, the decoder yields one at a time
                    for (int i = 0; i < n; i++) {
                        cobsCoDec.receivedBytes(new byte[]{buf[i]});
                        final byte[] message = cobsCoDec.retrieveMessage();
                        if (message != null) {
                            try {
                                support.onMessage(message);
                            } catch (final RuntimeException e) {
                                // an uncaught exception here would kill the app
                                LOG.error("Failed to handle RFCOMM message", e);
                            }
                        }
                    }
                }
                LOG.info("RFCOMM connection closed");
            } catch (final IOException e) {
                if (running) {
                    LOG.warn("RFCOMM error", e);
                }
            } finally {
                out = null;
                closeQuietly(socket);
            }
        }
    }

    public void close() {
        running = false;
        closeQuietly(socket);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (final IOException ignored) {
            }
        }
    }

    private static void closeQuietly(final BluetoothSocket s) {
        if (s != null) {
            try {
                s.close();
            } catch (final IOException ignored) {
            }
        }
    }
}
