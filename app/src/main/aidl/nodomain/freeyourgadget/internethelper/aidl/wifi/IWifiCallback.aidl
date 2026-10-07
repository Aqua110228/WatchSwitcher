package nodomain.freeyourgadget.internethelper.aidl.wifi;

oneway interface IWifiCallback {
    void onAvailable(String networkRequestId);
    void onUnavailable(String networkRequestId, String msg);
    void onLost(String networkRequestId);
}
