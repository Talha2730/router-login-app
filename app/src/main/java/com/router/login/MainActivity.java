package com.router.login;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String START_URL = "http://192.168.0.1/login.htm";
    // Confirmed D-Link DWR-M960 home URL. Reloading it resets the router's
    // in-page Settings/Features/Management view back to the actual Home tab.
    private static final String ROUTER_HOME_URL = "http://192.168.0.1/main.html";
    private static final int TEAL = Color.rgb(0, 139, 163);
    private static final int INK = Color.rgb(23, 49, 65);
    private static final int PALE = Color.rgb(241, 247, 249);
    private static final int BORDER = Color.rgb(214, 228, 233);
    private WebView web;
    private LinearLayout root;
    private ScrollView dashboard;
    private LinearLayout contentToolbar;
    private FrameLayout webHolder;
    private TextView sectionHeading;
    private LinearLayout dashboardContent;
    private boolean authenticated = false;
    private boolean showingSection = false;
    private boolean initialDashboardShown = false;
    private boolean homeNavigationPending = false;
    private String bridgeSource;
    private String mobileLoginSource;
    private static final long ROUTER_CHECK_INTERVAL_MS = 12000L;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback wifiCallback;
    private Network activeWifi;
    private TextView routerConnectionNotice;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService routerProbeExecutor = Executors.newSingleThreadExecutor();
    private boolean networkMonitoring = false;
    private boolean routerAvailable = false;
    private boolean reconnectRequired = false;
    private boolean probeInFlight = false;
    private int failedProbes = 0;
    private final Runnable routerMonitor = new Runnable() {
        @Override public void run() {
            if (!networkMonitoring) return;
            probeRouterConnection();
            mainHandler.postDelayed(this, ROUTER_CHECK_INTERVAL_MS);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(5, 91, 113));
        getWindow().setNavigationBarColor(Color.BLACK);

        try {
            InputStream input = getAssets().open("router_bridge.js");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int count;
            while ((count = input.read(data)) != -1) buffer.write(data, 0, count);
            input.close();
            bridgeSource = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            bridgeSource = "";
        }
        // Read the separate, visual-only login page enhancement.
        // It never extracts or stores usernames and passwords.
        try {
            InputStream input = getAssets().open("mobile_login.js");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int count;
            while ((count = input.read(data)) != -1) buffer.write(data, 0, count);
            input.close();
            mobileLoginSource = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            mobileLoginSource = "";
        }
        buildLayout();
        configureWebView();
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable shape(int color, int radius, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (strokeColor != Color.TRANSPARENT) bg.setStroke(dp(1), strokeColor);
        return bg;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(size);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private void buildLayout() {
        root = vertical();
        root.setBackgroundColor(PALE);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(10), dp(12), dp(10));
        top.setBackgroundColor(TEAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(66)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.router_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout heading = vertical();
        heading.setPadding(dp(11), 0, 0, 0);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        heading.addView(text("D-Link WiFi", 22, Color.WHITE, true));
        TextView subtitle = text("ROUTER CONTROL", 11, Color.rgb(210, 245, 250), true);
        heading.addView(subtitle);

        Button exit = new Button(this);
        exit.setText("Exit");
        exit.setAllCaps(false);
        exit.setTextColor(Color.WHITE);
        exit.setTextSize(13);
        exit.setBackground(shape(Color.rgb(4, 107, 131), 10, Color.rgb(110, 202, 216)));
        top.addView(exit, new LinearLayout.LayoutParams(dp(70), dp(40)));
        exit.setOnClickListener(v -> confirmExit());

        // Small non-blocking reconnect banner. Live content stays visible
        // even when Wi-Fi is temporarily unavailable.
        routerConnectionNotice = text("", 12, Color.WHITE, true);
        routerConnectionNotice.setGravity(Gravity.CENTER_VERTICAL);
        routerConnectionNotice.setPadding(dp(15), 0, dp(12), 0);
        routerConnectionNotice.setBackgroundColor(Color.rgb(180, 106, 20));
        routerConnectionNotice.setVisibility(View.GONE);
        root.addView(routerConnectionNotice,
            new LinearLayout.LayoutParams(-1, dp(37)));

        dashboard = new ScrollView(this);
        dashboard.setFillViewport(true);
        LinearLayout home = vertical();
        dashboardContent = home;
        home.setPadding(dp(12), dp(7), dp(12), dp(9));
        dashboard.addView(home);

        // Show the real authenticated D-Link Home page here instead of
        // attempting to copy status values from the router's proprietary HTML.
        // Connected clients, signal, provider and uptime are rendered by the
        // router itself, so their values remain accurate.
        LinearLayout liveHeading = new LinearLayout(this);
        liveHeading.setGravity(Gravity.CENTER_VERTICAL);
        Button refreshLive = new Button(this);
        refreshLive.setText("Refresh");
        refreshLive.setAllCaps(false);
        refreshLive.setTextColor(TEAL);
        refreshLive.setTextSize(12);
        refreshLive.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        refreshLive.setBackground(shape(Color.WHITE, 10, BORDER));
        LinearLayout.LayoutParams refreshParams =
            new LinearLayout.LayoutParams(dp(86), dp(34));
        liveHeading.addView(refreshLive, refreshParams);
        LinearLayout.LayoutParams liveHeadingParams =
            new LinearLayout.LayoutParams(-1, dp(38));
        home.addView(liveHeading, liveHeadingParams);
        refreshLive.setOnClickListener(v -> refreshRouterHome());

        webHolder = new FrameLayout(this);
        webHolder.setBackground(shape(Color.WHITE, 13, BORDER));
        LinearLayout.LayoutParams liveWebParams =
            new LinearLayout.LayoutParams(-1, dp(dashboardWebHeight()));
        liveWebParams.bottomMargin = dp(7);
        home.addView(webHolder, liveWebParams);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        webHolder.addView(web, new FrameLayout.LayoutParams(-1, -1));

        TextView sectionsHeading = text("Router Controls", 17, INK, true);
        LinearLayout.LayoutParams sectionsParams =
            new LinearLayout.LayoutParams(-1, -2);
        sectionsParams.bottomMargin = dp(7);
        home.addView(sectionsHeading, sectionsParams);

        LinearLayout first = new LinearLayout(this);
        LinearLayout second = new LinearLayout(this);
        home.addView(first);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1, -2);
        secondParams.topMargin = dp(8);
        home.addView(second, secondParams);

        first.addView(sectionCard("Settings", "Wi-Fi & network", android.R.drawable.ic_menu_manage), cardParams(false));
        first.addView(sectionCard("Features", "Router options", android.R.drawable.ic_menu_agenda), cardParams(true));
        second.addView(sectionCard("Management", "Administration", android.R.drawable.ic_menu_preferences),
            new LinearLayout.LayoutParams(-1, dp(97)));

        TextView address = text("Router address  •  192.168.0.1", 12, Color.rgb(117, 133, 142), false);
        address.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, dp(24));
        addressParams.topMargin = dp(3);
        home.addView(address, addressParams);

        // A dedicated Home button instead of "Log out of router".
        // It reopens the real router Home page, even after visiting Settings.
        Button homeButton = new Button(this);
        homeButton.setText("⌂  Home");
        homeButton.setTextSize(16);
        homeButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        homeButton.setTextColor(TEAL);
        homeButton.setAllCaps(false);
        homeButton.setBackground(shape(Color.WHITE, 12, BORDER));
        LinearLayout.LayoutParams homeButtonParams = new LinearLayout.LayoutParams(-1, dp(52));
        homeButtonParams.topMargin = dp(20);
        homeButtonParams.bottomMargin = dp(18);
        home.addView(homeButton, homeButtonParams);
        homeButton.setOnClickListener(v -> returnToDashboard());

        root.addView(dashboard, new LinearLayout.LayoutParams(-1, 0, 1f));

        contentToolbar = new LinearLayout(this);
        contentToolbar.setPadding(dp(12), dp(9), dp(12), dp(9));
        contentToolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button backToMenu = new Button(this);
        backToMenu.setText("‹  Home");
        backToMenu.setAllCaps(false);
        backToMenu.setTextSize(13);
        backToMenu.setTextColor(TEAL);
        backToMenu.setBackground(shape(Color.WHITE, 10, BORDER));
        contentToolbar.addView(backToMenu, new LinearLayout.LayoutParams(dp(100), dp(42)));
        backToMenu.setOnClickListener(v -> returnToDashboard());

        sectionHeading = text("Home", 18, INK, true);
        sectionHeading.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        contentToolbar.addView(sectionHeading, headingParams);
        root.addView(contentToolbar, new LinearLayout.LayoutParams(-1, -2));

        showLogin();
    }


    private int dashboardWebHeight() {
        float density = Math.max(0.1f, getResources().getDisplayMetrics().density);
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;
        // Give the router's actual Live Home screen the visual priority.
        // The other cards are deliberately below it in the scrollable page.
        return Math.max(335, Math.min(420, Math.round(heightDp * 0.54f)));
    }

    private void attachWebToMainScreen() {
        if (webHolder.getParent() == root) return;
        if (webHolder.getParent() instanceof ViewGroup)
            ((ViewGroup) webHolder.getParent()).removeView(webHolder);
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, 0, 1f);
        params.setMargins(dp(7), dp(2), dp(7), dp(7));
        root.addView(webHolder, params);
        webHolder.setVisibility(View.VISIBLE);
    }

    private void attachWebToDashboard() {
        if (webHolder.getParent() == dashboardContent) return;
        if (webHolder.getParent() instanceof ViewGroup)
            ((ViewGroup) webHolder.getParent()).removeView(webHolder);
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, dp(dashboardWebHeight()));
        params.bottomMargin = dp(7);
        dashboardContent.addView(webHolder, 1, params);
        webHolder.setVisibility(View.VISIBLE);
        web.postDelayed(this::fitPageToScreen, 150);
    }

    private void refreshRouterHome() {
        returnToDashboard();
    }

    private void returnToDashboard() {
        if (!authenticated) {
            showLogin();
            return;
        }
        // Previously, only moving WebView back to the dashboard preserved the
        // selected Settings page. Reload the known local router Home URL first.
        // This works even when the router's JavaScript menu is currently hidden.
        homeNavigationPending = true;
        web.loadUrl(ROUTER_HOME_URL);

        // Some firmware switches content asynchronously without a load event.
        // Make the native Home screen visible after a short safety delay.
        web.postDelayed(() -> {
            if (!homeNavigationPending || !authenticated) return;
            String current = web.getUrl();
            if (current != null && current.contains("/login")) {
                homeNavigationPending = false;
                showLogin();
            } else {
                homeNavigationPending = false;
                showDashboard();
                web.postDelayed(this::fitPageToScreen, 250);
            }
        }, 1600);
    }

    private LinearLayout.LayoutParams cardParams(boolean right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(108), 1f);
        if (right) lp.leftMargin = dp(9);
        return lp;
    }

    private View sectionCard(String title, String description, int iconRes) {
        LinearLayout card = vertical();
        card.setPadding(dp(13), dp(10), dp(8), dp(6));
        card.setBackground(shape(Color.WHITE, 15, BORDER));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(TEAL);
        icon.setScaleType(ImageView.ScaleType.FIT_START);
        card.addView(icon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(5);
        card.addView(text(title, 18, INK, true), titleParams);
        card.addView(text(description, 11, Color.rgb(83, 102, 114), true));
        card.setContentDescription("Open " + title);
        card.setOnClickListener(v -> openSection(title));
        return card;
    }

    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        web.setInitialScale(0);
        settings.setDefaultTextEncodingName("UTF-8");

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                onRouterPageFinished(view, url);
                view.postDelayed(() -> onRouterPageFinished(view, view.getUrl()), 450);
                view.postDelayed(() -> onRouterPageFinished(view, view.getUrl()), 1200);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    homeNavigationPending = false;
                    // Preserve the prior authenticated session in memory.
                    // It might still work when the router reconnects.
                    routerAvailable = false;
                    reconnectRequired = true;
                    failedProbes = 0;
                    updateConnectionNotice("Router not reachable — waiting to reconnect...");
                    mainHandler.postDelayed(() -> probeRouterConnection(), 1200);
                }
            }
        });
    }

    private void onRouterPageFinished(WebView view, String url) {
        if (view != web) return;
        fitPageToScreen();
        // Readability enhancement is scoped strictly to the router login page.
        if (url != null && url.toLowerCase(Locale.US).contains("/login")
                && mobileLoginSource != null && !mobileLoginSource.isEmpty()) {
            view.evaluateJavascript(mobileLoginSource, null);
        }
        if (bridgeSource.isEmpty()) return;
        view.evaluateJavascript(bridgeSource, result -> {
            if (web != view) return;
            boolean menuReady = "true".equals(result);
            if (menuReady) {
                authenticated = true;
                if (homeNavigationPending) {
                    homeNavigationPending = false;
                    showDashboard();
                } else if (!initialDashboardShown) {
                    initialDashboardShown = true;
                    showDashboard();
                }
            } else if (url != null && url.toLowerCase(Locale.US).contains("login")) {
                homeNavigationPending = false;
                if (authenticated || !initialDashboardShown) showLogin();
            }
        });
    }

    private void fitPageToScreen() {
        if (web.getWidth() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float cssWidth = web.getWidth() / Math.max(0.1f, density);
        String script = String.format(Locale.US,
            "(function(){try{" +
            "var doc=document.documentElement,body=document.body;if(!doc)return;" +
            "var measured=Math.max(980,doc.scrollWidth||0,doc.offsetWidth||0," +
            "body?(body.scrollWidth||0):0,body?(body.offsetWidth||0):0);" +
            "var width=Math.max(980,Math.min(1800,measured));" +
            "var scale=Math.min(1,%.2f/width);" +
            "var head=document.head||document.getElementsByTagName('head')[0];" +
            "if(!head)return;" +
            "var meta=head.querySelector('meta[name=viewport]');" +
            "if(!meta){meta=document.createElement('meta');meta.name='viewport';head.appendChild(meta);}" +
            "var fixed='width='+Math.round(width)+',initial-scale='+scale.toFixed(4)" +
            "+',minimum-scale='+scale.toFixed(4)+',maximum-scale='+scale.toFixed(4)" +
            "+',user-scalable=no';" +
            "if(meta.content!==fixed)meta.content=fixed;" +
            "}catch(e){}})();", cssWidth);
        web.evaluateJavascript(script, null);
    }

    private void showLogin() {
        homeNavigationPending = false;
        authenticated = false;
        initialDashboardShown = false;
        showingSection = false;
        dashboard.setVisibility(View.GONE);
        contentToolbar.setVisibility(View.GONE);
        attachWebToMainScreen();
    }

    private void emphasizeLiveStatus() {
        if (authenticated && !showingSection && web != null) {
            web.evaluateJavascript(
                "window.DLinkBridge && window.DLinkBridge.styleStatus()", null);
        }
    }

    private void showDashboard() {
        showingSection = false;
        attachWebToDashboard();
        dashboard.setVisibility(View.VISIBLE);
        contentToolbar.setVisibility(View.GONE);
        webHolder.setVisibility(View.VISIBLE);
        dashboard.smoothScrollTo(0, 0);
        web.postDelayed(this::emphasizeLiveStatus, 250);
        web.postDelayed(this::emphasizeLiveStatus, 1100);
    }

    private void openSection(String section) {
        if (!authenticated) {
            Toast.makeText(this, "Please sign in to your router first.", Toast.LENGTH_SHORT).show();
            showLogin();
            return;
        }
        showingSection = true;
        sectionHeading.setText(section);
        dashboard.setVisibility(View.GONE);
        contentToolbar.setVisibility(View.VISIBLE);
        attachWebToMainScreen();
        web.post(() -> {
            fitPageToScreen();
            String js = "window.DLinkBridge && window.DLinkBridge.open(" +
                org.json.JSONObject.quote(section) + ")";
            web.evaluateJavascript(js, response -> {
                if (!"true".equals(response)) {
                    // Keep the original router menu usable if the firmware uses
                    // nonstandard markup that the menu bridge cannot identify.
                    web.evaluateJavascript("window.DLinkBridge&&window.DLinkBridge.showOriginalMenu()", null);
                    Toast.makeText(this, "Use the router menu below to open " + section,
                        Toast.LENGTH_LONG).show();
                }
                web.postDelayed(() -> fitPageToScreen(), 350);
            });
        });
    }

    private void logOut() {
        web.evaluateJavascript(
            "window.DLinkBridge && window.DLinkBridge.open('Logout')", value -> {
            if ("true".equals(value)) {
                showLogin();
            } else {
                web.evaluateJavascript("window.DLinkBridge&&window.DLinkBridge.showOriginalMenu()", null);
                showingSection = true;
                sectionHeading.setText("Logout");
                dashboard.setVisibility(View.GONE);
                contentToolbar.setVisibility(View.VISIBLE);
                attachWebToMainScreen();
                Toast.makeText(this, "Use Logout in the router's menu.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmExit() {
        new AlertDialog.Builder(this).setTitle("Exit D-Link WiFi?")
            .setMessage("Close the router control app?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Exit", (dialog, which) -> finishAffinity())
            .show();
    }

    @Override public void onBackPressed() {
        if (showingSection) {
            returnToDashboard();
        } else if (authenticated) {
            confirmExit();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            confirmExit();
        }
    }


    // This callback watches Wi-Fi even when the router provides no Internet.
    // A mobile-data default connection does not mean the local router is reachable.
    @Override protected void onStart() {
        super.onStart();
        startNetworkMonitoring();
    }

    @Override protected void onStop() {
        stopNetworkMonitoring();
        // On returning to the app, recheck the session and reload the Home
        // page if the router has since reconnected.
        reconnectRequired = true;
        super.onStop();
    }

    private void updateConnectionNotice(String message) {
        if (routerConnectionNotice == null) return;
        if (message == null || message.isEmpty()) {
            routerConnectionNotice.setVisibility(View.GONE);
        } else {
            routerConnectionNotice.setText(message);
            routerConnectionNotice.setVisibility(View.VISIBLE);
        }
    }

    private void refreshCurrentWifi() {
        if (connectivityManager == null) return;
        Network candidate = null;
        try {
            for (Network network : connectivityManager.getAllNetworks()) {
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    candidate = network;
                    break;
                }
            }
        } catch (SecurityException ignored) {
            updateConnectionNotice("Network permission required to monitor Wi-Fi.");
        }
        activeWifi = candidate;
        try {
            // Force local 192.168.0.1 traffic over Wi-Fi, even if Android
            // prefers cellular data because the router has no Internet.
            connectivityManager.bindProcessToNetwork(candidate);
        } catch (SecurityException ignored) {
            // Normal routing may still reach the router.
        }
    }

    private void startNetworkMonitoring() {
        if (networkMonitoring) return;
        networkMonitoring = true;
        connectivityManager =
            (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            updateConnectionNotice("Network monitoring unavailable.");
            return;
        }
        wifiCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                mainHandler.post(() -> {
                    if (!networkMonitoring) return;
                    refreshCurrentWifi();
                    mainHandler.postDelayed(() -> probeRouterConnection(), 400);
                });
            }

            @Override public void onLost(Network network) {
                mainHandler.post(() -> {
                    if (!networkMonitoring) return;
                    refreshCurrentWifi();
                    if (activeWifi == null) {
                        routerDisconnected("Router Wi-Fi disconnected — reconnecting...");
                    } else {
                        reconnectRequired = true;
                    }
                    probeRouterConnection();
                });
            }

            @Override public void onCapabilitiesChanged(
                    Network network, NetworkCapabilities capabilities) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    mainHandler.post(() -> {
                        if (!networkMonitoring) return;
                        refreshCurrentWifi();
                        probeRouterConnection();
                    });
                }
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                // Router Wi-Fi may have no Internet access; watch it anyway.
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
            connectivityManager.registerNetworkCallback(request, wifiCallback);
        } catch (Exception ignored) {
            wifiCallback = null;
        }
        refreshCurrentWifi();
        if (activeWifi == null)
            routerDisconnected("Connect to D-Link Wi-Fi — automatic retry is on");
        mainHandler.removeCallbacks(routerMonitor);
        mainHandler.post(routerMonitor);
    }

    private void stopNetworkMonitoring() {
        networkMonitoring = false;
        mainHandler.removeCallbacks(routerMonitor);
        if (connectivityManager != null) {
            if (wifiCallback != null) {
                try {
                    connectivityManager.unregisterNetworkCallback(wifiCallback);
                } catch (Exception ignored) { }
                wifiCallback = null;
            }
            try {
                connectivityManager.bindProcessToNetwork(null);
            } catch (SecurityException ignored) { }
        }
        activeWifi = null;
    }

    private void routerDisconnected(String message) {
        routerAvailable = false;
        reconnectRequired = true;
        updateConnectionNotice(message);
    }

    private void probeRouterConnection() {
        if (!networkMonitoring || probeInFlight) return;
        if (activeWifi == null) {
            refreshCurrentWifi();
            if (activeWifi == null) {
                routerDisconnected("Connect to D-Link Wi-Fi — automatic retry is on");
                return;
            }
        }
        final Network network = activeWifi;
        probeInFlight = true;
        routerProbeExecutor.execute(() -> {
            boolean reachable = false;
            HttpURLConnection connection = null;
            try {
                // A successful HTTP response proves that the local router
                // is reachable; Internet access is not required.
                connection = (HttpURLConnection) network.openConnection(
                    new URL(START_URL));
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(2500);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                int status = connection.getResponseCode();
                reachable = (status >= 200 && status < 400)
                    || status == 401 || status == 403;
            } catch (Exception ignored) {
                reachable = false;
            } finally {
                if (connection != null) connection.disconnect();
            }

            final boolean connected = reachable;
            mainHandler.post(() -> {
                probeInFlight = false;
                if (!networkMonitoring || activeWifi == null
                        || !activeWifi.equals(network)) return;
                if (connected) {
                    failedProbes = 0;
                    boolean shouldReconnect = reconnectRequired;
                    routerAvailable = true;
                    reconnectRequired = false;
                    updateConnectionNotice(null);
                    if (shouldReconnect) reloadAfterReconnection();
                } else {
                    failedProbes++;
                    // Avoid flashing the offline indicator on one transient
                    // slow response while the router is actually available.
                    if (failedProbes >= 2 || !routerAvailable) {
                        routerDisconnected("Router unreachable — retrying automatically...");
                    }
                }
            });
        });
    }

    private void reloadAfterReconnection() {
        if (!networkMonitoring || web == null) return;
        // An existing session should reopen the real Home dashboard.
        // Expired sessions are handled by the router's login redirect.
        if (authenticated) {
            homeNavigationPending = true;
            showingSection = false;
            web.loadUrl(ROUTER_HOME_URL);
        } else {
            initialDashboardShown = false;
            showLogin();
            web.loadUrl(START_URL);
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        stopNetworkMonitoring();
        routerProbeExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
