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
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("UTF-8");
        web.setInitialScale(0);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // D-Link's old router UI uses a fixed-width desktop layout.
                // Fit that whole layout to the phone instead of using
                // width=device-width (which clips the right-hand side).
                // Repeat once for pages that add their controls dynamically.
                fitEntireRouterPage(view);
                view.postDelayed(() -> {
                    if (view == web) fitEntireRouterPage(view);
                }, 350);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                        "Router not reachable. Connect to the router Wi-Fi.",
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

    private void fitEntireRouterPage(WebView view) {
        if (view.getWidth() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float phoneCssWidth = view.getWidth() / Math.max(0.1f, density);

        // Never alter login inputs, authentication forms, or router settings.
        // This only adjusts the viewport scale; HTML remains unchanged.
        String script = String.format(Locale.US,
            "(function(){try{" +
            "var doc=document.documentElement,body=document.body;" +
            "if(!doc)return;" +
            "var measured=Math.max(%d,doc.scrollWidth||0,doc.offsetWidth||0," +
            "body?(body.scrollWidth||0):0,body?(body.offsetWidth||0):0);" +
            "var fullWidth=Math.max(%d,Math.min(%d,measured));" +
            "var scale=Math.min(1,%.2f/fullWidth);" +
            "var head=document.head||document.getElementsByTagName('head')[0];" +
            "if(!head)return;" +
            "var viewport=head.querySelector('meta[name=viewport]');" +
            "if(!viewport){viewport=document.createElement('meta');" +
            "viewport.name='viewport';head.appendChild(viewport);}" +
            "var wanted='width='+Math.round(fullWidth)+',initial-scale='+scale.toFixed(4)" +
            "+',minimum-scale=0.1,maximum-scale=5,user-scalable=yes';" +
            "if(viewport.content!==wanted)viewport.content=wanted;" +
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
