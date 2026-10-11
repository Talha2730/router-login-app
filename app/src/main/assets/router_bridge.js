(function() {
  "use strict";
  var sections = ["Home", "Settings", "Features", "Management", "Logout"];
  var found = {};
  var hidden = null;
  var previousDisplay = "";

  function normalize(s) {
    return String(s || "").replace(/\\s+/g, " ").trim().toLowerCase();
  }

  function docs() {
    var result = [document];
    var frames = document.querySelectorAll("iframe,frame");
    for (var i = 0; i < frames.length; i++) {
      try {
        if (frames[i].contentDocument) result.push(frames[i].contentDocument);
      } catch (e) { /* Ignore cross-origin frames. */ }
    }
    return result;
  }

  function labelOf(node) {
    if (!node) return "";
    if (node.tagName === "IMG") return node.getAttribute("alt") || node.getAttribute("title") || "";
    if (node.tagName === "INPUT") return node.value || node.getAttribute("title") || "";
    var own = node.textContent || "";
    if (own.length > 45 && node.children.length > 0) return "";
    return node.getAttribute("aria-label") || node.getAttribute("title") || own;
  }

  function findItem(label) {
    var target = normalize(label);
    var results = [];
    var allDocs = docs();
    for (var d = 0; d < allDocs.length; d++) {
      var elements = allDocs[d].querySelectorAll("a,button,input,img,span,div,li,td,[role=button],[onclick]");
      for (var i = 0; i < elements.length; i++) {
        var el = elements[i];
        if (normalize(labelOf(el)) !== target) continue;
        var rect = el.getBoundingClientRect();
        var clickNode = el;
        var parent = el;
        for (var n = 0; n < 5 && parent; n++, parent = parent.parentElement) {
          if (parent.tagName === "A" || parent.tagName === "BUTTON" ||
              parent.hasAttribute("onclick") || parent.getAttribute("role") === "button") {
            clickNode = parent;
            break;
          }
        }
        var top = Math.max(0, rect.top);
        // Primary router navigation usually appears near the top of the page.
        var score = top + (clickNode === el ? 20 : 0) + (d > 0 ? 20 : 0);
        if (rect.width <= 0 && rect.height <= 0) score += 300;
        results.push({ el: clickNode, score: score });
      }
    }
    results.sort(function(a,b){ return a.score - b.score; });
    return results.length ? results[0].el : null;
  }

  function commonAncestor(a, b) {
    if (!a || !b || a.ownerDocument !== b.ownerDocument) return null;
    var ancestor = a;
    while (ancestor && ancestor !== a.ownerDocument.body) {
      if (ancestor.contains(b)) return ancestor;
      ancestor = ancestor.parentElement;
    }
    return null;
  }

  function refresh() {
    for (var k = 0; k < sections.length; k++) {
      var key = sections[k];
      // Keep the original element if the menu is hidden.
      var item = found[key];
      if (!item || !item.isConnected) found[key] = findItem(key);
    }
    var ready = !!(found.Home && found.Settings && found.Features && found.Management);
    if (ready && !hidden) {
      var root = commonAncestor(found.Home, found.Settings);
      root = commonAncestor(root, found.Features);
      root = commonAncestor(root, found.Management);
      if (root && found.Logout) root = commonAncestor(root, found.Logout);
      if (root && root !== root.ownerDocument.body &&
          root !== root.ownerDocument.documentElement) {
        var box = root.getBoundingClientRect();
        if (box.height > 15 && box.height < 210 && box.top < 310) {
          hidden = root;
          previousDisplay = root.style.display;
          root.style.display = "none";
        }
      }
    }
    return ready;
  }

  window.DLinkBridge = {
    refresh: refresh,
    open: function(label) {
      if (sections.indexOf(label) < 0) return false;
      refresh();
      var el = found[label];
      if (!el || !el.isConnected) return false;
      try {
        el.click();
        return true;
      } catch(e) {
        return false;
      }
    },
    showOriginalMenu: function() {
      if (hidden) {
        hidden.style.display = previousDisplay;
        hidden = null;
      }
    }
  };
  return refresh();
})();