package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs;

import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum RedmiBudsGestureAction implements RedmiBudsPreferenceEntry {
    NONE((byte) 0x08, R.string.none),
    PLAY_PAUSE((byte) 0x01, R.string.pref_media_playpause),
    PREVIOUS_TRACK((byte) 0x02, R.string.pref_media_previous),
    NEXT_TRACK((byte) 0x03, R.string.pref_media_next),
    VOLUME_UP((byte) 0x04, R.string.pref_media_volumeup),
    VOLUME_DOWN((byte) 0x05, R.string.pref_media_volumedown),
    ;

    private final byte code;
    @StringRes
    private final int label;

    RedmiBudsGestureAction(final byte code, @StringRes final int label) {
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
