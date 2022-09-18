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
package com.example.android.wearable.alpha.data.watchface

import android.content.Context
import android.graphics.drawable.Icon
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.ListUserStyleSetting
import com.example.android.wearable.alpha.R

// Defaults for all styles.
// X_COLOR_STYLE_ID - id in watch face database for each style id.
// X_COLOR_STYLE_NAME_RESOURCE_ID - String name to display in the user settings UI for the style.
// X_COLOR_STYLE_ICON_ID - Icon to display in the user settings UI for the style.
const val AMBIENT_COLOR_STYLE_ID = "ambient_style_id"
private const val AMBIENT_COLOR_STYLE_NAME_RESOURCE_ID = R.string.ambient_style_name
private const val AMBIENT_COLOR_STYLE_ICON_ID = R.drawable.white_style

const val RED_COLOR_STYLE_ID = "red_style_id"
private const val RED_COLOR_STYLE_NAME_RESOURCE_ID = R.string.red_style_name
private const val RED_COLOR_STYLE_ICON_ID = R.drawable.red_style

const val GREEN_COLOR_STYLE_ID = "green_style_id"
private const val GREEN_COLOR_STYLE_NAME_RESOURCE_ID = R.string.green_style_name
private const val GREEN_COLOR_STYLE_ICON_ID = R.drawable.green_style

const val BLUE_COLOR_STYLE_ID = "blue_style_id"
private const val BLUE_COLOR_STYLE_NAME_RESOURCE_ID = R.string.blue_style_name
private const val BLUE_COLOR_STYLE_ICON_ID = R.drawable.blue_style

const val WHITE_COLOR_STYLE_ID = "white_style_id"
private const val WHITE_COLOR_STYLE_NAME_RESOURCE_ID = R.string.white_style_name
private const val WHITE_COLOR_STYLE_ICON_ID = R.drawable.white_style

/**
 * Represents watch face color style options the user can select (includes the unique id, the
 * complication style resource id, and general watch face color style resource ids).
 *
 * The companion object offers helper functions to translate a unique string id to the correct enum
 * and convert all the resource ids to their correct resources (with the Context passed in). The
 * renderer will use these resources to render the actual colors and ComplicationDrawables of the
 * watch face.
 */
enum class ColorStyleIdAndResourceIds(
    val id: String,
    @StringRes val nameResourceId: Int,
    @DrawableRes val iconResourceId: Int,
    @DrawableRes val complicationStyleDrawableId: Int,
    @ColorRes val primaryColorId: Int,
    @ColorRes val frameColorId: Int,
    @ColorRes val gridColorId: Int,
    @ColorRes val tideColorId: Int,
    @ColorRes val lowBatteryColorId: Int,
    @ColorRes val backgroundColorId: Int
) {
    AMBIENT(
        id = AMBIENT_COLOR_STYLE_ID,
        nameResourceId = AMBIENT_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = AMBIENT_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_white_style,
        primaryColorId = R.color.ambient_primary_color,
        frameColorId = R.color.ambient_frame_color,
        gridColorId = R.color.ambient_grid_color,
        tideColorId = R.color.ambient_tide_color,
        lowBatteryColorId = R.color.ambient_low_battery_color,
        backgroundColorId = R.color.ambient_background_color
    ),

    RED(
        id = RED_COLOR_STYLE_ID,
        nameResourceId = RED_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = RED_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_red_style,
        primaryColorId = R.color.red_primary_color,
        frameColorId = R.color.red_frame_color,
        gridColorId = R.color.red_grid_color,
        tideColorId = R.color.red_tide_color,
        lowBatteryColorId = R.color.red_low_battery_color,
        backgroundColorId = R.color.red_background_color
    ),

    GREEN(
        id = GREEN_COLOR_STYLE_ID,
        nameResourceId = GREEN_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = GREEN_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_green_style,
        primaryColorId = R.color.green_primary_color,
        frameColorId = R.color.green_frame_color,
        gridColorId = R.color.green_grid_color,
        tideColorId = R.color.green_tide_color,
        lowBatteryColorId = R.color.green_low_battery_color,
        backgroundColorId = R.color.green_background_color
    ),

    BLUE(
        id = BLUE_COLOR_STYLE_ID,
        nameResourceId = BLUE_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = BLUE_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_blue_style,
        primaryColorId = R.color.blue_primary_color,
        frameColorId = R.color.blue_frame_color,
        gridColorId = R.color.blue_grid_color,
        tideColorId = R.color.blue_tide_color,
        lowBatteryColorId = R.color.blue_low_battery_color,
        backgroundColorId = R.color.blue_background_color
    ),

    WHITE(
        id = WHITE_COLOR_STYLE_ID,
        nameResourceId = WHITE_COLOR_STYLE_NAME_RESOURCE_ID,
        iconResourceId = WHITE_COLOR_STYLE_ICON_ID,
        complicationStyleDrawableId = R.drawable.complication_white_style,
        primaryColorId = R.color.white_primary_color,
        frameColorId = R.color.white_frame_color,
        gridColorId = R.color.white_grid_color,
        tideColorId = R.color.white_tide_color,
        lowBatteryColorId = R.color.white_low_battery_color,
        backgroundColorId = R.color.white_background_color
    );

    companion object {
        /**
         * Translates the string id to the correct ColorStyleIdAndResourceIds object.
         */
        fun getColorStyleConfig(id: String): ColorStyleIdAndResourceIds {
            return when (id) {
                AMBIENT.id -> AMBIENT
                RED.id -> RED
                GREEN.id -> GREEN
                BLUE.id -> BLUE
                WHITE.id -> WHITE
                else -> WHITE
            }
        }

        /**
         * Returns a list of [UserStyleSetting.ListUserStyleSetting.ListOption] for all
         * ColorStyleIdAndResourceIds enums. The watch face settings APIs use this to set up
         * options for the user to select a style.
         */
        fun toOptionList(context: Context): List<ListUserStyleSetting.ListOption> {
            val colorStyleIdAndResourceIdsList = enumValues<ColorStyleIdAndResourceIds>()

            return colorStyleIdAndResourceIdsList.map { colorStyleIdAndResourceIds ->
                ListUserStyleSetting.ListOption(
                    UserStyleSetting.Option.Id(colorStyleIdAndResourceIds.id),
                    context.resources,
                    colorStyleIdAndResourceIds.nameResourceId,
                    Icon.createWithResource(
                        context,
                        colorStyleIdAndResourceIds.iconResourceId
                    )
                )
            }
        }
    }
}
