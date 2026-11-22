package com.example.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Base64
import com.example.map.DarkMapStyle
import com.example.model.DistanceUnit
import com.example.model.RoutePoint
import com.example.model.RunHistoryEntry
import com.example.model.RunnerRunSummary
import com.example.model.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * Renders the end-of-run dual-map shareable PNG card (1080x1350) directly via Android Canvas:
 * - Header with both runner names and date ("Sivaganesh & Shukirthan — 22 Nov 2026")
 * - Two side-by-side dark OpenStreetMap panels (each fitted to that runner's city & route)
 * - Stats under each panel in the viewer's chosen unit (distance, time, avg pace, top speed)
 */
object ShareCardGenerator {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1350
    private const val TILE_SIZE = 256

    private val ORANGE = Color.parseColor("#FF6B2C")
    private val BLUE = Color.parseColor("#38BDF8")
    private val BG_DARK = Color.parseColor("#0B0D11")
    private val PANEL_DARK = Color.parseColor("#141821")
    private val CARD_BORDER = Color.parseColor("#283142")
    private val INK_WHITE = Color.parseColor("#F8FAFC")
    private val INK_MUTED = Color.parseColor("#94A3B8")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    suspend fun renderShareCard(
        entry: RunHistoryEntry,
        unit: DistanceUnit
    ): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)

        val leftRunner = entry.runners.getOrNull(0)
        val rightRunner = entry.runners.getOrNull(1)

        drawHeader(canvas, leftRunner, rightRunner, entry.createdAt)

        val panelTop = 215f
        val panelHeight = 560f
        val marginX = 42f
        val gapX = 24f
        val panelWidth = (CARD_WIDTH - marginX * 2 - gapX) / 2f

        val leftRect = RectF(marginX, panelTop, marginX + panelWidth, panelTop + panelHeight)
        val rightRect = RectF(
            marginX + panelWidth + gapX,
            panelTop,
            CARD_WIDTH - marginX,
            panelTop + panelHeight
        )

        drawMapPanel(canvas, leftRect, leftRunner, ORANGE, "You")
        drawMapPanel(canvas, rightRect, rightRunner, BLUE, "Partner")

        val statsTop = panelTop + panelHeight + 28f
        drawStatsColumn(canvas, leftRect.left, statsTop, panelWidth, leftRunner, ORANGE, unit)
        drawStatsColumn(canvas, rightRect.left, statsTop, panelWidth, rightRunner, BLUE, unit)

        drawFooter(canvas, entry.roomCode)
        bitmap
    }

    fun encodeCompactBase64(bitmap: Bitmap): String {
        val scaledWidth = 540
        val scaledHeight = (bitmap.height * (scaledWidth.toFloat() / bitmap.width)).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
        if (scaled != bitmap) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeBase64Bitmap(encoded: String): Bitmap? {
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                Color.parseColor("#111622"),
                BG_DARK,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // Subtle dual top accent bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        accentPaint.color = ORANGE
        canvas.drawRect(0f, 0f, CARD_WIDTH / 2f, 8f, accentPaint)
        accentPaint.color = BLUE
        canvas.drawRect(CARD_WIDTH / 2f, 0f, CARD_WIDTH.toFloat(), 8f, accentPaint)
    }

    private fun drawHeader(
        canvas: Canvas,
        left: RunnerRunSummary?,
        right: RunnerRunSummary?,
        createdAt: Long
    ) {
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 24f
            letterSpacing = 0.22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("RUN TOGETHER", CARD_WIDTH / 2f, 74f, tagPaint)

        val namesTitle = when {
            left != null && right != null -> "${left.displayName} & ${right.displayName}"
            left != null -> left.displayName
            else -> "Run Together"
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_WHITE
            textSize = if (namesTitle.length > 26) 40f else 48f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(namesTitle, CARD_WIDTH / 2f, 134f, titlePaint)

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(UnitFormatter.dateFormatted(createdAt), CARD_WIDTH / 2f, 178f, datePaint)
    }

    private fun drawMapPanel(
        canvas: Canvas,
        rect: RectF,
        runner: RunnerRunSummary?,
        accentColor: Int,
        fallbackLabel: String
    ) {
        val radius = 28f
        val clipPath = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        // Panel dark fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL_DARK }
        canvas.drawRect(rect, fillPaint)

        val points = runner?.points.orEmpty().filter { it.lat != 0.0 || it.lng != 0.0 }
        if (points.isNotEmpty()) {
            drawOsmTilesAndRoute(canvas, rect, points, accentColor)
        } else {
            drawEmptyMapPlaceholder(canvas, rect, runner?.displayName ?: fallbackLabel)
        }

        // Top runner pill badge
        val labelText = runner?.displayName ?: fallbackLabel
        drawRunnerPill(canvas, rect.left + 18f, rect.top + 18f, labelText, accentColor)

        canvas.restoreToCount(saveCount)

        // Crisp border with accent tint
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }

    private fun drawOsmTilesAndRoute(
        canvas: Canvas,
        rect: RectF,
        points: List<RoutePoint>,
        accentColor: Int
    ) {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        for (p in points) {
            minLat = min(minLat, p.lat)
            maxLat = max(maxLat, p.lat)
            minLng = min(minLng, p.lng)
            maxLng = max(maxLng, p.lng)
        }

        val centerLat = (minLat + maxLat) / 2.0
        val centerLng = (minLng + maxLng) / 2.0
        val zoom = computeFitZoom(minLat, maxLat, minLng, maxLng, rect.width(), rect.height())

        val centerWorldX = lngToWorldX(centerLng, zoom)
        val centerWorldY = latToWorldY(centerLat, zoom)

        // Draw dark grid fallback first in case any OSM tile fails offline
        drawSubtleGrid(canvas, rect)

        // Fetch and composite OSM tiles covering rect
        val halfW = rect.width() / 2.0
        val halfH = rect.height() / 2.0
        val minTileX = floor((centerWorldX - halfW) / TILE_SIZE).toInt()
        val maxTileX = floor((centerWorldX + halfW) / TILE_SIZE).toInt()
        val minTileY = floor((centerWorldY - halfH) / TILE_SIZE).toInt()
        val maxTileY = floor((centerWorldY + halfH) / TILE_SIZE).toInt()
        val maxTileIndex = (1 shl zoom) - 1

        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = DarkMapStyle.createColorFilter()
        }

        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                if (ty < 0 || ty > maxTileIndex) continue
                val wrappedX = ((tx % (maxTileIndex + 1)) + (maxTileIndex + 1)) % (maxTileIndex + 1)
                val tileBitmap = fetchOsmTile(zoom, wrappedX, ty) ?: continue
                val drawLeft = (rect.centerX() + (tx * TILE_SIZE - centerWorldX)).toFloat()
                val drawTop = (rect.centerY() + (ty * TILE_SIZE - centerWorldY)).toFloat()
                val dst = RectF(drawLeft, drawTop, drawLeft + TILE_SIZE, drawTop + TILE_SIZE)
                canvas.drawBitmap(tileBitmap, null, dst, tilePaint)
                tileBitmap.recycle()
            }
        }

        // Subtle dark vignette for route readability
        val scrimPaint = Paint().apply {
            color = Color.argb(55, 11, 13, 17)
        }
        canvas.drawRect(rect, scrimPaint)

        // Project and draw route polyline
        val routePath = Path()
        var firstX = 0f
        var firstY = 0f
        var lastX = 0f
        var lastY = 0f

        points.forEachIndexed { idx, pt ->
            val sx = (rect.centerX() + (lngToWorldX(pt.lng, zoom) - centerWorldX)).toFloat()
            val sy = (rect.centerY() + (latToWorldY(pt.lat, zoom) - centerWorldY)).toFloat()
            if (idx == 0) {
                routePath.moveTo(sx, sy)
                firstX = sx
                firstY = sy
            } else {
                routePath.lineTo(sx, sy)
            }
            lastX = sx
            lastY = sy
        }

        if (points.size >= 2) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                alpha = 90
                style = Paint.Style.STROKE
                strokeWidth = 18f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(routePath, glowPaint)

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = 8.5f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(routePath, linePaint)
        }

        // Start dot (white ring) and End dot (filled accent)
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK_WHITE }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawCircle(firstX, firstY, 9f, whitePaint)
        canvas.drawCircle(firstX, firstY, 5f, accentPaint)

        canvas.drawCircle(lastX, lastY, 12f, whitePaint)
        canvas.drawCircle(lastX, lastY, 8f, accentPaint)
    }

    private fun drawEmptyMapPlaceholder(canvas: Canvas, rect: RectF, label: String) {
        drawSubtleGrid(canvas, rect)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("No GPS route", rect.centerX(), rect.centerY() - 8f, textPaint)
        textPaint.textSize = 22f
        textPaint.alpha = 170
        canvas.drawText(label, rect.centerX(), rect.centerY() + 28f, textPaint)
    }

    private fun drawSubtleGrid(canvas: Canvas, rect: RectF) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2636")
            strokeWidth = 1.5f
        }
        val step = 48f
        var x = rect.left + step
        while (x < rect.right) {
            canvas.drawLine(x, rect.top, x, rect.bottom, gridPaint)
            x += step
        }
        var y = rect.top + step
        while (y < rect.bottom) {
            canvas.drawLine(rect.left, y, rect.right, y, gridPaint)
            y += step
        }
    }

    private fun drawRunnerPill(
        canvas: Canvas,
        left: Float,
        top: Float,
        name: String,
        accentColor: Int
    ) {
        val displayName = name.take(16)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bounds = Rect()
        textPaint.getTextBounds(displayName, 0, displayName.length, bounds)

        val pillW = bounds.width() + 58f
        val pillH = 44f
        val pillRect = RectF(left, top, left + pillW, top + pillH)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(215, 11, 13, 17)
        }
        canvas.drawRoundRect(pillRect, 22f, 22f, bgPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawCircle(left + 22f, top + pillH / 2f, 7f, dotPaint)

        canvas.drawText(displayName, left + 40f, top + 30f, textPaint)
    }

    private fun drawStatsColumn(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        runner: RunnerRunSummary?,
        accentColor: Int,
        unit: DistanceUnit
    ) {
        val cardRect = RectF(left, top, left + width, top + 430f)
        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL_DARK }
        canvas.drawRoundRect(cardRect, 24f, 24f, cardBg)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CARD_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(cardRect, 24f, 24f, borderPaint)

        val padX = left + 26f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 20f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bigMetricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 68f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 26f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val distanceStr = if (runner != null) {
            UnitFormatter.distanceValue(runner.distanceMeters, unit)
        } else {
            "--"
        }
        val distUnit = UnitFormatter.distanceUnitLabel(unit)

        canvas.drawText("DISTANCE", padX, top + 46f, labelPaint)
        canvas.drawText(distanceStr, padX, top + 114f, bigMetricPaint)
        val distWidth = bigMetricPaint.measureText(distanceStr)
        canvas.drawText(" $distUnit", padX + distWidth, top + 112f, unitPaint)

        // Divider
        val divPaint = Paint().apply { color = CARD_BORDER; strokeWidth = 2f }
        canvas.drawLine(padX, top + 140f, left + width - 26f, top + 140f, divPaint)

        // Time
        canvas.drawText("TIME", padX, top + 182f, labelPaint)
        val timeStr = if (runner != null) UnitFormatter.durationFormatted(runner.elapsedMs) else "--:--"
        canvas.drawText(timeStr, padX, top + 226f, subValuePaint)

        // Avg Pace
        canvas.drawText("AVG PACE", padX, top + 278f, labelPaint)
        val paceStr = if (runner != null) {
            UnitFormatter.paceFormatted(runner.distanceMeters, runner.elapsedMs, unit)
        } else {
            "--:--"
        }
        canvas.drawText(paceStr, padX, top + 322f, subValuePaint)

        // Top Speed
        canvas.drawText("TOP SPEED", padX, top + 372f, labelPaint)
        val topSpeedStr = if (runner != null) {
            "${UnitFormatter.speedValue(runner.topSpeedMps, unit)} ${UnitFormatter.speedUnitLabel(unit)}"
        } else {
            "--"
        }
        canvas.drawText(topSpeedStr, padX, top + 414f, subValuePaint)
    }

    private fun drawFooter(canvas: Canvas, roomCode: String) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK_MUTED
            textSize = 24f
            letterSpacing = 0.12f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val text = if (roomCode.isNotBlank()) "ROOM $roomCode  •  RUN TOGETHER" else "RUN TOGETHER"
        canvas.drawText(text, CARD_WIDTH / 2f, CARD_HEIGHT - 46f, footerPaint)
    }

    private fun computeFitZoom(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        panelW: Float,
        panelH: Float
    ): Int {
        val usableW = panelW * 0.72
        val usableH = panelH * 0.72
        for (z in 17 downTo 10) {
            val dx = kotlin.math.abs(lngToWorldX(maxLng, z) - lngToWorldX(minLng, z))
            val dy = kotlin.math.abs(latToWorldY(minLat, z) - latToWorldY(maxLat, z))
            if (dx <= usableW && dy <= usableH) {
                return z
            }
        }
        return 10
    }

    private fun lngToWorldX(lng: Double, zoom: Int): Double {
        return ((lng + 180.0) / 360.0) * TILE_SIZE * 2.0.pow(zoom)
    }

    private fun latToWorldY(lat: Double, zoom: Int): Double {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val rad = Math.toRadians(clampedLat)
        val mercN = ln(tan(rad) + 1.0 / cos(rad))
        return (1.0 - mercN / PI) / 2.0 * TILE_SIZE * 2.0.pow(zoom)
    }

    private fun fetchOsmTile(zoom: Int, x: Int, y: Int): Bitmap? {
        return try {
            val url = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "RunTogetherAndroid/1.0 (OpenStreetMap route snapshot)")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }
}
