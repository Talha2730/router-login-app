# D-Link WiFi v2.1 — Private Personal Login Autofill

Lightweight Android control app for the D-Link DWR-M960 local router at `http://192.168.0.1/login.htm`.

## v2.1

- The router Login form supports **optional, private username/password autofill**, enabled only on the authenticated owner's **personal signed APK** and **only on the local router host** `http://192.168.0.1`.
- Autofill fills empty username/password fields. It never clicks **Login** automatically and does not transmit credentials to any outside service.
- The optional `assets/private_login_defaults.json` is **not in this public repository**, not in GitHub builds, and never printed to build logs.
- The real login values are only packaged into a private signed APK outside GitHub. Anyone who obtains that APK can extract the values: **do not share the personal APK**. For good security, change any factory-default router administrator password.
- The prior D-Link WiFi logo, mobile-friendly Login screen, fixed zoom, Home dashboard, Settings/Features/Management, Exit button, and automatic reconnect are unchanged.
- Version code 12 (version name 2.1), Android target SDK 35.

## APK builds

GitHub Actions can build the usual **Debug APK** and a **non-debuggable unsigned Release APK**. The GitHub build intentionally **does not contain personalized credentials**. Personal signed copies are created offline with an existing locally backed-up private signing certificate.

The Google Play Protect warning "Play Protect hasn't seen an app from this developer before" relates to its recognition and scanning of sideloaded apps. A release signature or API level alone **cannot permanently disable this protection**. See [Google's official Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance).

## Supported network

- Local router Login: `http://192.168.0.1/login.htm`
- Router Home: `http://192.168.0.1/main.html`
- Auto reconnect while app is open; recheck when reopened; no background auto-launch.
