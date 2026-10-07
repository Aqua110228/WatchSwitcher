/*  Copyright (C) 2024-2025 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.util.maps;

import android.content.Context;

import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Dimension;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.util.LatLongUtils;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;

public final class MapsManager {
    private static final Logger LOG = LoggerFactory.getLogger(MapsManager.class);

    public static final String PREF_MAPS_FOLDER = "maps_folder";
    public static final String PREF_TRACK_COLOR = "maps_track_color";
    public static final String PREF_MAP_THEME = "maps_theme";

    private final Context mContext;
    private final MapView mapView;
    private final MapDataLoader mapDataLoader;
    private Polyline polyline;

    private TileRendererLayer tileRendererLayer;
    private boolean tileRenderedAdded;

    private boolean isMapLoaded = false;

    public MapsManager(final Context context, final MapView mapView) {
        this.mContext = context;
        this.mapView = mapView;
        this.mapDataLoader = new MapDataLoader(context);
    }

    public void loadMaps() {
        if (tileRendererLayer != null) {
            mapView.getLayerManager().getLayers().remove(tileRendererLayer);
            tileRendererLayer.onDestroy();
            tileRendererLayer.getTileCache().purge();
            tileRendererLayer = null;
        }

        mapView.getModel().displayModel.setBackgroundColor(GBApplication.getWindowBackgroundColor(mapView.getContext()));

        isMapLoaded = false;

        AndroidGraphicFactory.createInstance(GBApplication.app());

        final MapLoadResult loadResult = mapDataLoader.loadMultiMapDataStore();
        isMapLoaded = loadResult.getAnyLoaded();

        final TileCache tileCache = AndroidUtil.createTileCache(
                mContext,
                "mapcache",
                mapView.getModel().displayModel.getTileSize(),
                1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor()
        );

        tileRendererLayer = new TileRendererLayer(
                tileCache,
                loadResult.getDataStore(),
                mapView.getModel().mapViewPosition,
                true,
                false,
                false,
                AndroidGraphicFactory.INSTANCE
        );

        tileRendererLayer.setXmlRenderTheme(mapDataLoader.resolveTheme());
        // Do not add the tile renderer layer before setting the bounding box in setTrack,
        // otherwise we load the entire map to memory and might crash with OOM
    }

    public boolean isMapLoaded() {
        return isMapLoaded;
    }

    public void onDestroy() {
        if (polyline != null) {
            mapView.getLayerManager().getLayers().remove(polyline);
            polyline.onDestroy();
            polyline = null;
        }

        if (tileRendererLayer != null) {
            if (tileRenderedAdded) {
                mapView.getLayerManager().getLayers().remove(tileRendererLayer);
            }
            tileRendererLayer.onDestroy();
            tileRendererLayer.getTileCache().purge();
            tileRendererLayer = null;
        }

        isMapLoaded = false;
    }

    private BoundingBox minimalBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        final LatLong center = new LatLong(minLat + (maxLat - minLat) / 2, minLon + (maxLon - minLon) / 2);
        final double minLatDistance = LatLongUtils.latitudeDistance(1000);
        final double minLonDistance = LatLongUtils.longitudeDistance(1000, center.latitude);
        if ((maxLat - minLat) < minLatDistance) {
            maxLat = center.latitude + minLatDistance/2;
            minLat = center.latitude - minLatDistance/2;
        }
        if ((maxLon - minLon) < minLonDistance) {
            maxLon = center.longitude + minLonDistance/2;
            minLon = center.longitude - minLonDistance/2;
        }

        return new BoundingBox(minLat, minLon, maxLat, maxLon);
    }

    public void setTrack(final List<? extends GPSCoordinate> trackPoints) {
        final Accumulator latitudeAccumulator = new Accumulator();
        final Accumulator longitudeAccumulator = new Accumulator();
        for (GPSCoordinate trackPoint : trackPoints) {
            latitudeAccumulator.add(trackPoint.getLatitude());
            longitudeAccumulator.add(trackPoint.getLongitude());
        }
        final double maxLat = latitudeAccumulator.getMax();
        final double minLat = latitudeAccumulator.getMin();
        final double maxLon = longitudeAccumulator.getMax();
        final double minLon = longitudeAccumulator.getMin();
        final LatLong center = new LatLong(minLat + (maxLat - minLat) / 2, minLon + (maxLon - minLon) / 2);

        final List<LatLong> points = trackPoints.stream()
                .map(p -> new LatLong(p.getLatitude(), p.getLongitude()))
                .collect(Collectors.toList());

        if (polyline == null) {
            final Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
            final int trackColor = mapDataLoader.resolveTrackColor();
            paint.setColor(trackColor);
            paint.setStrokeWidth(8);
            paint.setStyle(Style.STROKE);

            polyline = new Polyline(paint, AndroidGraphicFactory.INSTANCE);
            mapView.addLayer(polyline);
        }
        polyline.setPoints(points);

        mapView.setCenter(center);

        final byte zoom = LatLongUtils.zoomForBounds(
                new Dimension(mapView.getWidth(), mapView.getHeight()),
                minimalBoundingBox(minLat, minLon, maxLat, maxLon),
                mapView.getModel().displayModel.getTileSize()
        );
        mapView.setZoomLevel(zoom);

        if (!tileRenderedAdded) {
            mapView.getLayerManager().getLayers().add(0, tileRendererLayer);
            tileRenderedAdded = true;
        }
    }

    public void reload() {
        if (polyline != null) {
            final int trackColor = mapDataLoader.resolveTrackColor();
            polyline.getPaintStroke().setColor(trackColor);
            polyline.requestRedraw();
        }

        loadMaps();
    }
}
