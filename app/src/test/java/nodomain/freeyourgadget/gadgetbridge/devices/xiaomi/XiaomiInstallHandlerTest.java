package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.install.InstallActivity;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand10Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8ActiveCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand9Coordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class XiaomiInstallHandlerTest extends TestBase {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testResolutionMatches() throws IOException {
        final InstallActivity activity = validate(new MiBand9Coordinator(), buildWatchface(192, 490));

        verify(activity).setInfoText(getContext().getString(R.string.watchface_install_resolution_matches));
        verify(activity).setInstallConfirmation(getContext().getString(R.string.watchface_install_confirm_risk));
        verify(activity).setInstallEnabled(true);
    }

    @Test
    public void testResolutionMatchesScreen() throws IOException {
        final InstallActivity activity = validate(new MiBand10Coordinator(), buildWatchface(212, 520));

        verify(activity).setInfoText(getContext().getString(R.string.watchface_install_resolution_matches));
        verify(activity).setInstallConfirmation(getContext().getString(R.string.watchface_install_confirm_risk));
        verify(activity).setInstallEnabled(true);
    }

    @Test
    public void testResolutionDoesNotMatch() throws IOException {
        final InstallActivity activity = validate(new MiBand8Coordinator(), buildWatchface(230, 328));

        verify(activity).setInfoText(getContext().getString(R.string.fwinstaller_file_not_compatible_to_device));
        verify(activity, never()).setInstallConfirmation(any());
        verify(activity).setInstallEnabled(false);
    }

    @Test
    public void testPreviewFitsScreenWithKnownSizes() throws IOException {
        final InstallActivity activity = validate(new MiBand8Coordinator(), buildWatchface(120, 306));

        verify(activity).setInfoText(getContext().getString(R.string.watchface_install_aspect_ratio_matches));
        verify(activity).setInstallConfirmation(getContext().getString(R.string.watchface_install_confirm_risk));
        verify(activity).setInstallEnabled(true);
    }

    @Test
    public void testPreviewMissingWithKnownSizes() throws IOException {
        final byte[] watchface = buildWatchface(122, 310);
        watchface[0x20] = 0;

        final InstallActivity activity = validate(new MiBand8Coordinator(), watchface);

        verify(activity).setInfoText(getContext().getString(R.string.watchface_install_compatibility_unknown));
        verify(activity).setInstallConfirmation(getContext().getString(R.string.watchface_install_confirm_risk));
        verify(activity).setInstallEnabled(true);
    }

    @Test
    public void testPreviewFitsScreen() throws IOException {
        final InstallActivity activity = validate(new MiBand8ActiveCoordinator(), buildWatchface(110, 205));

        verify(activity).setInfoText(getContext().getString(R.string.watchface_install_aspect_ratio_matches));
        verify(activity).setInstallConfirmation(getContext().getString(R.string.watchface_install_confirm_risk));
        verify(activity).setInstallEnabled(true);
    }

    @Test
    public void testPreviewLargerThanScreen() throws IOException {
        final InstallActivity activity = validate(new MiBand8ActiveCoordinator(), buildWatchface(192, 490));

        verify(activity).setInfoText(getContext().getString(R.string.fwinstaller_file_not_compatible_to_device));
        verify(activity, never()).setInstallConfirmation(any());
        verify(activity).setInstallEnabled(false);
    }

    @Test
    public void testPreviewAspectRatioDoesNotMatch() throws IOException {
        final InstallActivity activity = validate(new MiBand8ActiveCoordinator(), buildWatchface(160, 160));

        verify(activity).setInfoText(getContext().getString(R.string.fwinstaller_file_not_compatible_to_device));
        verify(activity, never()).setInstallConfirmation(any());
        verify(activity).setInstallEnabled(false);
    }

    @Test
    public void testPreviewMissing() throws IOException {
        final byte[] watchface = buildWatchface(192, 490);
        watchface[0x20] = 0;

        final InstallActivity activity = validate(new MiBand8ActiveCoordinator(), watchface);

        verify(activity).setInfoText(getContext().getString(R.string.watchface_install_compatibility_unknown));
        verify(activity).setInstallConfirmation(getContext().getString(R.string.watchface_install_confirm_risk));
        verify(activity).setInstallEnabled(true);
    }

    private static byte[] buildWatchface(final int previewWidth, final int previewHeight) {
        return XiaomiFWHelperTest.buildWatchfaceV2(0, 0, previewWidth, previewHeight, new byte[0]);
    }

    private InstallActivity validate(final DeviceCoordinator coordinator, final byte[] watchface) throws IOException {
        final File file = tempFolder.newFile();
        Files.write(file.toPath(), watchface);

        final GBDevice device = mock(GBDevice.class);
        when(device.isInitialized()).thenReturn(true);
        when(device.getDeviceCoordinator()).thenReturn(coordinator);

        final InstallActivity activity = mock(InstallActivity.class);
        new XiaomiInstallHandler(Uri.fromFile(file), getContext()).validateInstallation(activity, device);
        return activity;
    }
}
