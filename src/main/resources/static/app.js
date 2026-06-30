"use strict";

const $ = (sel) => document.querySelector(sel);

function cookie(name) {
  const m = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
  return m ? decodeURIComponent(m[1]) : null;
}

async function api(method, path, body) {
  const opts = { method, headers: { "Content-Type": "application/json" } };
  const csrf = cookie("XSRF-TOKEN");
  if (csrf) opts.headers["X-XSRF-TOKEN"] = csrf;
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(path, opts);
  let data = null;
  const ct = res.headers.get("content-type") || "";
  if (ct.includes("application/json")) data = await res.json().catch(() => null);
  else if (res.status !== 204) data = await res.text().catch(() => null);
  return { status: res.status, ok: res.ok, data };
}

function logEntry(lines, fail) {
  const log = $("#log");
  const div = document.createElement("div");
  div.className = "entry" + (fail ? " fail" : "");
  div.textContent = Array.isArray(lines) ? lines.join("\n") : String(lines);
  log.appendChild(div);
  log.scrollTop = log.scrollHeight;
}

function logYou(text) {
  const log = $("#log");
  const div = document.createElement("div");
  div.className = "entry you";
  div.textContent = "> " + text;
  log.appendChild(div);
}

function renderState(s) {
  if (!s) return;
  const parts = [];
  if (s.hasCharacter) {
    parts.push(`<span class="k">PC:</span> ${s.characterName} (L${s.level} ${s.characterClass})`);
    parts.push(`<span class="k">HP:</span> ${s.currentHp}/${s.maxHp}`);
    parts.push(`<span class="k">Where:</span> ${s.inDungeon ? "dungeon L" + s.dungeonLevel + ", turn " + s.dungeonTurn : "town"}`);
    parts.push(`<span class="k">State:</span> ${s.sessionState}${s.inCombat ? " ⚔️" : ""}`);
    if (s.retainers) parts.push(`<span class="k">Retainers:</span> ${s.retainers}`);
  } else {
    parts.push("<em>No character yet — roll or pregen one.</em>");
  }
  $("#state").innerHTML = parts.join("<br>");
}

// Apply an ActionResult: log its lines and refresh state, then the character panel.
function applyResult(r) {
  if (r.data && r.data.lines) logEntry(r.data.lines, !r.data.ok);
  else if (r.status === 403) logEntry("Not permitted on this instance.", true);
  else if (r.status === 429) logEntry("Slow down — rate limited.", true);
  else if (!r.ok) logEntry("Request failed (" + r.status + ").", true);
  if (r.data && r.data.state) { renderState(r.data.state); loadCharacter(); }
}

async function loadCharacter() {
  const r = await api("GET", "/api/character");
  const el = $("#character");
  if (r.status === 204 || !r.data) { el.innerHTML = "<em>None yet.</em>"; return; }
  const c = r.data;
  el.innerHTML = `<pre>${c.name} — L${c.level} ${c["class"]}\n`
    + `HP ${c.hitPoints}  AC ${c.armorClass} (asc ${c.armorClassAscending})  THAC0 ${c.thac0}\n`
    + `Gold ${c.gold}  Weapon ${c.weapon} (${c.weaponDamage})</pre>`;
}

async function loadModules() {
  const r = await api("GET", "/api/modules");
  if (!r.ok || !Array.isArray(r.data)) return;
  const sel = $("#module-select");
  for (const m of r.data) {
    const o = document.createElement("option");
    o.value = m; o.textContent = m;
    sel.appendChild(o);
  }
}

