const $ = s => document.querySelector(s);
const el = (t, cls, txt) => { const e = document.createElement(t); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; };
const side = g => g === true ? "good" : (g === false ? "bad" : "plain");
const sideWord = g => g === true ? "Good" : (g === false ? "Bad" : "Neutral");

/* Colored list of [name, good] pairs. */
function names(list) {
  const d = el("span");
  if (!list.length) { d.textContent = "None"; return d; }
  list.forEach(([n, g], i) => {
    d.appendChild(el("span", g === true ? "g" : (g === false ? "b" : "n"), n));
    if (i < list.length - 1) d.appendChild(document.createTextNode(", "));
  });
  return d;
}
function fxList(fx) {
  const ul = el("ul", "fx");
  fx.forEach(([s, up]) => ul.appendChild(el("li", up === true ? "up" : (up === false ? "down" : null), s)));
  return ul;
}
function metaList() {
  const dl = el("dl", "meta");
  dl.add = (k, v) => {
    dl.appendChild(el("dt", null, k));
    const d = el("dd");
    if (typeof v === "string") d.textContent = v; else d.appendChild(v);
    dl.appendChild(d);
  };
  return dl;
}

/* ---- review state: one doc per item in "notes": {text, verdict: "approve" | "reject" | null} ---- */
const state = {};
const cardIds = [];
function reviewBox(id, name, labels) {
  labels = labels || {};
  state[id] = state[id] || { text: "", verdict: null };
  cardIds.push(id);
  const box = el("div", "review");
  const row = el("div", "verdict");
  row.setAttribute("role", "group");
  row.setAttribute("aria-label", "Verdict on " + name);
  const ok = el("button", "vbtn ok", labels.ok || "Approve"); ok.type = "button"; ok.dataset.v = "approve"; ok.dataset.id = id;
  const no = el("button", "vbtn no", labels.no || "Reject"); no.type = "button"; no.dataset.v = "reject"; no.dataset.id = id;
  ok.setAttribute("aria-pressed", "false"); no.setAttribute("aria-pressed", "false");
  const st = el("div", "status"); st.id = "status-" + id;
  row.append(ok, no, st);
  const ta = el("textarea"); ta.id = "note-" + id; ta.dataset.id = id;
  ta.placeholder = labels.note || ("Notes on " + name + " (what to change, ideas)");
  ta.setAttribute("aria-label", "Notes on " + name);
  box.append(row, ta);
  return box;
}
function card(item, kind, name) {
  const c = el("article", "card " + side(item.good));
  c.dataset.id = item.id; c.dataset.kind = kind; c.dataset.side = side(item.good);
  const h = el("div", "head");
  h.appendChild(el("h3", null, name || item.name));
  const pill = el("span", "vpill"); pill.hidden = true; pill.id = "pill-" + item.id;
  c.head = h; c.pill = pill;
  c.appendChild(h);
  return c;
}
function finishCard(c, item, name) {
  c.head.appendChild(c.pill);
  c.appendChild(reviewBox(item.id, name || item.name));
}
function showVerdict(id) {
  const v = (state[id] || {}).verdict;
  document.querySelectorAll('.vbtn[data-id="' + id + '"]').forEach(b => b.setAttribute("aria-pressed", String(b.dataset.v === v)));
  const p = document.getElementById("pill-" + id);
  if (p) {
    p.hidden = !v;
    p.className = "vpill " + (v === "approve" ? "ok" : "no");
    p.textContent = v === "approve" ? (p.dataset.ok || "Approved") : (p.dataset.no || "Rejected");
  }
  const c = document.querySelector('.card[data-id="' + id + '"]');
  if (c) c.dataset.verdict = v || "";
}

/* ---- filters and the progress line ---- */
let kindFilter = "all", reviewFilter = "any";
function applyFilter() {
  document.querySelectorAll(".card").forEach(c => {
    const s = state[c.dataset.id] || {};
    const k = kindFilter === "all" || kindFilter === c.dataset.kind || kindFilter === c.dataset.side;
    const r = reviewFilter === "any" || (reviewFilter === "todo" && !s.verdict) || reviewFilter === s.verdict
      || (reviewFilter === "noted" && (s.text || "").trim().length > 0);
    c.hidden = !(k && r);
  });
  document.querySelectorAll(".group").forEach(g => {
    const any = [...g.querySelectorAll(".card")].some(c => !c.hidden);
    g.hidden = !any;
  });
  const none = ![...document.querySelectorAll(".card")].some(c => !c.hidden);
  $("#empty").hidden = !none;
  let a = 0, r = 0, n = 0;
  cardIds.forEach(id => {
    const s = state[id];
    if (s.verdict === "approve") a++; else if (s.verdict === "reject") r++;
    if ((s.text || "").trim()) n++;
  });
  const total = cardIds.length;
  $("#c-ok").textContent = a; $("#c-no").textContent = r; $("#c-todo").textContent = total - a - r; $("#c-notes").textContent = n;
  $("#m-ok").style.width = (100 * a / total) + "%"; $("#m-no").style.width = (100 * r / total) + "%";
}
function chips(groupSel, set) {
  document.querySelectorAll(groupSel + " .chip").forEach(b => b.addEventListener("click", () => {
    set(b.dataset.f);
    document.querySelectorAll(groupSel + " .chip").forEach(x => x.setAttribute("aria-pressed", String(x === b)));
    applyFilter();
  }));
}

