# D-Link WiFi v2.0 — Updated Android security target

This lightweight Android app opens your local D-Link DWR-M960 router:
- Login: `http://192.168.0.1/login.htm`
- Live dashboard: `http://192.168.0.1/main.html`

## Security update in v2.0

The earlier build targeted Android 9 (SDK 28). On modern phones, Google Play Protect displayed **"Unsafe app blocked — This app was built for an older version of Android"**. This release fixes the obsolete target rather than attempting to turn off or bypass Play Protect:

- **targetSdk 35** (Android 15), minSdk 23.
- Restricts unencrypted HTTP to the **one local router address**, `192.168.0.1`, using Android Network Security Configuration. Other HTTP hosts are blocked by policy. The router itself only provides its admin page over local HTTP; credentials still travel without HTTPS encryption within the local network.
- Handles modern Android 15 edge-to-edge status/navigation bar insets so touch controls are not obscured.
- GitHub Actions checks that the built APK really targets API 35 and verifies its debug signature before uploading it.

## Unchanged functionality

- Automatic local Wi-Fi reconnection while the app is open.
- Show the real signal strength, connected clients, network status, LTE band and uptime on the live dashboard.
- Settings, Features and Management controls below the live dashboard.
- Home and Refresh buttons, Exit and D-Link WiFi launcher logo.
- Mobile fixed-width layout with pinch zoom disabled.

## APK installation

Open [GitHub Actions](https://github.com/talhakalwal21/router-login-app/actions), choose the **latest successful** Build Router Login APK run, and download **Artifacts → Router-Login-APK**. Extract `app-debug.apk` and install it.

**Important:** An APK built by GitHub Actions is still a **debug-signed, sideloaded app**, not a Google Play-distributed verified release. Targeting API 35 addresses the *old Android version* warning, but Google Play Protect may still apply other security checks. Do **not** disable Play Protect. If an APK raises another warning, inspect that warning rather than bypassing it.

GitHub-hosted builds may use different debug signing keys between runs; if Android refuses to update the existing installation, uninstall the older D-Link WiFi app, then install the new APK. No laptop Android Studio installation is needed.
