# Photo Frame TV

Android TV app (Xiaomi Mi TV Stick compatible): custom photo screensaver + built-in wireless upload server.
No cloud, no accounts, no phone app. About 1 MB APK (only dependency: ZXing core for QR generation).

## Build the APK
Requires Android Studio (Hedgehog or newer) **or** JDK 17 + Android SDK (platform 34).

Android Studio: File > Open > this folder > Build > Build APK(s).
Command line (with Gradle 8.2+ installed once, to create the wrapper):

    gradle wrapper --gradle-version 8.2
    ./gradlew assembleRelease
    # -> app/build/outputs/apk/release/app-release.apk   (debug-signed, directly installable)

## Install on the Mi TV Stick
1. TV: Settings > Device Preferences > About > tap "Build" 7 times; then Developer options > enable USB/Network debugging.
2. Computer on same Wi-Fi:

       adb connect <TV-IP>:5555
       adb install -r app-release.apk

## Use
1. Open "Photo Frame". A QR code is shown.
2. Phone (same Wi-Fi): scan the QR -> browser upload page -> choose photos -> Upload.
3. Photos appear on the TV as thumbnails; progress shows on the phone.
4. Screensaver: Settings > Device Preferences > Screen saver > choose "Photo Frame"
   (or: `adb shell settings put secure screensaver_components com.photoframe.tv/.PhotoDreamService`).

Remote: D-pad navigates buttons. In Slideshow: Left/Right = prev/next, OK = pause, Down/Menu = delete photo, Back = exit.

## How the upload works
`PhotoServer.kt` is a small HTTP server (ports 8080-8099). QR = `http://<tv-ip>:<port>/?t=<secret>`.
The page uploads each photo as a raw-body POST (`/api/upload`) so the browser shows true progress; the TV validates it is an
image and stores it in the app's private storage. Uploads require the secret token from the QR code.
The server runs while the app is open (stops when you exit it with Back).
