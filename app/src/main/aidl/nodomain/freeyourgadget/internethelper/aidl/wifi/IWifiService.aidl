package nodomain.freeyourgadget.internethelper.aidl.wifi;

import nodomain.freeyourgadget.internethelper.aidl.wifi.IWifiCallback;

interface IWifiService {
    int version();
    String getCurrentSsid();
    boolean isWifiEnabled();

    String connect(String ssid, String password, IWifiCallback callback);
    void disconnect(String networkRequestId);
}
