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
package com.example.android.wearable.alpha.editor

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.android.wearable.alpha.data.watchface.ColorStyleIdAndResourceIds
import com.example.android.wearable.alpha.data.watchface.TideLocationResourceIds
import com.example.android.wearable.alpha.databinding.ActivityWatchFaceConfigBinding
import com.example.android.wearable.alpha.utils.LEFT_COMPLICATION_ID
import com.example.android.wearable.alpha.utils.RIGHT_COMPLICATION_ID
import com.example.android.wearable.alpha.utils.format
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.OnTokenCanceledListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Allows user to edit certain parts of the watch face (color style, sunrise location, minute arm
 * length) by using the [WatchFaceConfigStateHolder]. (All widgets are disabled until data is
 * loaded.)
 */
class WatchFaceConfigActivity : ComponentActivity() {
    private val stateHolder: WatchFaceConfigStateHolder by lazy {
        WatchFaceConfigStateHolder(
            lifecycleScope,
            this@WatchFaceConfigActivity
        )
    }

    private lateinit var binding: ActivityWatchFaceConfigBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var colorIdx = 0
    private var tideRegionIdx = 0
    private var tideSpotIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate()")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding = ActivityWatchFaceConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable widgets until data loads and values are set.
        binding.colorStylePickerButton.isEnabled = false
        binding.sunriseLocationButton.isEnabled = false
        binding.tideRegionChangeButton.isEnabled = false
        binding.tideSpotChangeButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            stateHolder.uiState
                .collect { uiState: WatchFaceConfigStateHolder.EditWatchFaceUiState ->
                    when (uiState) {
                        is WatchFaceConfigStateHolder.EditWatchFaceUiState.Loading -> {
                            Log.d(TAG, "StateFlow Loading: ${uiState.message}")
                        }
                        is WatchFaceConfigStateHolder.EditWatchFaceUiState.Success -> {
                            Log.d(TAG, "StateFlow Success.")
                            updateWatchFacePreview(uiState.userStylesAndPreview)
                        }
                        is WatchFaceConfigStateHolder.EditWatchFaceUiState.Error -> {
                            Log.e(TAG, "Flow error: ${uiState.exception}")
                        }
                    }
                }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateWatchFacePreview(
        userStylesAndPreview: WatchFaceConfigStateHolder.UserStylesAndPreview
    ) {
        Log.d(TAG, "updateWatchFacePreview: $userStylesAndPreview")

        val colorStyleId: String = userStylesAndPreview.colorStyleId
        Log.d(TAG, "\tselected color style: $colorStyleId")

        binding.locationLabel.text = "${userStylesAndPreview.sunriseLat.format(3)}, ${
            userStylesAndPreview.sunriseLon.format(3)
        }"
        tideRegionIdx = userStylesAndPreview.tideRegionIdx.toInt()
        tideSpotIdx = userStylesAndPreview.tideSpotIdx.toInt()
        val tideRegion = enumValues<TideLocationResourceIds>()[tideRegionIdx]
        if (tideSpotIdx >= tideRegion.locations.size){
            tideSpotIdx = 0
        }
        val tideSpot : String = tideRegion.locations[tideSpotIdx]
        binding.tideRegion.text = tideRegion.regionName
        binding.tideSpot.text = tideSpot
        binding.preview.watchFaceBackground.setImageBitmap(userStylesAndPreview.previewImage)

