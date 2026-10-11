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
    for (var d = 0; d < result.length && result.length < 20; d++) {
      try {
        var frames = result[d].querySelectorAll("iframe,frame");
        for (var i = 0; i < frames.length && result.length < 20; i++) {
          try {
            var nested = frames[i].contentDocument;
            if (nested && result.indexOf(nested) < 0) result.push(nested);
          } catch (e) { /* Other origins cannot be read. */ }
        }
      } catch (e) { }
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


  // Increase the contrast of the actual router's status tiles without copying
  // their values or altering settings, authentication, or page navigation.
  function emphasizeRouterStatus() {
    var labels = [
      "signal intensity","signal strength","network provider","network status",
      "connection uptime","ip address","default gateway","primary dns server",
      "secondary dns server","imei","net type","band"
    ];
    var changed = 0;
    var pages = docs();
    for (var d = 0; d < pages.length; d++) {
      var doc = pages[d];
      var text = (doc.body && doc.body.textContent) || "";
      if (!/signal intensity|signal strength/i.test(text) ||
          !/network provider|network status/i.test(text)) continue;
      var elements = doc.querySelectorAll("td,th,span,small,label,p,div,strong,b");
      for (var i = 0; i < elements.length; i++) {
        var el = elements[i];
        if (el.children.length > 0 && (el.textContent || "").length > 38) continue;
        var value = normalize(el.textContent);
        var isClients = value.indexOf("connected clients:") === 0;
        var isLabel = labels.indexOf(value) !== -1;
        var isHeading = value === "internet" || value === "mobile network";
        if (!isLabel && !isHeading && !isClients) continue;

        var cell = el.closest("td,th");
        var table = cell ? cell.closest("table") : null;
        if (table && table.querySelectorAll("tr").length <= 3) {
          var tiles = table.querySelectorAll("td,th");
          for (var t = 0; t < tiles.length; t++) {
            // Increase readable text size, but keep long labels inside the
            // router's fixed-width status table cells.
            var tileLength = normalize(tiles[t].textContent).length;
            var tileSize = tileLength > 18 ? "17px" : "21px";
            tiles[t].style.setProperty("font-weight", "700", "important");
            tiles[t].style.setProperty("font-size", tileSize, "important");
            tiles[t].style.setProperty("color", "#163749", "important");
            tiles[t].style.setProperty("line-height", "1.25", "important");
          }
        }
        el.style.setProperty("font-weight", "700", "important");
        el.style.setProperty("font-size",
          isHeading ? "26px" : isClients ? "22px" :
          value.length > 16 ? "18px" : "22px", "important");
        el.style.setProperty("color", "#14384b", "important");
        changed++;
      }
    }
    return changed > 0;
  }


  // Mobile native dashboard uses a strict allowlist of non-secret, labeled
  // router status fields. Unlike global page regexes, candidates must be near
  // the corresponding label and pass field-specific validation.
  function readMobileStatus() {
    var keys = {
      signal: ["Signal Intensity", "Signal Strength", "RSSI"],
      clients: ["Connected Clients", "Connected Devices"],
      network: ["Network Status", "Connection Status"],
      provider: ["Network Provider"],
      type: ["Net Type", "Network Type"],
      band: ["Band", "LTE Band"],
      uptime: ["Connection Uptime", "Uptime"],
      ip: ["IP Address", "WAN IP Address"],
      gateway: ["Default Gateway"],
      primaryDns: ["Primary DNS Server"],
      secondaryDns: ["Secondary DNS Server"],
      imei: ["IMEI"]
    };
    var answer = {};
    var sources = docs();

    function trimText(value) {
      return String(value == null ? "" : value).replace(/\s+/g, " ").trim();
    }

    function valid(key, candidate) {
      var text = trimText(candidate);
      if (!text || text.length > 75) return false;
      if (key === "signal") return /^-?\d{1,3}\s*dBm$/i.test(text) || /^\d{1,3}\s*%$/.test(text);
      if (key === "clients") return /^\d{1,3}$/.test(text);
      if (key === "network") return /^(connected|disconnected|connecting|online|offline|limited|no service|registered|unregistered)$/i.test(text);
      if (key === "type") return /^(2G|3G|4G|5G|LTE|LTE-A|NR|HSPA\+?|WCDMA|GSM|EDGE)$/i.test(text);
      if (key === "band") return /^(?:LTE\s*)?BAND\s*\d+[A-Z]?$/i.test(text) || /^B\d{1,3}$/i.test(text);
      if (key === "uptime") return /(?:\d+\s*(?:day|hour|min|sec)|^\d{1,2}:\d{2}(?::\d{2})?$)/i.test(text);
      if (key === "ip" || key === "gateway" || key === "primaryDns" || key === "secondaryDns")
        return /^(?:\d{1,3}\.){3}\d{1,3}$/.test(text);
      if (key === "imei") return /^\d{14,17}$/.test(text);
      if (key === "provider")
        return /^[A-Za-z0-9][A-Za-z0-9 ._+\-]{1,40}$/.test(text) &&
          !/^(home|settings|features|management|connected|disconnected|internet|mobile network|ipv4|ipv6)$/i.test(text);
      return false;
    }

    function resolve(doc, key, aliases) {
      var items = doc.querySelectorAll("td,th,span,div,p,label,strong,b,li,small,h1,h2,h3,h4");
      var best = "", score = -1;
      function consider(candidate, rank) {
        var value = trimText(candidate);
        if (valid(key, value) && rank > score) {
          best = value;
          score = rank;
        }
      }

      for (var i = 0; i < items.length; i++) {
        var labelNode = items[i];
        if (labelNode.closest && labelNode.closest("#dlink-mobile-status-render")) continue;
        var label = trimText(labelNode.textContent);
        if (!label || label.length > 55) continue;
        var matched = "";
        for (var a = 0; a < aliases.length; a++) {
          if (normalize(label) === normalize(aliases[a]) ||
              normalize(label) === normalize(aliases[a]) + ":") {
            matched = aliases[a];
            break;
          }
          if (normalize(label).indexOf(normalize(aliases[a]) + ":") === 0) {
            matched = aliases[a];
            consider(label.substring(matched.length + 1), 125);
          }
        }
        if (!matched) continue;

        var next = labelNode.nextElementSibling;
        if (next) {
          consider(next.textContent, 105);
          if (next.firstElementChild) consider(next.firstElementChild.textContent, 100);
        }

        var parent = labelNode.parentElement;
        if (!parent) continue;
        var remainder = trimText(parent.textContent);
        if (normalize(remainder).indexOf(normalize(matched)) === 0)
          consider(remainder.slice(matched.length).replace(/^[:\s]+/, ""), 112);

        if (parent.nextElementSibling) {
          consider(parent.nextElementSibling.textContent, 98);
          if (parent.nextElementSibling.firstElementChild)
            consider(parent.nextElementSibling.firstElementChild.textContent, 94);
        }

        var cell = labelNode.closest("td,th");
        if (cell && cell.nextElementSibling)
          consider(cell.nextElementSibling.textContent, 102);
        if (cell && cell.parentElement && cell.parentElement.nextElementSibling) {
          var row = cell.parentElement;
          var following = row.nextElementSibling;
          var at = Array.prototype.indexOf.call(row.children, cell);
          if (following.children[at]) consider(following.children[at].textContent, 115);
        }
        var container = parent.parentElement;
        if (container && container.childElementCount <= 3 &&
            trimText(container.textContent).length < 95 &&
            normalize(container.textContent).indexOf(normalize(matched)) === 0)
          consider(trimText(container.textContent).slice(matched.length).replace(/^[:\s]+/, ""), 80);
      }

      // Some firmware versions expose status values in successive text lines.
      if (!best && doc.body) {
        var lines = String(doc.body.innerText || "")
          .split(/[\n\r\t]+/).map(trimText).filter(Boolean);
        for (var l = 0; l < lines.length; l++) {
          for (var j = 0; j < aliases.length; j++) {
            var wanted = normalize(aliases[j]);
            var line = normalize(lines[l]);
            if (line.indexOf(wanted + ":") === 0)
              consider(lines[l].substring(aliases[j].length + 1), 70);
            if (line === wanted || line === wanted + ":") {
              if (l + 1 < lines.length) consider(lines[l + 1], 65);
            }
          }
        }
      }

      // D-Link desktop firmware often puts ALL four column headings in one
      // table row and ALL values in the next. Therefore sibling text alone is
      // not reliable: use measured positions to pair heading to its value.
      if (!best) {
        var leaves = doc.querySelectorAll("td,th,span,div,p,label,strong,b,small,li,a");
        var headings = [], candidates = [];
        for (var n = 0; n < leaves.length; n++) {
          var item = leaves[n];
          if (!item.getBoundingClientRect) continue;
          var itemText = trimText(item.textContent);
          if (!itemText || itemText.length > 75) continue;
          var rect = item.getBoundingClientRect();
          if (rect.width <= 0 || rect.height <= 0) continue;
          var isHeading = aliases.some(function(label) {
            return normalize(itemText) === normalize(label) ||
              normalize(itemText) === normalize(label) + ":";
          });
          if (isHeading) {
            headings.push({ rect: rect, text: itemText });
          } else if (valid(key, itemText) &&
                     (!item.children.length || item.children.length < 2)) {
            candidates.push({ rect: rect, text: itemText,
              complexity: item.children.length });
          }
        }
        var bestDistance = 999999;
        for (var h = 0; h < headings.length; h++) {
          var head = headings[h].rect;
          var hx = head.left + head.width / 2;
          for (var v = 0; v < candidates.length; v++) {
            var node = candidates[v];
            var box = node.rect;
            var vx = box.left + box.width / 2;
            var dx = Math.abs(vx - hx);
            var dy = box.top - head.bottom;
            if (dy < -7 || dy > 110 || dx > Math.max(80, head.width * 1.8))
              continue;
            var distance = dy * 2 + dx * 1.5 +
              (node.complexity ? 12 : 0);
            if (distance < bestDistance) {
              bestDistance = distance;
              best = node.text;
            }
          }
        }
      }

      // Some versions place the label and value together on one line.
      if (!best && doc.body) {
        var raw = String(doc.body.innerText || "");
        for (var a = 0; a < aliases.length; a++) {
          var escaped = aliases[a].replace(/[^A-Za-z0-9 ]/g, "");
          var line = new RegExp("(?:^|\\n)\\s*" + escaped +
            "\\s*:\\s*([^\\r\\n]+)", "im");
          var found = raw.match(line);
          if (found && valid(key, found[1])) { best = trimText(found[1]); break; }
        }
      }
      return best;
    }

    for (var key in keys) {
      for (var d = 0; d < sources.length; d++) {
        var value = resolve(sources[d], key, keys[key]);
        if (value) {
          answer[key] = value;
          break;
        }
      }
    }
    return answer;
  }

  window.DLinkBridge = {
    refresh: refresh,
    styleStatus: emphasizeRouterStatus,
    status: readMobileStatus,
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
  var ready = refresh();
  emphasizeRouterStatus();
  return ready;
})();