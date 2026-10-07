package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsAmbientSoundCycle implements RedmiBudsPreferenceEntry {
    NOISE_CANCELLING_OFF((byte) 0x03, R.string.redmi_buds_5_pro_combo_anc_off),
    TRANSPARENCY_OFF((byte) 0x05, R.string.redmi_buds_5_pro_combo_transparency_off),
    NOISE_CANCELLING_TRANSPARENCY((byte) 0x06, R.string.redmi_buds_5_pro_combo_anc_transparency),
    ALL((byte) 0x07, R.string.redmi_buds_5_pro_combo_all),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsAmbientSoundCycle(final byte code, @StringRes final int label) {
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
