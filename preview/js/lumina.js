/* ============================================================================
   Lumina prototype behaviour
   - spring-ish motion via Web Animations (mirrors LuminaSpring in Kotlin)
   - pointer-tracking specular on every .glass surface (Kyant InteractiveHighlight)
   - screen machine: home · omnibar · serp · tabs + sheets
   ========================================================================== */

const ROOT = document.documentElement;
const $  = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];

const SPRING       = "cubic-bezier(.22,1.14,.30,1)";
const SPRING_SOFT  = "cubic-bezier(.25,.9,.3,1)";
const STAGGER      = 30;
const reduced = () => ROOT.dataset.motion === "off";

/* ------------------------------------------------------------------ data -- */

const favourites = [
  { name: "GitHub",   letter: "G", tint: "rgba(140,160,255,.22)" },
  { name: "YouTube",  letter: "Y", tint: "rgba(255,120,120,.22)" },
  { name: "Docs",     letter: "D", tint: "rgba(110,200,255,.22)" },
  { name: "News",     letter: "N", tint: "rgba(180,140,255,.22)" },
  { name: "Reddit",   letter: "R", tint: "rgba(255,170,110,.22)" },
];

const recent = [
  { title: "DuckDuckGo — Privacy, simplified.", domain: "duckduckgo.com", letter: "D" },
  { title: "Android Developers — Compose",     domain: "developer.android.com", letter: "A" },
  { title: "Kyant0 / AndroidLiquidGlass",      domain: "github.com", letter: "K" },
];

const suggestions = [
  { text: "best private browser android",        icon: "i-search" },
  { text: "best private browser android 2026",   icon: "i-search" },
  { text: "is lumina free and open source",      icon: "i-search" },
  { text: "liquid glass ui compose tutorial",    icon: "i-clock"  },
  { text: "duckduckgo vs brave privacy test",    icon: "i-globe"  },
];

const results = [
  {
    domain: "eff.org", letter: "E", title: "Cover Your Tracks — see how trackers view your browser",
    snippet: "Test your browser's fingerprint resistance. Lumina blocks third-party scripts, adds canvas noise and rejects cookie walls, so the fingerprint drifts between sessions.",
    trackers: 0, sources: ["eff.org"],
  },
  {
    domain: "privacyguides.org", letter: "P", title: "Android browsers — the privacy comparison",
    snippet: "A maintained comparison of mobile browsers by telemetry, sandboxing and tracker-blocking. Chromium-based browsers inherit the system WebView sandbox; Gecko-based ones ship their own engine.",
    trackers: 2, sources: ["privacyguides.org"],
  },
  {
    domain: "developer.android.com", letter: "A", title: "WebView security and performance guidance",
    snippet: "Renderer priority, warm-up strategy and the sandbox model for Android WebView. Explains why a pre-created WebView cuts 300–400 ms off first page paint.",
    trackers: 1, sources: ["developer.android.com"],
  },
  {
    domain: "github.com", letter: "K", title: "Kyant0 / AndroidLiquidGlass — backdrop engine",
    snippet: "Compose Multiplatform liquid glass: AGSL lens refraction, blur, vibrancy and specular highlight as a Modifier. Apache-2.0. Powers Lumina's glass layer.",
    trackers: 0, sources: ["github.com"],
  },
];

const tabs = [
  { title: "DuckDuckGo — Privacy", domain: "duckduckgo.com", current: true },
  { title: "Liquid glass in Compose", domain: "kyant.gitbook.io", current: false },
  { title: "Android WebView docs", domain: "developer.android.com", current: false },
  { title: "EFF: Cover Your Tracks", domain: "eff.org", current: false },
];

const answerText =
  "The most private option is a browser that sends no first-party telemetry, blocks third-party " +
  "scripts by default and keeps the AI on-device. Practically that means: no analytics SDK in the " +
  "APK, tracker blocking on at first launch, and summaries produced by Gemini Nano instead of a " +
  "cloud call. Chromium-based shells inherit the WebView sandbox, which is fine — the leak is rarely " +
  "the engine, it's the telemetry the app adds around it.";

