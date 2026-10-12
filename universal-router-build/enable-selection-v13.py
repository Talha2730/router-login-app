"""Keep zoom disabled while enabling Android native select/copy/paste menus.

Patch runs after v1.2 Zoom Lock and after Auto Detect. No changes to Wi-Fi
detection, DNS/proxy, credentials or router page size are necessary.
"""
from pathlib import Path
path = Path("universal-router/app/src/main/java/com/universalrouter/connect/MainActivity.java")
s = path.read_text(encoding="utf-8")

def once(old, new):
    global s
    assert s.count(old) == 1, f"Unexpected source anchor ({s.count(old)}): {old[:100]!r}"
    s = s.replace(old, new)

once(
    "import android.content.Context;",
    "import android.content.Context;\nimport android.content.ClipboardManager;\nimport android.content.ClipData;"
)
once(
    "        web.setBackgroundColor(Color.WHITE);",
    """        // WebView default Android long-press text selection is available.
        // Two-finger zoom remains disabled by v1.2's gesture handler; the
        // single-finger long-press is not intercepted.
        web.setLongClickable(true);
        web.setHapticFeedbackEnabled(true);
        web.setBackgroundColor(Color.WHITE);"""
)
once(
    "                lockPageZoom(view);",
    """                lockPageZoom(view);
                enableWebTextSelection(view);"""
)
once(
    "    private boolean shouldBlock(Uri uri) {",
    '''    /**
     * Some desktop router pages disable text selection with user-select:none.
     * Preserve all page interactions and the original responsive viewport;
     * only restore selectable text, copy and paste using Android's native menu.
     */
    private void enableWebTextSelection(WebView view) {
        view.evaluateJavascript(
            "(function(){try{"
            + "var id='router-allow-text-selection';"
            + "if(document.getElementById(id))return;"
            + "var style=document.createElement('style');style.id=id;"
            + "style.textContent='html,body,body *"
            + "{-webkit-user-select:text!important;user-select:text!important;}"
            + "input,textarea,[contenteditable=true]"
            + "{-webkit-user-select:text!important;user-select:text!important;}"
            + "';"
            + "(document.head||document.documentElement).appendChild(style);"
            + "}catch(e){}})();", null);
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "No address to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager)
            getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Router Address", text));
            Toast.makeText(this, "Router address copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteAddressInto(EditText field) {
        ClipboardManager clipboard = (ClipboardManager)
            getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence pasted = clip.getItemAt(0).coerceToText(this);
        if (pasted == null) return;
        String value = pasted.toString().trim();
        if (value.length() > 2048) {
            Toast.makeText(this, "Router address is too long",
                Toast.LENGTH_SHORT).show();
            return;
        }
        field.setText(value);
        field.setSelection(field.length());
    }

    private boolean shouldBlock(Uri uri) {'''
)
once(
    '''        fields.addView(urlField, new LinearLayout.LayoutParams(-1, dp(49)));

        TextView ipTitle''',
    '''        fields.addView(urlField, new LinearLayout.LayoutParams(-1, dp(49)));

        LinearLayout addressActions = new LinearLayout(this);
        addressActions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams addressActionsParams =
            new LinearLayout.LayoutParams(-1, dp(42));
        fields.addView(addressActions, addressActionsParams);
        Button copyUrl = button("Copy URL", 95);
        Button pasteUrl = button("Paste URL", 95);
        addressActions.addView(copyUrl);
        LinearLayout.LayoutParams pasteParams =
            new LinearLayout.LayoutParams(dp(95), dp(40));
        pasteParams.leftMargin = dp(8);
        addressActions.addView(pasteUrl, pasteParams);
        copyUrl.setOnClickListener(v -> copyToClipboard(
            urlField.getText().toString().trim()));
        pasteUrl.setOnClickListener(v -> pasteAddressInto(urlField));

        TextView ipTitle'''
)
once(
    '        selectedLabel.setOnClickListener(v -> showRouterDialog());',
    '''        selectedLabel.setOnClickListener(v -> showRouterDialog());
        // Long-press the selected address to copy it without opening dialog.
        selectedLabel.setOnLongClickListener(v -> {
            if (router != null) copyToClipboard(router.url);
            return true;
        });'''
)
path.write_text(s, encoding="utf-8")
gradle=Path("universal-router/app/build.gradle")
g=gradle.read_text(encoding="utf-8")
assert g.count("versionCode 3")==1
assert g.count("versionName '1.2'")==1
g=g.replace("versionCode 3","versionCode 4").replace("versionName '1.2'","versionName '1.3'")
gradle.write_text(g,encoding="utf-8")
print("Universal Router Connect v1.3: select/copy/paste added; zoom lock kept.")
