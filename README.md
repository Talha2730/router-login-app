# D-Link WiFi v2.2 — Changeable Password Autofill

Lightweight Android control app for a D-Link DWR-M960 router at `http://192.168.0.1/login.htm`.

## Update your saved router password without rebuilding the APK

The router does not disclose its new admin password after it has been changed. Therefore, **changing the router password cannot automatically disclose the new value to the app**.

To keep Autofill in sync safely:
1. Change your login password in the router Management/Settings page.
2. Tap **Update Autofill Password** in the app (also available on the login screen and near the dashboard Home button).
3. Enter the new password once and tap **Save on this phone**.
4. From then on, Autofill uses that new saved password every time the D-Link login form opens; no APK rebuild is needed.

This **does not automatically click Login** or change the router's password by itself.

### Privacy

The new password is saved only on the phone in Android Keystore AES-GCM encrypted storage. The encryption key cannot be exported from Android Keystore and cloud backup of app data is disabled. The public GitHub source and GitHub Actions builds do not contain personalized passwords. The private signed personal APK may contain the initial login default selected by its owner, so **do not share the personal APK**. Use a strong router password instead of `admin` if the firmware allows.

## Features preserved
- Wi-Fi auto-reconnect when the app is open, including routers without Internet access.
- Live router Home dashboard showing signal, connected clients, carrier, status and uptime.
- Settings, Features and Management buttons.
- Home, Refresh and Exit.
- Mobile screen fit, fixed zoom, D-Link WiFi icon.
- Android 15 target SDK 35, scoped HTTP access to the local router only.

## Build

GitHub Actions → latest successful **Build Unsigned Release APK** for version 2.2 → `DLink-WiFi-Unsigned-Release`. An actual installable personal Release APK must then be signed privately with the same offline certificate used for the previous version.

Google Play Protect can still show an "unknown developer" warning for an independently distributed app. This cannot be disabled from within the app; do not disable Play Protect.
