package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerPreset;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsLongGestureAction;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class RedmiBuds6ActiveCoordinator extends AbstractRedmiBudsCoordinator {
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_redmi_buds_6_active;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Redmi Buds 6 Active");
    }

    @NonNull
    @Override
    public List<RedmiBudsEqualizerPreset> getEqualizerPresets() {
        return List.of(
            RedmiBudsEqualizerPreset.STANDARD,
            RedmiBudsEqualizerPreset.TREBLE,
            RedmiBudsEqualizerPreset.BASS,
            RedmiBudsEqualizerPreset.VOICE,
            RedmiBudsEqualizerPreset.VOLUME
        );
    }

    @Override
    public boolean supportsFindDevice(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsFindDevicePerEarbud(@NonNull final GBDevice device) {
        return true;
    }

    @NonNull
    @Override
    public List<RedmiBudsGestureAction> getSingleTapActions() {
        return List.of(
            RedmiBudsGestureAction.NONE,
            RedmiBudsGestureAction.PLAY_PAUSE,
            RedmiBudsGestureAction.PREVIOUS_TRACK,
            RedmiBudsGestureAction.NEXT_TRACK,
            RedmiBudsGestureAction.VOLUME_UP,
            RedmiBudsGestureAction.VOLUME_DOWN
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsGestureAction> getTapActions() {
        return List.of(
            RedmiBudsGestureAction.PLAY_PAUSE,
            RedmiBudsGestureAction.PREVIOUS_TRACK,
            RedmiBudsGestureAction.NEXT_TRACK,
            RedmiBudsGestureAction.VOLUME_UP,
            RedmiBudsGestureAction.VOLUME_DOWN
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsLongGestureAction> getLongPressActions() {
        return List.of(
            RedmiBudsLongGestureAction.NONE,
            RedmiBudsLongGestureAction.VOICE_ASSISTANT
        );
    }
}
