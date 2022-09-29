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
package com.example.android.wearable.alpha.utils

import android.content.Context
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import com.example.android.wearable.alpha.R
import com.example.android.wearable.alpha.data.watchface.ColorStyleIdAndResourceIds
import com.example.android.wearable.alpha.data.watchface.MINUTE_HAND_LENGTH_FRACTION_DEFAULT
import com.example.android.wearable.alpha.data.watchface.MINUTE_HAND_LENGTH_FRACTION_MAXIMUM
import com.example.android.wearable.alpha.data.watchface.MINUTE_HAND_LENGTH_FRACTION_MINIMUM
import com.example.android.wearable.alpha.data.watchface.LAT_MAX
import com.example.android.wearable.alpha.data.watchface.LAT_MIN
import com.example.android.wearable.alpha.data.watchface.LAT_DEFAULT
import com.example.android.wearable.alpha.data.watchface.LON_MAX
import com.example.android.wearable.alpha.data.watchface.LON_MIN
import com.example.android.wearable.alpha.data.watchface.LON_DEFAULT

// Keys to matched content in the  the user style settings. We listen for changes to these
// values in the renderer and if new, we will update the database and update the watch face
// being rendered.
const val COLOR_STYLE_SETTING = "color_style_setting"
const val SUNRISE_LAT_STYLE_SETTING = "sunrise_lat_style_setting"
const val SUNRISE_LON_STYLE_SETTING = "sunrise_lon_style_setting"
const val WATCH_HAND_LENGTH_STYLE_SETTING = "watch_hand_length_style_setting"

/*
 * Creates user styles in the settings activity associated with the watch face, so users can
 * edit different parts of the watch face. In the renderer (after something has changed), the
 * watch face listens for a flow from the watch face API data layer and updates the watch face.
 */
fun createUserStyleSchema(context: Context): UserStyleSchema {
    // 1. Allows user to change the color styles of the watch face (if any are available).
    val colorStyleSetting =
        UserStyleSetting.ListUserStyleSetting(
            UserStyleSetting.Id(COLOR_STYLE_SETTING),
            context.resources,
            R.string.colors_style_setting,
            R.string.colors_style_setting_description,
            null,
            ColorStyleIdAndResourceIds.toOptionList(context),
            listOf(
                WatchFaceLayer.BASE,
                WatchFaceLayer.COMPLICATIONS,
                WatchFaceLayer.COMPLICATIONS_OVERLAY
            )
        )

    // 2. Allows user to set location for sunrise and sunset.
    val sunriseLatStyleSetting = UserStyleSetting.DoubleRangeUserStyleSetting(
        UserStyleSetting.Id(SUNRISE_LAT_STYLE_SETTING),
        context.resources,
        R.string.sunrise_lat_setting,
        R.string.sunrise_lat_description,
        null,
        LAT_MIN.toDouble(),
        LAT_MAX.toDouble(),
        listOf(WatchFaceLayer.BASE),
        LAT_DEFAULT.toDouble(),
    )

    // 3. Allows user to set location for sunrise and sunset.
    val sunriseLonStyleSetting = UserStyleSetting.DoubleRangeUserStyleSetting(
        UserStyleSetting.Id(SUNRISE_LON_STYLE_SETTING),
        context.resources,
        R.string.sunrise_lon_setting,
        R.string.sunrise_lon_description,
        null,
        LON_MIN.toDouble(),
        LON_MAX.toDouble(),
        listOf(WatchFaceLayer.BASE),
        LON_DEFAULT.toDouble(),
    )

    // 4. Allows user to change the length of the minute hand.
    val watchHandLengthStyleSetting = UserStyleSetting.DoubleRangeUserStyleSetting(
        UserStyleSetting.Id(WATCH_HAND_LENGTH_STYLE_SETTING),
        context.resources,
        R.string.watchface_hand_length_setting,
        R.string.watchface_hand_length_setting_description,
        null,
        MINUTE_HAND_LENGTH_FRACTION_MINIMUM.toDouble(),
        MINUTE_HAND_LENGTH_FRACTION_MAXIMUM.toDouble(),
        listOf(WatchFaceLayer.COMPLICATIONS_OVERLAY),
        MINUTE_HAND_LENGTH_FRACTION_DEFAULT.toDouble()
    )

    // 4. Create style settings to hold all options.
    return UserStyleSchema(
        listOf(
            colorStyleSetting,
            sunriseLatStyleSetting,
            sunriseLonStyleSetting,
            watchHandLengthStyleSetting
        )
    )
}
