/* ============================================================================
   Lumina SERP skin — behaviour half (P5 layer A)
   Injected once at document_start. ~3 KB. No dependencies, no network.
   Job: theme attributes, result detection (for the native hand-off), skeletons.
   ========================================================================== */
(function () {
  "use strict";
  if (window.__luminaSkin) return;
  window.__luminaSkin = { version: 1, selectors: {} };

  var NS = window.__luminaSkin;

  // ---- selector map injected by the app (assets/serp/selectors.json) --------
  // Each key is a list; first match wins; no match = no-op (fail open).
  NS.selectors = NS.selectors || (window.__LUMINA_SELECTORS__ || {
    result: ["article[data-testid='result']", ".result", "div.web-result"],
    title: ["[data-testid='result-title-a']", ".result__a"],
    snippet: ["[data-result='snippet']", ".result__snippet"],
    url: ["[data-testid='result-url']", ".result__url"],
    ia: ["[data-testid='zci']", ".zci"]
  });

  function firstMatch(root, list) {
    for (var i = 0; i < list.length; i++) {
      try {
        var el = root.querySelector(list[i]);
        if (el) return el;
      } catch (e) { /* bad selector — skip */ }
    }
    return null;
  }
  function allMatches(root, list) {
    for (var i = 0; i < list.length; i++) {
      try {
        var els = root.querySelectorAll(list[i]);
        if (els && els.length) return [].slice.call(els);
      } catch (e) { /* skip */ }
    }
    return [];
  }

  // ---- theme (the app calls LuminaSkin.setTheme) ---------------------------
  NS.setTheme = function (theme) {               // "dark" | "light"
    document.documentElement.setAttribute("data-lumina-theme", theme);
  };
  NS.setMotion = function (on) {
    document.documentElement.setAttribute("data-lumina-motion", on ? "on" : "off");
  };

  // ---- structure: tag what we found, so the app can find it natively ------
  function tagResults() {
    var results = allMatches(document, NS.selectors.result);
    NS.lastCount = results.length;
    results.forEach(function (r, i) {
      if (!r.hasAttribute("data-lumina-index")) {
        r.setAttribute("data-lumina-index", String(i));
        r.setAttribute("data-lumina-row", "1");
      }
    });
    return results;
  }

  // ---- extract a result list for the native card layer (layer B) ----------
  NS.extract = function () {
    return tagResults().map(function (r) {
      var t = firstMatch(r, NS.selectors.title);
      var s = firstMatch(r, NS.selectors.snippet);
      var u = firstMatch(r, NS.selectors.url);
      return {
        title: t ? (t.textContent || "").trim() : "",
        url: u ? (u.getAttribute("href") || u.textContent || "").trim() : "",
        snippet: s ? (s.textContent || "").trim() : "",
        index: Number(r.getAttribute("data-lumina-index") || 0)
      };
    }).filter(function (x) { return x.title && x.url; });
  };

  // ---- infinite scroll: skeleton, never a spinner ------------------------
  function addSkeleton() {
    var host = firstMatch(document, ["#links", ".results", ".serp__results", "[data-testid='mainline']"]);
    if (!host || host.querySelector(".lumina-skeleton")) return;
    for (var i = 0; i < 2; i++) {
      var s = document.createElement("div");
      s.className = "lumina-skeleton";
      host.appendChild(s);
    }
  }

  function observe() {
    if (!("MutationObserver" in window)) return;
    var mo = new MutationObserver(function () {
      clearTimeout(NS._t);
      NS._t = setTimeout(function () {
        tagResults();
        var host = firstMatch(document, ["#links", ".results", ".serp__results"]);
        if (host) {
          var sk = host.querySelectorAll(".lumina-skeleton");
          for (var i = 0; i < sk.length; i++) sk[i].remove();
        }
      }, 60);
    });
    mo.observe(document.documentElement, { childList: true, subtree: true });
  }

  function boot() {
    tagResults();
    observe();
    // tell the app the skin is live (host listens on this channel)
    try {
      if (window.LuminaHost && window.LuminaHost.onSkinReady) {
        window.LuminaHost.onSkinReady({ results: NS.lastCount || 0 });
      }
    } catch (e) { /* no host bridge — fine, this is a normal page too */ }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot, { once: true });
  } else {
    boot();
  }
})();