/* --------------------------------------------------------------- helpers -- */

function animateIn(el, index = 0, distance = 24) {
  if (reduced() || !el.animate) return;
  el.animate(
    [
      { opacity: 0, transform: `translateY(${distance}px) scale(.985)` },
      { opacity: 1, transform: "none" },
    ],
    { duration: 460, delay: index * STAGGER, easing: SPRING, fill: "both" }
  );
}

function bounce(el) {
  if (reduced() || !el.animate) return;
  el.animate(
    [{ transform: "scale(1)" }, { transform: "scale(1.15)" }, { transform: "scale(1)" }],
    { duration: 340, easing: SPRING }
  );
}

/* --------------------------------------------------------------- renders -- */

function renderFavourites() {
  const row = $("#fav-row");
  row.innerHTML = "";
  favourites.forEach((f, i) => {
    const el = document.createElement("div");
    el.className = "glass fav pressable";
    el.style.background = `linear-gradient(150deg, ${f.tint}, rgba(255,255,255,.04))`;
    el.innerHTML = `<span class="letter">${f.letter}</span>`;
    el.title = f.name;
    el.addEventListener("click", () => go("serp"));
    row.appendChild(el);
    animateIn(el, i + 4, 16);
  });
}

function renderRecent() {
  const row = $("#recent-row");
  row.innerHTML = "";
  recent.forEach((r, i) => {
    const el = document.createElement("div");
    el.className = "recent";
    el.innerHTML = `
      <div class="favicon">${r.letter}</div>
      <div class="meta"><b>${r.title}</b><span>${r.domain}</span></div>`;
    el.addEventListener("click", () => go("serp"));
    row.appendChild(el);
    animateIn(el, i + 6, 12);
  });
}

function renderSuggestions() {
  const box = $("#om-suggest");
  box.innerHTML = "";
  suggestions.forEach((s, i) => {
    const el = document.createElement("div");
    el.className = "sugg" + (i === 0 ? " is-hi" : "");
    el.innerHTML = `<svg class="ic g" width="16" height="16"><use href="#${s.icon}"/></svg><span>${s.text}</span>`;
    el.addEventListener("click", () => {
      $("#om-text").textContent = s.text;
      go("serp");
    });
    box.appendChild(el);
  });
}

function renderSerp() {
  const serp = $("#serp");
  serp.innerHTML = "";

  // ---- AI answer card -----------------------------------------------------
  const answer = document.createElement("div");
  answer.className = "glass answer";
  answer.innerHTML = `
    <div class="head">
      <svg class="ic" width="14" height="14"><use href="#i-spark"/></svg> Lumina answer
      <span class="badge-od">on-device · 1.1s</span>
    </div>
    <p id="answer-body"></p>
    <div class="sources">
      <span class="src"><b>1</b> eff.org</span>
      <span class="src"><b>2</b> privacyguides.org</span>
      <span class="src"><b>3</b> developer.android.com</span>
    </div>`;
  serp.appendChild(answer);

  // streaming text — the one place a typewriter is legitimate
  const body = $("#answer-body", answer);
  if (reduced()) {
    body.textContent = answerText;
  } else {
    let i = 0;
    const step = () => {
      if (i > answerText.length) return;
      body.textContent = answerText.slice(0, i);
      i += 3;
      setTimeout(step, 12);
    };
    step();
  }

  // ---- results ------------------------------------------------------------
  results.forEach((r, i) => {
    const card = document.createElement("div");
    card.className = "glass result pressable";
    card.innerHTML = `
      <div class="top">
        <span class="domain"><i>${r.letter}</i>${r.domain}</span>
        ${r.trackers ? `<span class="track">⛨ ${r.trackers} blocked</span>` : ""}
      </div>
      <h2>${r.title}</h2>
      <p>${r.snippet}</p>
      <span class="more">open in Lumina →</span>`;
    card.addEventListener("click", () => go("tabs"));
    serp.appendChild(card);
    animateIn(card, i + 2);
  });

  // ---- related chips ------------------------------------------------------
  const chips = document.createElement("div");
  chips.className = "chips";
  chips.style.margin = "4px 0 8px";
  ["private browser 2026", "firefox focus vs brave", "webview sandbox", "on-device ai summary"]
    .forEach((t) => {
      const b = document.createElement("button");
      b.className = "glass chip pressable";
      b.textContent = t;
      b.addEventListener("click", () => go("omnibar"));
      chips.appendChild(b);
    });
  serp.appendChild(chips);

  // ---- instant answer footer ---------------------------------------------
  const foot = document.createElement("div");
  foot.className = "section-head";
  foot.style.margin = "14px 4px 0";
  foot.innerHTML = `<span>4 results · 0 ads</span><span style="text-transform:none;letter-spacing:0">private</span>`;
  serp.appendChild(foot);

  animateIn(answer, 0);
}

