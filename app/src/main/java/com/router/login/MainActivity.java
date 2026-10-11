package com.router.login;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
import java.util.LinkedHashMap;
import org.json.JSONObject;
import org.json.JSONTokener;

public class MainActivity extends Activity {
    private static final String START_URL = "http://192.168.0.1/login.htm";
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
    private boolean authenticated = false;
    private boolean showingSection = false;
    private boolean initialDashboardShown = false;
    private String bridgeSource;
    private String mobileLoginSource;
    private final LinkedHashMap<String, TextView> quickValues = new LinkedHashMap<>();
    private TextView quickStatusMessage;

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

        dashboard = new ScrollView(this);
        dashboard.setFillViewport(true);
        LinearLayout home = vertical();
        home.setPadding(dp(16), dp(25), dp(16), dp(18));
        dashboard.addView(home);

        TextView dashboardTitle = text("Router Dashboard", 27, INK, true);
        home.addView(dashboardTitle);
        TextView dashboardHint = text("Tap a section to open its controls", 14, Color.rgb(69, 90, 104), true);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(5);
        hintParams.bottomMargin = dp(14);
        home.addView(dashboardHint, hintParams);

        home.addView(createQuickStatusPanel());

        LinearLayout first = new LinearLayout(this);
        LinearLayout second = new LinearLayout(this);
        home.addView(first);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1, -2);
        secondParams.topMargin = dp(12);
        home.addView(second, secondParams);

        first.addView(sectionCard("Home", "Overview", android.R.drawable.ic_menu_view), cardParams(false));
        first.addView(sectionCard("Settings", "Wi-Fi & network", android.R.drawable.ic_menu_manage), cardParams(true));
        second.addView(sectionCard("Features", "Router options", android.R.drawable.ic_menu_agenda), cardParams(false));
        second.addView(sectionCard("Management", "Administration", android.R.drawable.ic_menu_preferences), cardParams(true));

        TextView address = text("Router address  •  192.168.0.1", 12, Color.rgb(117, 133, 142), false);
        address.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, dp(44));
        addressParams.topMargin = dp(17);
        home.addView(address, addressParams);

        Button logout = new Button(this);
        logout.setText("Log out of router");
        logout.setTextColor(TEAL);
        logout.setAllCaps(false);
        logout.setBackground(shape(Color.WHITE, 12, BORDER));
        LinearLayout.LayoutParams logoutParams = new LinearLayout.LayoutParams(-1, dp(52));
        logoutParams.topMargin = dp(7);
        home.addView(logout, logoutParams);
        logout.setOnClickListener(v -> logOut());

        root.addView(dashboard, new LinearLayout.LayoutParams(-1, 0, 1f));

        contentToolbar = new LinearLayout(this);
        contentToolbar.setPadding(dp(12), dp(9), dp(12), dp(9));
        contentToolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button backToMenu = new Button(this);
        backToMenu.setText("‹  Menu");
        backToMenu.setAllCaps(false);
        backToMenu.setTextSize(13);
        backToMenu.setTextColor(TEAL);
        backToMenu.setBackground(shape(Color.WHITE, 10, BORDER));
        contentToolbar.addView(backToMenu, new LinearLayout.LayoutParams(dp(100), dp(42)));
        backToMenu.setOnClickListener(v -> showDashboard());

        sectionHeading = text("Home", 18, INK, true);
        sectionHeading.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        contentToolbar.addView(sectionHeading, headingParams);
        root.addView(contentToolbar, new LinearLayout.LayoutParams(-1, -2));

        webHolder = new FrameLayout(this);
        webHolder.setBackground(shape(Color.WHITE, 12, BORDER));
        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        holderParams.setMargins(dp(7), dp(2), dp(7), dp(7));
        root.addView(webHolder, holderParams);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        webHolder.addView(web, new FrameLayout.LayoutParams(-1, -1));

        showLogin();
    }


    // Native status tiles live on the Home dashboard, above the section buttons.
    private View createQuickStatusPanel() {
        LinearLayout panel = vertical();
        panel.setBackground(shape(Color.WHITE, 16, BORDER));
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Quick Router Status", 20, INK, true),
            new LinearLayout.LayoutParams(0, -2, 1f));
        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setAllCaps(false);
        refresh.setTextSize(12);
        refresh.setTextColor(TEAL);
        refresh.setBackground(shape(PALE, 10, BORDER));
        header.addView(refresh, new LinearLayout.LayoutParams(dp(88), dp(40)));
        refresh.setOnClickListener(v -> refreshQuickStatus(true));
        panel.addView(header);

        quickStatusMessage = text("Reading router status...", 13,
            Color.rgb(104, 125, 135), false);
        LinearLayout.LayoutParams messageParams =
            new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(5);
        messageParams.bottomMargin = dp(11);
        panel.addView(quickStatusMessage, messageParams);

        addQuickRow(panel, "signal", "Signal", "network", "Network");
        addQuickRow(panel, "provider", "Provider", "clients", "Connected Devices");
        addQuickRow(panel, "type", "Network Type", "band", "LTE Band");
        addQuickRow(panel, "uptime", "Connection Uptime", "ip", "IP Address");

        LinearLayout.LayoutParams panelParams =
            new LinearLayout.LayoutParams(-1, -2);
        panelParams.bottomMargin = dp(18);
        panel.setLayoutParams(panelParams);
        return panel;
    }

    private void addQuickRow(LinearLayout panel, String leftKey, String leftLabel,
                             String rightKey, String rightLabel) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(9);
        panel.addView(row, rowParams);
        row.addView(quickTile(leftKey, leftLabel), quickTileParams(false));
        row.addView(quickTile(rightKey, rightLabel), quickTileParams(true));
    }

    private LinearLayout.LayoutParams quickTileParams(boolean right) {
        LinearLayout.LayoutParams result =
            new LinearLayout.LayoutParams(0, dp(79), 1f);
        if (right) result.leftMargin = dp(9);
        return result;
    }

    private View quickTile(String key, String label) {
        LinearLayout tile = vertical();
        tile.setPadding(dp(12), dp(9), dp(7), dp(7));
        tile.setBackground(shape(PALE, 10, BORDER));
        tile.addView(text(label, 12, Color.rgb(58, 80, 94), true));
        TextView value = text("—", 18, INK, true);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams =
            new LinearLayout.LayoutParams(-1, -2);
        valueParams.topMargin = dp(3);
        tile.addView(value, valueParams);
        quickValues.put(key, value);
        return tile;
    }

    private void clearQuickStatus() {
        for (TextView value : quickValues.values()) value.setText("—");
        if (quickStatusMessage != null)
            quickStatusMessage.setText("Sign in to the router to view status.");
    }

    private void refreshQuickStatus(boolean navigateHome) {
        if (!authenticated) {
            clearQuickStatus();
            return;
        }
        if (quickStatusMessage != null) quickStatusMessage.setText("Refreshing...");
        if (!navigateHome) {
            captureQuickStatus();
            return;
        }
        for (TextView value : quickValues.values()) value.setText("—");
        // User-triggered refresh only; avoid navigating away from unsaved settings.
        web.evaluateJavascript(
            "window.DLinkBridge && window.DLinkBridge.open('Home')", ignored -> {
                web.postDelayed(this::captureQuickStatus, 600);
                web.postDelayed(this::captureQuickStatus, 1500);
            });
    }

    private void captureQuickStatus() {
        if (!authenticated || web == null || quickValues.isEmpty()) return;
        web.evaluateJavascript(
            "(window.DLinkBridge && window.DLinkBridge.status) ? window.DLinkBridge.status() : null",
            result -> {
                if (!authenticated) return;
                try {
                    if (result == null || "null".equals(result) ||
                        "undefined".equals(result)) return;
                    Object decoded = new JSONTokener(result).nextValue();
                    if (decoded instanceof String)
                        decoded = new JSONTokener((String) decoded).nextValue();
                    if (!(decoded instanceof JSONObject)) return;
                    JSONObject values = (JSONObject) decoded;
                    int populated = 0;
                    for (String key : quickValues.keySet()) {
                        String value = values.optString(key, "").trim();
                        if (!value.isEmpty() && value.length() <= 90) {
                            quickValues.get(key).setText(value);
                            populated++;
                        }
                    }
                    if (quickStatusMessage != null) {
                        quickStatusMessage.setText(populated > 0
                            ? "Values read from your connected router"
                            : "Status not found. Open Home or tap Refresh.");
                    }
                } catch (Exception error) {
                    if (quickStatusMessage != null)
                        quickStatusMessage.setText("Status unavailable. Tap Refresh.");
                }
            });
    }

    private LinearLayout.LayoutParams cardParams(boolean right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(142), 1f);
        if (right) lp.leftMargin = dp(12);
        return lp;
    }

    private View sectionCard(String title, String description, int iconRes) {
        LinearLayout card = vertical();
        card.setPadding(dp(15), dp(17), dp(8), dp(10));
        card.setBackground(shape(Color.WHITE, 15, BORDER));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(TEAL);
        icon.setScaleType(ImageView.ScaleType.FIT_START);
        card.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        card.addView(text(title, 19, INK, true), titleParams);
        card.addView(text(description, 12, Color.rgb(83, 102, 114), true));
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
                    Toast.makeText(MainActivity.this, "Router unavailable. Connect to D-Link WiFi.",
                        Toast.LENGTH_LONG).show();
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
                if (!initialDashboardShown) {
                    initialDashboardShown = true;
                    showDashboard();
                }
                captureQuickStatus();
            } else if (url != null && url.toLowerCase(Locale.US).contains("login")) {
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
        authenticated = false;
        clearQuickStatus();
        initialDashboardShown = false;
        showingSection = false;
        dashboard.setVisibility(View.GONE);
        contentToolbar.setVisibility(View.GONE);
        webHolder.setVisibility(View.VISIBLE);
    }

    private void showDashboard() {
        showingSection = false;
        if (authenticated && web != null)
            web.postDelayed(this::captureQuickStatus, 250);
        dashboard.setVisibility(View.VISIBLE);
        contentToolbar.setVisibility(View.GONE);
        webHolder.setVisibility(View.GONE);
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
        webHolder.setVisibility(View.VISIBLE);
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
                webHolder.setVisibility(View.VISIBLE);
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
            showDashboard();
        } else if (authenticated) {
            confirmExit();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            confirmExit();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
