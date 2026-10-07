/*  Copyright (C) 2026 oddballza

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.onebyone;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericWeightSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericWeightSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * 1byone "Health Scale" protocol, as documented by openScale's OneByoneHandler:
 * <ul>
 *     <li>notifications on 0xFFF4, commands written to 0xFFF1</li>
 *     <li>FD 37 [unit] [group] 00.. [xor]: configure the display unit</li>
 *     <li>F1 [year BE] [month] [day] [hour] [min] [sec]: set the clock, ACKed with F1 00</li>
 *     <li>F2 00: request stored measurements, terminated by F2 00; F2 01: clear them</li>
 *     <li>CF ...: measurement frame (11 bytes live, 18 bytes with timestamp for stored ones)</li>
 * </ul>
 */
public class OneByoneScaleDeviceSupport extends AbstractBTLESingleDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(OneByoneScaleDeviceSupport.class);

    private static final UUID UUID_SERVICE_SCALE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_CHARACTERISTIC_COMMAND = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_CHARACTERISTIC_MEASUREMENT = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb");

    private static final byte CMD_UNIT = (byte) 0xFD;
    private static final byte CMD_TIME = (byte) 0xF1;
    private static final byte CMD_HISTORY = (byte) 0xF2;
    private static final byte HISTORY_REQUEST = 0x00;
    private static final byte HISTORY_CLEAR = 0x01;
    private static final byte EVENT_POWER_OFF = (byte) 0xF3;
    private static final byte FRAME_MEASUREMENT = (byte) 0xCF;

    private static final byte UNIT_KG = 0x00;
    private static final byte UNIT_LB = 0x01;
    private static final byte UNIT_ST = 0x02;

    private static final int LIVE_FRAME_LENGTH = 11;
    private static final int STORED_FRAME_LENGTH = 18;

    /** The scale sends every final measurement twice, a few hundred ms apart. */
    private static final long MIN_SAMPLE_INTERVAL_MS = 3000L;
    /**
     * A measurement seen live is also returned by the next history read on firmwares
     * that store it; a stored measurement this close to the last live one is a replay.
     */
    private static final long HISTORY_REPLAY_WINDOW_MS = 90_000L;
    /** Stored measurements dated further than this in the future have a bogus clock. */
    private static final long MAX_FUTURE_MS = 5 * 60_000L;

    private final BatteryInfoProfile<OneByoneScaleDeviceSupport> batteryInfoProfile;
    private final GBDeviceEventBatteryInfo batteryCmd = new GBDeviceEventBatteryInfo();

    private int historyCount = 0;
    private long lastSampleTimestamp = 0;

    public OneByoneScaleDeviceSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(GattService.UUID_SERVICE_BATTERY_SERVICE);
        addSupportedService(UUID_SERVICE_SCALE);

        batteryInfoProfile = new BatteryInfoProfile<>(this);
        batteryInfoProfile.addListener(intent -> {
            if (BatteryInfoProfile.ACTION_BATTERY_INFO.equals(intent.getAction())) {
                final BatteryInfo info = intent.getParcelableExtra(BatteryInfoProfile.EXTRA_BATTERY_INFO);
                if (info != null) {
                    handleBatteryInfo(info);
                }
            }
        });
        addSupportedProfile(batteryInfoProfile);
    }

    @NonNull
    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        builder.setDeviceState(GBDevice.State.INITIALIZING);
        builder.setCallback(this);

        batteryInfoProfile.requestBatteryInfo(builder);
        batteryInfoProfile.enableNotify(builder, true);

        builder.notify(UUID_CHARACTERISTIC_MEASUREMENT, true);
        builder.write(UUID_CHARACTERISTIC_COMMAND, buildUnitCommand());
        builder.write(UUID_CHARACTERISTIC_COMMAND, buildTimeCommand());
        // Not every firmware acknowledges the time command (the LeFu "Health Scale" does not),
        // so the stored measurements are requested unconditionally. Firmwares without storage
        // simply never answer.
        historyCount = 0;
        builder.write(UUID_CHARACTERISTIC_COMMAND, new byte[]{CMD_HISTORY, HISTORY_REQUEST});

        builder.setDeviceState(GBDevice.State.INITIALIZED);
        return builder;
    }

    @Override
    public boolean onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] data) {
        if (super.onCharacteristicChanged(gatt, characteristic, data)) {
            return true;
        }

        if (!UUID_CHARACTERISTIC_MEASUREMENT.equals(characteristic.getUuid())) {
            return false;
        }

        if (data.length == 2) {
            handleAck(data[0], data[1]);
            return true;
        }

        if (data.length > 0 && data[0] == FRAME_MEASUREMENT) {
            // Older 1byone firmwares send 16-byte CF frames with a different layout (the
            // composition computed on the scale), which are deliberately not parsed here.
            if (data.length != LIVE_FRAME_LENGTH && data.length < STORED_FRAME_LENGTH) {
                LOG.warn("Ignoring measurement frame of unexpected length: {}", GB.hexdump(data));
                return true;
            }
            if (!verifyChecksum(data)) {
                LOG.warn("Ignoring frame with bad checksum: {}", GB.hexdump(data));
                return true;
            }
            handleMeasurementFrame(data);
            return true;
        }

        LOG.warn("Unhandled frame: {}", GB.hexdump(data));
        return true;
    }

    private void handleAck(final byte command, final byte status) {
        if (command == CMD_TIME && status == 0x00) {
            LOG.debug("Time set");
        } else if (command == CMD_HISTORY && status == 0x00) {
            LOG.debug("Received {} stored measurements", historyCount);
            if (historyCount > 0) {
                historyCount = 0;
                sendCommand("clear history", new byte[]{CMD_HISTORY, HISTORY_CLEAR});
            }
        } else if (command == EVENT_POWER_OFF) {
            LOG.debug("Scale is powering off");
        } else {
            LOG.debug("Unhandled ack: {} {}", command, status);
        }
    }

    /** The last byte of a live frame is the XOR of all preceding bytes. */
    private static boolean verifyChecksum(final byte[] data) {
        if (data.length != LIVE_FRAME_LENGTH) {
            // Only verified on live frames; the layout of stored frames is unconfirmed.
            return true;
        }
        byte xor = 0;
        for (int i = 0; i < data.length - 1; i++) {
            xor ^= data[i];
        }
        return xor == data[data.length - 1];
    }

    /**
     * CF frame layout:
     * <ul>
     *     <li>bytes 1-2: impedance, big endian, in 0.1 ohm</li>
     *     <li>bytes 3-4: weight, little endian, in 0.01 kg</li>
     *     <li>byte 9: 1 while the reading is still in progress (sent every 200 ms as the weight settles);
     *         0 in the final measurement, which is sent twice</li>
     *     <li>byte 10: XOR of bytes 0-9</li>
     *     <li>bytes 11-17: timestamp (year BE, month, day, hour, minute, second), only in stored measurements</li>
     * </ul>
     */
    private void handleMeasurementFrame(final byte[] data) {
        final float weightKg = BLETypeConversions.toUint16(data, 3) / 100.0f;
        final float impedanceRaw = (((data[2] & 0xFF) << 8) | (data[1] & 0xFF)) / 10.0f;
        final boolean hasTimestamp = data.length >= STORED_FRAME_LENGTH;
        final boolean historic = hasTimestamp;

        if (data[9] == 1) {
            LOG.trace("Reading in progress: {} kg", weightKg);
            return;
        }
        final boolean hasImpedance = impedanceRaw != 0f;

        if (historic) {
            historyCount++;
        }

        long timestamp = System.currentTimeMillis();
        if (hasTimestamp) {
            final int year = ((data[11] & 0xFF) << 8) | (data[12] & 0xFF);
            final GregorianCalendar calendar = new GregorianCalendar();
            calendar.setLenient(false);
            calendar.set(year, (data[13] & 0xFF) - 1, data[14] & 0xFF, data[15] & 0xFF, data[16] & 0xFF, data[17] & 0xFF);
            try {
                timestamp = calendar.getTimeInMillis();
            } catch (final IllegalArgumentException e) {
                LOG.warn("Ignoring measurement with invalid timestamp: {}", GB.hexdump(data));
                return;
            }
        }

        if (historic) {
            if (timestamp - System.currentTimeMillis() > MAX_FUTURE_MS) {
                LOG.warn("Ignoring stored measurement from the future: {}", GB.hexdump(data));
                return;
            }
            if (Math.abs(timestamp - lastSampleTimestamp) < HISTORY_REPLAY_WINDOW_MS) {
                LOG.debug("Ignoring stored measurement already received live");
                return;
            }
        } else {
            if (timestamp - lastSampleTimestamp < MIN_SAMPLE_INTERVAL_MS) {
                return;
            }
            lastSampleTimestamp = timestamp;
        }

        // The scale reports 0.1 ohm resolution; the sample stores whole ohms.
        final Integer impedanceOhm = hasImpedance ? Math.round(impedanceRaw) : null;
        LOG.debug("Measurement: {} kg, impedance {} ohm, historic={}", weightKg, impedanceOhm, historic);

        if (!historic) {
            GB.toast(getContext().getString(R.string.weight_kg, weightKg), Toast.LENGTH_SHORT, GB.INFO);
        }

        saveSample(timestamp, weightKg, impedanceOhm);
    }

    private void saveSample(final long timestamp, final float weightKg, @Nullable final Integer impedanceOhm) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final GenericWeightSampleProvider provider = new GenericWeightSampleProvider(getDevice(), db.getDaoSession());

            final GenericWeightSample sample = new GenericWeightSample();
            sample.setTimestamp(timestamp);
            sample.setWeightKg(weightKg);
            sample.setImpedanceOhm(impedanceOhm);
            LOG.debug("Saving {}", sample);
            provider.persistSamples(sample, getContext());
            // let the dashboard and charts know there is a new value to show
            GB.signalActivityDataFinish(getDevice());
        } catch (final Exception e) {
            LOG.error("Error saving weight sample", e);
        }
    }

    private void handleBatteryInfo(final BatteryInfo info) {
        LOG.debug("Battery info: {}", info);
        batteryCmd.level = (short) info.getPercentCharged();
        handleGBDeviceEvent(batteryCmd);
    }

    private void sendCommand(final String taskName, final byte[] command) {
        final TransactionBuilder builder = createTransactionBuilder(taskName);
        builder.write(UUID_CHARACTERISTIC_COMMAND, command);
        builder.queue();
    }

    /**
     * FD 37 [unit] [group] 00 00 00 00 00 00 [xor of the preceding bytes]
     */
    private byte[] buildUnitCommand() {
        final byte unit;
        switch (GBApplication.getPrefs().getWeightUnit()) {
            case POUND:
                unit = UNIT_LB;
                break;
            case STONE:
                unit = UNIT_ST;
                break;
            default:
                unit = UNIT_KG;
        }

        final byte[] command = new byte[]{CMD_UNIT, 0x37, unit, 0x01, 0, 0, 0, 0, 0, 0, 0};
        byte checksum = 0;
        for (int i = 0; i < command.length - 1; i++) {
            checksum ^= command[i];
        }
        command[command.length - 1] = checksum;
        return command;
    }

    /**
     * F1 [year BE] [month] [day] [hour] [minute] [second]
     */
    private byte[] buildTimeCommand() {
        final Calendar now = Calendar.getInstance();
        final int year = now.get(Calendar.YEAR);
        return new byte[]{
                CMD_TIME,
                (byte) (year >> 8),
                (byte) year,
                (byte) (now.get(Calendar.MONTH) + 1),
                (byte) now.get(Calendar.DAY_OF_MONTH),
                (byte) now.get(Calendar.HOUR_OF_DAY),
                (byte) now.get(Calendar.MINUTE),
                (byte) now.get(Calendar.SECOND),
        };
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }
}
