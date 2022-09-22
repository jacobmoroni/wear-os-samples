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
package com.example.android.wearable.alpha

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
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
import com.example.android.wearable.alpha.data.watchface.ColorStyleIdAndResourceIds
import com.example.android.wearable.alpha.data.watchface.WatchFaceColorPalette.Companion.convertToWatchFaceColorPalette
import com.example.android.wearable.alpha.data.watchface.WatchFaceData
import com.example.android.wearable.alpha.utils.COLOR_STYLE_SETTING
import com.example.android.wearable.alpha.utils.DRAW_HOUR_PIPS_STYLE_SETTING
import com.example.android.wearable.alpha.utils.WATCH_HAND_LENGTH_STYLE_SETTING
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

class TideRenderArea() {
    var lowerLeftPx = floatArrayOf(0f, 0f)
    var upperRightPx = floatArrayOf(0f, 0f)
    var minHour = 0f
    var maxHour = 0f
    var minTide = 0f
    var maxTide = 0f
    var numHours = 0f
    var hourUnit = 0f
    var time0px = 0f
    var numFeet = 0f
    var footUnit = 0f
    var tide0px = 0f
    fun updateValues() {
        numHours = maxHour - minHour
        hourUnit = (upperRightPx[0] - lowerLeftPx[0]) / (numHours - 1)
        time0px = lowerLeftPx[0] - minHour * hourUnit
        numFeet = maxTide - minTide
        footUnit = (upperRightPx[1] - lowerLeftPx[1]) / numFeet
        tide0px = lowerLeftPx[1] - minTide * footUnit
    }
}

/**
 * Renders watch face via data in Room database. Also, updates watch face state based on setting
 * changes by user via [userStyleRepository.addUserStyleListener()].
 */
class AnalogWatchCanvasRenderer(
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
    // three values are changeable by the user (color scheme, ticks being rendered, and length of
    // the minute arm). Those dynamic values are saved in the watch face APIs and we update those
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

    // Used to paint the main hour hand text with the hour pips, i.e., 3, 6, 9, and 12 o'clock.
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = context.resources.getDimensionPixelSize(R.dimen.hour_mark_size).toFloat()
        textScaleX = 1.01f
    }

    // Default size of watch face drawing area, that is, a no size rectangle. Will be replaced with
    // valid dimensions from the system.
    private var currentWatchFaceSize = Rect(0, 0, 0, 0)
    private var size = 0f // Width of the watch face
    private var prevZdt: ZonedDateTime = ZonedDateTime.now();
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
                // TODO: Replace These settings with new settings (Lat Lon, Tide Location, Tide Color ...)
                DRAW_HOUR_PIPS_STYLE_SETTING -> {
                    val booleanValue = options.value as
                        UserStyleSetting.BooleanUserStyleSetting.BooleanOption

                    newWatchFaceData = newWatchFaceData.copy(
                        drawHourPips = booleanValue.value
                    )
                }
                WATCH_HAND_LENGTH_STYLE_SETTING -> {
                    val doubleValue = options.value as
                        UserStyleSetting.DoubleRangeUserStyleSetting.DoubleRangeOption

                    // The arm lengths are usually only calculated the first time the watch face is
                    // loaded to reduce the ops in the onDraw(). Because we updated the minute hand
                    // watch length, we need to trigger a recalculation.
//                    armLengthChangedRecalculateClockHands = true

                    // Updates length of minute hand based on edits from user.
                    val newMinuteHandDimensions = newWatchFaceData.minuteHandDimensions.copy(
                        lengthFraction = doubleValue.value.toFloat()
                    )

                    newWatchFaceData = newWatchFaceData.copy(
                        minuteHandDimensions = newMinuteHandDimensions
                    )
                }
            }
        }

        // Only updates if something changed.
        if (watchFaceData != newWatchFaceData) {
            watchFaceData = newWatchFaceData

            // Recreates Color and ComplicationDrawable from resource ids.
            watchFaceColors = convertToWatchFaceColorPalette(
                context,
                watchFaceData.activeColorStyle,
                watchFaceData.ambientColorStyle
            )

            // TODO: This will either go away or turn into the step count section
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
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        scope.cancel("AnalogWatchCanvasRenderer scope clear() request")
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
            parseTides()
            nextTideIdx = findNextTide(zonedDateTime)
            updateActiveTides(zonedDateTime)
            initialized = true
        }
        // TODO: Pull out all calculations and only run them when necessary
        updateSize(bounds)
        canvas.drawColor(backgroundColor)

        // This is to try to save battery by rendering less. Not sure if I can get it working
