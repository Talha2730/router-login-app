package com.jazzwifi.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Private-DNS-compatible wrapper for a Jazz Wi-Fi router.
 *
 * WebView ALWAYS opens the original http://jazz.wifi/index.html URL.
 * An HTTP proxy listening ONLY on this phone's 127.0.0.1 carries WebView
 * traffic over the selected Wi-Fi interface, with "Host: jazz.wifi".
 * The upstream IP is learned privately from DHCP/local-DNS; neither Android
 * Private DNS settings nor this URL are changed. No external proxy/VPN.
 */
public final class MainActivity extends Activity {
    private static final String ROUTER_NAME = "jazz.wifi";
    private static final String ROUTER_URL = "http://jazz.wifi/index.html";
    private static final long CHECK_INTERVAL_MS = 12000L;
    private static final int RED = Color.rgb(194, 13, 52);
    private static final int DARK = Color.rgb(26, 46, 60);
    private static final int BACKGROUND = Color.rgb(240, 246, 249);
    private static final int BORDER = Color.rgb(212, 227, 233);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ConnectivityManager connections;
    private ConnectivityManager.NetworkCallback wifiCallback;
    private Network wifi;
    private WebView browser;
    private TextView statusLabel;
    private TextView addressLabel;
    private TextView overlayLabel;
    private View overlay;
    private boolean visible = false;
    private boolean connected = false;
    private boolean scanning = false;
    private String currentIp;
    private String currentPath = "/index.html";
    private String manualIp;
    private boolean hadWebPage = false;
    private boolean mainFrameFailed = false;
    private boolean proxyReady = false;
    private boolean proxySetupPending = false;
    private JazzHostProxy localProxy;
    private int proxyPort = -1;
    private long refreshGeneration = 0;
    private final Runnable periodicCheck = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            checkRouter(false);
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius, int border) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) background.setStroke(dp(1), border);
        return background;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button button(String title, int width, boolean solid) {
        Button action = new Button(this);
        action.setText(title);
        action.setTextSize(14);
        action.setAllCaps(false);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setTextColor(solid ? Color.WHITE : RED);
        action.setBackground(rounded(solid ? RED : Color.WHITE, 13,
            solid ? Color.TRANSPARENT : BORDER));
        action.setMinHeight(0);
        action.setMinimumHeight(0);
        action.setPadding(dp(7), 0, dp(7), 0);
        action.setLayoutParams(new LinearLayout.LayoutParams(dp(width), dp(43)));
        return action;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manualIp = getPreferences(MODE_PRIVATE).getString("router_ip", "");
        connections = (ConnectivityManager) getSystemService(
            Context.CONNECTIVITY_SERVICE);
        buildInterface();
        configureBrowser();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(RED);
        header.setPadding(dp(16), dp(10), dp(15), dp(10));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(82)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.jazz_wifi_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams =
            new LinearLayout.LayoutParams(dp(52), dp(52));
        iconParams.rightMargin = dp(10);
        header.addView(logo, iconParams);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        titles.addView(label("Jazz WiFi", 23, Color.WHITE, true));
        titles.addView(label("LOCAL ROUTER CONTROL", 10,
            Color.rgb(255, 218, 228), false));

        Button exit = button("Exit", 69, false);
        exit.setTextColor(RED);
        header.addView(exit);
        exit.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Exit Jazz WiFi?")
            .setMessage("Close the router app?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Exit", (d, which) -> finishAffinity())
            .show());

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(12), dp(9), dp(12), dp(7));
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(62)));

        Button home = button("Home", 90, false);
        navigation.addView(home);
        home.setOnClickListener(v -> {
            if (connected && currentIp != null) loadHome();
            else checkRouter(true);
        });

        Button refresh = button("Refresh", 94, false);
        LinearLayout.LayoutParams refreshParams =
            new LinearLayout.LayoutParams(dp(94), dp(43));
        refreshParams.leftMargin = dp(9);
        navigation.addView(refresh, refreshParams);
        refresh.setOnClickListener(v -> {
            if (connected && browser != null) browser.reload();
            else checkRouter(true);
        });

        TextView spacer = label("", 10, DARK, false);
        navigation.addView(spacer, new LinearLayout.LayoutParams(0, -2, 1f));

        Button ip = button("Router IP", 92, false);
        navigation.addView(ip);
        ip.setOnClickListener(v -> editRouterIp());

        statusLabel = label("Connect your phone to Jazz Wi-Fi.", 12,
            Color.rgb(90, 109, 120), true);
        statusLabel.setGravity(Gravity.CENTER_VERTICAL);
        statusLabel.setPadding(dp(18), 0, dp(14), 0);
        root.addView(statusLabel, new LinearLayout.LayoutParams(-1, dp(37)));

        FrameLayout webFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams =
            new LinearLayout.LayoutParams(-1, 0, 1f);
        frameParams.setMargins(dp(8), dp(1), dp(8), dp(7));
        root.addView(webFrame, frameParams);
        webFrame.setBackground(rounded(Color.WHITE, 14, BORDER));

        browser = new WebView(this);
        browser.setBackgroundColor(Color.WHITE);
        webFrame.addView(browser, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(18), dp(14), dp(18), dp(14));
        loading.setBackgroundColor(Color.WHITE);
        webFrame.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        overlay = loading;

        ProgressBar progress = new ProgressBar(this);
        loading.addView(progress,
            new LinearLayout.LayoutParams(dp(42), dp(42)));
        overlayLabel = label("Detecting Jazz Wi-Fi router...", 15, DARK, true);
        overlayLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams overlayParams =
            new LinearLayout.LayoutParams(-1, -2);
        overlayParams.topMargin = dp(20);
        loading.addView(overlayLabel, overlayParams);

        addressLabel = label("Router address: http://jazz.wifi/index.html", 11,
            Color.rgb(108, 121, 130), false);
        addressLabel.setGravity(Gravity.CENTER);
        root.addView(addressLabel, new LinearLayout.LayoutParams(-1, dp(34)));
    }

    private void configureBrowser() {
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.setInitialScale(0);

        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                return rewriteOrBlock(request.getUrl());
            }

            @Override public void onPageStarted(WebView view, String url,
                    android.graphics.Bitmap favicon) {
                mainFrameFailed = false;
            }

            @Override public void onReceivedHttpError(WebView view,
                    WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    mainFrameFailed = true;
                    setStatus("Jazz router HTTP error " + response.getStatusCode(),
                        RED);
                    showOverlay("Jazz router page returned HTTP "
                        + response.getStatusCode()
                        + ".\nCheck Wi-Fi or refresh.");
                }
            }

            @Override public boolean shouldOverrideUrlLoading(
                    WebView view, String url) {
                return rewriteOrBlock(Uri.parse(url));
            }

            @Override public void onPageFinished(WebView view, String url) {
                // Only an actual successful router document can set green.
                // WebView calls onPageFinished even for a network error page.
                if (isRouterUrl(url) && !mainFrameFailed) {
                    hadWebPage = true;
                    hideOverlay();
                    setStatus("Jazz WiFi connected · Private DNS can remain ON",
                        Color.rgb(35, 122, 81));
                    addressLabel.setText(ROUTER_URL);
                }
            }

            @Override public void onReceivedError(WebView view,
                    WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                mainFrameFailed = true;
                connected = false;
                hadWebPage = false;
                showOverlay("Unable to load " + ROUTER_URL
                    + ".\nChecking local Jazz router connection...");
                setStatus("Jazz website unavailable — retrying automatically", RED);
                handler.postDelayed(() -> checkRouter(false), 950);
            }
        });
    }

    private boolean rewriteOrBlock(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme();
        if ("about".equalsIgnoreCase(scheme)) return false;
        if (!"http".equalsIgnoreCase(scheme)) return true;
        if (ROUTER_NAME.equalsIgnoreCase(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 80)) return false;
        Toast.makeText(this, "Only " + ROUTER_URL + " is allowed.",
            Toast.LENGTH_SHORT).show();
        return true;
    }

    private boolean isRouterUrl(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            return "http".equalsIgnoreCase(uri.getScheme())
                && ROUTER_NAME.equalsIgnoreCase(uri.getHost());
        } catch (Exception ignored) { return false; }
    }

    private void ensureProxyAndLoad() {
        if (localProxy == null || proxyPort <= 0) {
            showOverlay("Local Jazz DNS helper unavailable. Please reopen the app.");
            return;
        }
        if (proxyReady) {
            browser.loadUrl(ROUTER_URL);
            return;
        }
        if (proxySetupPending) return;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            setStatus("Android System WebView needs updating for Private DNS",
                RED);
            showOverlay("Update Android System WebView in Google Play, then "
                + "reopen Jazz WiFi. The original address is " + ROUTER_URL);
            return;
        }
        proxySetupPending = true;
        try {
            ProxyConfig config = new ProxyConfig.Builder()
                .addProxyRule("http://127.0.0.1:" + proxyPort)
                .build();
            ProxyController.getInstance().setProxyOverride(
                config, runnable -> handler.post(runnable), () -> {
                    proxySetupPending = false;
                    proxyReady = true;
                    if (visible && connected) browser.loadUrl(ROUTER_URL);
                });
        } catch (Exception ignored) {
            proxySetupPending = false;
            setStatus("Local Jazz WebView proxy could not start", RED);
            showOverlay("Unable to configure Private DNS helper. "
                + "Update Android System WebView and retry.");
        }
    }

    private void showOverlay(String message) {
        overlayLabel.setText(message);
        overlay.setVisibility(View.VISIBLE);
    }

    private void hideOverlay() {
        overlay.setVisibility(View.GONE);
    }

    private void setStatus(String message, int color) {
        statusLabel.setText(message);
        statusLabel.setTextColor(color);
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        try {
            if (localProxy == null) localProxy = new JazzHostProxy();
            proxyPort = localProxy.start();
        } catch (Exception ignored) {
            proxyPort = -1;
            showOverlay("Unable to start secure local router helper.");
        }
        monitorWifi();
        checkRouter(true);
        handler.removeCallbacks(periodicCheck);
        handler.postDelayed(periodicCheck, CHECK_INTERVAL_MS);
    }

    @Override protected void onStop() {
        visible = false;
        ++refreshGeneration;
        handler.removeCallbacks(periodicCheck);
        if (connections != null) {
            if (wifiCallback != null) {
                try { connections.unregisterNetworkCallback(wifiCallback); }
                catch (Exception ignored) { }
                wifiCallback = null;
            }
            try { connections.bindProcessToNetwork(null); }
            catch (Exception ignored) { }
        }
        wifi = null;
        super.onStop();
    }

    private void monitorWifi() {
        if (connections == null || wifiCallback != null) return;
        NetworkRequest request = new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        wifiCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                handler.post(() -> checkRouter(false));
            }

            @Override public void onLost(Network network) {
                handler.post(() -> {
                    if (!visible) return;
                    if (wifi != null && wifi.equals(network)) {
                        wifi = null;
                        connected = false;
                        currentIp = null;
                        hadWebPage = false;
                        if (localProxy != null) localProxy.setTarget(null, null);
                        showOverlay("Jazz Wi-Fi disconnected.\nConnect again to continue.");
                        setStatus("Waiting for Jazz Wi-Fi...", RED);
                    }
                    checkRouter(false);
                });
            }

            @Override public void onCapabilitiesChanged(
                    Network network, NetworkCapabilities caps) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    handler.post(() -> {
                        if (visible && !connected) checkRouter(false);
                    });
                }
            }
        };
        try {
            connections.registerNetworkCallback(request, wifiCallback);
        } catch (Exception ignored) {
            wifiCallback = null;
        }
    }

    private Network findWifi() {
        if (connections == null) return null;
        try {
            for (Network network : connections.getAllNetworks()) {
                NetworkCapabilities caps = connections.getNetworkCapabilities(network);
                if (caps != null &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    return network;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static final class RouterEndpoint {
        final String ip;
        final String path;
        RouterEndpoint(String ip, String path) {
            this.ip = ip;
            this.path = path;
        }
    }

    private RouterEndpoint tryCandidate(Network network, String ip) {
        if (!LocalResolver.isPrivateIpv4(ip)) return null;
        // Probe using exact hostname semantics: HTTP Host: jazz.wifi.
        // A raw Wi-Fi socket bypasses the system Private DNS resolver.
        try (Socket upstream = network.getSocketFactory().createSocket()) {
            upstream.connect(new InetSocketAddress(ip, 80), 1600);
            upstream.setSoTimeout(1600);
            OutputStream out = upstream.getOutputStream();
            out.write(("GET /index.html HTTP/1.1\r\n"
                + "Host: jazz.wifi\r\n"
                + "Accept: text/html,application/xhtml+xml\r\n"
                + "Connection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream input = new BufferedInputStream(upstream.getInputStream());
            StringBuilder responseLine = new StringBuilder();
            for (int i = 0; i < 160; i++) {
                int ch = input.read();
                if (ch == -1 || ch == '\n') break;
                if (ch != '\r') responseLine.append((char) ch);
            }
            String header = responseLine.toString();
            if (!header.startsWith("HTTP/1.")) return null;
            String[] status = header.split(" ");
            if (status.length < 2) return null;
            int code = Integer.parseInt(status[1]);
            if ((code >= 200 && code < 400) || code == 401 || code == 403)
                return new RouterEndpoint(ip, "/index.html");
        } catch (Exception ignored) { }
        return null;
    }

    private void checkRouter(boolean requested) {
        if (!visible || scanning || connections == null) return;
        final Network wifiNow = findWifi();
        if (wifiNow == null) {
            connected = false;
            wifi = null;
            currentIp = null;
            showOverlay("Connect to your Jazz Wi-Fi.\n"
                + "The app will open your router automatically.");
            setStatus("Jazz Wi-Fi not connected — waiting...", RED);
            addressLabel.setText(ROUTER_URL);
            if (localProxy != null) localProxy.setTarget(null, null);
            return;
        }
        final boolean networkChanged = wifi == null || !wifi.equals(wifiNow);
        if (networkChanged) {
            wifi = wifiNow;
            connected = false;
            currentIp = null;
            showOverlay("Finding the local Jazz Wi-Fi router...");
        }
        try {
            connections.bindProcessToNetwork(wifiNow);
        } catch (Exception ignored) {
            // Normal Wi-Fi routing may still work on some Android devices.
        }
        if (requested) setStatus("Checking Jazz Wi-Fi connection...", DARK);
        final String knownIp = currentIp;
        final String savedManual = manualIp;
        final LinkProperties link = connections.getLinkProperties(wifiNow);
        final long generation = ++refreshGeneration;
        scanning = true;
        worker.execute(() -> {
            RouterEndpoint result = null;
            if (knownIp != null)
                result = tryCandidate(wifiNow, knownIp);
            if (result == null) {
                List<String> candidates =
                    LocalResolver.routerCandidates(wifiNow, link, savedManual);
                for (String ip : candidates) {
                    if (knownIp != null && knownIp.equals(ip)) continue;
                    result = tryCandidate(wifiNow, ip);
                    if (result != null) break;
                }
            }
            final RouterEndpoint found = result;
            handler.post(() -> {
                scanning = false;
                if (!visible || generation != refreshGeneration
                        || !wifiNow.equals(wifi)) return;
                if (found == null) {
                    connected = false;
                    currentIp = null;
                    hadWebPage = false;
                    if (localProxy != null) localProxy.setTarget(null, null);
                    showOverlay("Jazz router not found yet.\n"
                        + "Keep Wi-Fi connected or tap Router IP.");
                    setStatus("Router offline — retrying automatically", RED);
                    addressLabel.setText(ROUTER_URL);
                    return;
                }

                boolean switchedRouter = !found.ip.equals(currentIp);
                boolean wasDisconnected = !connected;
                currentIp = found.ip;
                currentPath = found.path;
                connected = true;
                if (localProxy != null) localProxy.setTarget(wifiNow, currentIp);
                setStatus("Jazz router detected · opening " + ROUTER_NAME, DARK);
                addressLabel.setText(ROUTER_URL);
                if (requested || wasDisconnected || switchedRouter || !hadWebPage) {
                    loadHome();
                }
            });
        });
    }

    private void loadHome() {
        if (!connected || currentIp == null) {
            checkRouter(true);
            return;
        }
        showOverlay("Opening Jazz router dashboard...");
        ensureProxyAndLoad();
    }

    private void editRouterIp() {
        EditText entry = new EditText(this);
        entry.setSingleLine(true);
        entry.setTextSize(17);
        entry.setInputType(InputType.TYPE_CLASS_PHONE);
        entry.setHint("Example: 192.168.1.1");
        entry.setText(manualIp == null ? "" : manualIp);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(24), dp(10), dp(24), dp(2));
        holder.addView(entry, new LinearLayout.LayoutParams(-1, dp(55)));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Local Jazz router IP")
            .setMessage("The website will ALWAYS remain " + ROUTER_URL
                + ". This optional IP is used only to contact your Jazz "
                + "router over Wi-Fi when local DNS cannot resolve its name.")
            .setView(holder)
            .setNeutralButton("Auto-detect", (d, which) -> {
                saveManualIp("");
                checkRouter(true);
            })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create();
        dialog.setOnShowListener(v -> dialog.getButton(
            AlertDialog.BUTTON_POSITIVE).setOnClickListener(click -> {
                String value = entry.getText().toString().trim();
                if (!LocalResolver.isPrivateIpv4(value)) {
                    entry.setError("Enter a private Wi-Fi IPv4 address");
                    return;
                }
                saveManualIp(value);
                dialog.dismiss();
                checkRouter(true);
            }));
        dialog.show();
    }

    private void saveManualIp(String value) {
        manualIp = value;
        getPreferences(MODE_PRIVATE).edit().putString("router_ip", value).apply();
        currentIp = null;
        connected = false;
        hadWebPage = false;
    }

    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else new AlertDialog.Builder(this)
            .setTitle("Exit Jazz WiFi?")
            .setMessage("Close the router control app?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Exit", (d, which) -> finishAffinity())
            .show();
    }

    @Override protected void onDestroy() {
        visible = false;
        handler.removeCallbacksAndMessages(null);
        if (connections != null) {
            try { connections.bindProcessToNetwork(null); }
            catch (Exception ignored) { }
        }
        worker.shutdownNow();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                ProxyController.getInstance().clearProxyOverride(
                    Runnable::run, () -> { });
            } catch (Exception ignored) { }
        }
        if (localProxy != null) localProxy.stop();
        if (browser != null) {
            browser.stopLoading();
            ViewGroup parent = (ViewGroup) browser.getParent();
            if (parent != null) parent.removeView(browser);
            browser.destroy();
        }
        super.onDestroy();
    }
}
