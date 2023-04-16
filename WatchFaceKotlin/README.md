atchFace Sample (Kotlin)
===============================
Demonstrates watch faces using the new androidX libraries (Kotlin).

Introduction
------------
With the release of the androidX watch face libraries (late 2020), Wear OS has simplified
watch face development.

This sample gives you an early preview of how you can simplify your development with the new 100%
Kotlin libraries.

Steps to build in Android Studio
--------------------------------
Because a watch face only contains a service, that is, there is no Activity, you first need to turn
off the launch setting that opens an Activity on your device.

To do that (and once the project is open) go to Run -> Edit Configurations. Select the **app**
module and the **General** tab. In the Launch Options, change **Launch:** to **Nothing**. This will
allow you to successfully install the watch face on the Wear device.

When installed, you will need to select the watch face in the watch face picker, i.e., the watch
face will not launch on its own like a regular app.

For more information, check out our code lab:
[1]: https://codelabs.developers.google.com/codelabs/watchface/index.html

Screenshots
-------------

<img src="screenshots/analog-face.png" width="400" alt="Analog Watchface"/>
<img src="screenshots/analog-watch-side-config-all.png" width="400" alt="Analog Watchface Config"/>
<img src="screenshots/analog-watch-side-config-1.png" width="400" alt="Analog Watchface Config"/>
<img src="screenshots/analog-watch-side-config-2.png" width="400" alt="Analog Watchface"/>

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the "gradlew build" command or
use "Import Project" in Android Studio.

Support
-------

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/android/wear-os-samples

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.

**How To Tips:
- Connecting to the watch to run on there: 
  
  on the watch go in to developer settings and make sure `ADB debugging` is set to true. Then enable `Debug over Wi-Fi` (Leave this one turned off when not actively using it because it saps your battery) 

  Then go into connection settings, click on wifi when you are connected to a network, click on it and scroll down to find the IP address of the watch
  then you need to run the adb connect command in a terminal. the valid executable is stored by default in `~/Android/Sdk/platform-tools/adb` so to get this to run a bit easier. you can link this into /usr/bin/adb by doing this: 
    ```
    sudo ln -s ~/Android/Sdk/platform-tools/adb /usr/bin/adb
    ```

  After you run that, you will have to accept the connection on your watch. There is an option to always allow, that way in the future it will just connect.

  Then you are good to run the app directly on your watch

- Adding tides to watch: use `tide_grabber.py` located in `WatchFaceKotlin/app/src/main/assets`
  This will print when run without any command line args:

  ```
  To run this script: python3 tide_grabber.py <Station ID> <year> or python3 tide_grabber.py auto
  If 'auto' argument passed in, it will automatically load designated tides
  Station ID can be found by visiting https://tidesandcurrents.noaa.gov/map/index.html?type=TidePredictions&region=, then selecting the desired location
  Years available are the current year +/- 2 years
  Example for Balboa Pier, Newport Beach 2023: python3 tide_grabber.py 9410583 2023
  ```

  To add an auto downloaded tide, just find the station id from the link above, then add it to the list that starts on line 86 of tide_grabber.py. 
  
  then run `tide_grabber.py auto`. This will download 5 years of tides for all the locations in that list and save them to the assets folder

  You will also need to add information in the following places:

  `TideLocationResourseIds.kt` just follow the current design scheme to add tides into this. This is the order that they will cycle through in the settings. So far I have organized it from north to south

  I think that is it

