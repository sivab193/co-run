package com.example.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.RoutePoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

enum class MapFocusMode {
    ME,
    PARTNER,
    BOTH
}

/**
 * Dark-styled OpenStreetMap (osmdroid) ColorMatrix filter that turns bright Mapnik tiles
 * into an athletic obsidian-slate dark map without requiring any paid tile provider or API key.
 */
object DarkMapStyle {
    fun createColorFilter(): ColorMatrixColorFilter {
        // Invert luminance, slightly cool & desaturate so bright orange/blue polylines pop cleanly.
        val invert = ColorMatrix(
            floatArrayOf(
                -0.88f, 0f, 0f, 0f, 232f,
                0f, -0.88f, 0f, 0f, 238f,
                0f, 0f, -0.82f, 0f, 245f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return ColorMatrixColorFilter(invert)
    }
}

private class RunnerDotOverlay(
    private var point: GeoPoint?,
    private val fillColor: Int
) : Overlay() {
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        alpha = 70
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    private val screenPt = android.graphics.Point()

    fun updatePoint(geoPoint: GeoPoint?) {
        point = geoPoint
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val gp = point ?: return
        val proj: Projection = mapView.projection
        proj.toPixels(gp, screenPt)
        val x = screenPt.x.toFloat()
        val y = screenPt.y.toFloat()
        canvas.drawCircle(x, y, 26f, haloPaint)
        canvas.drawCircle(x, y, 14f, borderPaint)
        canvas.drawCircle(x, y, 10f, corePaint)
    }
}

@Composable
fun DualRouteMapView(
    myPath: List<RoutePoint>,
    myCurrentLocation: RoutePoint?,
    partnerPath: List<RoutePoint>,
    partnerCurrentLocation: RoutePoint?,
    focusMode: MapFocusMode,
    focusRequestToken: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val myColorInt = AndroidColor.parseColor("#FF6B2C")
    val partnerColorInt = AndroidColor.parseColor("#38BDF8")

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(DarkMapStyle.createColorFilter())
            overlayManager.tilesOverlay.loadingBackgroundColor = AndroidColor.parseColor("#0B0D11")
            overlayManager.tilesOverlay.loadingLineColor = AndroidColor.parseColor("#1C2230")
            controller.setZoom(15.5)
        }
    }

    val myPolyline = remember {
        Polyline(mapView).apply {
            outlinePaint.color = myColorInt
            outlinePaint.strokeWidth = 11f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }

    val partnerPolyline = remember {
        Polyline(mapView).apply {
            outlinePaint.color = partnerColorInt
            outlinePaint.strokeWidth = 11f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }

    val myDotOverlay = remember { RunnerDotOverlay(null, myColorInt) }
    val partnerDotOverlay = remember { RunnerDotOverlay(null, partnerColorInt) }
    var hasInitialCentered by remember { mutableStateOf(false) }

    DisposableEffect(mapView) {
        mapView.overlays.clear()
        mapView.overlays.add(partnerPolyline)
        mapView.overlays.add(myPolyline)
        mapView.overlays.add(partnerDotOverlay)
        mapView.overlays.add(myDotOverlay)
        onDispose {
            mapView.onDetach()
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(myPath, myCurrentLocation, partnerPath, partnerCurrentLocation, focusMode, focusRequestToken) {
        val myGeoPoints = myPath.map { GeoPoint(it.lat, it.lng) }
        val partnerGeoPoints = partnerPath.map { GeoPoint(it.lat, it.lng) }

        myPolyline.setPoints(myGeoPoints)
        partnerPolyline.setPoints(partnerGeoPoints)

        val myHead = myCurrentLocation?.takeIf { it.lat != 0.0 || it.lng != 0.0 }
            ?.let { GeoPoint(it.lat, it.lng) } ?: myGeoPoints.lastOrNull()
        val partnerHead = partnerCurrentLocation?.takeIf { it.lat != 0.0 || it.lng != 0.0 }
            ?.let { GeoPoint(it.lat, it.lng) } ?: partnerGeoPoints.lastOrNull()

        myDotOverlay.updatePoint(myHead)
        partnerDotOverlay.updatePoint(partnerHead)

        when (focusMode) {
            MapFocusMode.ME -> {
                if (myHead != null) {
                    if (!hasInitialCentered || focusRequestToken > 0) {
                        mapView.controller.setZoom(16.5)
                        hasInitialCentered = true
                    }
                    mapView.controller.animateTo(myHead)
                }
            }
            MapFocusMode.PARTNER -> {
                if (partnerHead != null) {
                    mapView.controller.setZoom(16.5)
                    mapView.controller.animateTo(partnerHead)
                    hasInitialCentered = true
                }
            }
            MapFocusMode.BOTH -> {
                val combined = buildList {
                    addAll(myGeoPoints)
                    addAll(partnerGeoPoints)
                    if (myHead != null) add(myHead)
                    if (partnerHead != null) add(partnerHead)
                }
                if (combined.size >= 2) {
                    val bbox = BoundingBox.fromGeoPointsSafe(combined)
                    mapView.post {
                        try {
                            mapView.zoomToBoundingBox(bbox.increaseByScale(1.3f), true, 90)
                        } catch (_: Exception) {
                        }
                    }
                    hasInitialCentered = true
                } else if (combined.size == 1) {
                    mapView.controller.setZoom(16.0)
                    mapView.controller.animateTo(combined.first())
                    hasInitialCentered = true
                }
            }
        }
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
