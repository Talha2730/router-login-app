(function() {
  "use strict";
  try {
    // This runs ONLY on the local D-Link login URL.
    // Change appearance only: never inspect, copy, or submit credentials.
    if (!/\/login(?:\.|\/|$)/i.test(location.pathname)) return false;
    var password = document.querySelector('input[type="password"]');
    if (!password) return false;

    var panel = null;
    var node = password.parentElement;
    for (var i = 0; i < 9 && node; i++, node = node.parentElement) {
      var box = node.getBoundingClientRect();
      var fields = node.querySelectorAll('input').length;
      var submit = node.querySelector('input[type="submit"],input[type="button"],button');
      if (fields >= 2 && submit && box.width >= 180 && box.width <= 700 &&
          box.height >= 90 && box.height <= 600) {
        // Prefer the login form's outer panel rather than a single field row.
        panel = node;
        if (box.width >= 270 && box.height >= 155) break;
      }
    }
    if (!panel) return false;

    var box = panel.getBoundingClientRect();
    var viewport = document.querySelector('meta[name="viewport"]');
    var widthMatch = viewport && /(?:^|,)width=(\d+)/.exec(viewport.content || '');
    var screenWidth = widthMatch ? parseInt(widthMatch[1],10) : document.documentElement.clientWidth;
    if (!screenWidth || !isFinite(screenWidth)) screenWidth = 980;
    var panelWidth = box.width > 0 ? box.width : 350;
    var factor = Math.min(2.25, Math.max(1.0, (screenWidth * 0.84) / panelWidth));

    panel.setAttribute('data-dlink-mobile-login', 'true');
    panel.style.setProperty('position', 'fixed', 'important');
    panel.style.setProperty('left', ((screenWidth - panelWidth * factor)/2).toFixed(1) + 'px', 'important');
    panel.style.setProperty('top', '155px', 'important');
    panel.style.setProperty('transform', 'scale(' + factor.toFixed(3) + ')', 'important');
    panel.style.setProperty('transform-origin', 'top left', 'important');
    panel.style.setProperty('z-index', '10000', 'important');

    var style = document.getElementById('dlink-readable-login-styles');
    if (!style) {
      style = document.createElement('style');
      style.id = 'dlink-readable-login-styles';
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent =
      '[data-dlink-mobile-login="true"] input:not([type="hidden"]){' +
        'font-size:16px!important;font-weight:600!important;min-height:32px!important;' +
      '}' +
      '[data-dlink-mobile-login="true"] button,' +
      '[data-dlink-mobile-login="true"] input[type="submit"],' +
      '[data-dlink-mobile-login="true"] input[type="button"]{' +
        'font-size:16px!important;font-weight:700!important;min-height:35px!important;' +
      '}';
    return true;
  } catch (e) {
    return false;
  }
})();