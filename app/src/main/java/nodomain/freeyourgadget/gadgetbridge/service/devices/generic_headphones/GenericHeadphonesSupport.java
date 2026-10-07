package nodomain.freeyourgadget.gadgetbridge.service.devices.generic_headphones;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.externalevents.BluetoothDisconnectReceiver;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.service.AbstractBluetoothDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.HeadphoneHelper;

public class GenericHeadphonesSupport extends AbstractBluetoothDeviceSupport implements HeadphoneHelper.Callback {

    private HeadphoneHelper headphoneHelper;
    private BluetoothDisconnectReceiver mBlueToothDisconnectReceiver = null;
    private BluetoothProfile headsetProxy = null;
    private boolean headsetStateReceiverRegistered = false;

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            // The proxy is the phone's own headset service, which is there whether or not these
            // headphones are connected, so ask it about them rather than take its arrival as a connection.
            headsetProxy = proxy;
            setConnectionState(proxy.getConnectionState(getHeadphones()));
        }

        @Override
        public void onServiceDisconnected(int profile) {
            headsetProxy = null;
        }
    };

    private final BroadcastReceiver headsetStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || !gbDevice.getAddress().equalsIgnoreCase(device.getAddress())) {
                return;
            }
            setConnectionState(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED));
        }
    };

    private BluetoothDevice getHeadphones() {
        return getBluetoothAdapter().getRemoteDevice(gbDevice.getAddress());
    }

    private void setConnectionState(final int headsetState) {
        if (headsetState == BluetoothProfile.STATE_CONNECTED) {
            gbDevice.setState(GBDevice.State.INITIALIZED);
        } else {
            gbDevice.setState(GBDevice.State.WAITING_FOR_RECONNECT);
        }
        gbDevice.sendDeviceUpdateIntent(getContext());
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        headphoneHelper.onSetCallState(callSpec);
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        headphoneHelper.onNotification(notificationSpec);
    }

    @Override
    public void setContext(GBDevice gbDevice, BluetoothAdapter btAdapter, Context context) {
        super.setContext(gbDevice, btAdapter, context);
        headphoneHelper = new HeadphoneHelper(getContext(), getDevice(), this);
    }

    @Override
    public void onSendConfiguration(String config) {
        if (!headphoneHelper.onSendConfiguration(config))
            super.onSendConfiguration(config);
    }

    @Override
    public void dispose() {
        if (headphoneHelper != null) {
            headphoneHelper.dispose();
            headphoneHelper = null;
        }
        if (mBlueToothDisconnectReceiver != null) {
            getContext().unregisterReceiver(mBlueToothDisconnectReceiver);
            mBlueToothDisconnectReceiver = null;
        }
        if (headsetStateReceiverRegistered) {
            getContext().unregisterReceiver(headsetStateReceiver);
            headsetStateReceiverRegistered = false;
        }
        if (headsetProxy != null) {
            getBluetoothAdapter().closeProfileProxy(BluetoothProfile.HEADSET, headsetProxy);
            headsetProxy = null;
        }
    }

    @Override
    public boolean connect() {
        if (isConnected()) {
            return false;
        }
        gbDevice.setState(GBDevice.State.CONNECTING);
        gbDevice.sendDeviceUpdateIntent(getContext(), GBDevice.DeviceUpdateSubject.CONNECTION_STATE);

        final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
        versionCmd.fwVersion2 = "N/A";
        handleGBDeviceEvent(versionCmd);

        // listen before asking, so a connection completing in between is not missed
        if (!headsetStateReceiverRegistered) {
            ContextCompat.registerReceiver(getContext(), headsetStateReceiver, new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED);
            headsetStateReceiverRegistered = true;
        }

        if (headsetProxy != null) {
            // reconnecting while waiting for the headphones: the headset service is already bound
            setConnectionState(headsetProxy.getConnectionState(getHeadphones()));
        } else {
            getBluetoothAdapter().getProfileProxy(getContext(), profileListener, BluetoothProfile.HEADSET);
        }

        if (mBlueToothDisconnectReceiver == null) {
            mBlueToothDisconnectReceiver = new BluetoothDisconnectReceiver();
            ContextCompat.registerReceiver(getContext(), mBlueToothDisconnectReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED), ContextCompat.RECEIVER_EXPORTED);
        }
        return true;
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

}
