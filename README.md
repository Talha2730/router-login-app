# D-Link WiFi Android app (v1.5)

A lightweight Android app for a local D-Link DWR-M960 router: `http://192.168.0.1/login.htm`.

## What's new in 1.5
- The native dashboard has larger, bold headings, section-card text and quick router-status values.
- The router's original login form is enlarged and centered on small Android screens.
- Login fields and the Login button use larger text while keeping the original router authentication form.
- Pinch-to-zoom remains disabled. The Android window adjusts when the keyboard appears.

## Router dashboard
Quick status cards read **Signal**, **Network**, **Provider**, **Connected Devices**, **Network Type**, **LTE Band**, **Connection Uptime**, and **IP Address** from the logged-in router's existing web page. Unknown fields show a dash. Tap Refresh to re-read the router Home page.

Home, Settings, Features, and Management are individual touch cards. Logout and Exit controls remain.

## Install
Open GitHub -> Actions -> latest successful **Build Router Login APK** -> Artifacts -> **Router-Login-APK**, extract `app-debug.apk`.

If Android reports "App not installed" or a signing conflict, uninstall the previous D-Link WiFi APK and install the new one. These GitHub-built debug APKs may have different signing certificates.

Connect the phone to your router Wi-Fi. Router credentials are submitted to the original local router page, not to GitHub.

Note: Visual improvements use D-Link firmware page structure. Check on the actual phone/router; the login form may need a minor adjustment on unusual firmware versions.
