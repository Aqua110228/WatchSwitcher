/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.externalevents.gps;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowLocationManager;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Each start registers a fresh listener with the phone, and each listener forwards every fix to
 * the device, so a provider that is started twice must not leave two listeners behind.
 */
public class GBLocationServiceTest extends TestBase {
    private GBLocationService service;
    private ShadowLocationManager locationManager;
    private GBDevice band;

    @Before
    public void setUpService() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        locationManager = shadowOf((LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE));
        locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        locationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        service = new GBLocationService(getContext());
        band = createDummyGDevice("00:00:00:00:00:61");
    }

    @Test
    public void startingAProviderTwiceKeepsOneListener() {
        start(band, GBLocationProviderType.GPS);
        start(band, GBLocationProviderType.GPS);

        assertEquals(1, listeners(LocationManager.GPS_PROVIDER));
    }

    @Test
    public void providersOfDifferentTypesRunSideBySide() {
        start(band, GBLocationProviderType.GPS);
        start(band, GBLocationProviderType.NETWORK);
        start(band, GBLocationProviderType.NETWORK);

        assertEquals(1, listeners(LocationManager.GPS_PROVIDER));
        assertEquals(1, listeners(LocationManager.NETWORK_PROVIDER));
    }

    @Test
    public void eachDeviceGetsItsOwnProvider() {
        start(band, GBLocationProviderType.GPS);
        start(createDummyGDevice("00:00:00:00:00:62"), GBLocationProviderType.GPS);

        assertEquals(2, listeners(LocationManager.GPS_PROVIDER));
    }

    @Test
    public void stopEndsEveryProviderOfTheDevice() {
        start(band, GBLocationProviderType.GPS);
        start(band, GBLocationProviderType.NETWORK);
        stop(band);

        assertEquals(0, listeners(LocationManager.GPS_PROVIDER));
        assertEquals(0, listeners(LocationManager.NETWORK_PROVIDER));
    }

    private void start(final GBDevice device, final GBLocationProviderType type) {
        final Intent intent = new Intent(GBLocationService.ACTION_START);
        intent.putExtra(GBDevice.EXTRA_DEVICE, device);
        intent.putExtra(GBLocationService.EXTRA_TYPE, type.name());
        intent.putExtra(GBLocationService.EXTRA_INTERVAL, 1000);
        service.onReceive(getContext(), intent);
    }

    private void stop(final GBDevice device) {
        final Intent intent = new Intent(GBLocationService.ACTION_STOP);
        intent.putExtra(GBDevice.EXTRA_DEVICE, device);
        service.onReceive(getContext(), intent);
    }

    private int listeners(final String provider) {
        return locationManager.getLocationUpdateListeners(provider).size();
    }
}
