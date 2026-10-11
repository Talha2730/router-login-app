# D-Link WiFi v2.3 — Truly Readable Mobile Dashboard

This Android app is for the local D-Link DWR-M960 router on `http://192.168.0.1/login.htm`.

## What changed

The firmware provides a desktop-width (about 980 CSS pixels) Home/status page. When WebView fits all four columns on a phone, signal and network values inevitably become tiny. Increasing fonts or zooming the **whole** web page caused the right side to be cropped.

Version **2.3 replaces that miniature status page** with **12 large native Android status cards**, fed by the original logged-in router WebView in the background:

- Signal Strength, Connected Devices
- Network Status, Network Provider
- Connection Uptime, LTE Band
- Network Type, IP Address
- Default Gateway, Primary DNS
- Secondary DNS, IMEI

The app reads *only* labeled, non-secret router values from the same authenticated local page. The cards refresh approximately every 12 seconds while the app is in the foreground, as well as after Home/Refresh/navigation events. Values that cannot be extracted from a particular router firmware show a dash rather than an invented number.

Use **Open Original Router Page** for the original firmware view if some fields cannot be read. The original page is kept loaded in the app, so Settings, Features, Management and Home still work. New native cards are vertically scrollable on small phones, not horizontally cropped.

All previous features are retained:
- Wi-Fi automatic reconnect, Home, Refresh, Exit, D-Link icon.
- Router Login visual fit and optional private autofill.
- Change stored login password securely with Android Keystore.
- Zoom disabled.
- Android target SDK 35.
- Same application ID and (for private releases) same signing key.

## APK

The public GitHub Action builds unsigned releases and debug builds. For personal APK installation, download the privately signed version provided in the conversation. Your personal router passwords should not be committed to this repository.

An unknown-developer Play Protect warning may still appear for independent APK distribution; don't disable Play Protect.
