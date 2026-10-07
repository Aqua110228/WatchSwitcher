package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsEqualizerPreset implements RedmiBudsPreferenceEntry {
    STANDARD((byte) 0x00, R.string.redmi_buds_5_pro_equalizer_preset_standard),
    BALANCED((byte) 0x15, R.string.redmi_buds_5_pro_anc_balanced),
    TREBLE((byte) 0x06, R.string.redmi_buds_5_pro_equalizer_preset_treble),
    BASS((byte) 0x05, R.string.redmi_buds_5_pro_equalizer_preset_bass),
    VOICE((byte) 0x01, R.string.redmi_buds_5_pro_equalizer_preset_voice),
    VOLUME((byte) 0x07, R.string.redmi_buds_6_active_equalizer_preset_volume),
    CUSTOM((byte) 0x0A, R.string.redmi_buds_5_pro_equalizer_preset_custom),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsEqualizerPreset(final byte code, @StringRes final int label) {
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
