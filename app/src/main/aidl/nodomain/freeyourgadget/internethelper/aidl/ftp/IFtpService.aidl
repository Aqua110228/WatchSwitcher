package nodomain.freeyourgadget.internethelper.aidl.ftp;

import android.os.ParcelFileDescriptor;
import nodomain.freeyourgadget.internethelper.aidl.ftp.IFtpCallback;

interface IFtpService {
    int version();

    String createClient(IFtpCallback callback);
    void destroyClient(String client);

    void connect(String client, String host, int port, String networkRequestId);
    void disconnect(String client);

    void login(String client, String username, String password);

    void list(String client, String path);
    void upload(String client, in ParcelFileDescriptor src, String remoteDest, long size);
    void download(String client, String remoteSrc, in ParcelFileDescriptor dest);
    void delete(String client, String path);
    void mkdirs(String client, String path);
}
