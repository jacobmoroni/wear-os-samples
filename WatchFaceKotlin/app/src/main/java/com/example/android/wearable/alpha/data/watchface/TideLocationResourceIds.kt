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
        locations = arrayOf("Newport Beach, CA", "Oceanside, CA", "La Jolla, CA"),
        ids = arrayOf("9410583", "TWC0419", "9410230"),
        regionName = "West Coast"
    ),

    EAST_COAST(
        locations = arrayOf("Long Island, NY", "Seaside Heights, NJ", "Outer Banks, NC", "Cocoa Beach, FL"),
        ids = arrayOf("8512354", "8533071", "8652226", "8721649"),
        regionName = "East Coast"
    );

    companion object {
        fun getTideRegion(idx: Int) : TideLocationResourceIds {
            return when (idx) {
                0 -> TideLocationResourceIds.WEST_COAST
                1 -> TideLocationResourceIds.EAST_COAST
                else -> TideLocationResourceIds.WEST_COAST
            }
        }

        /**
         * Translates the string id to the correct region object.
         */
        fun getTideLocation(region: TideLocationResourceIds, idx: Int): Pair<String, String> {
            if (idx < region.locations.size){
                return Pair(region.locations[idx], region.ids[idx])
            } else {
                return Pair("Null", "Null")
            }
        }
    }
}
