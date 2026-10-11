# Jazz WiFi v1.0 — Android App (Private DNS Friendly)

An **independent Android application** for the router page:
`http://jazz.wifi/index.html`. This project is inside `jazz-wifi/`
and **does not overwrite the existing D-Link WiFi app**.

## Why `jazz.wifi` sometimes fails with Android Private DNS

`jazz.wifi` is a router-local hostname. Private DNS may send DNS lookups
to a resolver that cannot resolve that private name. The solution here
does **not** disable or override Android's Private DNS setting.

This app:

1. Requires a Wi-Fi connection to the Jazz router, then locates the
   connected Wi-Fi network even when Android's mobile data is the default.
2. Uses the Wi-Fi DHCP default gateway and a **direct UDP request to the
   Wi-Fi router's local DNS service** (when available) to find
   the private IPv4 address for `jazz.wifi`.
3. Opens `http://<local-router-IP>/index.html` directly, so normal page
   navigation does not require resolving `jazz.wifi` with system DNS.
4. Rewrites top-level navigation back to `jazz.wifi` onto that local IP.
5. Provides an optional **Router IP** button if auto-detection fails.
6. Automatically reconnects and reloads when the Jazz Wi-Fi router
   becomes reachable again while the app is open.
7. Preserves the firmware's real Dashboard/Login/Settings as shown by
   the router, rather than inventing fake status data.

The app **does not** contain a VPN, DNS override for other apps,
external proxy, remote login service, or stored router password.
The router's admin authentication is unchanged.

## Controls
- **Home**: open the portal home page.
- **Refresh**: refresh the current router page.
- **Router IP**: specify or reset a saved private router IPv4 address.
- **Exit**: close the app.
- Original router menu: all genuine features remain inside the web page.

## Limits
IP access is supported on most MiFi routers, but firmware may require
the `Host: jazz.wifi` HTTP header or absolute `jazz.wifi` script URLs.
Those models may still need a firmware-specific adjustment.
Private DNS **can remain ON**, but actual compatibility needs testing
against your Jazz device before it can be guaranteed.

Since the Jazz router uses HTTP on its local network, connect only to
your own trusted Wi-Fi; the admin interface is not HTTPS-encrypted.

## Build APK
In this repository go to **Actions → Build Jazz WiFi APK**, open the
latest successful run and download `Jazz-WiFi-Debug-APK`.

For normal installation, use the separately **privately signed Release APK**
produced from the `Jazz-WiFi-Unsigned-Release` GitHub artifact.
Android target SDK: 35 (Android 15); minimum SDK: 23.

Signing keys and router credentials are never committed to GitHub.
