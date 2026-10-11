package com.router.login;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String ROUTER_URL = "http://192.168.0.1/login.htm";
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

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Add a mobile viewport and improve sizing on legacy router pages.
                // Does not collect, intercept, or modify login credentials.
                String script = "(function(){"
                    + "try{"
                    + "var head=document.head||document.getElementsByTagName('head')[0];"
                    + "if(head && !document.querySelector('meta[name=viewport]')){"
                    + "var m=document.createElement('meta');m.name='viewport';"
                    + "m.content='width=device-width,initial-scale=1,minimum-scale=0.5';"
                    + "head.appendChild(m);}"
                    + "var s=document.getElementById('router-mobile-fit');"
                    + "if(!s){s=document.createElement('style');s.id='router-mobile-fit';"
                    + "s.textContent='html,body{max-width:100%;overflow-x:auto;}'"
                    + "+'img,video,iframe{max-width:100%;height:auto;}'"
                    + "+'input,button,select{max-width:100%;box-sizing:border-box;}';"
                    + "(head||document.documentElement).appendChild(s);}"
                    + "}catch(e){}"
                    + "})();";
                view.evaluateJavascript(script, null);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                        "Router not reachable. Connect to router Wi-Fi and retry.",
                        Toast.LENGTH_LONG).show();
                }
            }
        });
        web.loadUrl(ROUTER_URL);
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
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
