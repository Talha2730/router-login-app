package com.router.login;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.text.InputType;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONTokener;
import org.json.JSONObject;
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
    private final LinkedHashMap<String, TextView> mobileStatusValues = new LinkedHashMap<>();
    private TextView mobileStatusMessage;
    private View mobileStatusPanel;
    private Button originalStatusButton;
    private LinearLayout liveSignalBars;
    private boolean inlineOriginalFallback = false;
    private boolean statusReadSuccessfully = false;
    private long statusCheckEpoch = 0L;
    private boolean statusQueryPending = false;
    private final Runnable statusRefreshMonitor = new Runnable() {
        @Override public void run() {
            if (networkMonitoring && authenticated && !showingSection)
                readMobileRouterStatus();
            if (networkMonitoring) mainHandler.postDelayed(this, 12000L);
        }
    };
    private boolean authenticated = false;
    private boolean showingSection = false;
    private boolean initialDashboardShown = false;
    private boolean homeNavigationPending = false;
    private String bridgeSource;
    private String mobileLoginSource;
    // Optional per-device login defaults are added to the private, signed APK,
    // never uploaded with the public GitHub source or its unsigned build.
    private String localLoginFillSource = "";
    private EncryptedLoginStore encryptedLoginStore;
    private String savedLoginUsername = "admin";
    private LinearLayout passwordBar;
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
        // Optional private personalization: absent in the public source build.
        // This is only used to fill visible form fields, never auto-submitted.
        // Because APK assets are extractable, avoid using a sensitive password.
        try (InputStream input = getAssets().open("private_login_defaults.json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] bytes = new byte[512];
            int n;
            while ((n = input.read(bytes)) != -1) b.write(bytes, 0, n);
            JSONObject defaults = new JSONObject(
                new String(b.toByteArray(), StandardCharsets.UTF_8));
            String username = defaults.optString("username", "");
            String password = defaults.optString("password", "");
            if (!username.isEmpty() && !password.isEmpty()) {
                savedLoginUsername = username;
                localLoginFillSource = buildLocalLoginFillScript(username, password);
            }
        } catch (Exception ignored) {
            // No password is ever required or committed to the public repo.
        }

        // The mobile owner can replace an old password without rebuilding
        // the APK. An Android Keystore encryption key protects saved values.
        encryptedLoginStore = new EncryptedLoginStore(this);
        EncryptedLoginStore.Login savedLogin = encryptedLoginStore.read();
        if (savedLogin != null) {
            savedLoginUsername = savedLogin.username;
            localLoginFillSource = buildLocalLoginFillScript(
                savedLogin.username, savedLogin.password);
        }
        buildLayout();
        configureWebView();
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    private boolean isLocalRouterLoginUrl(String url) {
        if (url == null) return false;
        try {
            Uri parsed = Uri.parse(url);
            String path = parsed.getPath();
            return "http".equalsIgnoreCase(parsed.getScheme())
                && "192.168.0.1".equals(parsed.getHost())
                && path != null
                && path.toLowerCase(Locale.US).startsWith("/login");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String buildLocalLoginFillScript(String username, String password) {
        // JSONObject.quote escapes JS strings. Only the known local router
        // login page receives this script; no automatic Login click occurs.
        return "(function(){try{"
            + "var p=document.querySelector('input[type=password]');if(!p)return false;"
            + "var forms=p.form||document;"
            + "var fields=forms.querySelectorAll('input[type=text],input[type=email],input:not([type])');"
            + "var user=null;for(var i=0;i<fields.length;i++){"
            + "var f=fields[i];var key=((f.name||'')+' '+(f.id||'')+' '+(f.placeholder||'')).toLowerCase();"
            + "if(/user|login|account|name/.test(key)){user=f;break;}"
            + "if(!user&&f!==p)user=f;}"
            + "function change(f,value){if(!f||f.value)return;"
            + "f.value=value;"
            + "f.dispatchEvent(new Event('input',{bubbles:true}));"
            + "f.dispatchEvent(new Event('change',{bubbles:true}));}"
            + "change(user," + JSONObject.quote(username) + ");"
            + "change(p," + JSONObject.quote(password) + ");"
            + "return true;}catch(e){return false;}})();";
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

        // Android 15 (targetSdk 35) displays apps behind the status/navigation
        // bars by default. Keep login fields and router buttons out of them.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets safe = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return windowInsets;
            });
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            root.requestApplyInsets();
        }

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

        // A clear native control is available on Login AND Management pages,
        // so a changed router password can be saved immediately afterward.
        passwordBar = new LinearLayout(this);
        passwordBar.setPadding(dp(10), dp(3), dp(12), dp(3));
        passwordBar.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        Button updateAutofill = new Button(this);
        updateAutofill.setText("Update Autofill Password");
        updateAutofill.setTextSize(12);
        updateAutofill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        updateAutofill.setAllCaps(false);
        updateAutofill.setTextColor(TEAL);
        updateAutofill.setBackground(shape(Color.WHITE, 9, BORDER));
        passwordBar.addView(updateAutofill,
            new LinearLayout.LayoutParams(dp(194), dp(38)));
        root.addView(passwordBar,
            new LinearLayout.LayoutParams(-1, dp(44)));
        updateAutofill.setOnClickListener(v -> showUpdatePasswordDialog());

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
        liveHeading.addView(text("Live Router Status", 17, INK, true),
            new LinearLayout.LayoutParams(0, -2, 1f));
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
            new LinearLayout.LayoutParams(-1, dp(42));
        home.addView(liveHeading, liveHeadingParams);
        refreshLive.setOnClickListener(v -> refreshRouterHome());

        // Readable Android-native cards display whitelisted router status
        // fields rather than shrinking a 980px-wide desktop interface.
        mobileStatusPanel = createMobileStatusPanel();
        home.addView(mobileStatusPanel);

        // Keep the original authenticated router page alive off-screen (1dp):
        // it supplies the actual values and still handles router navigation.
        webHolder = new FrameLayout(this);
        webHolder.setBackground(shape(Color.WHITE, 13, BORDER));
        LinearLayout.LayoutParams liveWebParams =
            new LinearLayout.LayoutParams(-1, dp(1));
        home.addView(webHolder, liveWebParams);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        webHolder.addView(web, new FrameLayout.LayoutParams(-1, -1));

        originalStatusButton = new Button(this);
        originalStatusButton.setText("Open Original Router Page");
        originalStatusButton.setTextSize(12);
        originalStatusButton.setAllCaps(false);
        originalStatusButton.setTextColor(TEAL);
        originalStatusButton.setBackground(shape(Color.WHITE, 10, BORDER));
        LinearLayout.LayoutParams originalParams =
            new LinearLayout.LayoutParams(-1, dp(40));
        originalParams.bottomMargin = dp(16);
        home.addView(originalStatusButton, originalParams);
        originalStatusButton.setOnClickListener(v -> {
            if (inlineOriginalFallback) {
                setInlineOriginalFallback(false);
                beginStatusLoadingWindow();
                readMobileRouterStatus();
            } else {
                showOriginalHomePage();
            }
        });

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

        // Also accessible from Dashboard, without wasting room above Live Status.
        Button saveAutofill = new Button(this);
        saveAutofill.setText("Update Autofill Password");
        saveAutofill.setAllCaps(false);
        saveAutofill.setTextColor(TEAL);
        saveAutofill.setTextSize(12);
        saveAutofill.setBackground(shape(PALE, 10, BORDER));
        LinearLayout.LayoutParams autofillParams =
            new LinearLayout.LayoutParams(-1, dp(40));
        autofillParams.bottomMargin = dp(12);
        home.addView(saveAutofill, autofillParams);
        saveAutofill.setOnClickListener(v -> showUpdatePasswordDialog());

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



    private View createMobileStatusPanel() {
        LinearLayout holder = vertical();
        holder.setPadding(dp(1), dp(3), dp(1), dp(2));
        addMobileStatusRow(holder, "signal", "Signal Strength",
            "clients", "Connected Devices");
        addMobileStatusRow(holder, "network", "Network Status",
            "provider", "Network Provider");
        addMobileStatusRow(holder, "uptime", "Connection Uptime",
            "band", "LTE Band");
        addMobileStatusRow(holder, "type", "Network Type",
            "ip", "IP Address");
        addMobileStatusRow(holder, "gateway", "Default Gateway",
            "primaryDns", "Primary DNS");
        addMobileStatusRow(holder, "secondaryDns", "Secondary DNS",
            "imei", "IMEI");

        mobileStatusMessage = text("Connecting to router...", 12,
            Color.rgb(91, 107, 120), false);
        mobileStatusMessage.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams messageParams =
            new LinearLayout.LayoutParams(-1, dp(29));
        holder.addView(mobileStatusMessage, messageParams);
        return holder;
    }

    private void addMobileStatusRow(LinearLayout container,
        String firstKey, String firstName, String secondKey, String secondName) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        container.addView(row, params);
        LinearLayout.LayoutParams left =
            new LinearLayout.LayoutParams(0, dp(92), 1f);
        LinearLayout.LayoutParams right =
            new LinearLayout.LayoutParams(0, dp(92), 1f);
        right.leftMargin = dp(9);
        row.addView(createMobileStatusCard(firstKey, firstName), left);
        row.addView(createMobileStatusCard(secondKey, secondName), right);
    }

    private View createMobileStatusCard(String key, String title) {
        LinearLayout card = vertical();
        card.setPadding(dp(12), dp(11), dp(7), dp(6));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(shape(Color.WHITE, 13, BORDER));
        TextView caption = text(title, 12, Color.rgb(67, 93, 107), true);
        caption.setMaxLines(2);
        card.addView(caption);

        int size = "imei".equals(key) ? 15 : 19;
        TextView value = text("—", size, INK, true);
        value.setMaxLines(2);
        value.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
        LinearLayout.LayoutParams labelParams =
            new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(4);
        if ("signal".equals(key)) {
            LinearLayout signalRow = new LinearLayout(this);
            signalRow.setGravity(Gravity.CENTER_VERTICAL);
            signalRow.addView(value,
                new LinearLayout.LayoutParams(0, -2, 1f));
            liveSignalBars = new LinearLayout(this);
            liveSignalBars.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            // The original D-Link DWR-M960 has FIVE signal bars.
            for (int i = 0; i < 5; i++) {
                View bar = new View(this);
                bar.setBackground(shape(Color.rgb(202, 211, 219), 2,
                    Color.TRANSPARENT));
                LinearLayout.LayoutParams barParams =
                    new LinearLayout.LayoutParams(dp(5), dp(7 + 4 * i));
                if (i > 0) barParams.leftMargin = dp(3);
                liveSignalBars.addView(bar, barParams);
            }
            liveSignalBars.setContentDescription("Signal unavailable");
            signalRow.addView(liveSignalBars,
                new LinearLayout.LayoutParams(-2, dp(28)));
            card.addView(signalRow, labelParams);
        } else {
            card.addView(value, labelParams);
        }
        mobileStatusValues.put(key, value);
        return card;
    }

    private void updateLiveSignalBars(String rawValue) {
        if (liveSignalBars == null) return;
        int count = 0;
        try {
            // Display an estimate of one to five signal bars from real
            // router signal strength, never a hardcoded live reading.
            Matcher dbmMatch = Pattern.compile("(-?\\d{1,3})\\s*dBm",
                Pattern.CASE_INSENSITIVE).matcher(rawValue);
            if (dbmMatch.find()) {
                int dbm = Integer.parseInt(dbmMatch.group(1));
                if (dbm <= -105) count = 1;
                else if (dbm <= -95) count = 2;
                else if (dbm <= -85) count = 3;
                else if (dbm <= -75) count = 4;
                else count = 5;
            } else {
                Matcher percentage = Pattern.compile("(\\d{1,3})\\s*%")
                    .matcher(rawValue);
                if (percentage.find()) {
                    int percent = Integer.parseInt(percentage.group(1));
                    if (percent >= 0 && percent <= 100)
                        count = Math.max(1, Math.min(5, (percent + 19) / 20));
                }
            }
        } catch (Exception ignored) { }
        int activeColor = count == 1 ? Color.rgb(215, 60, 64)
            : count <= 3 ? Color.rgb(218, 168, 31)
            : Color.rgb(34, 167, 82);
        for (int i = 0; i < liveSignalBars.getChildCount(); i++) {
            View bar = liveSignalBars.getChildAt(i);
            int color = i < count ? activeColor : Color.rgb(202, 211, 219);
            bar.setBackground(shape(color, 2, Color.TRANSPARENT));
        }
        liveSignalBars.setContentDescription(
            count == 0 ? "Signal unavailable" : count + " of 5 signal bars");
    }

    /**
     * The router's firmware can update status asynchronously. Give it a few
     * seconds after a Home navigation before deciding extraction is unsupported.
     * If reading still fails, show the original page automatically instead
     * of leaving 12 empty, misleading status cards on screen.
     */
    private void beginStatusLoadingWindow() {
        statusReadSuccessfully = false;
        final long generation = ++statusCheckEpoch;
        if (mobileStatusMessage != null)
            mobileStatusMessage.setText("Reading live status from router...");
        web.postDelayed(() -> {
            if (generation != statusCheckEpoch || statusReadSuccessfully
                    || !authenticated || showingSection) return;
            setInlineOriginalFallback(true);
        }, 8000);
    }

    private void setInlineOriginalFallback(boolean showOriginal) {
        inlineOriginalFallback = showOriginal;
        if (mobileStatusPanel != null)
            mobileStatusPanel.setVisibility(showOriginal ? View.GONE : View.VISIBLE);
        if (originalStatusButton != null)
            originalStatusButton.setText(showOriginal
                ? "Retry Large Live Cards" : "Open Original Router Page");
        if (webHolder != null && webHolder.getParent() == dashboardContent) {
            ViewGroup.LayoutParams holder = webHolder.getLayoutParams();
            if (holder != null) {
                holder.height = dp(showOriginal ? dashboardWebHeight() : 1);
                webHolder.setLayoutParams(holder);
            }
            web.setLayoutParams(new FrameLayout.LayoutParams(-1,
                showOriginal ? -1 : dp(420)));
            web.postDelayed(this::fitPageToScreen, 200);
        }
    }

    // Query only labeled, non-secret status fields from the local router page.
    private void readMobileRouterStatus() {
        if (web == null || !authenticated || showingSection || statusQueryPending)
            return;
        statusQueryPending = true;
        web.evaluateJavascript(
            "(function(){try{if(!window.DLinkBridge||!window.DLinkBridge.status)"
            + "return null;return JSON.stringify(window.DLinkBridge.status());}"
            + "catch(e){return null;}})()", result -> {
                statusQueryPending = false;
                if (!authenticated || showingSection || mobileStatusValues.isEmpty())
                    return;
                try {
                    if (result == null || "null".equals(result)) return;
                    Object decoded = new JSONTokener(result).nextValue();
                    if (decoded instanceof String)
                        decoded = new JSONTokener((String) decoded).nextValue();
                    if (!(decoded instanceof JSONObject)) return;
                    JSONObject values = (JSONObject) decoded;
                    int populated = 0;
                    for (String key : mobileStatusValues.keySet()) {
                        String value = values.optString(key, "").trim();
                        if (!value.isEmpty() && value.length() <= 75) {
                            mobileStatusValues.get(key).setText(value);
                            if ("signal".equals(key)) updateLiveSignalBars(value);
                            populated++;
                        }
                    }
                    // Do not call the native cards "working" merely because
                    // one incidental label matched. Require a useful group of
                    // status fields before hiding the proven original view.
                    boolean coreFound =
                        !values.optString("signal", "").isEmpty()
                        || !values.optString("network", "").isEmpty()
                        || !values.optString("clients", "").isEmpty();
                    if (populated >= 3 && coreFound) {
                        statusReadSuccessfully = true;
                        mobileStatusMessage.setText("Live values from D-Link router");
                        if (inlineOriginalFallback) setInlineOriginalFallback(false);
                    } else {
                        mobileStatusMessage.setText(
                            "Waiting for router readings; original page will open if needed.");
                    }
                } catch (Exception ignored) {
                    mobileStatusMessage.setText(
                        "Reading live status. Original page will be shown if unavailable.");
                }
            });
    }

    private void showOriginalHomePage() {
        if (!authenticated) return;
        showingSection = true;
        sectionHeading.setText("Original Router Page");
        dashboard.setVisibility(View.GONE);
        contentToolbar.setVisibility(View.VISIBLE);
        passwordBar.setVisibility(View.GONE);
        attachWebToMainScreen();
    }

    private int dashboardWebHeight() {
        float density = Math.max(0.1f, getResources().getDisplayMetrics().density);
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;
        // Give the router's actual Live Home screen the visual priority.
        // The other cards are deliberately below it in the scrollable page.
        return Math.max(335, Math.min(420, Math.round(heightDp * 0.54f)));
    }

    private void attachWebToMainScreen() {
        if (webHolder.getParent() != root) {
            if (webHolder.getParent() instanceof ViewGroup)
                ((ViewGroup) webHolder.getParent()).removeView(webHolder);
            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, 0, 1f);
            params.setMargins(dp(7), dp(2), dp(7), dp(7));
            root.addView(webHolder, params);
        }
        // Full-size WebView for Login/Settings/Features/Management.
        web.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        webHolder.setVisibility(View.VISIBLE);
    }

    private void attachWebToDashboard() {
        if (webHolder.getParent() != dashboardContent) {
            if (webHolder.getParent() instanceof ViewGroup)
                ((ViewGroup) webHolder.getParent()).removeView(webHolder);
            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1,
                    dp(inlineOriginalFallback ? dashboardWebHeight() : 1));
            dashboardContent.addView(webHolder, 2, params);
        }
        // A 1dp-high WebView doesn't render many routers' status tables.
        // Keep the view itself at 420dp while its parent clips the output
        // to 1dp. The Android-native cards above remain the visible UI.
        setInlineOriginalFallback(inlineOriginalFallback);
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
        // Visual login adjustments and optional local personal autofill
        // are strictly scoped to the D-Link's own 192.168.0.1 login URL.
        if (isLocalRouterLoginUrl(url) && isLocalRouterLoginUrl(view.getUrl())) {
            if (mobileLoginSource != null && !mobileLoginSource.isEmpty())
                view.evaluateJavascript(mobileLoginSource, null);
            if (!localLoginFillSource.isEmpty())
                view.evaluateJavascript(localLoginFillSource, null);
        }
        if (bridgeSource.isEmpty()) return;
        view.evaluateJavascript(bridgeSource, result -> {
            if (web != view) return;
            boolean menuReady = "true".equals(result);
            if (menuReady) {
                authenticated = true;
                web.postDelayed(this::readMobileRouterStatus, 650);
                web.postDelayed(this::readMobileRouterStatus, 1600);
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


    /**
     * The router cannot disclose a changed administrator password.
     * Ask the owner to enter it once, then save it only on this device.
     * Do not read passwords out of router settings pages.
     */
    private void showUpdatePasswordDialog() {
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(9), dp(20), dp(3));

        TextView explanation = text(
            "After changing your router password, enter the NEW password once. "
            + "It will be encrypted on this phone and used for future login autofill. "
            + "This does not change the password on the router.",
            13, INK, false);
        content.addView(explanation,
            new LinearLayout.LayoutParams(-1, -2));

        EditText username = new EditText(this);
        username.setSingleLine(true);
        username.setTextSize(15);
        username.setHint("Router username");
        username.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_NORMAL);
        username.setText(savedLoginUsername);
        LinearLayout.LayoutParams userParams =
            new LinearLayout.LayoutParams(-1, dp(52));
        userParams.topMargin = dp(10);
        content.addView(username, userParams);

        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setTextSize(15);
        password.setHint("New router password");
        password.setInputType(InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(password, new LinearLayout.LayoutParams(-1, dp(52)));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Update saved login")
            .setView(content)
            .setNegativeButton("Cancel", (d, which) -> { })
            .setPositiveButton("Save on this phone", null)
            .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String user = username.getText().toString().trim();
                String pass = password.getText().toString();
                if (user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, "Enter both username and new password.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                if (!encryptedLoginStore.save(user, pass)) {
                    Toast.makeText(this, "Could not securely save password.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                savedLoginUsername = user;
                localLoginFillSource = buildLocalLoginFillScript(user, pass);
                dialog.dismiss();
                Toast.makeText(this, "Autofill password updated on this phone.",
                    Toast.LENGTH_LONG).show();

                // Reload the Login form to discard a previously auto-filled
                // old password. Never reload router Settings/Management forms.
                if (web != null && isLocalRouterLoginUrl(web.getUrl()))
                    web.loadUrl(START_URL);
            });
        });
        dialog.show();
    }

    private void showLogin() {
        homeNavigationPending = false;
        authenticated = false;
        initialDashboardShown = false;
        for (TextView status : mobileStatusValues.values()) status.setText("—");
        if (mobileStatusMessage != null)
            mobileStatusMessage.setText("Connect and log in to D-Link router.");
        updateLiveSignalBars("");
        statusCheckEpoch++;
        statusReadSuccessfully = false;
        showingSection = false;
        dashboard.setVisibility(View.GONE);
        contentToolbar.setVisibility(View.GONE);
        passwordBar.setVisibility(View.VISIBLE);
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
        passwordBar.setVisibility(View.GONE);
        webHolder.setVisibility(View.VISIBLE);
        dashboard.smoothScrollTo(0, 0);
        beginStatusLoadingWindow();
        web.postDelayed(this::readMobileRouterStatus, 500);
        web.postDelayed(this::readMobileRouterStatus, 1800);
        web.postDelayed(this::readMobileRouterStatus, 5000);
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
        passwordBar.setVisibility(View.VISIBLE);
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
        mainHandler.removeCallbacks(statusRefreshMonitor);
        mainHandler.postDelayed(statusRefreshMonitor, 5000);
    }

    private void stopNetworkMonitoring() {
        networkMonitoring = false;
        mainHandler.removeCallbacks(routerMonitor);
        mainHandler.removeCallbacks(statusRefreshMonitor);
        statusQueryPending = false;
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
