# D-Link WiFi (Router Login) — Android

Lightweight offline-capable WebView wrapper for the D-Link router page at `http://192.168.0.1/login.htm`.
To use the app, connect your Android phone to the same router Wi-Fi.

- **Fixed screen:** WebView pinch-zoom and zoom controls disabled.
- **Desktop layout fit:** old router HTML scales down automatically to the phone width, preserving original navigation.
- **App name:** D-Link WiFi
- **Logo:** user-supplied blue Wi-Fi logo (launcher icon, including adaptive icon on Android 8+).
- **APK version:** 1.2

## Build and install
GitHub Actions -> **Build Router Login APK** -> latest successful run -> **Artifacts** -> **Router-Login-APK**. Extract `app-debug.apk` and install on the phone.
If the phone still shows the previous app name or logo, uninstall the old Router Login app before installing the new APK.
The router login credentials are used on the local router page; they are not uploaded to GitHub.