function status(id, text, err) {
  const s = document.getElementById("status-" + id);
  if (s) { s.textContent = text; s.classList.toggle("err", !!err); }
}

/* ---- saving ---- */
async function startStore() {
  chips("#f-kind", v => kindFilter = v);
  chips("#f-review", v => reviewFilter = v);
  applyFilter();
  const banner = $("#banner");
  const areas = document.querySelectorAll("textarea[data-id]");
  const buttons = document.querySelectorAll(".vbtn");
  let db = null;
  try { db = await window.claude?.use?.("db"); } catch (_) { db = null; }
  if (!db) {
    banner.hidden = false;
    banner.textContent = "Verdicts and notes can't be saved in this view. Open the page signed in to claude.ai.";
    areas.forEach(a => a.disabled = true);
    buttons.forEach(b => b.disabled = true);
    return;
  }
  const col = db.collection("notes");
  const timers = {}, saving = {}, dirty = {}, saved = {};
  const loadTimer = setTimeout(() => {
    banner.hidden = false;
    banner.textContent = "Saved verdicts and notes can't be reached yet. What you do saves once the connection is back.";
  }, 10000);
  col.onSnapshot(snap => {
    if (!snap.metadata.fromCache) { clearTimeout(loadTimer); banner.hidden = true; }
    snap.docs.forEach(d => {
      const data = d.data() || {};
      const id = d.id;
      const s = state[id] = state[id] || { text: "", verdict: null };
      saved[id] = { text: data.text || "", verdict: data.verdict || null };
      if (dirty[id] || saving[id]) return;
      s.verdict = saved[id].verdict;
      const a = document.getElementById("note-" + id);
      if (a && document.activeElement !== a && a.value !== saved[id].text) a.value = saved[id].text;
      if (!a || document.activeElement !== a) s.text = saved[id].text;
      showVerdict(id);
    });
    applyFilter();
  }, err => {
    clearTimeout(loadTimer);
    banner.hidden = false;
    banner.textContent = "Saved verdicts and notes could not be loaded (" + err.code + ").";
  });

  async function save(id) {
    if (saving[id]) { dirty[id] = true; return; }
    const a = document.getElementById("note-" + id);
    const s = state[id];
    s.text = a.value;
    saving[id] = true; dirty[id] = false; status(id, "Saving...");
    const doc = { text: s.text, verdict: s.verdict || null, updatedAt: new Date().toISOString() };
    try {
      await col.doc(id).set(doc);
      saved[id] = { text: doc.text, verdict: doc.verdict };
      status(id, "Saved");
    } catch (e) {
      status(id, e && (e.code === "not_granted" || e.code === "permission_denied") ? "You can read this page but not change it." : "Couldn't save. Try again.", true);
    } finally {
      saving[id] = false;
      const cur = saved[id] || {};
      if (dirty[id] && (a.value !== cur.text || (s.verdict || null) !== cur.verdict)) save(id); else dirty[id] = false;
      applyFilter();
    }
  }
  areas.forEach(a => {
    const id = a.dataset.id;
    state[id] = state[id] || { text: "", verdict: null };
    a.addEventListener("input", () => { dirty[id] = true; state[id].text = a.value; status(id, "Typing..."); clearTimeout(timers[id]); timers[id] = setTimeout(() => save(id), 900); });
    a.addEventListener("blur", () => { if (dirty[id]) { clearTimeout(timers[id]); save(id); } });
  });
  buttons.forEach(b => b.addEventListener("click", () => {
    const id = b.dataset.id, s = state[id];
    s.verdict = s.verdict === b.dataset.v ? null : b.dataset.v;
    showVerdict(id);
    applyFilter();
    clearTimeout(timers[id]);
    dirty[id] = true;
    save(id);
  }));
}
