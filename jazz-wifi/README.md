# Jazz WiFi v1.1 — exact jazz.wifi URL with Private DNS enabled

The only router web address exposed to Android WebView is:

**http://jazz.wifi/index.html**

The previous version guessed router IPs like `192.168.8.1` and directly navigated to numeric addresses. That could show a false connection status and fail when the router uses virtual-host routing.

## Private DNS fix

- On Wi-Fi, learn a LAN address from a direct local DNS lookup for `jazz.wifi`, or the Wi-Fi network's gateway / a manually selected router IP. No D-Link IPs or invented fallback IPs are used.
- Probe `/index.html` using a Wi-Fi-bound TCP socket and **`Host: jazz.wifi`**.
- Keep **exactly** `http://jazz.wifi/index.html` in the WebView address and navigation.
- Use Android WebKit's per-app `ProxyController` pointing to a **127.0.0.1-only HTTP proxy**. Only requests addressed to `jazz.wifi` are allowed. Normal HTTP requests, POST login submissions, cookies, relative URLs, CSS, JavaScript and XMLHttpRequests pass through this local proxy with the original hostname intact.
- Proxy forwards requests to the private router IP **over the active Wi-Fi Network**, not public Internet, mobile data, or external DNS.
- The Android device's Private DNS setting is untouched. No VPN is installed, and there is no public proxy or third-party relay.
- Never show green "Connected" simply because a candidate IP replied; the actual WebView document must complete without a main-frame network error.
- Home, Refresh, automatic reconnect, Router IP fallback, and Exit preserved.

**Limitations:** This implementation is HTTP-only because the router URL is HTTP. It requires an Android System WebView implementation with `PROXY_OVERRIDE` support. If that feature is missing, the app asks the user to update Android System WebView. On a router where local DNS/DHCP does not reveal its IP, the user can enter the correct router IP once into **Router IP**; the *WebView's URL* stays `jazz.wifi`. Connection to the correct Jazz router's Wi-Fi is required.

## Build

GitHub Actions → **Build Jazz WiFi APK** → Debug APK or unsigned Release. The personal APK can be signed privately using the previous Jazz WiFi certificate.

Google Play Protect may still warn about an unknown sideloaded developer. The app does not disable Play Protect.
