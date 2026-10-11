(function() {
  "use strict";
  var sections = ["Home", "Settings", "Features", "Management", "Logout"];
  var found = {};
  var hidden = null;
  var previousDisplay = "";

  function normalize(s) {
    return String(s || "").replace(/\s+/g, " ").trim().toLowerCase();
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


  // Read only a small allowlist of non-secret status fields from the local router.
  // Values are extracted from the authenticated page already open in WebView.
  var STATUS_FIELDS = {
    signal: ["Signal Intensity", "Signal Strength", "RSSI"],
    network: ["Network Status", "Connection Status"],
    provider: ["Network Provider", "Operator", "Carrier"],
    clients: ["Connected Clients", "Connected Devices"],
    type: ["Net Type", "Network Type"],
    band: ["Band", "LTE Band"],
    uptime: ["Connection Uptime", "Uptime"],
    ip: ["IP Address", "WAN IP Address"]
  };

  function statusText(el) {
    if (!el || el.tagName === "INPUT") return "";
    var t = String(el.textContent || "").replace(/\s+/g, " ").trim();
    return t.length <= 90 ? t : "";
  }

  function validStatus(key, value) {
    var v = String(value || "").replace(/\s+/g, " ").trim();
    if (!v || v.length > 70) return false;
    for (var k in STATUS_FIELDS)
      if (STATUS_FIELDS[k].some(function(label) { return normalize(label) === normalize(v); }))
        return false;
    if (key === "signal") return /-?\d{1,3}\s*dBm/i.test(v) || /^\d{1,3}%$/.test(v);
    if (key === "network") return /^(connected|disconnected|connecting|offline|online|no service|limited|registered)$/i.test(v);
    if (key === "clients") return /^\d{1,3}$/.test(v);
    if (key === "type") return /^(2G|3G|4G|5G|LTE|NR|HSPA\+?|WCDMA|GSM|EDGE|LTE[\s-]?[A-Z0-9]+)$/i.test(v);
    if (key === "band") return /^(?:LTE[\s-]*)?BAND[\s-]*\d+[A-Z]?$/i.test(v) || /^B\d{1,3}$/i.test(v);
    if (key === "uptime") return /\d+\s*(?:day|hour|min|sec|:)/i.test(v) || /^\d{1,2}:\d{2}(?::\d{2})?$/.test(v);
    if (key === "ip") return /^(?:\d{1,3}\.){3}\d{1,3}$/.test(v);
    if (key === "provider") return v.length > 1 && !/^(home|settings|features|management|logout|internet|mobile network|ipv4|ipv6)$/i.test(v);
    return false;
  }

  function statusFromDocument(doc, key) {
    var aliases = STATUS_FIELDS[key];
    if (!aliases) return "";
    var els = doc.querySelectorAll("th,td,span,div,p,label,strong,b,small,li");
    var best = "", bestRank = -1;
    function consider(value, rank) {
      if (validStatus(key, value) && rank > bestRank) {
        best = value.replace(/\s+/g, " ").trim();
        bestRank = rank;
      }
    }
    for (var i = 0; i < els.length; i++) {
      var el = els[i], label = statusText(el);
      if (!label) continue;
      var matched = false;
      for (var a = 0; a < aliases.length; a++) {
        var alias = normalize(aliases[a]), normalized = normalize(label);
        if (normalized === alias || normalized === alias + ":") matched = true;
        if (key === "clients" && normalized.indexOf(alias + ":") === 0)
          consider(label.substring(aliases[a].length + 1), 110);
      }
      if (!matched) continue;
      if (el.nextElementSibling) consider(statusText(el.nextElementSibling), 90);
      var parent = el.parentElement;
      if (!parent) continue;
      if (parent.nextElementSibling) {
        consider(statusText(parent.nextElementSibling), 80);
        if (parent.nextElementSibling.children.length === 1)
          consider(statusText(parent.nextElementSibling.firstElementChild), 72);
      }
      var cell = el.closest("th,td");
      if (cell && cell.nextElementSibling)
        consider(statusText(cell.nextElementSibling), 85);
      if (cell && cell.parentElement && cell.parentElement.nextElementSibling) {
        var row = cell.parentElement, next = row.nextElementSibling;
        var col = Array.prototype.indexOf.call(row.children, cell);
        if (next.children[col]) consider(statusText(next.children[col]), 100);
        if (next.children.length === 1) consider(statusText(next.children[0]), 70);
      }
      var table = el.closest("table");
      if (table && table.querySelectorAll("tr").length === 2) {
        var rows = table.querySelectorAll("tr");
        var header = rows[0].querySelectorAll("th,td"), values = rows[1].querySelectorAll("th,td");
        for (var c = 0; c < header.length; c++)
          if (header[c].contains(el) && values[c]) consider(statusText(values[c]), 105);
      }
    }
    return best;
  }

  function readQuickStatus() {
    var output = {}, documents = docs();
    for (var key in STATUS_FIELDS) {
      for (var i = 0; i < documents.length; i++) {
        var value = statusFromDocument(documents[i], key);
        if (value) { output[key] = value; break; }
      }
    }
    return output;
  }

  window.DLinkBridge = {
    refresh: refresh,
    status: readQuickStatus,
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