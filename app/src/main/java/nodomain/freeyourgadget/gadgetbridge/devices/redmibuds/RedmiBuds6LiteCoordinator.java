/*  Copyright (C) 2026 Mahmoud Adel

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
package nodomain.freeyourgadget.gadgetbridge.devices.redmibuds;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundCycle;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsAmbientSoundMode;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsGestureAction;
import nodomain.freeyourgadget.gadgetbridge.devices.redmibuds.prefs.RedmiBudsLongGestureAction;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class RedmiBuds6LiteCoordinator extends AbstractRedmiBudsCoordinator {
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_redmi_buds_6_lite;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile(".*Redmi Buds 6 Lite.*");
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
    public List<RedmiBudsAmbientSoundMode> getAmbientSoundModes() {
        return List.of(RedmiBudsAmbientSoundMode.values());
    }

    @Override
    public boolean getSupportsCustomEqualizer() {
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
    public RedmiBudsGestureAction getDefaultDoubleTapAction() {
        return RedmiBudsGestureAction.NONE;
    }

    @NonNull
    @Override
    public RedmiBudsGestureAction getDefaultTripleTapActionLeft() {
        return RedmiBudsGestureAction.NONE;
    }

    @NonNull
    @Override
    public RedmiBudsGestureAction getDefaultTripleTapActionRight() {
        return RedmiBudsGestureAction.NONE;
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
    public boolean getSupportsAutoAnswer() {
        return true;
    }
}