function renderTabs() {
  const grid = $("#tabs-grid");
  grid.innerHTML = "";
  tabs.forEach((t, i) => {
    const el = document.createElement("div");
    el.className = "glass tab-card pressable" + (t.current ? " is-current" : "");
    el.innerHTML = `
      <div class="tc-top"><svg class="ic" width="12" height="12"><use href="#i-globe"/></svg>${t.domain}</div>
      <div class="tc-preview"></div>
      <div class="tc-title">${t.title}</div>`;
    el.addEventListener("click", () => go("serp"));
    grid.appendChild(el);
    animateIn(el, i);
  });
  const add = document.createElement("div");
  add.className = "tab-new";
  add.innerHTML = `<svg class="ic" width="26" height="26"><use href="#i-plus"/></svg>`;
  add.addEventListener("click", () => go("omnibar"));
  grid.appendChild(add);
  animateIn(add, tabs.length);
}

/* ------------------------------------------------------- screen machine --- */

let current = "home";

function go(view) {
  if (view === current) return;
  const from = $(`[data-view="${current}"]`);
  const to   = $(`[data-view="${view}"]`);
  if (!to) return;

  from.classList.remove("is-active");
  to.classList.add("is-active");
  current = view;

  if (view === "serp") renderSerp();
  if (view === "tabs") renderTabs();

  // stagger the visible content of the incoming screen
  const items = $$(".glass, .greeting, .recent, .section", to)
    .filter((el) => el.closest(".bottom") === null);
  items.forEach((el, i) => animateIn(el, Math.min(i, 8), 18));

  $$("#seg-screen button").forEach((b) => b.classList.toggle("is-on", b.dataset.goto === view));
  positionGlow(to);
  bounce($(`[data-view="${view}"] .nav-item.is-active`));
}

function positionGlow(scope) {
  const bar = $(".bottom", scope) || $(".bottom");
  if (!bar) return;
  const glow = $(".glow", bar);
  const active = $(".nav-item.is-active", bar) || $(".nav-item", bar);
  if (!glow || !active) return;
  const x = active.offsetLeft + active.offsetWidth / 2 - glow.offsetWidth / 2;
  glow.style.transform = `translateX(${x}px)`;
}

/* ---------------------------------------------------------------- sheets -- */

function openSheet(name) {
  closeSheets();
  $(`#sheet-${name}`)?.classList.add("is-open");
  $("#scrim").classList.add("is-open");
  const sheet = $(`#sheet-${name}`);
  if (sheet) $$(".stat, .toggle-row, .blocked, .bubble", sheet).forEach((el, i) => animateIn(el, i, 14));
  if (name === "ai") streamAnswer();
}

function closeSheets() {
  $$(".sheet").forEach((s) => s.classList.remove("is-open"));
  $("#scrim").classList.remove("is-open");
}

function streamAnswer() {
  const el = $("#ai-stream");
  if (!el || el.dataset.done) return;
  el.dataset.done = "1";
  const text =
    "Because the leak is usually the app, not the engine: no analytics SDK at all, tracker " +
    "blocking on at first launch, and summaries generated on-device with Gemini Nano. " +
    "Lumina ships all three — with a per-site shield sheet so you can verify it.";
  if (reduced()) { el.textContent = text; return; }
  let i = 0;
  const step = () => {
    if (i > text.length) return;
    el.textContent = text.slice(0, i);
    i += 3;
    setTimeout(step, 14);
  };
  step();
}

