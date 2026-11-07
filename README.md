# PhotoFrame TV

Native Android TV photo frame. The TV shows a QR code and hosts a small web server;
scan the code with your phone's normal camera, pick photos, and they appear on the TV.
No cloud, no accounts, no phone app.

- Targets the original Xiaomi Mi TV Stick (Android 9) and other Android TV devices (minSdk 21).
- Photos are stored privately on the TV (`filesDir/photos`) and survive app restarts.
- Phone and TV must be on the same Wi-Fi network.

## Remote control

| Screen | Keys |
|---|---|
| Main menu | D-pad to move, OK to choose |
| Library | OK on a photo = select/unselect; buttons at the bottom for Select all / Delete selected |
| Main menu | Shuffle and Captions toggles (saved), Slide duration button |
| Slideshow | Left/Right = previous/next, OK (or Play/Pause) = pause/resume, Up/Down = slide duration (3-60 s), **hold OK** (or Menu/Info) = settings list for Shuffle and Captions, Green/Yellow colour keys = toggle Shuffle/Captions if your remote has them, Back = exit |

## Shuffle and captions

- **Shuffle** plays every photo once in random order, then reshuffles; the first photo of a new round is never the one just shown. Turning it on/off mid-show does not interrupt the current photo.
- **Captions** show at the bottom of the screen. On the phone page tick "Add a caption to each photo" to type one per photo before uploading; photos without a typed caption show their file name instead.
- Both settings are saved and also apply to the system screensaver.

## Photo formats

JPEG, PNG, WebP, GIF (first frame), BMP and HEIC/HEIF. HEIC is converted to JPEG by the phone's browser when it can
(iPhones and recent Android Chrome). If the phone can't convert it, the original is sent and the TV tries to decode it
natively (works only if the TV has an HEVC image decoder); otherwise the phone page shows a clear error.

## System screensaver

Settings > Device Preferences > Screen saver > choose "PhotoFrame TV".
The main menu has a "Set up screensaver" button that opens the system screensaver settings.
If your TV does not show third-party screensavers, enable it over adb (TV and PC on the same network):

    adb connect <tv-ip>:5555
    adb shell settings put secure screensaver_components com.s7zb.photoframetv/.PhotoDream

The in-app slideshow always works regardless.

## Upload server and background mode

By default the server runs only while the app is open (low battery/RAM use). "Background server: On" starts a
foreground service (with a notification) so uploads keep working while the app is not on screen. One shared
server instance is used, so there are no port conflicts (ports 8080-8090 are tried).

## Build

GitHub Actions (`.github/workflows/build.yml`) builds `PhotoFrameTV.apk` on every push.
Locally: JDK 17 + Android SDK 34 + Gradle 8.7, then `gradle assembleDebug`.
The APK is signed with the committed `app/photoframe.keystore` so updates install over older versions.
