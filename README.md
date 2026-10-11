# D-Link WiFi v1.8 — Full Live Home & reliable navigation

Lightweight Android app for the local D-Link DWR-M960 router.

Login address: `http://192.168.0.1/login.htm`
Authenticated router Home address (confirmed in browser screenshot): `http://192.168.0.1/main.html`

## Changes in 1.8

- Made the live router Home view taller on phone screens, so Signal, Internet, Connected Clients, LTE Band, Provider and Connection Uptime are easier to view.
- Moved **Settings**, **Features**, and **Management** underneath the larger live Home area. The main dashboard remains vertically scrollable.
- Replaced the **Log out of router** button with a bold **Home** button, with extra spacing below the controls.
- Changed the in-section **‹ Menu** button to **‹ Home**.
- Fixed Android Back, section back, Home, and Refresh navigation: they now reload the actual router `main.html` Home page instead of displaying a previously selected Settings/Features/Management page. In addition to page-load detection there is a fallback if the router switches content asynchronously.
- Increased font weight and font size of the router's original signal, connected-client and status-table values where supported by its HTML.
- Kept the original D-Link WiFi launcher icon and app name, and kept pinch-to-zoom disabled.

The app does not read/store router passwords or change router configuration on its own. The Home button **does not log out** of the router session; Exit closes the Android app.

## Build & install

GitHub → Actions → latest successful **Build Router Login APK** → **Artifacts** → **Router-Login-APK** → extract `app-debug.apk`.

Connect your phone to the same router's Wi-Fi. The router admin pages are only reachable on the local network. As the router firmware cannot be accessed from GitHub's build environment, verify that returning Home works on your device. If signing certificates differ from a previous debug APK, uninstall the previous app before installing the update.
