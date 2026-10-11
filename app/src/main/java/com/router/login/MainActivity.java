package com.router.login;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String ROUTER_URL = "http://192.168.0.1/login.htm";
    // Keep older D-Link firmware's fixed-width UI intact; scale the entire page.
    private static final int MIN_DESKTOP_WIDTH = 980;
    private static final int MAX_DESKTOP_WIDTH = 1800;
    private WebView web;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Fixed screen: turn off pinch-to-zoom and all zoom controls.
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setDefaultTextEncodingName("UTF-8");

        // Let WebView start in overview mode. Subsequent page loads re-fit.
        web.setInitialScale(0);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                fitPageToScreen(view);
                view.postDelayed(() -> {
                    if (view == web) fitPageToScreen(view);
                }, 400);
                view.postDelayed(() -> {
                    if (view == web) fitPageToScreen(view);
                }, 1000);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                        "Router unavailable. Connect to D-Link WiFi and retry.",
                        Toast.LENGTH_LONG).show();
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(ROUTER_URL);
        }
    }

    private void fitPageToScreen(WebView view) {
        if (view.getWidth() <= 0) return;

        // Translate the Android viewport to CSS pixels used by the legacy router.
        float density = getResources().getDisplayMetrics().density;
        float phoneCssWidth = view.getWidth() / Math.max(0.1f, density);

        // No CSS reflow or login form changes: only set a fixed desktop viewport
        // and the scale necessary to fit it within the phone display.
        String script = String.format(Locale.US,
            "(function(){try{" +
            "var doc=document.documentElement,body=document.body;if(!doc)return;" +
            "var measured=Math.max(%d,doc.scrollWidth||0,doc.offsetWidth||0," +
            "body?(body.scrollWidth||0):0,body?(body.offsetWidth||0):0);" +
            "var width=Math.max(%d,Math.min(%d,measured));" +
            "var scale=Math.min(1,%.2f/width);" +
            "var head=document.head||document.getElementsByTagName('head')[0];" +
            "if(!head)return;" +
            "var meta=head.querySelector('meta[name=viewport]');" +
            "if(!meta){meta=document.createElement('meta');" +
            "meta.name='viewport';head.appendChild(meta);}" +
            "var fixed='width='+Math.round(width)+',initial-scale='+scale.toFixed(4)" +
            "+',minimum-scale='+scale.toFixed(4)" +
            "+',maximum-scale='+scale.toFixed(4)" +
            "+',user-scalable=no';" +
            "if(meta.content!==fixed)meta.content=fixed;" +
            "}catch(e){}})();",
            MIN_DESKTOP_WIDTH, MIN_DESKTOP_WIDTH, MAX_DESKTOP_WIDTH, phoneCssWidth);
        view.evaluateJavascript(script, null);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (web != null) web.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
