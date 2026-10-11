# D-Link WiFi Android app (v1.4)

A lightweight local-router control app for `http://192.168.0.1/login.htm` on the D-Link DWR-M960.

## Home — Quick Router Status
The native Home Dashboard now contains easy-to-read cards for **Signal**, **Network Status**, **Provider**, **Connected Devices**, **Network Type**, **LTE Band**, **Connection Uptime**, and **IP Address**, with a **Refresh** button.

These values come from the authenticated router page already open in the app. They are **not** hardcoded, downloaded from the internet, or sent to GitHub. Values not exposed by a particular router firmware are shown as a dash (—). Refresh explicitly opens the router Home section and re-reads it.

## Menu
Home, Settings, Features, and Management remain separate, touch-friendly cards and use the router's original navigation. An Exit button closes the app. Pinch zoom stays disabled.

## Install
Open GitHub Actions and choose the latest successful **Build Router Login APK**, then under **Artifacts** download **Router-Login-APK**. Extract and install `app-debug.apk`. Connect your Android phone to the router Wi-Fi first. The installed app does not require GitHub to work.