//        if (zonedDateTime.second != prevZdt.second) {
//
//        }
        if (zonedDateTime.minute != prevZdt.minute) {
            if (!Duration.between(zonedDateTime, tideList[nextTideIdx].first).isNegative) {
                nextTideIdx = findNextTide(zonedDateTime)
            }
            updateActiveTides(zonedDateTime)
        }
        prevZdt = zonedDateTime


        drawTime(canvas, zonedDateTime)
        drawDayAndDate(canvas, zonedDateTime)

        if (renderParameters.drawMode == DrawMode.INTERACTIVE &&
            renderParameters.watchFaceLayers.contains(WatchFaceLayer.BASE)
        ) {
            //Tide Grid
            val tideArea = TideRenderArea()
            tideArea.lowerLeftPx = floatArrayOf(0f, 0.3f * size)
            tideArea.upperRightPx = floatArrayOf(0.8f * size, 0.1f * size)
            tideArea.minHour = -4f
            tideArea.maxHour = 16f
            tideArea.minTide = -2f
            tideArea.maxTide = 6f
            tideArea.updateValues()
            drawGrid(canvas, tideArea)
            val lat = 40.297119f
            val lon = -111.695007f
            val sunriseTime = calculateSunriseAndSunset(zonedDateTime, lat, lon, true)
            val sunsetTime = calculateSunriseAndSunset(zonedDateTime, lat, lon, false)
            val moonPhase = calculateMoonPhase(zonedDateTime)
            drawSunriseAndSunsetTime(canvas, sunriseTime, sunsetTime)
            //            drawBatteryPercent(canvas)
            //            drawStepCount(canvas)
            drawDaylightBlock(canvas, zonedDateTime, tideArea, sunriseTime, sunsetTime)
            drawTides(canvas, tideArea)
            drawTideAxes(canvas, tideArea)
            drawTideInfo(canvas, zonedDateTime)
            drawMoonFrame(canvas)
            drawMoonPhase(canvas, moonPhase)
            drawFrame(canvas)
            drawLogos(canvas)
            drawComplications(canvas, zonedDateTime)
        }

    }

    private fun AssetManager.readFile(fileName: String) = open(fileName)
        .bufferedReader()
        .use { it.readText() }

    private fun parseTides() {
        try {
            val fileName = "tides_2022_npb.txt"
            val fileContent = context.assets.readFile(fileName)
            val lines = fileContent.split("\n")
            for (i in 20 until lines.size-1) {
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
                val highLow: Boolean = line[8] == "H"
                tideList.add(Pair(timestamp, Pair(tide, highLow)))
            }
            Log.d("this", "tides:loaded ${tideList[tideList.size-1]}")
        } catch (e: Exception) {
            Log.d("this", "tides loading exception: $e")
        }
    }

    private fun findNextTide(zdt: ZonedDateTime): Int {
        for (i in 0 until tideList.size) {
            if (!Duration.between(zdt, tideList[i].first).isNegative) {
                return i
            }
        }
        return -1
        return -1
    }

    private fun updateActiveTides(zdt: ZonedDateTime) {
        if (nextTideIdx <= 2){
            if (nextTideIdx == -1){
                activeTides[0] = Pair(-16f, Pair(-2f, false))
                activeTides[1] = Pair(-8f, Pair(6f, true))
                activeTides[2] = Pair(0f, Pair(-2f, false))
                activeTides[3] = Pair(8f, Pair(6f, true))
                activeTides[4] = Pair(16f, Pair(-2f, false))
                activeTides[5] = Pair(24f, Pair(6f, true))
            }
            initialized = false

        } else {
            for (i in 0 .. 5){
                val tideIdx = nextTideIdx + i - 2
                val time = Duration.between(zdt, tideList[tideIdx].first).seconds.toFloat()/3600f
                Log.d("this", "tides: $i: time: $time: entry: ${tideList[tideIdx]}")
                val tideEntry : Pair<Float, Pair<Float, Boolean>> = Pair(time, tideList[tideIdx].second)
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

    private fun calculateSunriseAndSunset(zdt: ZonedDateTime,
                                          lat: Float,
                                          lon: Float,
                                          sunrise: Boolean): Float {
        /*
        localOffset will be <0 for western hemisphere and >0 for eastern hemisphere
        */
        val zenith = -0.83f

        //1. first calculate the day of the year
        val n1 = floor(275f * zdt.monthValue / 9f)
        val n2 = floor((zdt.monthValue + 9f) / 12f)
        val n3 = (1f + floor((zdt.year - 4f * floor(zdt.year / 4f) + 2f) / 3f))
        val dayOfYear = n1 - (n2 * n3) + zdt.dayOfMonth - 30f

        //2. convert the longitude to hour value and calculate an approximate time
        val lonHour = lon / 15.0f
        val t = if (sunrise) {
            dayOfYear + ((6f - lonHour) / 24f)   //if rising time is desired:
        } else {
            dayOfYear + ((18f - lonHour) / 24f)   //if setting time is desired:
        }

        //3. calculate the Sun's mean anomaly
        val meanAnomaly = (0.9856f * t) - 3.289f

        //4. calculate the Sun's true longitude
        val trueLon = (meanAnomaly + (1.916f * sin((Math.PI / 180f) * meanAnomaly)) +
            (0.020f * sin(2f * (Math.PI / 180f) * meanAnomaly)) + 282.634f).mod(360.0f)

        //5a. calculate the Sun's right ascension
        var rightAscension =
            (180f / Math.PI * atan(0.91764f * tan((Math.PI / 180f) * trueLon))).mod(360.0f)

        //5b. right ascension value needs to be in the same quadrant as trueLon
        val trueLonQuadrant = floor(trueLon / 90f) * 90f
        val rightAscensionQuadrant = floor(rightAscension / 90f) * 90f
        rightAscension += (trueLonQuadrant - rightAscensionQuadrant)

        //5c. right ascension value needs to be converted into hours
        rightAscension /= 15f

        //6. calculate the Sun's declination
        val sinDec = 0.39782f * sin((Math.PI / 180f) * trueLon)
        val cosDec = cos(asin(sinDec))

        //7a. calculate the Sun's local hour angle
        val cosH = (sin((Math.PI / 180f) * zenith) - (sinDec * sin((Math.PI / 180f) * lat))) /
            (cosDec * cos((Math.PI / 180f) * lat))
        /*
        if (cosH >  1)
        the sun never rises on this location (on the specified date)
        if (cosH < -1)
        the sun never sets on this location (on the specified date)
        */

        //7b. finish calculating local hour angle and convert into hours
        var localHourAngle = if (sunrise) {
            360f - (180f / Math.PI) * acos(cosH) // if rising time is desired:
        } else {
            (180f / Math.PI) * acos(cosH) // if setting time is desired:
        }
        localHourAngle /= 15f

        //8. calculate local mean time of rising/setting
        val localMeanTime = localHourAngle + rightAscension - (0.06571f * t) - 6.622f

        //9. adjust back to UTC
        val utcTime = (localMeanTime - lonHour).mod(24.0f)

        //10. convert UT value to local time zone of latitude/longitude
        val localOffset = zdt.offset.totalSeconds / 60f / 60f
        val localTime = (utcTime + localOffset + 24.0f).mod(24.0f)
        return localTime.toFloat()
    }

    private fun wrapAngle(angle: Double): Double {
        return angle - 360.0 * floor(angle / 360.0)
    }

    private fun kepler(m: Double, ecc: Double): Double {
        //Solve the equation of Kepler.
        var m_local = m
        val epsilon = 1e-6f
        m_local = m_local * Math.PI.toFloat() / 180
        var e = m_local
        while (true) {
            val delta = e - ecc * sin(e) - m_local
            e -= (delta / (1.0 - ecc * cos(e)))
            if (abs(delta) <= epsilon) {
                break
            }
        }
        return e
    }

    private fun calculateMoonPhase(zdt: ZonedDateTime): Float {
        //JDN stands for Julian Day Number
        // might need this in next line: - zdt.offset.totalSeconds /3600.0f
        val daySegment =
            (zdt.hour + (zdt.minute / 60.0) + (zdt.second / 3600.0)) / 24.0
        val a = ((zdt.monthValue + 9).toDouble() / 12.0).toInt()
        val b = (7 * (zdt.year + a).toDouble() / 4.0).toInt()
        val c = ((275 * (zdt.monthValue)).toDouble() / 9.0).toInt()
        val julianDatetime =
            (367 * (zdt.year) - b + c + zdt.dayOfMonth).toDouble() + 1721013.5 + daySegment
            (367 * (zdt.year) - b + c + zdt.dayOfMonth).toDouble() + 1721013.5 + daySegment
        //Angles here are in degrees
        //1980 January 0.0 in JDN
        //XXX: DateTime(1980).jdn yields 2444239.5 -- which one is right?
        val epoch = 2444238.5

        //Ecliptic longitude of the Sun at epoch 1980.0
        val eclipticLongitudeEpoch = 278.833540

        //Ecliptic longitude of the Sun at perigee
        val eclipticLongitudePerigee = 282.596403

        //Eccentricity of Earth's orbit
        val eccentricity = 0.016718

        //Elements of the Moon's orbit, epoch 1980.0

        //Moon's mean longitude at the epoch
        val moonMeanLongitudeEpoch = 64.975464
        //Mean longitude of the perigee at the epoch
        val moonMeanPerigeeEpoch = 349.383063

        val day = julianDatetime - epoch
        //Mean anomaly of the Sun
        val N = wrapAngle((360 / 365.2422) * day)
        // Convert from perigee coordinates to epoch 1980
        val M = wrapAngle(N + eclipticLongitudeEpoch - eclipticLongitudePerigee)

        //Solve Kepler's equation
        var Ec = kepler(M, eccentricity)
        Ec = (sqrt((1 + eccentricity) / (1 - eccentricity)) * tan(Ec / 2.0))
        //True anomaly
        Ec = (2 * (atan(Ec)) * 180.0 / Math.PI)
        //Suns's geometric ecliptic longuitude
        val lambdaSun = wrapAngle(Ec + eclipticLongitudePerigee)

        //Moon's mean longitude
        val moonLongitude = wrapAngle(13.1763966 * day + moonMeanLongitudeEpoch)
        //Moon's mean anomaly
        val MM = wrapAngle(moonLongitude - 0.1114041 * day - moonMeanPerigeeEpoch)
        val evection = 1.2739 * sin((2 * (moonLongitude - lambdaSun) - MM) * Math.PI / 180.0)
        //Annual equation
        val annualEq = 0.1858 * sin((M) * Math.PI / 180)
        //Correction term
        val A3 = 0.37 * sin((M) * Math.PI / 180)
        val MmP = MM + evection - annualEq - A3
        //Correction for the equation of the center
        val mEc = 6.2886 * sin((MmP) * Math.PI / 180.0)
        //Another correction term
        val A4 = 0.214 * sin((2 * MmP) * Math.PI / 180.0)
        //Corrected longitude
        val lP = moonLongitude + evection + mEc - annualEq + A4
        //Variation
        val variation = 0.6583 * sin((2 * (lP - lambdaSun)) * Math.PI / 180.0)
        //True longitude
        val lPP = lP + variation
        val moonAge = lPP - lambdaSun
        val phase = wrapAngle(moonAge) / 360.0f
        return phase.toFloat()
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
        val daylightHours = (sunsetTime - sunriseTime + 24f).mod(24f);
        Log.d("this", "daylight: $daylightHours")
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
        try{
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

        } catch (e : Exception){
            Log.d("this", "tide exception: $e")
        }

        val cleanUpRect = RectF()
        cleanUpRect.left = tA.upperRightPx[0]
        cleanUpRect.bottom = tA.lowerLeftPx[1]
        cleanUpRect.right = size
        cleanUpRect.top = tA.upperRightPx[1]
        gridPaint.style = Paint.Style.FILL
        gridPaint.color = watchFaceColors.activeBackgroundColor
        canvas.drawRect(cleanUpRect,gridPaint)
    }

    private fun drawTideAxes(canvas: Canvas, tA: TideRenderArea){
        val tideAxesPath = Path()
        primaryPaint.strokeWidth = watchFaceData.standardFrameWidth * size
        tideAxesPath.moveTo(tA.time0px, tA.lowerLeftPx[1])
        tideAxesPath.lineTo(tA.time0px, tA.upperRightPx[1])
        tideAxesPath.moveTo(tA.lowerLeftPx[0], tA.tide0px)
        tideAxesPath.lineTo(tA.upperRightPx[0], tA.tide0px)
        canvas.drawPath(tideAxesPath, primaryPaint)
    }

    fun Float.format(digits: Int) = "%.${digits}f".format(this)

    private fun drawTideInfo(canvas: Canvas, zdt: ZonedDateTime){
//        int f_ul_x = 0;
//        int f_ul_y = 109;
//        int f_w = 215;
//        int f_h = 35;
//
//        int label_x = 12;
//        int label_y = 137;
//        int label_f_sz = 12;
//
//        int tt_x = 60;
//        int tt_y = 134;
//        int tt_f_sz = 25;
//
//        int tl_x = 140;
//        int tl_y = tt_y;
//        int tl_f_sz = 25;
//
//        int ti_x = 52;
//        int ti_y = 135;
//        int ti_h = 18;
        val tidePair = tideList[nextTideIdx]
        val nextTideZdt = tidePair.first.withZoneSameInstant(zdt.zone)
        val nextTideHeight = tidePair.second.first
        val nextTideHigh = tidePair.second.second

        textPaint.textSize = .08f * size
        val hourMinuteFormatter = DateTimeFormatter.ofPattern("hh:mm")
        val hourMinuteString = nextTideZdt.format(hourMinuteFormatter)
        canvas.drawText(hourMinuteString, 0.18f * size, 0.38f * size, textPaint)
        val amPmFormatter = DateTimeFormatter.ofPattern("a")
        val amPmString = nextTideZdt.format(amPmFormatter)[0]
        textPaint.textSize = .05f * size
        canvas.drawText(amPmString.toString(), 0.38f * size, 0.38f * size, textPaint)

        textPaint.textSize = 0.08f * size
        val tideHeightString = if (nextTideHeight > 0){
            nextTideHeight.format(2)
        } else {
            nextTideHeight.format(1)
        }
        canvas.drawText(tideHeightString, 0.44f * size, 0.38f * size, textPaint)
        textPaint.textSize = 0.05f * size
        canvas.drawText("FT", 0.58f * size, 0.38f * size, textPaint)

        // Tide indicator
//        if (nextTideHigh) {
//            drawArrow(ti_x, ti_y, ti_h, 3, ad);
//        }
//        else
//        {
//            drawArrow(ti_x, ti_y-ti_h, -ti_h, 3, ad);
//        }
//        cairo_stroke(ad->cairo);
    }

    private fun drawArrow(baseX: Float, baseY: Float, height: Float, width: Float, canvas: Canvas){

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
        val lineWidth = watchFaceData.standardFrameWidth * size
        primaryPaint.style = Paint.Style.FILL
        val moonCx = 0.8f * size
        val moonCy = 0.2f * size - primaryPaint.strokeWidth / 2

        val moonPhasePath = Path()

        var curveX = 0f;
        //Moon phase is a double between 0 and 1. 0 == New moon. then waxes to full moon at 0.5. then wanes to new moon at 1
        if (moonPhase <= 0.5) {
            curveX = (-moonPhase + 0.25f) * moonD / 0.25f + moonCx
            val curveY1 = abs(-moonPhase + 0.25f) * moonR / 0.25f + moonCy
            val curveY2 = -abs(-moonPhase + 0.25f) * moonR / 0.25f + moonCy
            moonPhasePath.moveTo(moonCx, moonCy + moonR)
            moonPhasePath.cubicTo(moonCx + moonD, moonCy + moonR,
                                  moonCx + moonD, moonCy - moonR,
                                  moonCx, moonCy - moonR)
            moonPhasePath.cubicTo(curveX, curveY2,
                                  curveX, curveY1, moonCx, moonCy + moonR);
        } else {
            curveX = (-(moonPhase - 0.5f) + 0.25f) * moonD / 0.25f + moonCx
            val curveY1 = (abs(-(moonPhase - 0.5) + 0.25) * moonR / 0.25 + moonCy).toFloat();
            val curveY2 = (-abs(-(moonPhase - 0.5f) + 0.25f) * moonR / 0.25f + moonCy);
            moonPhasePath.moveTo(moonCx, moonCy + moonR);
            moonPhasePath.cubicTo(moonCx - moonD, moonCy + moonR,
                                  moonCx - moonD, moonCy - moonR, moonCx, moonCy - moonR);
            moonPhasePath.cubicTo(curveX, curveY2,
                                  curveX, curveY1, moonCx, moonCy + moonR);
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
            drawSeconds(canvas, zonedDateTime, false);
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

    // TODO: This may be helpful for showing how to integrate User defined variables
//    private fun drawClockHands(
//        canvas: Canvas,
//        bounds: Rect,
//        zonedDateTime: ZonedDateTime
//    ) {
//        // Only recalculate bounds (watch face size/surface) has changed or the arm of one of the
//        // clock hands has changed (via user input in the settings).
//        // NOTE: Watch face surface usually only updates one time (when the size of the device is
//        // initially broadcasted).
//        if (currentWatchFaceSize != bounds || armLengthChangedRecalculateClockHands) {
//            armLengthChangedRecalculateClockHands = false
//            currentWatchFaceSize = bounds
//            recalculateClockHands(bounds)
//        }
//
//        // Retrieve current time to calculate location/rotation of watch arms.
//        val secondOfDay = zonedDateTime.toLocalTime().toSecondOfDay()
//
//        // Determine the rotation of the hour and minute hand.
//
//        // Determine how many seconds it takes to make a complete rotation for each hand
//        // It takes the hour hand 12 hours to make a complete rotation
//        val secondsPerHourHandRotation = Duration.ofHours(12).seconds
//        // It takes the minute hand 1 hour to make a complete rotation
//        val secondsPerMinuteHandRotation = Duration.ofHours(1).seconds
//
//        // Determine the angle to draw each hand expressed as an angle in degrees from 0 to 360
//        // Since each hand does more than one cycle a day, we are only interested in the remainder
//        // of the secondOfDay modulo the hand interval
//        val hourRotation = secondOfDay.rem(secondsPerHourHandRotation) * 360.0f /
//            secondsPerHourHandRotation
//        val minuteRotation = secondOfDay.rem(secondsPerMinuteHandRotation) * 360.0f /
//            secondsPerMinuteHandRotation
//
//        canvas.withScale(
//            x = WATCH_HAND_SCALE,
//            y = WATCH_HAND_SCALE,
//            pivotX = bounds.exactCenterX(),
//            pivotY = bounds.exactCenterY()
//        ) {
//            val drawAmbient = renderParameters.drawMode == DrawMode.AMBIENT
//
//            clockHandPaint.style = if (drawAmbient) Paint.Style.STROKE else Paint.Style.FILL
//            clockHandPaint.color = if (drawAmbient) {
//                watchFaceColors.ambientPrimaryColor
//            } else {
//                watchFaceColors.activePrimaryColor
//            }
//
//            // Draw hour hand.
//            withRotation(hourRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//                drawPath(hourHandBorder, clockHandPaint)
//            }
//
//            // Draw minute hand.
//            withRotation(minuteRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//                drawPath(minuteHandBorder, clockHandPaint)
//            }
//            // Draw second hand if not in ambient mode
//            if (!drawAmbient) {
//                clockHandPaint.color = watchFaceColors.activeSecondaryColor
//
//                // Second hand has a different color style (secondary color) and is only drawn in
//                // active mode, so we calculate it here (not above with others).
//                val secondsPerSecondHandRotation = Duration.ofMinutes(1).seconds
//                val secondsRotation = secondOfDay.rem(secondsPerSecondHandRotation) * 360.0f /
//                    secondsPerSecondHandRotation
//                clockHandPaint.color = watchFaceColors.activeSecondaryColor
//
//                withRotation(secondsRotation, bounds.exactCenterX(), bounds.exactCenterY()) {
//                    drawPath(secondHand, clockHandPaint)
//                }
//            }
//        }
//    }

//    /*
//     * Rarely called (only when watch face surface changes; usually only once) from the
//     * drawClockHands() method.
//     */
//    private fun recalculateClockHands(bounds: Rect) {
//        Log.d(TAG, "recalculateClockHands()")
//        hourHandBorder =
//            createClockHand(
//                bounds,
//                watchFaceData.hourHandDimensions.lengthFraction,
//                watchFaceData.hourHandDimensions.widthFraction,
//                watchFaceData.gapBetweenHandAndCenterFraction,
//                watchFaceData.hourHandDimensions.xRadiusRoundedCorners,
//                watchFaceData.hourHandDimensions.yRadiusRoundedCorners
//            )
//        hourHandFill = hourHandBorder
//
//        minuteHandBorder =
//            createClockHand(
//                bounds,
//                watchFaceData.minuteHandDimensions.lengthFraction,
//                watchFaceData.minuteHandDimensions.widthFraction,
//                watchFaceData.gapBetweenHandAndCenterFraction,
//                watchFaceData.minuteHandDimensions.xRadiusRoundedCorners,
//                watchFaceData.minuteHandDimensions.yRadiusRoundedCorners
//            )
//        minuteHandFill = minuteHandBorder
//
//        secondHand =
//            createClockHand(
//                bounds,
//                watchFaceData.secondHandDimensions.lengthFraction,
//                watchFaceData.secondHandDimensions.widthFraction,
//                watchFaceData.gapBetweenHandAndCenterFraction,
//                watchFaceData.secondHandDimensions.xRadiusRoundedCorners,
//                watchFaceData.secondHandDimensions.yRadiusRoundedCorners
//            )
//    }

//    /**
//     * Returns a round rect clock hand if {@code rx} and {@code ry} equals to 0, otherwise return a
//     * rect clock hand.
//     *
//     * @param bounds The bounds use to determine the coordinate of the clock hand.
//     * @param length Clock hand's length, in fraction of {@code bounds.width()}.
//     * @param thickness Clock hand's thickness, in fraction of {@code bounds.width()}.
//     * @param gapBetweenHandAndCenter Gap between inner side of arm and center.
//     * @param roundedCornerXRadius The x-radius of the rounded corners on the round-rectangle.
//     * @param roundedCornerYRadius The y-radius of the rounded corners on the round-rectangle.
//     */
//    private fun createClockHand(
//        bounds: Rect,
//        length: Float,
//        thickness: Float,
//        gapBetweenHandAndCenter: Float,
//        roundedCornerXRadius: Float,
//        roundedCornerYRadius: Float
//    ): Path {
//        val width = bounds.width()
//        val centerX = bounds.exactCenterX()
//        val centerY = bounds.exactCenterY()
//        val left = centerX - thickness / 2 * width
//        val top = centerY - (gapBetweenHandAndCenter + length) * width
//        val right = centerX + thickness / 2 * width
//        val bottom = centerY - gapBetweenHandAndCenter * width
//        val path = Path()
//
//        if (roundedCornerXRadius != 0.0f || roundedCornerYRadius != 0.0f) {
//            path.addRoundRect(
//                left,
//                top,
//                right,
//                bottom,
//                roundedCornerXRadius,
//                roundedCornerYRadius,
//                Path.Direction.CW
//            )
//        } else {
//            path.addRect(
//                left,
//                top,
//                right,
//                bottom,
//                Path.Direction.CW
//            )
//        }
//        return path
//    }

    private fun calcBezierArc(cx: Float,
                              cy: Float,
                              sx: Float,
                              sy: Float,
                              sweepAngleDeg: Float): Array<Float> {
        val leg1x = sx - cx
        val leg1y = sy - cy
        val theta1 = atan2(leg1y, leg1x)
        val theta2 = theta1 + sweepAngleDeg * PI / 180f
        val radius = sqrt(leg1x * leg1x + leg1y * leg1y)
        val numSegments = 360f / sweepAngleDeg
        val ctrlPtLen = radius * 4f / 3f * tan(PI / (2 * numSegments))
        val cp3x = radius * cos(theta2) + cx
        val cp3y = radius * sin(theta2) + cy
        val ctrlPtAngle = atan2(ctrlPtLen, radius.toDouble())
        val lenToCtrlPt = sqrt(radius * radius + ctrlPtLen * ctrlPtLen)
        val cp1x = lenToCtrlPt * cos(theta1 + ctrlPtAngle) + cx
        val cp1y = lenToCtrlPt * sin(theta1 + ctrlPtAngle) + cy
        val cp2x = lenToCtrlPt * cos(theta2 - ctrlPtAngle) + cx
        val cp2y = lenToCtrlPt * sin(theta2 - ctrlPtAngle) + cy
        return arrayOf(cp1x.toFloat(),
                       cp1y.toFloat(),
                       cp2x.toFloat(),
                       cp2y.toFloat(),
                       cp3x.toFloat(),
                       cp3y.toFloat())
    }

    private fun calcPointOnArc(cx: Float,
                               cy: Float,
                               radius: Float,
                               distY: Float,
                               quadrant: Int): Array<Float> {
        val distX = sqrt(radius * radius - distY * distY)
        var px = 0f
        var py = 0f
        when (quadrant) {
            1 -> {
                px = cx + distX
                py = cy - distY
            }
            2 -> {
                px = cx - distX
                py = cy - distY
            }
            3 -> {
                px = cx - distX
                py = cy + distY
            }
            4 -> {
                px = cx + distX
                py = cy + distY
            }
        }
        return arrayOf(px, py)
    }

    private fun generateSquare(cx: Float,
                               cy: Float,
                               sideDist: Float,
                               rotation: Float,
                               lineWidth: Float): Array<FloatArray> {
        val points = arrayOf(floatArrayOf(-sideDist - lineWidth / 2, -sideDist),
                             floatArrayOf(sideDist, -sideDist),
                             floatArrayOf(sideDist, sideDist),
                             floatArrayOf(-sideDist, sideDist),
                             floatArrayOf(-sideDist, -sideDist - lineWidth / 2))
        if (rotation != 0f) {
            val theta = rotation * Math.PI / 180
            for (point in points) {
                val p1x = point[0] * cos(theta) + point[1] * sin(theta)
                val p1y = point[0] * -sin(theta) + point[1] * cos(theta)
                point[0] = p1x.toFloat()
                point[1] = p1y.toFloat()
            }
        }
        for (point in points) {
            point[0] += cx
            point[1] += cy
        }
        return points
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

        primaryPaint.style = Paint.Style.FILL
        canvas.drawCircle(0.725f * size, 0.355f * size, 0.023f * size, primaryPaint)

        // Sun rise/set arrows


        sunshinePath.moveTo(0.8f * size, 0.37f * size)
        sunshinePath.lineTo(0.8f * size, 0.315f * size)
        sunshinePath.lineTo(0.79f * size, 0.34f * size)

        sunshinePath.moveTo(0.8f * size, 0.38f * size)
        sunshinePath.lineTo(0.8f * size, 0.435f * size)
        sunshinePath.lineTo(0.79f * size, 0.41f * size)

        primaryPaint.style = Paint.Style.STROKE
        canvas.drawPath(sunshinePath, primaryPaint)

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

//    private fun drawNumberStyleOuterElement(
//        canvas: Canvas,
//        bounds: Rect,
//        numberRadiusFraction: Float,
//        outerCircleStokeWidthFraction: Float,
//        outerElementColor: Int,
//        standardFrameWidth: Float,
//        gapBetweenOuterCircleAndBorderFraction: Float
//    ) {
//        // Draws text hour indicators (12, 3, 6, and 9).
//        val textBounds = Rect()
//        textPaint.color = outerElementColor
//        for (i in 0 until 4) {
//            val rotation = 0.5f * (i + 1).toFloat() * Math.PI
//            val dx = sin(rotation).toFloat() * numberRadiusFraction * bounds.width().toFloat()
//            val dy = -cos(rotation).toFloat() * numberRadiusFraction * bounds.width().toFloat()
//            textPaint.getTextBounds(HOUR_MARKS[i], 0, HOUR_MARKS[i].length, textBounds)
//            canvas.drawText(
//                HOUR_MARKS[i],
//                bounds.exactCenterX() + dx - textBounds.width() / 2.0f,
//                bounds.exactCenterY() + dy + textBounds.height() / 2.0f,
//                textPaint
//            )
//        }
//        textPaint.color = watchFaceColors.activeFrameColor
//        canvas.drawText("Next tide", 120.0f, 120.0f, textPaint)
//        val arcBounds = RectF()
//        arcBounds.bottom = 100f
//        arcBounds.top = 60f
//        arcBounds.left = 100f
//        arcBounds.right = 150f
//        canvas.drawArc(arcBounds, 30f, 140f, true, textPaint)
//        arcBounds.left = 200f
//        arcBounds.right = 210f
//        canvas.drawArc(arcBounds, 30f, 140f, true, textPaint)
//
//        val tidePath = Path()
//        tidePath.moveTo(80f, 150f)
//        tidePath.cubicTo(100f, 150f, 100f, 130f, 120f, 130f)
//        tidePath.lineTo(120f, 100f)
//        tidePath.lineTo(80f, 100f)
//        tidePath.lineTo(80f, 150f)
//
//        canvas.drawPath(tidePath, textPaint)
//
//        // Draws dots for the remain hour indicators between the numbers above.
//        framePaint.strokeWidth = outerCircleStokeWidthFraction * bounds.width()
//        framePaint.color = outerElementColor
//        canvas.save()
//        for (i in 0 until 12) {
//            if (i % 3 != 0) {
//                drawTopMiddleCircle(
//                    canvas,
//                    bounds,
//                    standardFrameWidth,
//                    gapBetweenOuterCircleAndBorderFraction
//                )
//            }
//            canvas.rotate(360.0f / 12.0f, bounds.exactCenterX(), bounds.exactCenterY())
//        }
//        canvas.restore()
//    }
//
//    /** Draws the outer circle on the top middle of the given bounds. */
//    private fun drawTopMiddleCircle(
//        canvas: Canvas,
//        bounds: Rect,
//        radiusFraction: Float,
//        gapBetweenOuterCircleAndBorderFraction: Float
//    ) {
//        outerElementPaint.style = Paint.Style.FILL_AND_STROKE
//
//        // X and Y coordinates of the center of the circle.
//        val centerX = 0.5f * bounds.width().toFloat()
//        val centerY = bounds.width() * (gapBetweenOuterCircleAndBorderFraction + radiusFraction)
//
//        canvas.drawCircle(
//            centerX,
//            centerY,
//            radiusFraction * bounds.width(),
//            outerElementPaint
//        )
//    }

    companion object {
        private const val TAG = "AnalogWatchCanvasRenderer"

        // Painted between pips on watch face for hour marks.
        private val HOUR_MARKS = arrayOf("3", "6", "9", "12")

        // Used to canvas.scale() to scale watch hands in proper bounds. This will always be 1.0.
        private const val WATCH_HAND_SCALE = 1.0f
    }
}
