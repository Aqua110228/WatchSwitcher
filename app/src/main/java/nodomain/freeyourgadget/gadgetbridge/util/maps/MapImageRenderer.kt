package nodomain.freeyourgadget.gadgetbridge.util.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathDashPathEffect
import android.graphics.PointF
import android.os.Parcelable
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import org.mapsforge.core.graphics.TileBitmap
import org.mapsforge.core.model.Tile
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import kotlin.math.ceil
import kotlin.math.hypot
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import kotlinx.parcelize.Parcelize

/**
 * A single point of a GPS track, identified by latitude/longitude.
 * Sequence order is implicit in list position.
 */
@Parcelize
data class TrackPoint(val lat: Double, val lon: Double) : Parcelable

/**
 * Visual style for the position marker drawn on the rendered map.
 */
data class MarkerStyle(
    val color: Int = Color.RED,
    val radiusPx: Float = 6f,
    val strokeColor: Int = Color.WHITE,
    val strokeWidthPx: Float = 2f
)

/**
 * Visual style for the GPS track polyline drawn on the rendered map.
 */
data class TrackStyle(
    val color: Int = Color.BLUE,
    val strokeWidthPx: Float = 8f,
    val arrowSpacingPx: Float = 32f,
    val useArrows: Boolean = true
)

/**
 * Headless mapsforge map renderer, designed to run from a background Service
 * (no dependency on Activity/Fragment/MapView or any GUI lifecycle).
 *
 * The renderer keeps state across calls: geographic center, zoom level, output
 * dimensions, pivot position and drawing styles. This lets a caller update only
 * what changed (e.g. [moveTo] on every location update) and call [renderFrame]
 * repeatedly without re-allocating buffers each time.
 *
 * The pivot describes where the "subject" (e.g. the device's GPS position) sits
 * within the final image, expressed as a fraction [0..1] of the output dimensions:
 * (0.5, 0.5) is the geometric center, (0.5, 2/3) is horizontally centered and one
 * third of the way up from the bottom edge.
 */
