/*  Copyright (C) 2024 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry;

public enum TouchConfigValue implements LabeledEntry {
    OFF(0x00, R.string.sony_button_mode_off),
    PLAY_PAUSE(0x01, R.string.moondrop_touch_action_play_pause),
    VOICE_ASSISTANT(0x03, R.string.pref_title_touch_voice_assistant), // oppo
    VOICE_ASSISTANT_REALME(0x04, R.string.pref_title_touch_voice_assistant),
    PREVIOUS(0x05, R.string.pref_media_previous),
    NEXT(0x06, R.string.pref_media_next),
    NOISE_CONTROL(0x08, R.string.moondrop_touch_action_anc_mode),
    GAME_MODE(0x11, R.string.prefs_game_mode),
    VOLUME_UP(0x0B, R.string.pref_media_volumeup),
    VOLUME_DOWN(0x0C, R.string.pref_media_volumedown),
    ;

    private final int code;
    private final int label;

    TouchConfigValue(final int code, @StringRes final int label) {
        this.code = code;
        this.label = label;
    }

    @Override
    @StringRes
    public int getLabel() {
        return label;
    }

    public int getCode() {
        return code;
    }

    public String getPrefId() {
        return this.name().toLowerCase();
    }

    @Nullable
    public static TouchConfigValue fromCode(final int code) {
        for (final TouchConfigValue param : TouchConfigValue.values()) {
            if (param.code == code) {
                return param;
            }
        }

        return null;
    }

    @Nullable
    public static TouchConfigValue fromPrefId(final String value) {
        for (final TouchConfigValue param : TouchConfigValue.values()) {
            if (param.name().equalsIgnoreCase(value)) {
                return param;
            }
        }

        return null;
    }
}
