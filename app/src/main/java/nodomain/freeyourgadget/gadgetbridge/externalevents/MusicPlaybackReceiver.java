/*  Copyright (C) 2015-2024 andre, Andreas Shimokawa, Avamander, Carsten
    Pfeiffer, Daniele Gobbetti

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
package nodomain.freeyourgadget.gadgetbridge.externalevents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;

public class MusicPlaybackReceiver extends BroadcastReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(MusicPlaybackReceiver.class);
    private static MusicSpec lastMusicSpec = new MusicSpec();
    private static MusicStateSpec lastStateSpec = new MusicStateSpec();

    public int parseTime(String time) {
        // will accept "23", "1:23", "03:07:23"
        String[] segments = time.split(":");
        int result = 0;
        for (String element : segments) {
            result = result*60 + Integer.parseInt(element, 10);
        }
        // single number must be milliseconds
        if (segments.length == 1)
            return result / 1000;
        return result;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        /*
        Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            LOG.info(String.format("%s %s (%s)", key,
                    value != null ? value.toString() : "null", value != null ? value.getClass().getName() : "no class"));
        }
        */
        MusicSpec musicSpec = lastMusicSpec.copyOf();
        MusicStateSpec stateSpec = new MusicStateSpec(lastStateSpec);

        Bundle incomingBundle = intent.getExtras();

        if (incomingBundle == null) {
            LOG.warn("Not processing incoming null bundle.");
            return;
        }

        for (String key : incomingBundle.keySet()) {
            Object incoming = incomingBundle.get(key);
            if (incoming instanceof String && "artist".equals(key)) {
                musicSpec.setArtist((String) incoming);
            } else if (incoming instanceof String && "album".equals(key)) {
                musicSpec.setAlbum((String) incoming);
            } else if (incoming instanceof String && "track".equals(key)) {
                musicSpec.setTrack((String) incoming);
            } else if (incoming instanceof String && "title".equals(key) && musicSpec.getTrack() == null) {
                musicSpec.setTrack((String) incoming);
            } else if (incoming instanceof Integer && "duration".equals(key)) {
                musicSpec.setDuration((Integer) incoming / 1000);
            } else if (incoming instanceof Long && "duration".equals(key)) {
                musicSpec.setDuration(((Long) incoming).intValue() / 1000);
            } else if (incoming instanceof Integer && "position".equals(key)) {
                stateSpec.setPosition((Integer) incoming / 1000);
            } else if (incoming instanceof Long && "position".equals(key)) {
                stateSpec.setPosition(((Long) incoming).intValue() / 1000);
            } else if (incoming instanceof Boolean && "playing".equals(key)) {
                stateSpec.setState((byte) (((Boolean) incoming) ? MusicStateSpec.STATE_PLAYING : MusicStateSpec.STATE_PAUSED));
                stateSpec.setPlayRate((byte) (((Boolean) incoming) ? 100 : 0));
            } else if (incoming instanceof String && "duration".equals(key)) {
                musicSpec.setDuration(parseTime((String) incoming));
            } else if (incoming instanceof String && "trackno".equals(key)) {
                musicSpec.setTrackNr(Integer.parseInt((String) incoming));
            } else if (incoming instanceof String && "totaltrack".equals(key)) {
                musicSpec.setTrackCount(Integer.parseInt((String) incoming));
            } else if (incoming instanceof Integer && "pos".equals(key)) {
                stateSpec.setPosition((Integer) incoming);
            } else if (incoming instanceof Integer && "repeat".equals(key)) {
                if ((Integer) incoming > 0) {
                    stateSpec.setRepeat((byte) 1);
                } else {
                    stateSpec.setRepeat((byte) 0);
                }
            } else if (incoming instanceof Integer && "shuffle".equals(key)) {
                if ((Integer) incoming > 0) {
                    stateSpec.setShuffle((byte) 1);
                } else {
                    stateSpec.setShuffle((byte) 0);
                }
            }
        }

        if (!lastMusicSpec.equals(musicSpec)) {
            lastMusicSpec = musicSpec;
            LOG.info("Update Music Info: " + musicSpec.getArtist() + " / " + musicSpec.getAlbum() + " / " + musicSpec.getTrack());
            GBApplication.deviceService().onSetMusicInfo(musicSpec);
        } else {
            LOG.info("Got metadata changed intent, but nothing changed, ignoring.");
        }

        if (!lastStateSpec.equals(stateSpec)) {
            lastStateSpec = stateSpec;
            LOG.info("Update Music State: state=" + stateSpec.getState() + ", position= " + stateSpec.getPosition());
            GBApplication.deviceService().onSetMusicState(stateSpec);
        } else {
            LOG.info("Got state changed intent, but not enough has changed, ignoring.");
        }
    }
}
