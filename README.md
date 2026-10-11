# D-Link WiFi Android app (v1.3)

Lightweight Android wrapper for the local D-Link DWR-M960 router at `http://192.168.0.1/login.htm`.

## Native router menu
- **Home** — opens router Home page.
- **Settings** — opens the router's Settings page.
- **Features** — opens router Features page.
- **Management** — opens router Management page.
- **Logout** — router logout action.
- **Exit** — closes the Android app (with confirmation).

Once logged in, users see a clean, touch-friendly two-by-two dashboard. Tapping a tile selects that section and displays its original router page. A small JavaScript bridge locates and uses the real menu elements in the original web page. It never reads or stores credentials. If the router firmware uses unsupported menu markup, the original menu is shown as fallback.

The app fits legacy desktop-oriented router pages within the phone width and disables pinch zoom. Wi-Fi network access to 192.168.0.1 is required; the app does not require GitHub to run after installation.

## Build APK
GitHub -> Actions -> latest successful **Build Router Login APK** -> Artifacts -> Router-Login-APK -> extract `app-debug.apk`.
