"""Lock user zoom without resizing legacy router pages.

Applied after the v1.1 Auto Detect patch is extracted, so the original router
navigation, private-DNS proxy, auto-detection, and login form remain unchanged.
"""
from pathlib import Path

source = Path("universal-router/app/src/main/java/com/universalrouter/connect/MainActivity.java")
text = source.read_text(encoding="utf-8")

def once(old, new):
    global text
    assert text.count(old) == 1, f"Unexpected source layout for: {old[:100]!r}"
    text = text.replace(old, new)

once("import android.view.Gravity;", "import android.view.Gravity;\nimport android.view.MotionEvent;")

once(
    '        web = new WebView(this);\n        web.setBackgroundColor(Color.WHITE);',
    '''        web = new WebView(this);
        // Prevent two-finger pinch gestures even if the router injects
        // its own viewport or page-level zoom handlers. Normal single-touch
        // taps, text input, and one-finger scrolling continue to work.
        web.setOnTouchListener(new View.OnTouchListener() {
            private boolean pinchBlocked;

            @Override public boolean onTouch(View view, MotionEvent event) {
                final int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_POINTER_DOWN
                        || event.getPointerCount() > 1) {
                    if (!pinchBlocked) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        web.onTouchEvent(cancel);
                        cancel.recycle();
                    }
                    pinchBlocked = true;
                    return true;
                }
                if (pinchBlocked) {
                    if (action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        pinchBlocked = false;
                    }
                    return true;
                }
                return false;
            }
        });
        web.setBackgroundColor(Color.WHITE);'''
)
once(
    '        settings.setDisplayZoomControls(false);',
    '''        settings.setDisplayZoomControls(false);
        // 0 retains normal "fit to screen" behavior for older fixed-width
        // router pages; do not force initial-scale=1 (which could crop them).
        web.setInitialScale(0);'''
)
once(
    '''            @Override public void onPageFinished(WebView view, String url) {
                if (router != null && !mainPageFailed && isSelectedRouterUrl(url)) {''',
    '''            @Override public void onPageFinished(WebView view, String url) {
                lockPageZoom(view);
                if (router != null && !mainPageFailed && isSelectedRouterUrl(url)) {'''
)
once(
    '    private boolean shouldBlock(Uri uri) {',
    '''    /**
     * Disable the page's double-tap and pinch zoom settings without
     * rewriting the original desktop/mobile viewport width or scale.
     * Retain scrolling in both directions for legacy router settings.
     */
    private void lockPageZoom(WebView view) {
        view.evaluateJavascript(
            "(function(){try{"
            + "var meta=document.querySelector('meta[name=viewport]');"
            + "if(meta){var s=meta.getAttribute('content')||'';"
            + "s=s.replace(/(?:^|,)\\\\s*(?:user-scalable|maximum-scale|minimum-scale)\\\\s*=\\\\s*[^,]*/gi,'');"
            + "meta.setAttribute('content',(s?s+',':'')+'maximum-scale=1,user-scalable=no');}"
            + "document.documentElement.style.touchAction='pan-x pan-y';"
            + "if(document.body)document.body.style.touchAction='pan-x pan-y';"
            + "}catch(e){}})();", null);
    }

    private boolean shouldBlock(Uri uri) {'''
)

source.write_text(text, encoding="utf-8")
gradle = Path("universal-router/app/build.gradle")
data = gradle.read_text(encoding="utf-8")
assert data.count("versionCode 2") == 1
assert data.count("versionName '1.1'") == 1
data = data.replace("versionCode 2", "versionCode 3")
data = data.replace("versionName '1.1'", "versionName '1.2'")
gradle.write_text(data, encoding="utf-8")
print("Universal Router Connect 1.2: zoom gestures locked, fit-to-screen preserved.")