function download(name, text, type) {
  const blob = new Blob([text], { type: type || "application/json" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = name;
  a.click();
  URL.revokeObjectURL(a.href);
}

// --- wiring ---------------------------------------------------------------

function wire() {
  document.querySelectorAll("[data-move]").forEach((b) =>
    b.addEventListener("click", async () => {
      logYou("move " + b.dataset.move);
      applyResult(await api("POST", "/api/delve/move", { direction: b.dataset.move }));
    }));

  const simple = { look: ["GET", "/api/delve/look"], search: ["POST", "/api/delve/search"],
    attack: ["POST", "/api/delve/attack"], flee: ["POST", "/api/delve/flee"],
    quaff: ["POST", "/api/delve/quaff"], town: ["POST", "/api/delve/town"], party: ["GET", "/api/delve/party"] };
  document.querySelectorAll("[data-action]").forEach((b) =>
    b.addEventListener("click", async () => {
      const [m, p] = simple[b.dataset.action];
      logYou(b.dataset.action);
      applyResult(await api(m, p, m === "POST" ? {} : undefined));
    }));

  $("#cmd-send").addEventListener("click", sendCommand);
  $("#cmd").addEventListener("keydown", (e) => { if (e.key === "Enter") sendCommand(); });

  $("#roll-btn").addEventListener("click", async () => {
    applyResult(await api("POST", "/api/character/roll", { cls: $("#roll-class").value, name: $("#roll-name").value }));
  });
  $("#pregen-btn").addEventListener("click", async () => {
    applyResult(await api("POST", "/api/character/pregen",
      { cls: $("#roll-class").value, level: Number($("#pregen-level").value), name: $("#roll-name").value }));
  });
  $("#enter-btn").addEventListener("click", async () => {
    const module = $("#module-select").value;
    logYou("enter " + (module || "(procedural)"));
    applyResult(await api("POST", "/api/delve/enter", { module: module || null }));
  });

  $("#export-text").addEventListener("click", async () => {
    const r = await api("GET", "/api/dm/export?format=text");
    if (r.data) download("character.txt", r.data, "text/plain");
  });
  $("#export-json").addEventListener("click", async () => {
    const r = await api("GET", "/api/dm/export?format=json");
    if (r.data) download("character.json", JSON.stringify(r.data, null, 2));
  });
  $("#npc-btn").addEventListener("click", async () => {
    const r = await api("POST", "/api/dm/npc",
      { cls: $("#npc-class").value, level: Number($("#npc-level").value), name: $("#npc-name").value });
    if (r.ok && r.data) { download("npc.json", JSON.stringify(r.data, null, 2)); logEntry("Generated NPC " + (r.data.name || "")); }
    else logEntry("Could not generate NPC (check class).", true);
  });
  $("#roster-btn").addEventListener("click", async () => {
    const r = await api("POST", "/api/dm/roster",
      { count: Number($("#roster-count").value), level: Number($("#roster-level").value), cls: null });
    if (r.ok && r.data) { download("roster.json", JSON.stringify(r.data, null, 2)); logEntry("Generated " + r.data.length + " pregens."); }
  });
  $("#logout").addEventListener("click", () => { document.location = "/logout"; });
}

async function sendCommand() {
  const raw = $("#cmd").value.trim();
  if (!raw) return;
  $("#cmd").value = "";
  logYou(raw);
  const [verb, ...rest] = raw.split(/\s+/);
  const arg = rest.join(" ");
  const v = verb.toLowerCase();
  let r;
  switch (v) {
    case "open": r = await api("POST", "/api/delve/open", { direction: arg }); break;
    case "move": case "go": r = await api("POST", "/api/delve/move", { direction: arg }); break;
    case "prepare": r = await api("POST", "/api/delve/prepare", { spell: arg }); break;
    case "hire": { const [c, ...n] = rest; r = await api("POST", "/api/delve/hire", { cls: c, name: n.join(" ") }); break; }
    case "dismiss": r = await api("POST", "/api/delve/dismiss", { name: arg }); break;
    case "cast": {
      // last token may be a target number
      let target = null, spell = arg;
      const last = rest[rest.length - 1];
      if (rest.length > 1 && /^\d+$/.test(last)) { target = Number(last); spell = rest.slice(0, -1).join(" "); }
      r = await api("POST", "/api/delve/cast", { spell, target }); break;
    }
    case "attack": r = await api("POST", "/api/delve/attack", { target: arg ? Number(arg) : null }); break;
    default: logEntry("Unknown command. Try: open <dir>, cast <spell> [n], prepare <spell>, hire <class> <name>, dismiss <name>.", true); return;
  }
  applyResult(r);
}

async function init() {
  const r = await api("GET", "/api/state");
  if (r.status === 401 || r.status === 403 || r.status === 302 || !r.ok) {
    $("#login").classList.remove("hidden");
    return;
  }
  $("#app").classList.remove("hidden");
  $("#who").textContent = "playing as Discord id " + (r.data.characterName || "you");
  wire();
  renderState(r.data);
  loadCharacter();
  loadModules();
  logEntry("Welcome to delve. Roll a character (or pregen one), then Enter a dungeon.");
}

init();
