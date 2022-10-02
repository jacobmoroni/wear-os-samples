package com.example.android.wearable.alpha.data.watchface

import android.content.Context
import android.graphics.drawable.Icon
import androidx.wear.watchface.style.UserStyleSetting

enum class TideLocationResourceIds(
    val locations: Array<String>,
    val ids:Array<String>,
    val regionName: String
) {
    WEST_COAST(
        locations = arrayOf("Newport Beach", "Oceanside", "La Jolla"),
        ids = arrayOf("9410583", "TWC0419", "9410230"),
        regionName = "west_coast"
    ),

    EAST_COAST(
        locations = arrayOf("Long Island, NY", "Seaside Heights, NJ", "Outer Banks, NC", "Cocoa Beach, FL"),
        ids = arrayOf("8512354", "8533071", "8652226", "8721649"),
        regionName = "east_coast"
    );

    companion object {
        /**
         * Translates the string id to the correct region object.
         */
        fun getTideLocation(regionName: String, idx: Int): String {
            val region = when (regionName) {
                TideLocationResourceIds.WEST_COAST.regionName -> TideLocationResourceIds.WEST_COAST
                TideLocationResourceIds.EAST_COAST.regionName -> TideLocationResourceIds.EAST_COAST
                else -> TideLocationResourceIds.WEST_COAST
            }
            return region.locations[idx]
        }

        /**
         * Returns a list of [UserStyleSetting.ListUserStyleSetting.ListOption] for all
         * TideLocationResourceIds enums. The watch face settings APIs use this to set up
         * options for the user to select a style.
         */
        fun toOptionList(context: Context): List<UserStyleSetting.ListUserStyleSetting.ListOption> {
            val colorStyleIdAndResourceIdsList = enumValues<ColorStyleIdAndResourceIds>()

            return colorStyleIdAndResourceIdsList.map { colorStyleIdAndResourceIds ->
                UserStyleSetting.ListUserStyleSetting.ListOption(
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
