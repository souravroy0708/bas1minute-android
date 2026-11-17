(function () {
  "use strict";
  var $ = function (sel, root) { return (root || document).querySelector(sel); };
  var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };
  var pad = function (n) { return n < 10 ? "0" + n : "" + n; };
  var reduceMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- Launch countdowns (one or more per page, same launch moment) ---------- */
  var countdowns = $$("[data-countdown]");
  var dockLeft = $("#dock-left");
  if (countdowns.length) {
    var launch = new Date(countdowns[0].getAttribute("data-launch")).getTime();
    var lastMinute = -1;
    var tick = function () {
      if (isNaN(launch)) return false;
      var diff = launch - Date.now();
      if (diff <= 0) {
        countdowns.forEach(function (cd) { cd.classList.add("is-live"); });
        if (dockLeft) dockLeft.textContent = "We’re live";
        return false;
      }
      var s = Math.floor(diff / 1000);
      var d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
      countdowns.forEach(function (cd) {
        $('[data-cd="days"]', cd).textContent = d;
        $('[data-cd="hours"]', cd).textContent = pad(h);
        $('[data-cd="minutes"]', cd).textContent = pad(m);
        $('[data-cd="seconds"]', cd).textContent = pad(sec);
        $('[data-cd="sweep"]', cd).setAttribute("stroke-dasharray", ((60 - sec) / 60 * 100).toFixed(1) + " 100");
      });
      if (m !== lastMinute) {
        lastMinute = m;
        var said = d + " days, " + h + " hours and " + m + " minutes until launch.";
        countdowns.forEach(function (cd) { $('[data-cd="sr"]', cd).textContent = said; });
        if (dockLeft) dockLeft.textContent = d > 0 ? d + (d === 1 ? " day" : " days") + " to launch" : h + "h " + m + "m to launch";
      }
      return true;
    };
    if (tick()) { var cdTimer = setInterval(function () { if (!tick()) clearInterval(cdTimer); }, 1000); }
  }

  /* ---------- Copy-to-clipboard buttons ---------- */
  document.addEventListener("click", function (ev) {
    var btn = ev.target.closest("[data-copy]");
    if (!btn) return;
    var text = btn.getAttribute("data-copy");
    if (!text) return;
    var label = $("span", btn);
    var done = function () {
      var old = label.textContent;
      label.textContent = "Link copied";
      setTimeout(function () { label.textContent = old; }, 2000);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(function () { window.prompt("Copy your invite link:", text); });
    } else {
      window.prompt("Copy your invite link:", text);
    }
  });

  /* ---------- Waitlist form: browser checks, fetch submit, plain POST without JS ---------- */
  var form = $("#waitlist-form");
  if (form) {
    var card = $("#join-card");
    var fields = {
      name: { input: $("#f-name"), error: $("#e-name") },
      contact: { input: $("#f-contact"), error: $("#e-contact") }
    };
    var defaults = { name: fields.name.error.textContent, contact: fields.contact.error.textContent };
    var setError = function (key, msg) {
      var f = fields[key];
      if (!f) return;
      f.input.setAttribute("aria-invalid", msg ? "true" : "false");
      f.error.textContent = msg || defaults[key];
      f.error.classList.toggle("show", !!msg);
    };
    var validContact = function (v) {
      v = v.trim();
      if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) return true;
      var digits = v.replace(/\D/g, "");
      if (digits.length === 12 && digits.indexOf("91") === 0) digits = digits.slice(2);
      else if (digits.length === 11 && digits.charAt(0) === "0") digits = digits.slice(1);
      return digits.length === 10 && "6789".indexOf(digits.charAt(0)) !== -1;
    };
    fields.name.input.addEventListener("input", function () { setError("name", ""); });
    fields.contact.input.addEventListener("input", function () { setError("contact", ""); });

    form.addEventListener("submit", function (ev) {
      ev.preventDefault();
      var okName = fields.name.input.value.trim().length > 0;
      var okContact = validContact(fields.contact.input.value);
      setError("name", okName ? "" : defaults.name);
      setError("contact", okContact ? "" : defaults.contact);
      if (!okName) { fields.name.input.focus(); return; }
      if (!okContact) { fields.contact.input.focus(); return; }

      var btn = $('button[type="submit"]', form);
      btn.disabled = true; btn.textContent = "Joining…";
      var restore = function () { btn.disabled = false; btn.textContent = "Join the waitlist"; };

      fetch(form.action, {
        method: "POST",
        body: new FormData(form),
        headers: { "X-Requested-With": "XMLHttpRequest", "Accept": "application/json" },
        credentials: "same-origin"
      }).then(function (r) {
        return r.json();
      }).then(function (data) {
        if (!data.ok) {
          restore();
          var errs = data.errors || {};
          Object.keys(fields).forEach(function (k) { setError(k, errs[k] ? errs[k][0] : ""); });
          var first = Object.keys(fields).filter(function (k) { return errs[k]; })[0];
          if (first) fields[first].input.focus();
          return;
        }
        var title = data.created ? "You’re on the list" : "You’re already on the list";
        $("#success-title").textContent = data.first_name ? title + ", " + data.first_name + "." : title + ".";
        if (data.position) {
          var pos = $("#success-pos");
          $("span", pos).textContent = data.position;
          pos.hidden = false;
        }
        if (data.whatsapp_url) $("#wa-share").href = data.whatsapp_url;
        if (data.share_url) $("#copy-share").setAttribute("data-copy", data.share_url);
        var success = $("#success");
        success.hidden = false;
        card.classList.add("is-done");
        success.focus();
      }).catch(function () {
        restore();
        setError("contact", "We couldn’t save that. Check your connection and try again.");
      });
    });
  }

  /* ---------- Try-a-burst: a real 60-second timer ---------- */
  var timer = $("[data-timer]");
  if (timer) {
    var TOTAL = 60, CIRC = 552.9;
    var ui = {
      ring: $('[data-t="ring"]', timer), num: $('[data-t="num"]', timer), cue: $('[data-t="cue"]', timer),
      toggle: $('[data-t="toggle"]', timer), reset: $('[data-t="reset"]', timer), done: $('[data-t="done"]', timer)
    };
    var toggleIcon = $("use", ui.toggle), toggleLabel = $("span", ui.toggle);
    var left = TOTAL, running = false, handle = null, endAt = 0;
    var chosenCue = function () {
      var r = $('input[name="burst"]:checked');
      return r ? r.getAttribute("data-cue") : "";
    };
    var cueFor = function (s) {
      if (s === TOTAL) return chosenCue();
      if (s > 45) return chosenCue();
      if (s > 30) return "Good pace. Keep it going.";
      if (s > 10) return "Halfway done. Breathing harder is the point.";
      if (s > 0) return "Last few seconds. Finish strong!";
      return "Done. That’s one burst.";
    };
    var lastCue = "";
    var paint = function () {
      ui.num.textContent = left;
      ui.ring.style.strokeDashoffset = (CIRC * left / TOTAL).toFixed(1);
      var c = cueFor(left);
      if (c !== lastCue) { ui.cue.textContent = c; lastCue = c; }
    };
    var setButton = function (state) {
      var map = { start: ["#i-play", "Start my minute"], pause: ["#i-pause", "Pause"], resume: ["#i-play", "Resume"], again: ["#i-reset", "Do another minute"] };
      toggleIcon.setAttribute("href", map[state][0]);
      toggleLabel.textContent = map[state][1];
    };
    var stop = function () { running = false; clearInterval(handle); handle = null; };
    var finish = function () {
      stop();
      left = 0; paint();
      timer.classList.add("is-done");
      ui.done.hidden = false;
      setButton("again");
      if (navigator.vibrate) navigator.vibrate([200, 100, 200]);
    };
    var step = function () {
      left = Math.max(0, Math.ceil((endAt - Date.now()) / 1000));
      paint();
      if (left <= 0) finish();
    };
    var start = function () {
      if (left <= 0) reset();
      running = true;
      endAt = Date.now() + left * 1000;
      handle = setInterval(step, 250);
      setButton("pause");
    };
    var reset = function () {
      stop();
      left = TOTAL;
      timer.classList.remove("is-done");
      ui.done.hidden = true;
      lastCue = "";
      paint();
      setButton("start");
    };
    if (reduceMotion) ui.ring.style.transition = "none";
    ui.ring.style.strokeDashoffset = CIRC;
    ui.toggle.addEventListener("click", function () {
      if (timer.classList.contains("is-done")) { reset(); start(); return; }
      if (running) { stop(); setButton("resume"); } else { start(); }
    });
    ui.reset.addEventListener("click", reset);
    $$('input[name="burst"]').forEach(function (r) {
      r.addEventListener("change", function () { if (!running && left === TOTAL) { ui.cue.textContent = chosenCue(); lastCue = ui.cue.textContent; } });
    });
    document.addEventListener("visibilitychange", function () { if (running) step(); });
  }

  /* ---------- Sticky "on this page" nav highlights the section in view ---------- */
  var subLinks = $$(".subnav a[href^='#']");
  if (subLinks.length && "IntersectionObserver" in window) {
    var byId = {};
    subLinks.forEach(function (a) { byId[a.getAttribute("href").slice(1)] = a; });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (!e.isIntersecting) return;
        subLinks.forEach(function (a) { a.classList.remove("is-on"); });
        var a = byId[e.target.id];
        if (a) {
          a.classList.add("is-on");
          var row = a.parentNode;
          row.scrollTo({ left: a.offsetLeft - row.clientWidth / 2 + a.clientWidth / 2, behavior: reduceMotion ? "auto" : "smooth" });
        }
      });
    }, { rootMargin: "-45% 0px -50% 0px" });
    Object.keys(byId).forEach(function (id) { var s = document.getElementById(id); if (s) io.observe(s); });
  }

  /* ---------- Mobile menu: close after choosing a link, or on Escape ---------- */
  var menu = $(".menu");
  if (menu) {
    menu.addEventListener("click", function (ev) { if (ev.target.closest("a")) menu.removeAttribute("open"); });
    document.addEventListener("keydown", function (ev) {
      if (ev.key === "Escape" && menu.open) { menu.removeAttribute("open"); $("summary", menu).focus(); }
    });
  }

  /* ---------- Confirm before destructive circle actions ---------- */
  $$("form[data-confirm]").forEach(function (f) {
    f.addEventListener("submit", function (ev) { if (!window.confirm(f.getAttribute("data-confirm"))) ev.preventDefault(); });
  });

  /* ---------- 1-minute calm: breathe in 4s, out 6s, six times ---------- */
  var calm = $("[data-calm]");
  if (calm) {
    var c = {
      ring: $('[data-c="ring"]', calm), ball: $('[data-c="ball"]', calm), cue: $('[data-c="cue"]', calm),
      left: $('[data-c="left"]', calm), start: $('[data-c="start"]', calm), done: $('[data-c="done"]', calm),
      today: $('[data-c="today"]', calm), again: $('[data-c="again"]', calm)
    };
    var RING = 628.3, LEN = 60, running = false, t0 = 0, raf = null, lastPhase = "";
    var csrf = $("input[name=csrfmiddlewaretoken]", calm).value;
    var phaseAt = function (s) { return (s % 10) < 4 ? "in" : "out"; };
    var frame = function () {
      var s = Math.min((Date.now() - t0) / 1000, LEN);
      c.ring.style.strokeDashoffset = (RING * (1 - s / LEN)).toFixed(1);
      c.left.textContent = Math.ceil(LEN - s) + " seconds";
      var ph = phaseAt(Math.floor(s));
      if (ph !== lastPhase) {
        lastPhase = ph;
        c.cue.textContent = ph === "in" ? "Breathe in…" : "Breathe out, slowly…";
        c.ball.classList.toggle("is-in", ph === "in");
        c.ball.classList.toggle("is-out", ph === "out");
      }
      if (s >= LEN) return finish();
      raf = requestAnimationFrame(frame);
    };
    var finish = function () {
      running = false;
      c.cue.textContent = "Done. Notice how you feel.";
      c.ball.classList.remove("is-in"); c.ball.classList.add("is-out");
      c.start.hidden = true;
      if (navigator.vibrate) navigator.vibrate(120);
      fetch(calm.getAttribute("data-done-url"), {
        method: "POST", credentials: "same-origin",
        headers: { "X-CSRFToken": csrf, "X-Requested-With": "XMLHttpRequest" }
      }).then(function (r) { return r.json(); }).then(function (d) {
        if (d && typeof d.today === "number") c.today.textContent = d.today;
      }).catch(function () {}).then(function () { c.done.hidden = false; });
    };
    var begin = function () {
      running = true; lastPhase = ""; t0 = Date.now();
      c.done.hidden = true; c.start.hidden = true;
      raf = requestAnimationFrame(frame);
    };
    c.ring.style.transition = "none";
    c.start.addEventListener("click", function () { if (!running) begin(); });
    c.again.addEventListener("click", function () { begin(); });
  }

  /* ---------- Burst stepper on the log page ---------- */
  $$("[data-stepper]").forEach(function (box) {
    var input = $("input", box);
    $$("[data-step]", box).forEach(function (b) {
      b.addEventListener("click", function () {
        var v = parseInt(input.value || "0", 10) + parseInt(b.getAttribute("data-step"), 10);
        input.value = Math.max(0, Math.min(20, isNaN(v) ? 0 : v));
      });
    });
  });

  /* ---------- Hide the mobile dock while the join section is on screen ---------- */
  var dock = $("#dock"), joinBand = $("#join");
  if (dock && joinBand && "IntersectionObserver" in window) {
    new IntersectionObserver(function (entries) {
      dock.classList.toggle("hide", entries[0].isIntersecting);
    }, { threshold: 0.1 }).observe(joinBand);
  }
})();