class MapImageRenderer(
    context: Context,
    mapTheme: XmlRenderTheme? = null,
    trackStyle: TrackStyle? = null,
    val widthPx: Int = DEFAULT_WIDTH_PX,
    val heightPx: Int = DEFAULT_HEIGHT_PX,
    var zoomLevel: Byte = DEFAULT_ZOOM_LEVEL,
    val tileSize: Int = DEFAULT_TILE_SIZE,
    var pivotXFraction: Float = DEFAULT_PIVOT_X_FRACTION,
    var pivotYFraction: Float = DEFAULT_PIVOT_Y_FRACTION
) {
    private val appContext = context.applicationContext
    private val loader = MapDataLoader(appContext)
    private val mapDataStore: MultiMapDataStore = loader.loadMultiMapDataStore().dataStore

    private val resolvedTheme: RenderThemeFuture
    private val databaseRenderer: DatabaseRenderer
    private val displayModel = DisplayModel().apply { setFixedTileSize(tileSize) }

    var markerStyle: MarkerStyle = MarkerStyle()
    var trackStyle: TrackStyle = trackStyle ?: loader.resolveTrackStyle()
        set(value) {
            field = value
            applyTrackStyle(value)
        }

    var centerLat: Double = 0.0
        private set
    var centerLon: Double = 0.0
        private set

    // Size of the expanded working canvas, recomputed only when the pivot changes.
    private var expandedSide: Int = 0
    private var pivotXInFinal: Float = 0f
    private var pivotYInFinal: Float = 0f

    // The track to show on the map
    private var track: List<TrackPoint> = emptyList()

    // Bitmaps/canvases reused across renders to avoid repeated allocation.
    private lateinit var workingBitmap: Bitmap
    private lateinit var workingCanvas: Canvas
    private lateinit var rotatedBitmap: Bitmap
    private lateinit var rotatedCanvas: Canvas
    private lateinit var outputBitmap: Bitmap
    private lateinit var outputCanvas: Canvas

    private val markerPaintFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val markerPaintStroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val trackLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackArrowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    init {
        AndroidGraphicFactory.createInstance(appContext as GBApplication)
        databaseRenderer = DatabaseRenderer(mapDataStore, AndroidGraphicFactory.INSTANCE, null, null, false, false, null)
        val theme = mapTheme ?: loader.resolveTheme()
        resolvedTheme = RenderThemeFuture(AndroidGraphicFactory.INSTANCE, theme, displayModel)
        resolvedTheme.run()
        applyTrackStyle(this.trackStyle)
        recomputePivotGeometry()
    }

    /**
     * Recomputes the expanded working canvas size needed so that, at any rotation
     * angle, the final crop around the pivot never exposes empty (unrendered) area.
     * The canvas is a square centered on the pivot, sized as twice the distance
     * from the pivot to the farthest corner of the final output rectangle.
     */
    private fun recomputePivotGeometry() {
        pivotXInFinal = widthPx * pivotXFraction
        pivotYInFinal = heightPx * pivotYFraction

        val corners = listOf(
            0f to 0f, widthPx.toFloat() to 0f,
            0f to heightPx.toFloat(), widthPx.toFloat() to heightPx.toFloat()
        )
        val maxRadius = corners.maxOf { (cx, cy) ->
            hypot((cx - pivotXInFinal).toDouble(), (cy - pivotYInFinal).toDouble())
        }.toFloat()

        val newSide = ceil(2 * maxRadius).toInt()
        if (newSide != expandedSide) {
            expandedSide = newSide
            allocateBuffers()
        }
    }

    private fun allocateBuffers() {
        if (::workingBitmap.isInitialized) workingBitmap.recycle()
        if (::rotatedBitmap.isInitialized) rotatedBitmap.recycle()
        if (::outputBitmap.isInitialized) outputBitmap.recycle()

        workingBitmap = createBitmap(expandedSide, expandedSide)
        workingCanvas = Canvas(workingBitmap)
        rotatedBitmap = createBitmap(expandedSide, expandedSide)
        rotatedCanvas = Canvas(rotatedBitmap)
        outputBitmap = createBitmap(widthPx, heightPx)
        outputCanvas = Canvas(outputBitmap)
    }

    private fun applyTrackStyle(style: TrackStyle) {
        trackLinePaint.color = style.color
        trackLinePaint.strokeWidth = style.strokeWidthPx
        trackLinePaint.pathEffect = null

        trackArrowPaint.color = style.color
        trackArrowPaint.strokeWidth = style.strokeWidthPx
        trackArrowPaint.pathEffect = if (style.useArrows) {
            PathDashPathEffect(buildArrowStampPath(style.strokeWidthPx * 2f), style.arrowSpacingPx, 0f, PathDashPathEffect.Style.ROTATE)
        } else {
            null
        }
    }

    /** Moves the geographic center. Does not allocate; the working buffers are reused as-is. */
    fun moveTo(lat: Double, lon: Double) {
        centerLat = lat
        centerLon = lon
    }

    /** Sets the route polyline drawn on every subsequent [renderFrame] call. */
    fun setTrack(points: List<TrackPoint>) {
        track = points
    }

    /** Changes the pivot fraction (subject position within the final image) and reallocates buffers if needed. */
    fun setPivot(xFraction: Float, yFraction: Float) {
        pivotXFraction = xFraction
        pivotYFraction = yFraction
        recomputePivotGeometry()
    }

    /**
     * Absolute Mercator pixel coordinates of the top-left corner of the expanded
     * working canvas, given the current center, zoom and expanded canvas size.
     */
    private fun currentOrigin(): PointF {
        val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
        val centerPixelX = MercatorProjection.longitudeToPixelX(centerLon, mapSize)
        val centerPixelY = MercatorProjection.latitudeToPixelY(centerLat, mapSize)
        return PointF(
            (centerPixelX - expandedSide / 2.0).toFloat(),
            (centerPixelY - expandedSide / 2.0).toFloat()
        )
    }

    private fun renderTilesOnto(canvas: Canvas, bitmap: Bitmap, origin: PointF) {
        bitmap.eraseColor(Color.TRANSPARENT)

        val tileXMin = (origin.x / tileSize).toInt()
        val tileXMax = ((origin.x + expandedSide) / tileSize).toInt()
        val tileYMin = (origin.y / tileSize).toInt()
        val tileYMax = ((origin.y + expandedSide) / tileSize).toInt()

        for (tx in tileXMin..tileXMax) {
            for (ty in tileYMin..tileYMax) {
                val tile = Tile(tx, ty, zoomLevel, tileSize)
                val job = RendererJob(tile, mapDataStore, resolvedTheme, displayModel, 1f, false, false)
                val tileBitmap: TileBitmap = databaseRenderer.executeJob(job) ?: continue
                val androidBitmap = AndroidGraphicFactory.getBitmap(tileBitmap)
                val drawX = (tx * tileSize - origin.x)
                val drawY = (ty * tileSize - origin.y)
                canvas.drawBitmap(androidBitmap, drawX, drawY, null)
            }
        }
    }

    private fun drawMarkerOnto(canvas: Canvas, origin: PointF, lat: Double, lon: Double) {
        val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)
        val x = (MercatorProjection.longitudeToPixelX(lon, mapSize) - origin.x).toFloat()
        val y = (MercatorProjection.latitudeToPixelY(lat, mapSize) - origin.y).toFloat()

        markerPaintFill.color = markerStyle.color
        markerPaintStroke.color = markerStyle.strokeColor
        markerPaintStroke.strokeWidth = markerStyle.strokeWidthPx

        canvas.drawCircle(x, y, markerStyle.radiusPx, markerPaintFill)
        canvas.drawCircle(x, y, markerStyle.radiusPx, markerPaintStroke)
    }

    private fun buildArrowStampPath(sizePx: Float): Path {
        val lengthScale = sizePx * 0.6f
        val heightScale = sizePx * 0.75f
        return Path().apply {
            moveTo(-lengthScale, -heightScale)
            lineTo(lengthScale, 0f)
            lineTo(-lengthScale, heightScale)
        }
    }

    private fun drawTrackOnto(canvas: Canvas, origin: PointF, points: List<TrackPoint>) {
        if (points.size < 2) return
        val mapSize = MercatorProjection.getMapSize(zoomLevel, tileSize)

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = (MercatorProjection.longitudeToPixelX(point.lon, mapSize) - origin.x).toFloat()
            val y = (MercatorProjection.latitudeToPixelY(point.lat, mapSize) - origin.y).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, trackLinePaint)
        canvas.drawPath(path, trackArrowPaint)
    }

    /**
     * Renders a complete frame (map tiles + optional track + optional marker),
     * rotated by [bearingDegrees] using the Android [android.location.Location.getBearing]
     * convention (degrees clockwise from true north). The subject stays fixed at the
     * configured pivot position regardless of the rotation angle.
     *
     * Reuses internal buffers: the returned [Bitmap] is only valid until the next
     * call to this method. Copy it (e.g. `bitmap.copy(Bitmap.Config.ARGB_8888, false)`)
     * before handing it to any asynchronous consumer.
     *
     * @param bearingDegrees rotation angle, 0 = north-up.
     * @param drawMarkerAtCenter whether to draw a marker at the current center coordinates.
     * @return the rendered [widthPx] x [heightPx] bitmap.
     */
    fun renderFrame(
        bearingDegrees: Float = 0f,
        drawMarkerAtCenter: Boolean = false
    ): Bitmap {
        val origin = currentOrigin()

        renderTilesOnto(workingCanvas, workingBitmap, origin)
        drawTrackOnto(workingCanvas, origin, track)
        if (drawMarkerAtCenter) {
            drawMarkerOnto(workingCanvas, origin, centerLat, centerLon)
        }

        rotatedBitmap.eraseColor(Color.TRANSPARENT)
        rotatedCanvas.withRotation(-bearingDegrees, expandedSide / 2f, expandedSide / 2f) {
            drawBitmap(workingBitmap, 0f, 0f, null)
        }

        val cropX = (expandedSide / 2f - pivotXInFinal).toInt()
        val cropY = (expandedSide / 2f - pivotYInFinal).toInt()

        outputBitmap.eraseColor(Color.TRANSPARENT)
        outputCanvas.drawBitmap(rotatedBitmap, -cropX.toFloat(), -cropY.toFloat(), null)

        return outputBitmap
    }

    /** Releases all buffers and the underlying map data store. Call from e.g. Service.onDestroy. */
    fun close() {
        if (::workingBitmap.isInitialized) workingBitmap.recycle()
        if (::rotatedBitmap.isInitialized) rotatedBitmap.recycle()
        if (::outputBitmap.isInitialized) outputBitmap.recycle()
        resolvedTheme.decrementRefCount()
        mapDataStore.close()
    }

    companion object {
        const val DEFAULT_WIDTH_PX = 400
        const val DEFAULT_HEIGHT_PX = 400
        const val DEFAULT_ZOOM_LEVEL: Byte = 16
        const val DEFAULT_TILE_SIZE = 256
        const val DEFAULT_PIVOT_X_FRACTION = 0.5f
        const val DEFAULT_PIVOT_Y_FRACTION = 0.5f
    }
}
