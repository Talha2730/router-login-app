# D-Link WiFi v1.7 — Compact mobile dashboard

A small Android WebView application for the D-Link DWR-M960 at `http://192.168.0.1/login.htm`.

## v1.7 changes

- Removed three redundant labels from the dashboard: **Router Dashboard**, **Your live router home and controls**, and **Live Router Status**.
- Preserved the **actual live Home page** shown by the router, with its real signal, connected-client count, mobile network status, carrier, LTE band and uptime.
- Added conservative bold styling for the router's status tables (label/value text) to make the original status screen clearer without changing authentication or router configuration.
- Reduced blank space above the status page, sized the embedded Home view relative to the display height, and made the Settings / Features / Management cards more compact so they fit on typical phone screens. Vertical scrolling remains available on smaller phones.
- Kept the **Refresh**, **Logout**, **Exit**, D-Link WiFi launcher icon/name and disabled pinch-to-zoom.

## Download

GitHub → Actions → most recent successful **Build Router Login APK** → Artifacts → **Router-Login-APK** → extract `app-debug.apk`.

The app runs locally and needs a connection to the router's Wi-Fi. GitHub is only used to create the APK. The live page layout and font changes should be checked on the actual phone; some router firmware versions use different markup.

If installing over an older GitHub debug APK causes a signing conflict, uninstall the older application first.
