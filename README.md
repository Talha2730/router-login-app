# D-Link WiFi v1.9 — Automatic Router Reconnect

Lightweight Android Wi-Fi router control app for the D-Link DWR-M960.

- Local login: `http://192.168.0.1/login.htm`
- Authenticated Home: `http://192.168.0.1/main.html`

## New in v1.9: automatic reconnect

When **D-Link Wi-Fi disconnects** while this app is open, a small status strip indicates the app is waiting to reconnect. The Android app:
1. Watches Wi-Fi network arrival/loss events, including Wi-Fi networks **without Internet access**.
2. Checks the local router's HTTP endpoint in the background, with short timeouts, every 12 seconds while the app is on screen; it never tries to use the public Internet to detect this router.
3. When the router responds again, automatically reloads the correct page (no manual Refresh required).
4. If the previous router login session remains valid, automatically opens the **live Home dashboard**. Otherwise the router's normal **Login** screen is displayed so the user can sign in safely.
5. Rechecks upon returning to the app from the background.

When Android considers mobile data the default route, the app attempts to route its own connections through the connected Wi-Fi network so `192.168.0.1` continues to work.

### Preserved
- Larger native Live Home page for viewing **Signal**, **Connected Clients**, **Network Status**, **Provider**, and **Uptime**.
- Settings, Features, Management buttons below the live dashboard.
- Home button, Refresh, and Exit.
- Bold status text, D-Link WiFi name and existing logo.
- Pinch-to-zoom disabled.

### Notes
Auto-reconnect runs while the app is **open/on screen**, and checks connectivity again when the app is reopened. It does not start the Android app automatically in the background. A router authentication session that expires requires signing in again; the app does not store login passwords.

## Build and install APK
Open [GitHub Actions](https://github.com/talhakalwal21/router-login-app/actions), choose the latest successful **Build Router Login APK**, then **Artifacts → Router-Login-APK** and extract `app-debug.apk`.

Build/installation can be performed entirely online; Android Studio is not required on a laptop. The real D-Link router behavior must be verified on a device connected to the router Wi-Fi.
