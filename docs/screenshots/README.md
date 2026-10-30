# README screenshots

The gallery shows real production composables rendered on a physical Samsung Galaxy S22 Ultra. The library uses demonstration filenames and the viewer opens a local sample presentation. Capture does not edit the user's history or preferences. Screenshots contain app content rather than system notifications or phone identifiers.

| Asset | Production screen | Theme |
| --- | --- | --- |
| `library-light.png` | `HomeScreenContent` with sample file metadata | Light |
| `slides-light.png` | `ViewerScreen` opening the demo PPTX | Light |
| `settings-light.png` | `SettingsContent` with sample preferences | Light |

To refresh the gallery, connect a physical device and use its explicit serial:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=1
adb -s YOUR_DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s YOUR_DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s YOUR_DEVICE_SERIAL shell am instrument -w \
  -e class com.asta.cherryoffice.ReadmeScreenshotsTest \
  com.asta.cherryoffice.test/androidx.test.runner.AndroidJUnitRunner
adb -s YOUR_DEVICE_SERIAL pull \
  /sdcard/Android/data/com.asta.cherryoffice/files/readme/. docs/screenshots/
```

Inspect all three images before replacing the gallery. Keep the original app icon, avoid private content, and do not paint over the captured UI or generate substitute app screens. The sample presentation lives at `app/src/androidTest/assets/cherry-overview.pptx`.
