package com.egydead.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TARGET_URL = "https://tv10.egydead.live/h3/";
    private static final String ALLOWED_DOMAIN = "egydead.live";

    private WebView webView;
    private ProgressBar progressBar;

    // Small ad/tracker blocklist (host suffix match)
    private static final HashSet<String> AD_HOSTS = new HashSet<>();
    static {
        String[] hosts = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "pagead2.googlesyndication.com",
            "adnxs.com", "adsrvr.org", "amazon-adsystem.com", "taboola.com",
            "outbrain.com", "criteo.com", "scorecardresearch.com", "moatads.com",
            "pubmatic.com", "rubiconproject.com", "openx.net", "adform.net",
            "smartadserver.com", "adcolony.com", "unityads.unity3d.com",
            "supersonicads.com", "chartbeat.com", "hotjar.com", "mixpanel.com",
            "segment.io", "branch.io", "appsflyer.com", "adjust.com"
        };
        for (String h : hosts) AD_HOSTS.add(h);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // videos autoplay (TV remotes have no click)
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);

        webView.addJavascriptInterface(new TvBridge(), "TvBridge");
        webView.requestFocus(View.FOCUS_DOWN);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !isAllowed(req.getUrl().getHost());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String host = Uri2Host(url);
                return !isAllowed(host);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String host = req.getUrl().getHost();
                if (host != null && isAdHost(host)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, req);
            }

            @SuppressWarnings("deprecation")
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                String host = Uri2Host(url);
                if (host != null && isAdHost(host)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                // Focus first input if the page opened a search box (keyboard pops automatically)
                view.evaluateJavascript(
                    "(function(){" +
                    "  var f=document.querySelector('input[type=search],input[type=text],textarea');" +
                    "  if(f){f.focus();}" +
                    "})()", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        webView.loadUrl(TARGET_URL);
    }

    private static String Uri2Host(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAllowed(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return host.equals(ALLOWED_DOMAIN) || host.endsWith("." + ALLOWED_DOMAIN);
    }

    private static boolean isAdHost(String host) {
        host = host.toLowerCase(Locale.US);
        if (AD_HOSTS.contains(host)) return true;
        for (String ad : AD_HOSTS) {
            if (host.endsWith("." + ad)) return true;
        }
        return false;
    }

    // Called from JS when an input/textarea gets/loses focus -> show/hide keyboard
    public class TvBridge {
        @JavascriptInterface
        public void inputFocus(final boolean focused) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                applyKeyboard(focused);
            } else {
                runOnUiThread(() -> applyKeyboard(focused));
            }
        }
    }

    private void applyKeyboard(boolean show) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (show) {
            imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
        } else {
            imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
        }
    }

    // Inject focus listeners for inputs so keyboard auto-shows on remote focus
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && webView != null) {
            webView.evaluateJavascript(
                "(function(){" +
                "  if(window.__tvHooked)return; window.__tvHooked=1;" +
                "  function h(e){var t=e.target;" +
                "    if(t&&(t.tagName==='INPUT'||t.tagName==='TEXTAREA'||t.isContentEditable)){" +
                "      TvBridge.inputFocus(e.type==='focusin');" +
                "    }}" +
                "  document.addEventListener('focusin',h,true);" +
                "  document.addEventListener('focusout',h,true);" +
                "})()", null);
        }
    }

    // D-pad: WebView natively handles arrows to move focus between links/buttons.
    // ENTER/DOWN_CENTER clicks the focused element (WebView default).
    // BACK: go back in history, exit only at first page.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
