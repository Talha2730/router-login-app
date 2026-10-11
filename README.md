# D-Link WiFi v1.6 — Live Router Home Dashboard

A lightweight Android app for the local D-Link DWR-M960 router at `http://192.168.0.1/login.htm`.

## Changes in v1.6
- Removed the separate **Home** card and the unreliable duplicate status tiles that showed dashes.
- The **actual, authenticated D-Link Home page** is embedded at the top of the native Router Dashboard. Signal intensity, network status, connected clients, provider, uptime and IP information are rendered by the router's own page, not extracted or hardcoded.
- Retained three native section cards: **Settings**, **Features** and **Management**.
- Added a **Refresh** button above the live Home page and returned to Home after leaving a router section via **Menu**.
- Kept **Logout**, **Exit**, the D-Link WiFi name/logo, larger headings and disabled pinch zoom.

The live Home view is a fixed-height WebView inside the scrollable dashboard; scroll the dashboard to reach the control cards. Router content depends on an active Wi-Fi connection and an authenticated session.

## APK
GitHub Actions → latest successful **Build Router Login APK** → **Artifacts** → **Router-Login-APK** → extract and install `app-debug.apk`.

If Android reports a certificate conflict, uninstall the previous app and install this APK again. Backup settings you changed on the router as appropriate before upgrades; the APK itself does not store router records.

This build must still be verified on the DWR-M960 device because its admin page is only available on the local network.