/* ------------------------------------------------- pointer specular + press */

document.addEventListener("pointermove", (e) => {
  const glass = e.target.closest?.(".glass, .sheet");
  $$(".glass.is-live, .sheet.is-live").forEach((g) => { if (g !== glass) g.classList.remove("is-live"); });
  if (!glass) return;
  const r = glass.getBoundingClientRect();
  glass.style.setProperty("--mx", `${e.clientX - r.left}px`);
  glass.style.setProperty("--my", `${e.clientY - r.top}px`);
  glass.classList.add("is-live");
});

document.addEventListener("pointerdown", (e) => {
  const p = e.target.closest?.(".pressable, .nav-item, .chip, .fav, .result, .tab-card");
  if (p) p.classList.add("is-pressed");
});
document.addEventListener("pointerup", () => $$(".is-pressed").forEach((p) => p.classList.remove("is-pressed")));
document.addEventListener("pointercancel", () => $$(".is-pressed").forEach((p) => p.classList.remove("is-pressed")));

/* ----------------------------------------------------------- interactions - */

document.addEventListener("click", (e) => {
  const t = e.target;

  const sheetBtn = t.closest?.("[data-sheet]");
  if (sheetBtn) { openSheet(sheetBtn.dataset.sheet); return; }

  if (t.closest?.("#scrim")) { closeSheets(); return; }
  if (t.closest?.(".grabber")) { closeSheets(); return; }

  if (t.closest?.("[data-open-omnibar]")) { go("omnibar"); return; }

  const nav = t.closest?.("[data-nav]");
  if (nav) {
    const which = t.closest("[data-nav]").dataset.nav;
    if (which === "tabs") go("tabs");
    else if (which === "home") go("home");
    else if (which === "menu") openSheet("shield");
    return;
  }

  const goBtn = t.closest?.("[data-view-go]");
  if (goBtn) { go(goBtn.dataset.viewGo); return; }

  const sw = t.closest?.("[data-switch]");
  if (sw) {
    sw.setAttribute("aria-checked", sw.getAttribute("aria-checked") === "true" ? "false" : "true");
    bounce(sw);
    return;
  }

  const ctrl = t.closest?.("#seg-screen button");
  if (ctrl) { go(ctrl.dataset.goto); return; }
});

/* controls: tier / theme / motion */
$("#seg-tier").addEventListener("click", (e) => {
  const b = e.target.closest("button[data-tier]");
  if (!b) return;
  ROOT.dataset.tier = b.dataset.tier;
  $$("#seg-tier button").forEach((x) => x.classList.toggle("is-on", x === b));
});

$("#seg-theme").addEventListener("click", (e) => {
  const b = e.target.closest("button[data-theme]");
  if (!b) return;
  ROOT.dataset.theme = b.dataset.theme;
  $$("#seg-theme button").forEach((x) => x.classList.toggle("is-on", x === b));
});

$("#seg-motion").addEventListener("click", (e) => {
  const b = e.target.closest("button[data-motion]");
  if (!b) return;
  ROOT.dataset.motion = b.dataset.motion;
  $$("#seg-motion button").forEach((x) => x.classList.toggle("is-on", x === b));
});

/* keyboard: enter -> results, esc -> home */
document.addEventListener("keydown", (e) => {
  if (e.key === "Enter") go(current === "omnibar" ? "serp" : "serp");
  if (e.key === "Escape") { closeSheets(); if (current !== "home") go("home"); }
});

/* ---------------------------------------------------------------- boot ---- */

renderFavourites();
renderRecent();
renderSuggestions();
positionGlow($('[data-view="home"]'));
setTimeout(() => {
  $$('[data-view="home"] .glass, [data-view="home"] .greeting, [data-view="home"] .recent')
    .forEach((el, i) => animateIn(el, Math.min(i, 9), 20));
}, 120);