        enabledWidgets()
    }

    private fun enabledWidgets() {
        binding.colorStylePickerButton.isEnabled = true
        binding.sunriseLocationButton.isEnabled = true
        binding.tideRegionChangeButton.isEnabled = true
        binding.tideSpotChangeButton.isEnabled = true
    }

    fun onClickColorStylePickerButton(view: View) {
        Log.d(TAG, "onClickColorStylePickerButton() $view")

        // TODO (jacobolson) cycle through list
        // Selects a random color style from list.
        val colorStyleIdAndResourceIdsList = enumValues<ColorStyleIdAndResourceIds>()
        val size = colorStyleIdAndResourceIdsList.size
        colorIdx = (colorIdx + 1)%size
        val newColorStyle: ColorStyleIdAndResourceIds = colorStyleIdAndResourceIdsList[colorIdx]

        stateHolder.setColorStyle(newColorStyle.id)
        binding.colorStylePickerButton.setBackgroundColor(resources.getColor(newColorStyle.primaryColorId))
        binding.colorStylePickerButton.setTextColor(resources.getColor(newColorStyle.backgroundColorId))
    }

    fun onClickLeftComplicationButton(view: View) {
        Log.d(TAG, "onClickLeftComplicationButton() $view")
        stateHolder.setComplication(LEFT_COMPLICATION_ID)
    }

    fun onClickRightComplicationButton(view: View) {
        Log.d(TAG, "onClickRightComplicationButton() $view")
        stateHolder.setComplication(RIGHT_COMPLICATION_ID)
    }

    @SuppressLint("SetTextI18n")
    fun onClickSunriseLocationButton(view: View) {
        // TODO: This is failing sometimes. Not sure what lets it work and what makes it fail
        //  Last known always works, but not sure if that is enough
        Log.d(TAG, "onClickSunriseLocationButton() $view")
        binding.locationLabel.text = "Updating ..."
        if (ActivityCompat.checkSelfPermission(this,
                                               Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Location permission not granted")
            AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("This only requests permission once when Update Location button is pressed")
                .setPositiveButton(
                    "OK"
                ) { _, _ ->
                    //Prompt the user once explanation has been shown
                    ActivityCompat.requestPermissions(this,
                                                      arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                                                      MY_PERMISSIONS_REQUEST_LOCATION)
                }
                .create()
                .show()
            binding.locationLabel.text = "Try Again"
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                                               object : CancellationToken() {
                                                   override fun onCanceledRequested(p0: OnTokenCanceledListener) =
                                                       CancellationTokenSource().token

                                                   override fun isCancellationRequested() = false
                                               }).addOnSuccessListener { location: Location? ->
            if (location != null) {
                setLocation(location)
            } else {
                Toast.makeText(this,
                               "Cannot get location. Requesting Last Known",
                               Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Current location null")
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation: Location? ->
                    if (lastLocation != null) {
                        setLocation(lastLocation)
                    } else {
                        Toast.makeText(this, "Failed Again, try again later", Toast.LENGTH_SHORT)
                            .show()
                        Log.d(TAG, "last known location null")
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setLocation(location: Location) {
        val locationString = "${location.latitude.format(3)}, ${location.longitude.format(3)}"
        binding.locationLabel.text = locationString
        Log.d(TAG, "Location Set: $locationString")
        stateHolder.setSunriseLat(location.latitude)
        stateHolder.setSunriseLon(location.longitude)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location request granted")
                } else {
                    Log.d(TAG, "Location request denied")
                }
                return
            }
        }
    }

    fun onClickRegionChangeButton(view: View){
        Log.d(TAG, "onClickRegionChangeButton() $view")

        val tideStyleIdAndResourceIdsList = enumValues<TideLocationResourceIds>()
        val size = tideStyleIdAndResourceIdsList.size
        tideRegionIdx = (tideRegionIdx + 1)%size
        val newTideRegion: TideLocationResourceIds = tideStyleIdAndResourceIdsList[tideRegionIdx]
        tideSpotIdx = 0
        val tideSpot : String = newTideRegion.locations[tideSpotIdx]
        stateHolder.setTideRegionIdx(tideRegionIdx.toLong())
        stateHolder.setTideSpotIdx(tideSpotIdx.toLong())
        binding.tideRegion.text = newTideRegion.regionName
        binding.tideSpot.text = tideSpot
    }

    fun onClickSpotChangeButton(view: View){
        Log.d(TAG, "onClickSpotChangeButton() $view")

        val tideStyleIdAndResourceIdsList = enumValues<TideLocationResourceIds>()
        val tideRegion: TideLocationResourceIds = tideStyleIdAndResourceIdsList[tideRegionIdx]
        val size = tideRegion.locations.size
        tideSpotIdx = (tideSpotIdx + 1)%size
        val newTideSpot = tideRegion.locations[tideSpotIdx]
        stateHolder.setTideSpotIdx(tideSpotIdx.toLong())
        binding.tideSpot.text = newTideSpot
    }

    companion object {
        const val TAG = "WatchFaceConfigActivity"
        private const val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }
}
