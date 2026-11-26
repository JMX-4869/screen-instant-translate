/* 页内即时翻译：不改动原 DOM 结构，只在视口上叠一层译气泡。
   由书签/油猴引入，script src 里的 host 会自动推断。 */
(function () {
  if (window.__SI_LOADED__) {
    window.__SI_TOGGLE__ && window.__SI_TOGGLE__();
    return;
  }
  window.__SI_LOADED__ = true;

  var SELF = document.currentScript;
  var API = (SELF && SELF.src ? new URL(SELF.src).origin : location.origin);
  var CACHE_KEY = "__si_cache_v1";
  var cache = {};
  try { cache = JSON.parse(localStorage.getItem(CACHE_KEY) || "{}"); } catch (e) { cache = {}; }
  var mem = {};
  var saveTimer = null;

  function persist() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      try {
        var keys = Object.keys(cache);
        if (keys.length > 400) keys.slice(0, keys.length - 400).forEach(function (k) { delete cache[k]; });
        localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
      } catch (e) {}
    }, 800);
  }

  var layer = document.createElement("div");
  layer.id = "__si_layer";
  layer.style.cssText =
    "position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:2147483600;contain:layout style;";
  document.documentElement.appendChild(layer);

  var btn = document.createElement("div");
  btn.textContent = "译";
  btn.style.cssText =
    "position:fixed;right:14px;bottom:96px;width:44px;height:44px;border-radius:22px;" +
    "background:rgba(20,20,20,.82);color:#fff;font:600 17px/44px -apple-system,system-ui,sans-serif;" +
    "text-align:center;z-index:2147483601;pointer-events:auto;cursor:pointer;user-select:none;" +
    "box-shadow:0 2px 10px rgba(0,0,0,.35);backdrop-filter:blur(6px)";
  document.documentElement.appendChild(btn);

  var tip = document.createElement("div");
  tip.style.cssText =
    "position:fixed;right:14px;bottom:146px;padding:3px 8px;border-radius:10px;background:rgba(0,0,0,.72);" +
    "color:#fff;font:500 11px/16px system-ui;z-index:2147483601;pointer-events:none;opacity:0;transition:opacity .2s";
  document.documentElement.appendChild(tip);

  var state = { on: false, auto: false, results: new Map(), pending: new Set(), io: null };

  function say(msg, ms) {
    tip.textContent = msg;
    tip.style.opacity = "1";
    if (ms) setTimeout(function () { tip.style.opacity = "0"; }, ms);
  }

  function images() {
    var out = [];
    document.querySelectorAll("img").forEach(function (img) {
      var w = img.naturalWidth || 0, h = img.naturalHeight || 0;
      if (w < 240 || h < 240) return;
      var r = img.getBoundingClientRect();
      if (r.width < 120 || r.height < 120) return;
      out.push({ img: img, url: img.currentSrc || img.src, rect: r });
    });
    return out;
  }

  function keyOf(url) { return url; }

  function applyBox(rect, line, host) {
    var el = document.createElement("div");
    var vertical = line.vertical || line.h / Math.max(line.w, 1e-6) > 2.2; // 竖排日文
    var padX = Math.max(2, rect.width * 0.004);
    var padY = Math.max(1.5, rect.height * 0.003);
    var w = Math.max(8, line.w * rect.width + padX * 2);
    var h = Math.max(8, line.h * rect.height + padY * 2);
    var left = rect.left + line.x * rect.width - padX;
    var top = rect.top + line.y * rect.height - padY;
    // 不越出图片边界
    left = Math.max(rect.left, left);
    top = Math.max(rect.top, top);
    w = Math.min(w, rect.right - left);
    h = Math.min(h, rect.bottom - top);

    var fs = vertical ? w * 0.78 : h * 0.80;
    el.textContent = line.tr || line.text;
    el.style.cssText =
      "position:absolute;left:" + left + "px;top:" + top + "px;" +
      "width:" + w + "px;height:" + h + "px;" +
      "font-size:" + fs + "px;line-height:1.1;" +
      "color:#111;background:#fff;border-radius:3px;padding:1px 2px;" +
      "box-sizing:border-box;overflow:hidden;display:flex;align-items:center;" +
      "justify-content:" + (vertical ? "flex-start" : "center") + ";" +
      "writing-mode:" + (vertical ? "vertical-rl" : "horizontal-tb") + ";" +
      "white-space:" + (vertical ? "normal" : "pre-wrap") + ";" +
      "letter-spacing:0;text-align:center;";
    host.appendChild(el);

    // 缩到正好放得下，最多 12 次
    var guard = 0;
    while (guard++ < 12 && (el.scrollHeight > el.clientHeight + 1 || el.scrollWidth > el.clientWidth + 1)) {
      fs *= 0.9;
      if (fs < 7.5) break;
      el.style.fontSize = fs + "px";
    }
  }

  function render() {
    layer.textContent = "";
    if (!state.on) return;
    state.results.forEach(function (res, img) {
      if (!res || !res.lines || !res.lines.length) return;
      if (!img.isConnected) return;
      var r = img.getBoundingClientRect();
      if (r.bottom < -50 || r.top > innerHeight + 50) return;
      res.lines.forEach(function (line) {
        if (!line.tr && !line.text) return;
        applyBox(r, line, layer);
      });
    });
  }

  var raf = null;
  function schedule() {
    if (raf) return;
    raf = requestAnimationFrame(function () { raf = null; render(); });
  }
  addEventListener("scroll", schedule, { passive: true, capture: true });
  addEventListener("resize", schedule);
  addEventListener("orientationchange", schedule);

  function visible(list) {
    return list.filter(function (x) {
      var r = x.rect;
      return r.bottom > -100 && r.top < innerHeight + 100;
    });
  }

  async function translate(list) {
    var jobs = [];
    list.forEach(function (x) {
      var k = keyOf(x.url);
      if (state.results.has(x.img) || state.pending.has(k)) return;
      var hit = cache[k] || mem[k];
      if (hit) { state.results.set(x.img, hit); return; }
      state.pending.add(k);
      jobs.push({ id: k, url: x.url, referer: location.href });
    });
    if (!jobs.length) { schedule(); return; }

    say("识别中… " + jobs.length + " 张");
    var t0 = performance.now();
    try {
      var r = await fetch(API + "/api/translate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ items: jobs.slice(0, 6) })
      });
      var data = await r.json();
      var done = 0, needKey = false;
      jobs.slice(0, 6).forEach(function (j) {
        var res = data.results[j.id];
        state.pending.delete(j.id);
        if (!res || res.error) return;
        if (res.need_key) { needKey = true; return; }
        cache[j.id] = res; mem[j.id] = res; done++;
      });
      if (needKey) { say("还没填 API Key，先开设置页", 6000); return; }
      jobs.slice(6).forEach(function (j) { state.pending.delete(j.id); });
      // 把结果挂回对应 img
      list.forEach(function (x) {
        var k = keyOf(x.url);
        var res = cache[k] || mem[k];
        if (res) state.results.set(x.img, res);
      });
      persist();
      var ms = Math.round(performance.now() - t0);
      say(done + " 页 · " + ms + "ms", 2200);
    } catch (e) {
      say("服务未连上：" + (e.message || e).slice(0, 30), 4000);
    }
    schedule();
  }

  function scanOnce() {
    var list = visible(images());
    translate(list);
  }

  function startAuto() {
    state.io && state.io.disconnect();
    state.io = new IntersectionObserver(function (entries) {
      var list = entries.filter(function (e) { return e.isIntersecting; })
        .map(function (e) { return { img: e.target, url: e.target.currentSrc || e.target.src, rect: e.target.getBoundingClientRect() }; })
        .filter(function (x) { return (x.img.naturalWidth || 0) >= 240; });
      if (list.length) translate(list);
    }, { rootMargin: "400px 0px" });
    document.querySelectorAll("img").forEach(function (i) {
      if ((i.naturalWidth || 0) >= 240) state.io.observe(i);
    });
  }

  function on() {
    state.on = true;
    btn.style.background = "rgba(0,132,255,.9)";
    scanOnce();
  }
  function off() {
    state.on = false;
    btn.style.background = "rgba(20,20,20,.82)";
    layer.textContent = "";
  }

  window.__SI_TOGGLE__ = function () { state.on ? off() : on(); };

  var longTimer = null;
  btn.addEventListener("pointerdown", function () {
    longTimer = setTimeout(function () {
      state.auto = !state.auto;
      say(state.auto ? "自动模式开" : "自动模式关", 1500);
      btn.style.boxShadow = state.auto ? "0 0 0 3px rgba(0,132,255,.5)" : "0 2px 10px rgba(0,0,0,.35)";
      if (state.auto && state.on) startAuto();
    }, 600);
  });
  btn.addEventListener("pointerup", function () { clearTimeout(longTimer); });
  btn.addEventListener("click", function () {
    if (state.auto && !state.on) { on(); startAuto(); return; }
    state.on ? off() : on();
  });

  // 页面跳转/图片换页后自动跟随
  var mo = new MutationObserver(function () { if (state.on) schedule(); });
  mo.observe(document.documentElement, { childList: true, subtree: true });

  say("点「译」翻译本页", 2600);
})();
