/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.oceanTides

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import com.example.android.wearable.oceanTides.data.watchface.ColorStyleIdAndResourceIds
import com.example.android.wearable.oceanTides.data.watchface.TideLocationResourceIds
import com.example.android.wearable.oceanTides.data.watchface.TideRenderArea
import com.example.android.wearable.oceanTides.data.watchface.WatchFaceColorPalette.Companion.convertToWatchFaceColorPalette
import com.example.android.wearable.oceanTides.data.watchface.WatchFaceData
import com.example.android.wearable.oceanTides.utils.*
import java.lang.Exception
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.*
import kotlinx.coroutines.*

// Default for how long each frame is displayed at expected frame rate.
private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

/**
 * Renders watch face via data in Room database. Also, updates watch face state based on setting
 * changes by user via [userStyleRepository.addUserStyleListener()].
 */
class OceanTidesCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT
) {
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Represents all data needed to render the watch face. All value defaults are constants. Only
    // six values are changeable by the user (color scheme, sunrise location (lat and lon), and tide
    // (region and spot)). Those dynamic values are saved in the watch face APIs and we update those
    // here (in the renderer) through a Kotlin Flow.
    private var watchFaceData: WatchFaceData = WatchFaceData()

    // Converts resource ids into Colors and ComplicationDrawable.
    private var watchFaceColors = convertToWatchFaceColorPalette(
        context,
        watchFaceData.activeColorStyle,
        watchFaceData.ambientColorStyle
    )

    private val framePaint = Paint().apply {
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        isAntiAlias = true
    }

    private val tidePaint = Paint().apply {
        isAntiAlias = true
    }

    private val primaryPaint = Paint().apply {
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
        textScaleX = 1.01f
    }

    // Default size of watch face drawing area, that is, a no size rectangle. Will be replaced with
    // valid dimensions from the system.
    private var currentWatchFaceSize = Rect(0, 0, 0, 0)
    private var size = 0f // Width of the watch face
    private var prevZdt: ZonedDateTime = ZonedDateTime.now()
    private val tideList = Vector<Pair<ZonedDateTime, Pair<Float, Boolean>>>()
    private var activeTides = arrayOfNulls<Pair<Float, Pair<Float, Boolean>>>(6)
    private var nextTideIdx = 0
    private var initialized = false

    init {
        scope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                updateWatchFaceData(userStyle)
            }
        }
    }

    /*
     * Triggered when the user makes changes to the watch face through the settings activity. The
     * function is called by a flow.
     */
    private fun updateWatchFaceData(userStyle: UserStyle) {
        Log.d(TAG, "updateWatchFace(): $userStyle")

        var newWatchFaceData: WatchFaceData = watchFaceData
        var tideRegionIdx = 0
        var tideSpotIdx = 0
        // Loops through user style and applies new values to watchFaceData.
        for (options in userStyle) {
            when (options.key.id.toString()) {
                COLOR_STYLE_SETTING -> {
                    val listOption = options.value as
                        UserStyleSetting.ListUserStyleSetting.ListOption

                    newWatchFaceData = newWatchFaceData.copy(
                        activeColorStyle = ColorStyleIdAndResourceIds.getColorStyleConfig(
                            listOption.id.toString()
                        )
                    )
                }
                SUNRISE_LAT_STYLE_SETTING -> {
                    val doubleValue =
                        options.value as UserStyleSetting.DoubleRangeUserStyleSetting.DoubleRangeOption
                    newWatchFaceData = newWatchFaceData.copy(
                        sunriseLat = doubleValue.value.toFloat()
                    )
                }
                SUNRISE_LON_STYLE_SETTING -> {
                    val doubleValue =
                        options.value as UserStyleSetting.DoubleRangeUserStyleSetting.DoubleRangeOption
                    newWatchFaceData = newWatchFaceData.copy(
                        sunriseLon = doubleValue.value.toFloat()
                    )
                }
                TIDE_REGION_STYLE_SETTING -> {
                    val longValue =
                        options.value as UserStyleSetting.LongRangeUserStyleSetting.LongRangeOption
                    tideRegionIdx = longValue.value.toInt()
                }
                TIDE_SPOT_STYLE_SETTING -> {
                    val longValue =
                        options.value as UserStyleSetting.LongRangeUserStyleSetting.LongRangeOption
                    tideSpotIdx = longValue.value.toInt()
                }
            }
        }
        val region = TideLocationResourceIds.getTideRegion(tideRegionIdx)
        if (region.locations.size <= tideSpotIdx) {
            tideSpotIdx = 0
        }
        newWatchFaceData = newWatchFaceData.copy(
            tideRegion = region,
            tideRegionIdx = tideRegionIdx.toLong(),
            tideSpot = Pair(region.locations[tideSpotIdx], region.ids[tideSpotIdx]),
            tideSpotIdx = tideSpotIdx.toLong()
        )
        // Only updates if something changed.
        if (watchFaceData != newWatchFaceData) {
            watchFaceData = newWatchFaceData

            // Recreates Color and ComplicationDrawable from resource ids.
            watchFaceColors = convertToWatchFaceColorPalette(
                context,
                watchFaceData.activeColorStyle,
                watchFaceData.ambientColorStyle
            )

            // Applies the user chosen complication color scheme changes. ComplicationDrawables for
            // each of the styles are defined in XML so we need to replace the complication's
            // drawables.
            for ((_, complication) in complicationSlotsManager.complicationSlots) {
                ComplicationDrawable.getDrawable(
                    context,
                    watchFaceColors.complicationStyleDrawableId
                )?.let {
                    (complication.renderer as CanvasComplicationDrawable).drawable = it
                }
            }
            initialized = false
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        scope.cancel("OceanTidesCanvasRenderer scope clear() request")
        super.onDestroy()
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)

        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val backgroundColor = if (renderParameters.drawMode == DrawMode.AMBIENT) {
            watchFaceColors.ambientBackgroundColor
        } else {
            watchFaceColors.activeBackgroundColor
        }
        if (!initialized) {
            initializeWatchFaceState(zonedDateTime, bounds)
        }
        // TODO: Pull out all calculations and only run them when necessary
        updateSize(bounds)
        canvas.drawColor(backgroundColor)
        // This is to try to save battery by rendering less. Not sure if I can get it working
//        if (zonedDateTime.second != prevZdt.second) {
//
//        }
        if (zonedDateTime.minute != prevZdt.minute) {
            if (nextTideIdx == -1 || Duration.between(zonedDateTime,
                                                      tideList[nextTideIdx].first).isNegative) {
                nextTideIdx = findNextTide(zonedDateTime)
            }
            updateActiveTides(zonedDateTime)
        }
        if (zonedDateTime.hour != prevZdt.hour) {
            watchFaceData.sunriseTime = calculateSunriseAndSunset(zonedDateTime,
                                                                  watchFaceData.sunriseLat,
                                                                  watchFaceData.sunriseLon,
                                                                  true)
            watchFaceData.sunsetTime = calculateSunriseAndSunset(zonedDateTime,
                                                                 watchFaceData.sunriseLat,
                                                                 watchFaceData.sunriseLon,
                                                                 false)
            watchFaceData.moonPhase = calculateMoonPhase(zonedDateTime)

        }
        prevZdt = zonedDateTime

        // Draw time and date even if ambient mode
        drawTime(canvas, zonedDateTime)
        drawDayAndDate(canvas, zonedDateTime)

        if (renderParameters.drawMode == DrawMode.INTERACTIVE &&
            renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)
        ) {
            // Draw when interactive is enabled. (Not ambient)
            drawGrid(canvas, watchFaceData.tideArea)
            drawSunriseAndSunsetTime(canvas, watchFaceData.sunriseTime, watchFaceData.sunsetTime)
            //            drawBatteryPercent(canvas)
            //            drawStepCount(canvas)
            drawDaylightBlock(canvas,
                              zonedDateTime,
                              watchFaceData.tideArea,
                              watchFaceData.sunriseTime,
                              watchFaceData.sunsetTime)
            drawTides(canvas, watchFaceData.tideArea)
            drawTideAxes(canvas, watchFaceData.tideArea)
            drawTideInfo(canvas, zonedDateTime)
            drawMoonFrame(canvas)
            drawMoonPhase(canvas, watchFaceData.moonPhase)
            drawFrame(canvas)
            drawLogos(canvas)
            drawComplications(canvas, zonedDateTime)
        }

    }

    private fun initializeWatchFaceState(zdt: ZonedDateTime, bounds: Rect) {
        val success = if (zdt.monthValue == 12 && zdt.dayOfMonth > 15) {
            parseTides(watchFaceData.tideSpot.second, zdt.year, zdt.year + 1)
        } else if (zdt.monthValue == 1 && zdt.dayOfMonth < 5) {
            parseTides(watchFaceData.tideSpot.second, zdt.year - 1, zdt.year)
        } else {
            parseTides(watchFaceData.tideSpot.second, zdt.year)
        }
        nextTideIdx = findNextTide(zdt)
        updateActiveTides(zdt)
        updateSize(bounds)
        watchFaceData.tideArea.lowerLeftPx = floatArrayOf(0f, 0.3f * size)
        watchFaceData.tideArea.upperRightPx = floatArrayOf(0.8f * size, 0.1f * size)
        watchFaceData.tideArea.minHour = -4f
        watchFaceData.tideArea.maxHour = 16f
        watchFaceData.tideArea.minTide = floor(watchFaceData.tideArea.minTide)
        watchFaceData.tideArea.maxTide = ceil(watchFaceData.tideArea.maxTide)
        watchFaceData.tideArea.updateValues()
        watchFaceData.sunriseTime = calculateSunriseAndSunset(zdt,
                                                              watchFaceData.sunriseLat,
                                                              watchFaceData.sunriseLon,
                                                              true)
        watchFaceData.sunsetTime = calculateSunriseAndSunset(zdt,
                                                             watchFaceData.sunriseLat,
                                                             watchFaceData.sunriseLon,
                                                             false)
        watchFaceData.moonPhase = calculateMoonPhase(zdt)
        initialized = success
    }

    private fun parseTides(stationID: String, year: Int, year2: Int = 0): Boolean {
        try {
            var badYear = false
            var yearTemp = year
            if (yearTemp < 2022) {
                // This is kind of a hack. When changing the watch face settings, the year is
                // 2020 or 1998.
                // I don't have any tides for that year. So if that year comes up,
                // just load some tides and wait flag to reload correctly
                badYear = true
                yearTemp = 2022
            }
            val fileName = "tides_${stationID}_${yearTemp}.txt"
            val fileContent = context.assets.readFile(fileName)
            val lines = fileContent.split("\n")
            Log.d(TAG, "Tides Loading: ${lines[3]} ${lines[4]}")
            tideList.clear()
            watchFaceData.tideArea.minTide = 0f
            watchFaceData.tideArea.maxTide = 0f
            for (i in 20 until lines.size - 1) {
                val line = lines[i].split(" ", "\t")
                // [Date, Day, time, height_ft, , height_cm, , , H/L]
                val date = line[0].split("/")
                val time = line[2].split(":")
                val timestamp: ZonedDateTime = ZonedDateTime.of(date[0].toInt(),
                                                                date[1].toInt(),
                                                                date[2].toInt(),
                                                                time[0].toInt(),
                                                                time[1].toInt(),
                                                                0,
                                                                0,
                                                                ZoneId.of("UTC"))
                val tide: Float = line[3].toFloat()
                if (tide > watchFaceData.tideArea.maxTide) {
                    watchFaceData.tideArea.maxTide = tide
                } else if (tide < watchFaceData.tideArea.minTide) {
                    watchFaceData.tideArea.minTide = tide
                }
                val highLow: Boolean = line[8].contains("H")
                tideList.add(Pair(timestamp, Pair(tide, highLow)))
            }
            if (year2 != 0) {
                Log.d(TAG, "tides loading second year...")
                val fileName2 = "tides_${stationID}_${year2}.txt"
                val fileContent2 = context.assets.readFile(fileName2)
                val lines2 = fileContent2.split("\n")
                for (i in 20 until lines2.size - 1) {
                    val line = lines2[i].split(" ", "\t")
                    // [Date, Day, time, height_ft, , height_cm, , , H/L]
                    val date = line[0].split("/")
                    val time = line[2].split(":")
                    val timestamp: ZonedDateTime = ZonedDateTime.of(date[0].toInt(),
                                                                    date[1].toInt(),
                                                                    date[2].toInt(),
                                                                    time[0].toInt(),
                                                                    time[1].toInt(),
                                                                    0,
                                                                    0,
                                                                    ZoneId.of("UTC"))
                    val tide: Float = line[3].toFloat()
                    val highLow: Boolean = line[8].contains("H")
                    tideList.add(Pair(timestamp, Pair(tide, highLow)))
                }
            }
            return !badYear
        } catch (e: Exception) {
            Log.d(TAG, "tides loading exception: $e")
            return false
        }
    }

    private fun findNextTide(zdt: ZonedDateTime): Int {
        for (i in 0 until tideList.size) {
            if (!Duration.between(zdt, tideList[i].first).isNegative) {
                return i
            }
        }
        return -1
    }

    private fun updateActiveTides(zdt: ZonedDateTime) {
        if (nextTideIdx <= 2) {
            if (nextTideIdx <= 0) {
                activeTides[0] = Pair(-16f, Pair(-1f, false))
                activeTides[1] = Pair(-8f, Pair(5f, true))
                activeTides[2] = Pair(0f, Pair(1f, false))
                activeTides[3] = Pair(8f, Pair(3f, true))
                activeTides[4] = Pair(16f, Pair(-1f, false))
                activeTides[5] = Pair(24f, Pair(4f, true))
                nextTideIdx = 0
            }
            initialized = false

        } else {
            for (i in 0..5) {
                val tideIdx = nextTideIdx + i - 2
                val time = Duration.between(zdt, tideList[tideIdx].first).seconds.toFloat() / 3600f
                val tideEntry: Pair<Float, Pair<Float, Boolean>> =
                    Pair(time, tideList[tideIdx].second)
                activeTides[i] = tideEntry
            }
        }
    }


    // All calculations
    private fun updateSize(bounds: Rect) {
        if (currentWatchFaceSize != bounds || size != bounds.width().toFloat()) {
            currentWatchFaceSize = bounds
            size = bounds.width().toFloat()
        }
    }

    // ----- All drawing functions -----
    private fun drawComplications(canvas: Canvas, zonedDateTime: ZonedDateTime) {
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.enabled) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    private fun drawDaylightBlock(canvas: Canvas,
                                  zdt: ZonedDateTime,
                                  tA: TideRenderArea,
                                  sunriseTime: Float,
                                  sunsetTime: Float) {
        //draw daylight rectangle
        val daylightHours = (sunsetTime - sunriseTime + 24f).mod(24f)
        val sunriseDiff = (zdt.hour.toFloat() + zdt.minute.toFloat() / 60.0f) - sunriseTime
        tidePaint.strokeCap = Paint.Cap.SQUARE
        tidePaint.color = watchFaceColors.activeDaylightColor
        tidePaint.alpha = 120
        val daylightBox = RectF()
        daylightBox.bottom = tA.lowerLeftPx[1]
        daylightBox.left = tA.time0px - sunriseDiff * tA.hourUnit
        daylightBox.top = tA.upperRightPx[1]
        daylightBox.right = daylightBox.left + daylightHours * tA.hourUnit

        val nextDaylightBox = RectF()
        nextDaylightBox.bottom = tA.lowerLeftPx[1]
        nextDaylightBox.left = tA.time0px - (sunriseDiff - 24) * tA.hourUnit
        nextDaylightBox.top = tA.upperRightPx[1]
        nextDaylightBox.right = tA.time0px + nextDaylightBox.left + daylightHours * tA.hourUnit

        canvas.drawRect(daylightBox, tidePaint)
        canvas.drawRect(nextDaylightBox, tidePaint)
    }

    private fun drawTides(canvas: Canvas, tA: TideRenderArea) {

        //draw tide grid
        tidePaint.color = watchFaceColors.activeTideColor
        tidePaint.alpha = 255
        tidePaint.style = Paint.Style.FILL

        val tidePath = Path()
        //draw tide
        try {
            tidePath.moveTo(tA.lowerLeftPx[0], tA.tide0px)
            tidePath.lineTo(tA.time0px + activeTides[0]!!.first * tA.hourUnit,
                            tA.tide0px - activeTides[0]!!.second.first * tA.footUnit)
            for (i in 0 until 5) {
                tidePath.cubicTo(tA.time0px + (activeTides[i]!!.first + activeTides[i + 1]!!.first) / 2 * tA.hourUnit,
                                 tA.tide0px + (activeTides[i]!!.second.first * tA.footUnit),
                                 tA.time0px + (activeTides[i]!!.first + activeTides[i + 1]!!.first) / 2 * tA.hourUnit,
                                 tA.tide0px + (activeTides[i + 1]!!.second.first * tA.footUnit),
                                 tA.time0px + (activeTides[i + 1]!!.first * tA.hourUnit),
                                 tA.tide0px + (activeTides[i + 1]!!.second.first * tA.footUnit))
            }
            tidePath.lineTo(tA.upperRightPx[0], tA.tide0px)
            tidePath.close()
            canvas.drawPath(tidePath, tidePaint)

        } catch (e: Exception) {
            Log.d("this", "tide exception: $e")
        }

        val cleanUpRect = RectF()
        cleanUpRect.left = tA.upperRightPx[0]
        cleanUpRect.bottom = tA.lowerLeftPx[1]
        cleanUpRect.right = size
        cleanUpRect.top = tA.upperRightPx[1]
        gridPaint.style = Paint.Style.FILL
        gridPaint.color = watchFaceColors.activeBackgroundColor
        canvas.drawRect(cleanUpRect, gridPaint)
        canvas.drawText(watchFaceData.tideSpot.first, 0.25f * size, 0.095f * size, textPaint)
    }

    private fun drawTideAxes(canvas: Canvas, tA: TideRenderArea) {
        val tideAxesPath = Path()
        framePaint.color = watchFaceColors.activeFrameColor
        framePaint.strokeWidth = watchFaceData.standardFrameWidth * size
        tideAxesPath.moveTo(tA.time0px, tA.lowerLeftPx[1])
        tideAxesPath.lineTo(tA.time0px, tA.upperRightPx[1])
        tideAxesPath.moveTo(tA.lowerLeftPx[0], tA.tide0px)
        tideAxesPath.lineTo(tA.upperRightPx[0], tA.tide0px)
        canvas.drawPath(tideAxesPath, framePaint)
    }

    private fun drawTideInfo(canvas: Canvas, zdt: ZonedDateTime) {
        val tidePair = tideList[nextTideIdx]
        val nextTideZdt = tidePair.first.withZoneSameInstant(zdt.zone)
        val nextTideHeight = tidePair.second.first
        val nextTideHigh = tidePair.second.second

        textPaint.textSize = .08f * size
        val hourMinuteFormatter = DateTimeFormatter.ofPattern("hh:mm")
        val hourMinuteString = nextTideZdt.format(hourMinuteFormatter)
        canvas.drawText(hourMinuteString, 0.16f * size, 0.38f * size, textPaint)
        val amPmFormatter = DateTimeFormatter.ofPattern("a")
        val amPmString = nextTideZdt.format(amPmFormatter)[0]
        textPaint.textSize = .05f * size
        canvas.drawText(amPmString.toString(), 0.36f * size, 0.38f * size, textPaint)

        textPaint.textSize = 0.08f * size
        val tideHeightString = if (nextTideHeight > 0 && nextTideHeight < 10) {
            nextTideHeight.format(2)
        } else {
            nextTideHeight.format(1)
        }
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(tideHeightString, 0.58f * size, 0.38f * size, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 0.05f * size
        canvas.drawText("FT", 0.585f * size, 0.38f * size, textPaint)

        // Tide indicator
        val arrowPath = generateArrowPath(0.145f * size,
                                          0.38f * size,
                                          0.055f * size,
                                          0.01f * size,
                                          nextTideHigh)
        primaryPaint.color = watchFaceColors.activePrimaryColor
        primaryPaint.style = Paint.Style.FILL
        canvas.drawPath(arrowPath, primaryPaint)
        primaryPaint.style = Paint.Style.STROKE
        canvas.drawPath(arrowPath, primaryPaint)
    }

    private fun drawMoonFrame(canvas: Canvas) {
        val moonFrameBackground = Path()
        framePaint.strokeWidth = watchFaceData.standardFrameWidth * size
        moonFrameBackground.addCircle(0.8f * size,
                                      0.2f * size - framePaint.strokeWidth / 2,
                                      0.1f * size,
                                      Path.Direction.CCW)
        framePaint.color = watchFaceColors.activeBackgroundColor
        framePaint.style = Paint.Style.FILL
        canvas.drawPath(moonFrameBackground, framePaint)
        framePaint.color = watchFaceColors.activeFrameColor
        framePaint.style = Paint.Style.STROKE
        canvas.drawPath(moonFrameBackground, framePaint)
    }

    private fun drawMoonPhase(canvas: Canvas, moonPhase: Float) {
        val moonR = 0.09f * size
        val moonD = moonR * 4f / 3f
        //distance for bezier curves
        primaryPaint.style = Paint.Style.FILL
        val moonCx = 0.8f * size
        val moonCy = 0.2f * size - primaryPaint.strokeWidth / 2

        val moonPhasePath = Path()

        //Moon phase is a double between 0 and 1. 0 == New moon. then waxes to full moon at 0.5. then wanes to new moon at 1
        if (moonPhase <= 0.5) {
            val curveX = (-moonPhase + 0.25f) * moonD / 0.25f + moonCx
            val curveY1 = abs(-moonPhase + 0.25f) * moonR / 0.25f + moonCy
            val curveY2 = -abs(-moonPhase + 0.25f) * moonR / 0.25f + moonCy
            moonPhasePath.moveTo(moonCx, moonCy + moonR)
            moonPhasePath.cubicTo(moonCx + moonD, moonCy + moonR,
                                  moonCx + moonD, moonCy - moonR,
                                  moonCx, moonCy - moonR)
            moonPhasePath.cubicTo(curveX, curveY2,
                                  curveX, curveY1, moonCx, moonCy + moonR)
        } else {
            val curveX = (-(moonPhase - 0.5f) + 0.25f) * moonD / 0.25f + moonCx
            val curveY1 = (abs(-(moonPhase - 0.5) + 0.25) * moonR / 0.25 + moonCy).toFloat()
            val curveY2 = (-abs(-(moonPhase - 0.5f) + 0.25f) * moonR / 0.25f + moonCy)
            moonPhasePath.moveTo(moonCx, moonCy + moonR)
            moonPhasePath.cubicTo(moonCx - moonD, moonCy + moonR,
                                  moonCx - moonD, moonCy - moonR, moonCx, moonCy - moonR)
            moonPhasePath.cubicTo(curveX, curveY2,
                                  curveX, curveY1, moonCx, moonCy + moonR)
        }
        primaryPaint.color = (watchFaceColors.activePrimaryColor)
        canvas.drawPath(moonPhasePath, primaryPaint)
    }

    private fun drawBatteryPercent(canvas: Canvas) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
            context.registerReceiver(null, iFilter)
        }
        val batteryPct: Int? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            (level * 100 / scale.toFloat()).toInt()
        }
        val batPctString = "$batteryPct%"
        textPaint.textSize = .05f * size
        val circle = Path()
        circle.addCircle(centerX, centerY, .44f * size, Path.Direction.CCW)
        canvas.drawTextOnPath(batPctString, circle, (centerX) + 1.652f * size, 0f, textPaint)

        // Arc
        val batArc = Path()
        val batteryPercent = batteryPct!!.toFloat()
        val fullBatteryAngle = 72.5f
        val fullBatteryReturnAngleDiff = 11f
        val batteryPctAngle = batteryPercent / 100f * fullBatteryAngle
        var batteryReturnAngle = batteryPctAngle - fullBatteryReturnAngleDiff
        batArc.moveTo(0.5f * size, size)
        val arrayArc1 = calcBezierArc(0.5f * size, 0.5f * size, 0.5f * size, size, -batteryPctAngle)
        batArc.cubicTo(arrayArc1[0],
                       arrayArc1[1],
                       arrayArc1[2],
                       arrayArc1[3],
                       arrayArc1[4],
                       arrayArc1[5])
        val width = 0.047f * size - (watchFaceData.standardFrameWidth * size / 2f)
        val xPointRaw = arrayArc1[4] - width * sin(batteryPctAngle * Math.PI / 180).toFloat()
        val yPointRaw = arrayArc1[5] - width * cos(batteryPctAngle * Math.PI / 180).toFloat()
        if (batteryPercent > 98f) {
            batteryReturnAngle -= 1.5f
        }
        val xPoint = if (batteryPercent > 97f) {
            0.93f * size
        } else {
            xPointRaw
        }
        val yPoint = if (batteryPercent > 97f) {
            .65f * size
        } else if (batteryPercent > 15) {
            yPointRaw
        } else {
            (0.93f + (watchFaceData.standardFrameWidth / 2) + (0.017f * batteryPercent / 15f)) * size
        }
        batArc.lineTo(xPoint, yPoint)
        if (batteryReturnAngle > 0) {
            val arrayArc2 =
                calcBezierArc(0.5f * size, 0.5f * size, xPoint, yPoint, batteryReturnAngle)
            batArc.cubicTo(arrayArc2[0],
                           arrayArc2[1],
                           arrayArc2[2],
                           arrayArc2[3],
                           arrayArc2[4],
                           arrayArc2[5])
        }
        batArc.lineTo(0.5f * size, (0.93f + watchFaceData.standardFrameWidth / 2) * size)
        batArc.lineTo(0.5f * size, size)
        primaryPaint.style = Paint.Style.FILL
        if (batteryPercent <= 15) {
            primaryPaint.color = watchFaceColors.activeLowBatteryColor
        }
        canvas.drawPath(batArc, primaryPaint)
        primaryPaint.color = watchFaceColors.activePrimaryColor
    }

    private fun drawStepCount(canvas: Canvas) {
        // TODO: Get These Values for reals
        val stepCount = 10000
        val stepGoal = 6000
        val stepPct = min(stepCount.toFloat() / stepGoal.toFloat(), 1f)
        val stepCountString = stepCount.toString()
        val numDigitsInStepCount = stepCountString.length
        textPaint.textSize = .05f * size
        val circle = Path()
        circle.addCircle(centerX, centerY, .44f * size, Path.Direction.CCW)
        val stepCountOffset =
            (centerX) + 1.47f * size - (numDigitsInStepCount * textPaint.textSize * .5f)
        canvas.drawTextOnPath(stepCountString, circle, stepCountOffset, 0f, textPaint)

        // Arc
        val stepArc = Path()
        val fullStepsAngle = 72.5f
        val fullStepsReturnAngleDiff = 10.8f
        val stepPctAngle = stepPct * fullStepsAngle
        var stepReturnAngle = stepPctAngle - fullStepsReturnAngleDiff
        stepArc.moveTo(0.5f * size, size)
        val arrayArc1 = calcBezierArc(0.5f * size, 0.5f * size, 0.5f * size, size, stepPctAngle)
        stepArc.cubicTo(arrayArc1[0],
                        arrayArc1[1],
                        arrayArc1[2],
                        arrayArc1[3],
                        arrayArc1[4],
                        arrayArc1[5])
        val width = 0.047f * size - (watchFaceData.standardFrameWidth * size / 2f)
        val xPointRaw = arrayArc1[4] + width * sin(stepPctAngle * Math.PI / 180).toFloat()
        val yPointRaw = arrayArc1[5] - width * cos(stepPctAngle * Math.PI / 180).toFloat()
        if (stepPct > .98f) {
            stepReturnAngle -= 1.7f
        }
        val xPoint = if (stepPct > 0.97f) {
            0.07f * size
        } else {
            xPointRaw
        }
        val yPoint = if (stepPct > 0.97f) {
            .65f * size
        } else if (stepPct > 0.15) {
            yPointRaw
        } else {
            (0.93f + (watchFaceData.standardFrameWidth / 2) + (0.017f * stepPct / .15f)) * size
        }
        stepArc.lineTo(xPoint, yPoint)
        if (stepReturnAngle > 0) {
            val arrayArc2 =
                calcBezierArc(0.5f * size, 0.5f * size, xPoint, yPoint, -stepReturnAngle)
            stepArc.cubicTo(arrayArc2[0],
                            arrayArc2[1],
                            arrayArc2[2],
                            arrayArc2[3],
                            arrayArc2[4],
                            arrayArc2[5])
        }
        stepArc.lineTo(0.5f * size, (0.93f + watchFaceData.standardFrameWidth / 2) * size)
        stepArc.lineTo(0.5f * size, size)
        primaryPaint.style = Paint.Style.FILL
        canvas.drawPath(stepArc, primaryPaint)
    }

    private fun drawSunriseAndSunsetTime(canvas: Canvas,
                                         sunriseTime: Float,
                                         sunsetTime: Float) {
        val hourRise = (sunriseTime.toInt() + 23) % 12 + 1
        val hourSet = (sunsetTime.toInt() + 23) % 12 + 1
        val amPmRise = if (sunriseTime >= 12) {
            "P"
        } else {
            "A"
        }
        val amPmSet = if (sunsetTime >= 12) {
            "P"
        } else {
            "A"
        }
        val minuteRise = ((sunriseTime).mod(1.0f) * 60f).toInt()
        val minuteSet = ((sunsetTime).mod(1.0f) * 60f).toInt()
        val hourRiseString = hourRise.toString().padStart(2, '0')
        val hourSetString = hourSet.toString().padStart(2, '0')
        val minuteRiseString = minuteRise.toString().padStart(2, '0')
        val minuteSetString = minuteSet.toString().padStart(2, '0')
        val sunriseString = "$hourRiseString:$minuteRiseString"
        val sunsetString = "$hourSetString:$minuteSetString"
        textPaint.textSize = .05f * size
        canvas.drawText(sunriseString, 0.82f * size, 0.36f * size, textPaint)
        canvas.drawText(sunsetString, 0.82f * size, 0.43f * size, textPaint)
        textPaint.textSize = .03f * size
        canvas.drawText(amPmRise, 0.94f * size, 0.36f * size, textPaint)
        canvas.drawText(amPmSet, 0.94f * size, 0.43f * size, textPaint)
    }

    private fun drawDayAndDate(canvas: Canvas, zdt: ZonedDateTime) {
        val dateFormatter = DateTimeFormatter.ofPattern("MM.dd.yy")
        val dateString = zdt.format(dateFormatter)
        val dayOfWeekInt = zdt.dayOfWeek.value
        var dayOfWeekString = ""
        when (dayOfWeekInt) {
            1 -> dayOfWeekString = "MON"
            2 -> dayOfWeekString = "TUE"
            3 -> dayOfWeekString = "WED"
            4 -> dayOfWeekString = "THU"
            5 -> dayOfWeekString = "FRI"
            6 -> dayOfWeekString = "SAT"
            7 -> dayOfWeekString = "SUN"
        }
        textPaint.textSize = .1f * size
        canvas.drawText(dateString, 0.14f * size, 0.765f * size, textPaint)
        canvas.drawText(dayOfWeekString, 0.65f * size, 0.765f * size, textPaint)
    }

    private fun drawTime(canvas: Canvas,
                         zonedDateTime: ZonedDateTime) {
        val drawAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        textPaint.color = if (drawAmbient) {
            watchFaceColors.ambientPrimaryColor
        } else {
            watchFaceColors.activePrimaryColor
        }
        textPaint.textSize = .28f * size
        val hourMinuteFormatter = DateTimeFormatter.ofPattern("hh:mm")
        val hourMinuteString = zonedDateTime.format(hourMinuteFormatter)
        canvas.drawText(hourMinuteString, 0.03f * size, 0.63f * size, textPaint)
        val amPmFormatter = DateTimeFormatter.ofPattern("a")
        val amPmString = zonedDateTime.format(amPmFormatter)
        textPaint.textSize = .07f * size
        canvas.drawText(amPmString, 0.775f * size, 0.51f * size, textPaint)
        if (!drawAmbient) {
            drawSeconds(canvas, zonedDateTime, false)
        }
    }

    private fun drawSeconds(canvas: Canvas, zonedDateTime: ZonedDateTime, blitSecond: Boolean) {
        textPaint.textSize = .14f * size
        val left = 0.75f * size
        val bottom = 0.63f * size
        if (blitSecond) {
            primaryPaint.color = watchFaceColors.activeBackgroundColor
            primaryPaint.style = Paint.Style.FILL
            val secondBlit =
                RectF(left, bottom - 0.11f * size, left + 0.17f * size, bottom + 0.01f * size)
            canvas.drawRect(secondBlit, primaryPaint)
            primaryPaint.color = watchFaceColors.activePrimaryColor
        }
        val secondFormatter = DateTimeFormatter.ofPattern("ss")
        val secondString = zonedDateTime.format(secondFormatter)
        canvas.drawText(secondString, left, bottom, textPaint)
    }

    private fun drawGrid(canvas: Canvas,
                         tA: TideRenderArea) {
        val gridPath = Path()

        for (i in 0 until tA.numFeet.toInt() + 1) {
            gridPath.moveTo(tA.lowerLeftPx[0], tA.tide0px + tA.footUnit * (i + tA.minTide))
            gridPath.lineTo(tA.upperRightPx[0], tA.tide0px + tA.footUnit * (i + tA.minTide))
        }
        for (i in 0 until tA.numHours.toInt()) {
            gridPath.moveTo(tA.lowerLeftPx[0] + i * tA.hourUnit, tA.lowerLeftPx[1])
            gridPath.lineTo(tA.lowerLeftPx[0] + i * tA.hourUnit, tA.upperRightPx[1])
        }
        gridPaint.strokeWidth = watchFaceData.standardFrameWidth / 2 * size
        gridPaint.color = watchFaceColors.activeGridColor
        gridPaint.style = Paint.Style.STROKE
        canvas.drawPath(gridPath, gridPaint)
    }

    private fun drawFrame(canvas: Canvas) {
        framePaint.strokeWidth = watchFaceData.standardFrameWidth * size
        framePaint.color = watchFaceColors.activeFrameColor

        val framePath = Path()
//        // Battery Frame section
//        framePath.moveTo(size, .65f * size)
//        framePath.lineTo(0.93f * size, .65f * size)
//        val arrayArc1 = calcBezierArc(0.5f * size, 0.5f * size, 0.93f * size, 0.65f * size, 60f)
//        framePath.cubicTo(arrayArc1[0],
//                          arrayArc1[1],
//                          arrayArc1[2],
//                          arrayArc1[3],
//                          arrayArc1[4],
//                          arrayArc1[5])
//        // Step Frame Section
//        framePath.lineTo(0.5f * size, 0.93f * size)
//        framePath.lineTo(0.5f * size, size)
//        framePath.moveTo(0.5f * size, 0.93f * size)
//        framePath.lineTo(size - arrayArc1[4], arrayArc1[5])
//        val arrayArc2 =
//            calcBezierArc(0.5f * size, 0.5f * size, size - arrayArc1[4], arrayArc1[5], 60f)
//        framePath.cubicTo(arrayArc2[0],
//                          arrayArc2[1],
//                          arrayArc2[2],
//                          arrayArc2[3],
//                          arrayArc2[4],
//                          arrayArc2[5])
//
//
//        framePath.lineTo(0f, 0.65f * size)

        // date and day section
//        val topCrossLineStart =
//            calcPointOnArc(0.5f * size, 0.5f * size, .455f * size, 0.18f * size, 3)
//        val topCrossLineEnd =
//            calcPointOnArc(0.5f * size, 0.5f * size, .455f * size, 0.18f * size, 4)
//        framePath.moveTo(topCrossLineStart[0], topCrossLineStart[1])
//        framePath.lineTo(topCrossLineEnd[0], topCrossLineEnd[1])
//
//        val bottomCrossLineStart =
//            calcPointOnArc(0.5f * size, 0.5f * size, .455f * size, 0.28f * size, 3)
//        val bottomCrossLineEnd =
//            calcPointOnArc(0.5f * size, 0.5f * size, .455f * size, 0.28f * size, 4)
//        framePath.moveTo(bottomCrossLineStart[0], bottomCrossLineStart[1])
//        framePath.lineTo(bottomCrossLineEnd[0], bottomCrossLineEnd[1])
//
//        framePath.moveTo(0.55f * size, bottomCrossLineEnd[1])
//        framePath.lineTo(0.60f * size, topCrossLineEnd[1])

        // Day and date section simplified
        framePath.moveTo(0f, 0.68f * size)
        framePath.lineTo(size, 0.68f * size)
        framePath.moveTo(0f, 0.78f * size)
        framePath.lineTo(size, 0.78f * size)
        framePath.moveTo(0.55f * size, 0.78f * size)
        framePath.lineTo(0.60f * size, 0.68f * size)

        // Next tide section
        framePath.moveTo(0f, 0.3f * size)
        framePath.lineTo(0.65f * size, 0.3f * size)
        framePath.lineTo(0.65f * size, 0.4f * size)
        framePath.lineTo(0f, 0.4f * size)

        // Sunset/Sunrise section
        framePath.moveTo(1.0f * size, 0.3f * size)
        framePath.lineTo(0.66f * size, 0.3f * size)
        framePath.lineTo(0.66f * size, 0.4f * size)
        framePath.lineTo(0.82f * size, 0.45f * size)
        framePath.lineTo(1.0f * size, 0.45f * size)

        framePaint.style = Paint.Style.STROKE
        canvas.drawPath(framePath, framePaint)
    }

    private fun drawLogos(canvas: Canvas) {
        // Sunshine logo
        val sunshinePath = Path()
        var squarePoints = generateSquare(0.725f * size,
                                          0.355f * size,
                                          0.03f * size,
                                          45f,
                                          primaryPaint.strokeWidth)
        sunshinePath.moveTo(squarePoints[0][0], squarePoints[0][1])
        sunshinePath.lineTo(squarePoints[1][0], squarePoints[1][1])
        sunshinePath.lineTo(squarePoints[2][0], squarePoints[2][1])
        sunshinePath.lineTo(squarePoints[3][0], squarePoints[3][1])
        sunshinePath.lineTo(squarePoints[4][0], squarePoints[4][1])

        squarePoints = generateSquare(0.725f * size,
                                      0.355f * size,
                                      0.03f * size,
                                      0f,
                                      primaryPaint.strokeWidth)
        sunshinePath.moveTo(squarePoints[0][0], squarePoints[0][1])
        sunshinePath.lineTo(squarePoints[1][0], squarePoints[1][1])
        sunshinePath.lineTo(squarePoints[2][0], squarePoints[2][1])
        sunshinePath.lineTo(squarePoints[3][0], squarePoints[3][1])
        sunshinePath.lineTo(squarePoints[4][0], squarePoints[4][1])

        primaryPaint.style = Paint.Style.STROKE
        canvas.drawPath(sunshinePath, primaryPaint)
        primaryPaint.style = Paint.Style.FILL
        canvas.drawCircle(0.725f * size, 0.355f * size, 0.023f * size, primaryPaint)

        // Sun rise/set arrows


        val arrowUpPath =
            generateArrowPath(0.8f * size, 0.37f * size, 0.055f * size, 0.01f * size, true)
        val arrowDownPath =
            generateArrowPath(0.8f * size, 0.435f * size, 0.055f * size, 0.01f * size, false)

        primaryPaint.style = Paint.Style.FILL
        canvas.drawPath(arrowUpPath, primaryPaint)
        canvas.drawPath(arrowDownPath, primaryPaint)
        primaryPaint.style = Paint.Style.STROKE
        canvas.drawPath(arrowUpPath, primaryPaint)
        canvas.drawPath(arrowDownPath, primaryPaint)

//        // Battery Logo
//        val batteryPath = Path()
//        primaryPaint.strokeWidth = watchFaceData.standardFrameWidth * size / 2
//        primaryPaint.style = Paint.Style.FILL
//        batteryPath.moveTo(0.52f * size, 0.92f * size - primaryPaint.strokeWidth / 2)
//        batteryPath.lineTo(0.52f * size, 0.87f * size)
//        batteryPath.lineTo(0.53f * size, 0.87f * size)
//        batteryPath.lineTo(0.53f * size, 0.865f * size)
//        batteryPath.lineTo(0.54f * size, 0.865f * size)
//        batteryPath.lineTo(0.54f * size, 0.87f * size)
//        batteryPath.lineTo(0.55f * size, 0.87f * size)
//        batteryPath.lineTo(0.55f * size, 0.92f * size)
//        batteryPath.lineTo(0.52f * size, 0.92f * size)
//        canvas.drawPath(batteryPath, primaryPaint)

//        // Step Logo
//        val stepPath = Path()
//        stepPath.moveTo(0.48f * size, 0.9f * size)
//        stepPath.lineTo(0.45f * size, 0.92f * size)
//        stepPath.lineTo(0.42f * size, 0.92f * size)
//        stepPath.cubicTo(0.41f * size, 0.91f * size,
//                         0.425f * size, 0.907f * size,
//                         0.435f * size, 0.905f * size)
//        stepPath.lineTo(0.445f * size, 0.89f * size)
//        stepPath.cubicTo(0.445f * size, 0.87f * size,
//                         0.45f * size, 0.88f * size,
//                         0.46f * size, 0.88f * size)
//        stepPath.cubicTo(0.47f * size, 0.88f * size,
//                         0.47f * size, 0.87f * size,
//                         0.475f * size, 0.88f * size)
//        stepPath.cubicTo(0.475f * size, 0.89f * size,
//                         0.483f * size, 0.895f * size,
//                         0.48f * size, 0.9f * size)
//        canvas.drawPath(stepPath, primaryPaint)

        primaryPaint.strokeWidth = watchFaceData.standardFrameWidth * size
        primaryPaint.style = Paint.Style.STROKE
        // Next tide text
        textPaint.color = watchFaceColors.activePrimaryColor
        textPaint.textSize = .035f * size
        canvas.drawText("NEXT", 0.035f * size, 0.345f * size, textPaint)
        canvas.drawText("TIDE", 0.035f * size, 0.38f * size, textPaint)
    }

    companion object {
        private const val TAG = "OceanTidesCanvasRenderer"
    }
}
