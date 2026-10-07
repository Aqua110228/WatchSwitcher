package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsNoiseCancellingStrength implements RedmiBudsPreferenceEntry {
    BALANCED((byte) 0x00, R.string.redmi_buds_5_pro_anc_balanced),
    LIGHT((byte) 0x01, R.string.redmi_buds_5_pro_anc_light),
    DEEP((byte) 0x02, R.string.redmi_buds_5_pro_anc_deep),
    ADAPTIVE((byte) 0x03, R.string.pref_adaptive_noise_cancelling_title),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsNoiseCancellingStrength(final byte code, @StringRes final int label) {
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
