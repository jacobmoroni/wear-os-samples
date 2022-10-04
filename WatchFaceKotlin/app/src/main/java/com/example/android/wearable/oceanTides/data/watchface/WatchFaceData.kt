/*
 * Copyright 2021 The Android Open Source Project
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
package com.example.android.wearable.oceanTides.data.watchface

const val LAT_DEFAULT = 40.297119f
const val LAT_MAX = 90.0f
const val LAT_MIN = -90.0f

const val LON_DEFAULT = -111.695007f
const val LON_MAX = 180.0f
const val LON_MIN = -180.0f

const val REGION_IDX_MIN = 0L
const val REGION_IDX_MAX = 100L
const val REGION_IDX_DEFAULT = 0L

const val LOCATION_IDX_MIN = 0L
const val LOCATION_IDX_MAX = 100L
const val LOCATION_IDX_DEFAULT = 0L

private const val STANDARD_FRAME_WIDTH_FRACTION = 0.00584f


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
 * Represents all data needed to render the ocean tides watch face.
 */
data class WatchFaceData(
    val activeColorStyle: ColorStyleIdAndResourceIds = ColorStyleIdAndResourceIds.RED,
    val ambientColorStyle: ColorStyleIdAndResourceIds = ColorStyleIdAndResourceIds.AMBIENT,
    val sunriseLat: Float = LAT_DEFAULT,
    val sunriseLon: Float = LON_DEFAULT,
    var tideRegionIdx: Long = REGION_IDX_DEFAULT,
    var tideSpotIdx: Long = LOCATION_IDX_DEFAULT,
    var tideRegion: TideLocationResourceIds = TideLocationResourceIds.WEST_COAST,
    var tideSpot: Pair<String, String> = Pair("Newport Beach, CA", "9410583"),
    val tideArea: TideRenderArea = TideRenderArea(),
    var sunriseTime: Float = 0f,
    var sunsetTime: Float = 0f,
    var moonPhase: Float = 0f,
    val standardFrameWidth: Float = STANDARD_FRAME_WIDTH_FRACTION,
)
