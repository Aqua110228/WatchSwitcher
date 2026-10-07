package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundCycle;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundMode;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsEqualizerPreset;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsLongGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsNoiseCancellingStrength;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsTransparencyStrength;

public class RedmiBuds3ProCoordinator extends AbstractRedmiBudsCoordinator {
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_redmi_buds_3_pro;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("Redmi Buds 3 Pro");
    }

    @NonNull
    @Override
    public List<RedmiBudsAmbientSoundMode> getAmbientSoundModes() {
        return List.of(
            RedmiBudsAmbientSoundMode.OFF,
            RedmiBudsAmbientSoundMode.NOISE_CANCELLING,
            RedmiBudsAmbientSoundMode.TRANSPARENCY
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsNoiseCancellingStrength> getNoiseCancellingStrengths() {
        return List.of(
            RedmiBudsNoiseCancellingStrength.BALANCED,
            RedmiBudsNoiseCancellingStrength.LIGHT,
            RedmiBudsNoiseCancellingStrength.DEEP,
            RedmiBudsNoiseCancellingStrength.ADAPTIVE
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsTransparencyStrength> getTransparencyStrengths() {
        return List.of(
            RedmiBudsTransparencyStrength.REGULAR,
            RedmiBudsTransparencyStrength.VOICE
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsEqualizerPreset> getEqualizerPresets() {
        return List.of(
            RedmiBudsEqualizerPreset.STANDARD,
            RedmiBudsEqualizerPreset.TREBLE,
            RedmiBudsEqualizerPreset.BASS,
            RedmiBudsEqualizerPreset.VOICE
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
            RedmiBudsLongGestureAction.VOICE_ASSISTANT,
            RedmiBudsLongGestureAction.AMBIENT_SOUND_CONTROL
        );
    }

    @NonNull
    @Override
    public List<RedmiBudsAmbientSoundCycle> getAmbientSoundCycles() {
        return List.of(RedmiBudsAmbientSoundCycle.values());
    }

    @Override
    public boolean getSupportsWearingDetection() {
        return true;
    }

    @Override
    public boolean getSupportsAutoAnswer() {
        return true;
    }

    @Override
    public boolean getSupportsDoubleConnection() {
        return true;
    }
}
