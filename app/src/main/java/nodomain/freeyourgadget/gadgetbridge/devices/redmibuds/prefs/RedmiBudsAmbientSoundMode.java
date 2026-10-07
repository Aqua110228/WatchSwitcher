package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsAmbientSoundMode implements RedmiBudsPreferenceEntry {
    OFF((byte) 0x00, R.string.sony_ambient_sound_off),
    NOISE_CANCELLING((byte) 0x01, R.string.sony_ambient_sound_noise_cancelling),
    TRANSPARENCY((byte) 0x02, R.string.prefs_active_noise_cancelling_transparency),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsAmbientSoundMode(final byte code, @StringRes final int label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public byte getCode() {
        return code;
    }

    @Override
    public int getLabel() {
        return label;
    }
}
