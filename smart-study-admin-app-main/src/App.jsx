import React, { useState, useEffect, useCallback, useRef, useMemo } from "react";

/* ══════════ CONFIG ══════════ */
const FB      = (import.meta.env.VITE_FB_DATABASE_URL||"").replace(/\/+$/,"");
const FB_KEY  = import.meta.env.VITE_FB_API_KEY||"";
const FB_PROJ  = import.meta.env.VITE_FB_PROJECT_ID||"";
const GAS     = import.meta.env.VITE_GAS_URL;   // শুধু GAS standalone backup এ লাগবে — app আর call করে না
const IMGBB   = import.meta.env.VITE_IMGBB_API_KEY;
const SECRET  = import.meta.env.VITE_SECRET_KEY;    // GAS আর call নেই — legacy only
// FCM v1 — Service Account credentials (GitHub Secrets থেকে build time এ inject হয়)
const FCM_CLIENT_EMAIL = import.meta.env.VITE_FCM_CLIENT_EMAIL||"";
const FCM_PRIVATE_KEY = (() => {
  try {
    // Vite build এ VITE_FCM_PRIVATE_KEY string হিসেবে inject হয়
    // GitHub Secret এ \n (দুই char) থাকে — actual newline চাই
    const raw = import.meta.env.VITE_FCM_PRIVATE_KEY || "";
    return raw.split("\\n").join("\n");
  } catch(_) { return ""; }
})()

const C={bg:"#06080f",card:"#0c1220",border:"#16253d",accent:"#3b82f6",green:"#22c55e",red:"#ef4444",yellow:"#f59e0b",purple:"#8b5cf6",text:"#e2e8f0",muted:"#4b5e7a",panel:"#0e1a2e",navBg:"#080f1c"};

/* ══════════════════════════════════════════════════════════════
   🔥 ADMIN APP LOGCAT — Firebase Realtime DB Logger
   সব log, error, warn, API call, crash Firebase-এ জমা হবে
   Path: AdminAppLogcat/{sessionId}/{pushId}
   ══════════════════════════════════════════════════════════════ */
const _LC = (() => {
  // Bangladesh time (UTC+6)
  const _bdNow = () => {
    const now = new Date();
    const bd = new Date(now.getTime() + 6*60*60*1000);
    const pad = n => String(n).padStart(2,"0");
    return {
      date:   `${bd.getUTCFullYear()}-${pad(bd.getUTCMonth()+1)}-${pad(bd.getUTCDate())}`,
      time:   `${pad(bd.getUTCHours())}-${pad(bd.getUTCMinutes())}-${pad(bd.getUTCSeconds())}`,
      full:   `${bd.getUTCFullYear()}-${pad(bd.getUTCMonth()+1)}-${pad(bd.getUTCDate())} ${pad(bd.getUTCHours())}:${pad(bd.getUTCMinutes())}:${pad(bd.getUTCSeconds())}`,
    };
  };
  const _startTime = _bdNow();
  const _rand = Math.random().toString(36).slice(2,6);
  // Session ID: "2025-06-15 14:23:05 [abc1]" — Firebase এ এটাই key হবে, সরাসরি পড়া যাবে
  const _sessionId = `${_startTime.date} ${_startTime.time.replace(/-/g,":")} [${_rand}]`;
  // Date folder: 2025-06-15
  const _dateFolder = _startTime.date;

  const _device = (() => {
    try {
      const ua = navigator.userAgent;
      const isAndroid = /Android/.test(ua);
      const isIOS = /iPhone|iPad/.test(ua);
      const androidVer = isAndroid ? (ua.match(/Android ([\d.]+)/)||[])[1] : null;
      const model = isAndroid ? (ua.match(/;\s*([^;)]+)\sBuild/)||[])[1]?.trim() : null;
      return {
        platform: isAndroid ? "Android" : isIOS ? "iOS" : "Web",
        androidVersion: androidVer || null,
        model: model || null,
        userAgent: ua.slice(0, 120),
        language: navigator.language || null,
        online: navigator.onLine,
        screen: `${window.screen?.width||0}x${window.screen?.height||0}`,
      };
    } catch(e) { return { platform: "Unknown" }; }
  })();

  const _queue = [];
  let _flushing = false;
  let _logCount = 0;
  const MAX_QUEUE = 200;
  const MAX_LOGS_PER_SESSION = 2000;

  async function _pushToFirebase(entry) {
    if (!FB) return;
    try {
      let authQ = "";
      try { if (window.__adminIdToken) authQ = `?auth=${window.__adminIdToken}`; } catch(e){}
      // Path: AdminAppLogcat/{date}/{session_title}/logs
      // Firebase এ: AdminAppLogcat > 2025-06-15 > "2025-06-15 14:23:05 [abc1]" > log entries
      const url = `${FB}/AdminAppLogcat/${_dateFolder}/${encodeURIComponent(_sessionId)}/logs.json${authQ}`;
      await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(entry),
      });
    } catch(e) { /* Firebase write fail — silent */ }
  }

  async function _flush() {
    if (_flushing || _queue.length === 0) return;
    _flushing = true;
    while (_queue.length > 0) {
      const entry = _queue.shift();
      await _pushToFirebase(entry);
    }
    _flushing = false;
  }

  function _send(level, tag, message, extra) {
    if (_logCount >= MAX_LOGS_PER_SESSION) return;
    _logCount++;
    const now = new Date();
    const pad = n => String(n).padStart(2,"0");
    const bdTime = new Date(now.getTime() + 6*60*60*1000); // UTC+6 Bangladesh
    const tsLocal = `${bdTime.getUTCFullYear()}-${pad(bdTime.getUTCMonth()+1)}-${pad(bdTime.getUTCDate())} ${pad(bdTime.getUTCHours())}:${pad(bdTime.getUTCMinutes())}:${pad(bdTime.getUTCSeconds())}`;
    const entry = {
      ts: tsLocal,           // "2025-06-15 14:23:05" — readable Bangladesh time
      tsMs: now.getTime(),   // sort এর জন্য
      session: _sessionId,
      level,
      tag,
      message: String(message).slice(0, 800),
      ...(extra && Object.keys(extra).length > 0 ? { extra } : {}),
    };
    if (_queue.length < MAX_QUEUE) _queue.push(entry);
    setTimeout(_flush, 0);
  }

  const _origLog   = console.log.bind(console);
  const _origWarn  = console.warn.bind(console);
  const _origError = console.error.bind(console);

  function _serialize(args) {
    return args.map(a => {
      if (a === null) return "null";
      if (a === undefined) return "undefined";
      if (typeof a === "string") return a;
      if (a instanceof Error) return `${a.name}: ${a.message}`;
      try { return JSON.stringify(a); } catch(e) { return String(a); }
    }).join(" ").slice(0, 800);
  }

  console.log = (...args) => { _origLog(...args); _send("LOG", "console", _serialize(args)); };
  console.warn = (...args) => { _origWarn(...args); _send("WARN", "console", _serialize(args)); };
  console.error = (...args) => { _origError(...args); _send("ERROR", "console", _serialize(args)); };
  console.info = (...args) => { _origLog(...args); _send("INFO", "console", _serialize(args)); };

  window.addEventListener("error", (e) => {
    _send("CRASH", "uncaughtError", `${e.message} @ ${e.filename}:${e.lineno}:${e.colno}`, {
      stack: (e.error?.stack||"").slice(0,500),
    });
  });
  window.addEventListener("unhandledrejection", (e) => {
    const msg = e.reason instanceof Error
      ? `${e.reason.name}: ${e.reason.message}`
      : String(e.reason||"UnhandledRejection");
    _send("CRASH", "unhandledRejection", msg, { stack: (e.reason?.stack||"").slice(0,400) });
  });

  window.addEventListener("online",  () => _send("INFO", "network", "Device came ONLINE"));
  window.addEventListener("offline", () => _send("WARN", "network", "Device went OFFLINE"));

  document.addEventListener("visibilitychange", () => {
    _send("LIFECYCLE", "visibility", document.hidden ? "App went to BACKGROUND" : "App came to FOREGROUND");
  });

  _send("LIFECYCLE", "appStart", "Admin App started", {
    device: _device,
    fbUrl: FB ? FB.replace(/https?:\/\//, "").slice(0,40) : "NOT_SET",
    fbProject: FB_PROJ || "NOT_SET",
    fcmReady: !!(typeof FCM_CLIENT_EMAIL !== "undefined" && FCM_CLIENT_EMAIL),
    appVersion: "1.0",
  });

  return {
    log:       (tag, msg, extra) => _send("LOG",       tag, msg, extra),
    warn:      (tag, msg, extra) => _send("WARN",      tag, msg, extra),
    error:     (tag, msg, extra) => _send("ERROR",     tag, msg, extra),
    info:      (tag, msg, extra) => _send("INFO",      tag, msg, extra),
    auth:      (tag, msg, extra) => _send("AUTH",      tag, msg, extra),
    api:       (tag, msg, extra) => _send("API",       tag, msg, extra),
    lifecycle: (tag, msg, extra) => _send("LIFECYCLE", tag, msg, extra),
    crash:     (tag, msg, extra) => _send("CRASH",     tag, msg, extra),
    sessionId: _sessionId,
    device:    _device,
  };
})();
/* ══════════ END ADMIN APP LOGCAT ══════════ */



/* ══════════ ERROR BOUNDARY ══════════ */
class ErrorBoundary extends React.Component {
  constructor(p){super(p);this.state={err:null};}
  static getDerivedStateFromError(e){return{err:e};}
  componentDidCatch(e,info){console.error("App error:",e,info);_LC.crash("ErrorBoundary",`${e?.name||"Error"}: ${e?.message||"unknown"}`,{stack:(e?.stack||"").slice(0,400),componentStack:(info?.componentStack||"").slice(0,300)});}
  render(){
    if(this.state.err)return(
      <div style={{padding:32,color:"#ef4444",fontFamily:"monospace",background:"#06080f",minHeight:"100dvh"}}>
        <div style={{fontSize:28,marginBottom:12}}>⚠️ Error</div>
        <div style={{fontSize:12,marginBottom:8,color:"#e2e8f0"}}>{this.state.err?.message||"Unknown error"}</div>
        <button onClick={()=>this.setState({err:null})} style={{marginTop:16,padding:"8px 20px",background:"#3b82f6",color:"#fff",border:"none",borderRadius:8,cursor:"pointer"}}>রিলোড করুন</button>
      </div>
    );
    return this.props.children;
  }
}

/* ══════════ FIREBASE AUTH (email/password via REST) ══════════ */
let _idToken = null;
let _tokenExp = 0;

async function signInWithEmail(email, password) {
  _LC.auth("signIn", `Login attempt: ${email}`);
  let r, d;
  try {
    r = await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FB_KEY}`,
      {method:"POST",headers:{"Content-Type":"application/json"},
       body:JSON.stringify({email,password,returnSecureToken:true})}
    );
    d = await r.json();
  } catch(netErr) {
    _LC.error("signIn", `Network error during login: ${netErr.message}`, { email });
    throw netErr;
  }
  if(!r.ok) {
    const errMsg = d?.error?.message||"Login failed";
    _LC.error("signIn", `Login FAILED for ${email}: ${errMsg}`, { httpStatus: r.status, firebaseError: d?.error });
    throw new Error(errMsg);
  }
  _idToken = d.idToken;
  _tokenExp = Date.now() + (parseInt(d.expiresIn||3600)-60)*1000;
  window.__adminIdToken = _idToken; // expose for _LC flush
  _LC.auth("signIn", `Login SUCCESS: ${email}`, { uid: d.localId, expiresIn: d.expiresIn });
  // store refresh token so we can get new idToken without password
  localStorage.setItem("fb_refresh_token", d.refreshToken||"");
  localStorage.setItem("fb_email", email);
  try{ localStorage.setItem("fb_pass_enc", btoa(unescape(encodeURIComponent(password)))); }catch(_){}
  return d;
}

async function refreshTokenWithRefreshToken(refreshToken) {
  try {
    const r = await fetch(
      `https://securetoken.googleapis.com/v1/token?key=${FB_KEY}`,
      {method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded"},
       body:`grant_type=refresh_token&refresh_token=${encodeURIComponent(refreshToken)}`}
    );
    const d = await r.json();
    if(!r.ok || !d.id_token){
      _LC.warn("tokenRefresh", `Refresh token failed: HTTP ${r.status}`, { error: d?.error });
      return null;
    }
    _idToken = d.id_token;
    window.__adminIdToken = _idToken;
    _tokenExp = Date.now() + (parseInt(d.expires_in||3600)-60)*1000;
    localStorage.setItem("fb_refresh_token", d.refresh_token||refreshToken);
    _LC.auth("tokenRefresh", "Token refreshed successfully via refresh_token");
    return _idToken;
  } catch(e){
    _LC.error("tokenRefresh", `Token refresh network error: ${e.message}`);
    return null;
  }
}

async function refreshTokenIfNeeded() {
  if(_idToken && Date.now() < _tokenExp) return _idToken;
  
  // Try refresh token first (no password needed)
  const refreshToken = localStorage.getItem("fb_refresh_token");
  if(refreshToken){
    const t = await refreshTokenWithRefreshToken(refreshToken);
    if(t) return t;
  }
  
  // Fallback: re-login with saved credentials
  const email = localStorage.getItem("fb_email");
  const passEnc = localStorage.getItem("fb_pass_enc");
  if(email && passEnc){
    try{
      _LC.auth("tokenRefresh", `Falling back to re-login for: ${email}`);
      const pass = decodeURIComponent(escape(atob(passEnc)));
      await signInWithEmail(email, pass);
      return _idToken;
    }catch(e){
      _LC.error("tokenRefresh", `Fallback re-login FAILED: ${e.message}`);
      _idToken=null; return null;
    }
  }
  _LC.warn("tokenRefresh", "No credentials available — user must login manually");
  return null;
}

function _authQ(token){ return token ? `?auth=${token}` : ""; }

/* ══════════ FIREBASE REST ══════════ */
async function _checkResp(r){
  const txt = await r.text();
  if(!r.ok){
    let msg=`HTTP ${r.status}`;
    try{
      const j=JSON.parse(txt);
      if(j?.error){
        msg = typeof j.error==="string" ? j.error : (j.error?.message||JSON.stringify(j.error));
      }
    }catch(_){}
    console.error("Firebase write error:",r.status, msg, r.url);
    _LC.error("firebaseWrite", `Firebase write error: ${msg}`, { status: r.status, url: (r.url||"").split("?")[0].slice(-60) });
    throw new Error(msg);
  }
  try{ return JSON.parse(txt); }catch(_){ return txt; }
}
const _tok=()=>refreshTokenIfNeeded();
const fbGet   = async p=>{
  const t=await _tok();
  try {
    const r=await fetch(`${FB}/${p}.json${_authQ(t)}`);
    const data = await r.json();
    if(data?.error) _LC.error("fbGet", `fbGet error at ${p}: ${data.error}`, { path: p });
    return data;
  } catch(e) {
    _LC.error("fbGet", `fbGet network fail: ${e.message}`, { path: p });
    throw e;
  }
};
const fbPatch  = async(p,d)=>{
  const t=await _tok();
  if(!t){ _LC.error("fbPatch","Not authenticated — token missing",{path:p}); throw new Error("Not authenticated — please re-login"); }
  if(!p||p.includes("/undefined")||p.includes("/null")){ _LC.error("fbPatch","Invalid path",{path:p}); throw new Error("Invalid path: "+p); }
  const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify(d)});
  return _checkResp(r);
};
const fbSet   = async(p,d)=>{
  const t=await _tok();
  if(!t){ _LC.error("fbSet","Not authenticated — token missing",{path:p}); throw new Error("Not authenticated — please re-login"); }
  const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(d)});
  return _checkResp(r);
};
const fbPush  = async(p,d)=>{const t=await _tok();const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(d)});return _checkResp(r);};
const fbDelete= async p=>{
  const t=await _tok();
  if(!t){ _LC.error("fbDelete","Not authenticated — token missing",{path:p}); throw new Error("Not authenticated — please re-login"); }
  if(!p||p.includes("/undefined")||p.includes("/null")){ _LC.error("fbDelete","Invalid path",{path:p}); throw new Error("Invalid path: "+p); }
  const r=await fetch(`${FB}/${p}.json${_authQ(t)}`,{method:"DELETE"});
  return _checkResp(r);
};

/* ══ fbDeleteBatch — Firebase multi-path DELETE (single REST call) ══
   Firebase PATCH with {key: null} = atomic multi-delete.
   Root path = sheet (e.g. "QBank"), keys = _fbKey array.
   Much faster than serial fbDelete per item.
   Firebase limits: ~1000 keys per call, ~10MB body — we chunk at 500.
   ══════════════════════════════════════════════════════════════════ */
const BATCH_SZ = 500;
async function fbDeleteBatch(sheet, fbKeys, onProgress) {
  if (!fbKeys || fbKeys.length === 0) return 0;
  const t = await _tok();
  if (!t) throw new Error("Not authenticated — please re-login");
  let deleted = 0;
  for (let i = 0; i < fbKeys.length; i += BATCH_SZ) {
    const chunk = fbKeys.slice(i, i + BATCH_SZ);
    const body = {};
    chunk.forEach(k => { body[k] = null; }); // null = delete in Firebase
    const r = await fetch(`${FB}/${sheet}.json${_authQ(t)}`, {
      method: "PATCH",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(body),
    });
    await _checkResp(r);
    deleted += chunk.length;
    if (onProgress) onProgress(deleted, fbKeys.length);
    _LC.log("fbDeleteBatch", `Batch ${Math.ceil((i+1)/BATCH_SZ)}: deleted ${deleted}/${fbKeys.length} from ${sheet}`);
  }
  return deleted;
}

/* ══ fbPatchBatch — parallel PATCH with concurrency limit ══
   For rename: patch N items, CONCURRENCY at a time.
   onProgress(done, total) optional callback.
   ══════════════════════════════════════════════════════════ */
async function fbPatchBatch(items, onProgress, concurrency) {
  concurrency = concurrency || 20;
  let done = 0;
  for (let i = 0; i < items.length; i += concurrency) {
    const chunk = items.slice(i, i + concurrency);
    await Promise.all(chunk.map(({path, data}) => fbPatch(path, data)));
    done += chunk.length;
    if (onProgress) onProgress(done, items.length);
  }
  return done;
}

/* ── Admin FCM Token Save ──
   Login এর পরে admin এর FCM token Firebase এ save করো।
   Main app এই token ব্যবহার করে admin কে push notification পাঠায়।
   Path: users/{adminPhone}/fcmToken (lowercase users — main app এখান থেকে পড়ে)
   Also sets Users/{phone}/Role = "admin" যাতে main app admin চিনতে পারে
   ─────────────────────────────────────────────────────────────────── */
async function _saveAdminFcmToken() {
  try {
    // Capacitor FCM plugin দিয়ে token নাও
    const plugin = window.Capacitor?.Plugins?.FcmToken;
    if (!plugin) { _LC.warn("FCM","FcmToken plugin not available"); return; }
    const { token } = await plugin.getToken();
    if (!token) { _LC.warn("FCM","Empty FCM token"); return; }

    // Admin phone — Users node থেকে admin এর phone বের করো
    const t = await _tok();
    const usersRaw = await (await fetch(`${FB}/Users.json${_authQ(t)}`)).json();
    const users = Object.entries(usersRaw||{});
    const adminEntry = users.find(([,u])=>(u?.Role||u?.role||"").toLowerCase()==="admin");
    let adminPhone = adminEntry ? adminEntry[0] : null;

    if (!adminPhone) {
      // Phone নেই — UID দিয়ে fallback path ব্যবহার করো
      _LC.warn("FCM","No admin phone found — saving to AdminFCMTokens");
      await fbSet("AdminFCMTokens/token", token);
      _LC.info("FCM","✅ Admin FCM token saved to AdminFCMTokens/token");
      return;
    }

    // users/{phone}/fcmToken — main app এখান থেকে পড়ে
    // admin app এর token আলাদা field এ রাখি যাতে main app এর
    // users/{phone}/fcmToken (regular user token) overwrite না হয়
    await fbSet(`users/${adminPhone}/adminFcmToken`, token);
    _LC.info("FCM",`✅ Admin FCM token saved for ${adminPhone}`);

    // Token refresh listener
    window.addEventListener("fcmTokenRefresh", async (e) => {
      try {
        const newToken = e.detail?.token || JSON.parse(e.detail||"{}").token;
        if (newToken) {
          await fbSet(`users/${adminPhone}/fcmToken`, newToken);
          _LC.info("FCM","🔄 FCM token refreshed");
        }
      } catch(_) {}
    });
  } catch(e) {
    _LC.error("FCM","_saveAdminFcmToken: " + e.message);
  }
}


/* ══════════════════════════════════════════════════════════════
   🔄 BACKGROUND TASK MANAGER
   — সব API call (GAS GET/POST) এখানে queue হয়
   — App minimize / screen off হলেও কাজ চলে
   — WakeLock API দিয়ে CPU জাগিয়ে রাখে
   — visibilitychange এ pending tasks flush করে
   — React component এ live badge দেখায়
   ══════════════════════════════════════════════════════════════ */
const _BGM = (() => {
  const RETRY_DELAYS = [1000, 3000, 8000];
  const MAX_QUEUE    = 500;
  let _queue   = [];
  let _running = false;
  let _wakeLock= null;
  let _listeners = [];
  let _activeCount = 0;
  let _doneCount   = 0;
  let _failCount   = 0;

  async function _acquireWake() {
    if (!navigator.wakeLock) return;
    try {
      if (_wakeLock && _wakeLock.released === false) return;
      _wakeLock = await navigator.wakeLock.request("screen");
      _LC.info("BGM", "WakeLock acquired");
    } catch(e) { _LC.warn("BGM", "WakeLock failed: " + e.message); }
  }
  async function _releaseWake() {
    if (_wakeLock && !_wakeLock.released) {
      try { await _wakeLock.release(); } catch(_){}
      _wakeLock = null;
    }
  }

  document.addEventListener("visibilitychange", async () => {
    if (!document.hidden && _queue.length > 0) {
      _LC.lifecycle("BGM", "App foregrounded — flushing " + _queue.length + " pending tasks");
      await _acquireWake();
      _flush();
    }
  });

  function _notify() {
    _listeners.forEach(fn => { try { fn(); } catch(_){} });
  }

  async function _flush() {
    if (_running) return;
    if (_queue.length === 0) { _releaseWake(); _notify(); return; }
    _running = true;
    await _acquireWake();
    _notify();

    while (_queue.length > 0) {
      const task = _queue[0];
      _activeCount++;
      _notify();
      try {
        await task.fn();
        _queue.shift();
        _doneCount++;
        _LC.log("BGM", "✔ Task done: " + task.label, {doneCount:_doneCount});
      } catch(e) {
        task.retries = (task.retries||0) + 1;
        const delay = RETRY_DELAYS[task.retries - 1];
        if (delay !== undefined) {
          _LC.warn("BGM", "↩ Retry " + task.retries + " for: " + task.label + " — " + e.message);
          await new Promise(r => setTimeout(r, delay));
        } else {
          _LC.error("BGM", "✗ Task failed: " + task.label + " — " + e.message);
          _queue.shift();
          _failCount++;
        }
      }
      _activeCount = Math.max(0, _activeCount - 1);
      _notify();
    }

    _running = false;
    _releaseWake();
    _notify();
  }

  function enqueue(fn, label) {
    label = label || "task";
    if (_queue.length >= MAX_QUEUE) { _LC.warn("BGM","Queue full: "+label); return; }
    _queue.push({ fn, label, retries: 0, ts: Date.now() });
    _LC.log("BGM", "⏳ Enqueued: " + label + " (queue=" + _queue.length + ")");
    _notify();
    setTimeout(_flush, 50);
  }

  /* ── Native Foreground Service bridge (Android) ── */
  function _nativeStart(label) {
    try {
      const plugin = window.Capacitor?.Plugins?.BgSync;
      if (plugin) plugin.start({ title: "Admin: কাজ চলছে…", text: label || "Background sync" });
    } catch(_) {}
  }
  function _nativeUpdate(label) {
    try {
      const plugin = window.Capacitor?.Plugins?.BgSync;
      if (plugin) plugin.update({ title: "Admin: কাজ চলছে…", text: label });
    } catch(_) {}
  }
  function _nativeStop() {
    try {
      const plugin = window.Capacitor?.Plugins?.BgSync;
      if (plugin) plugin.stop();
    } catch(_) {}
  }

  // Patch enqueue/flush to call native service
  const _origEnqueue = enqueue;
  function enqueueWithNative(fn, label) {
    const wasEmpty = _queue.length === 0;
    _origEnqueue(fn, label);
    if (wasEmpty) _nativeStart(label || "task");
  }

  // Patch flush to stop service when done
  const _origFlushCheck = _notify;
  // Override notify to also call native stop when done
  _listeners._nativeCheck = () => {
    if (!_running && _queue.length === 0) _nativeStop();
    else if (_running && _queue.length > 0) _nativeUpdate("বাকি: " + _queue.length + "টি কাজ");
  };
  _listeners.push(_listeners._nativeCheck);

  return {
    enqueue: enqueueWithNative,
    getState: () => ({ pending:_queue.length, active:_activeCount, done:_doneCount, failed:_failCount, running:_running }),
    subscribe: fn => { _listeners.push(fn); return () => { _listeners = _listeners.filter(x=>x!==fn); }; },
  };
})();

function useBGM() {
  const [state, setState] = React.useState(() => _BGM.getState());
  useEffect(() => {
    const unsub = _BGM.subscribe(() => setState({..._BGM.getState()}));
    return unsub;
  }, []);
  return state;
}

function BgTaskIndicator() {
  const { pending, active, done, failed, running } = useBGM();
  const total = pending + active;
  if (total === 0 && !running) return null;
  const isPulsing = running || active > 0;
  return (
    <div style={{
      position:"fixed", top:56, right:10, zIndex:9999,
      background: failed > 0 ? "#ef444422" : "#3b82f622",
      border:"1px solid " + (failed > 0 ? "#ef4444aa" : "#3b82f6aa"),
      borderRadius:20, padding:"4px 10px",
      display:"flex", alignItems:"center", gap:6,
      fontSize:10, fontWeight:700, color: failed > 0 ? "#ef4444" : "#3b82f6",
      backdropFilter:"blur(6px)",
      animation: isPulsing ? "bgm-pulse 1.4s ease-in-out infinite" : "none",
      pointerEvents:"none",
    }}>
      <span style={{
        width:7, height:7, borderRadius:"50%",
        background: failed>0 ? "#ef4444" : "#3b82f6",
        display:"inline-block",
        animation: isPulsing ? "bgm-dot 1.4s ease-in-out infinite" : "none",
      }}/>
      {running || active > 0
        ? "⚙️ " + (pending + active) + "টি কাজ চলছে…"
        : "⏳ " + pending + "টি অপেক্ষায়"}
      {failed > 0 && <span style={{color:"#ef4444"}}>{"⚠️"}{failed}</span>}
    </div>
  );
}

/* ══════════ GAS helpers — REMOVED (GAS no longer called from app) ══════════
   GAS এখন standalone — শুধু Firebase থেকে pull করে Sheet backup দেয়।
   App থেকে আর কোনো GAS call নেই।
   ════════════════════════════════════════════════════════════════════════ */
const gasBg  = ()=>{};      // no-op — GAS আর call হয় না
const gasPost = ()=>{};     // no-op
const gasCall = async ()=>({});  // no-op

/* ══════════════════════════════════════════════════════════════════════════
   📲 FCM v1 DIRECT — Firebase Cloud Messaging HTTP v1 API
   Service Account JWT দিয়ে OAuth token নিয়ে FCM v1 call — instant।
   Legacy API deprecated — এটাই নতুন standard।
   Token path: Users/{phoneKey}/fcmToken  (main app সেখানে save করে)
   ══════════════════════════════════════════════════════════════════════════ */

/* ── JWT বানাও Service Account দিয়ে (browser crypto API) ── */
async function _fcmGetAccessToken() {
  if (!FCM_CLIENT_EMAIL || !FCM_PRIVATE_KEY) {
    _LC.warn("fcmGetToken", "FCM credentials missing");
    return null;
  }
  try {
    if (!crypto?.subtle) {
      _LC.error("fcmGetToken", "crypto.subtle unavailable");
      return null;
    }
    const now = Math.floor(Date.now() / 1000);
    const b64url = obj => {
      const s = typeof obj === "string" ? obj : JSON.stringify(obj);
      return btoa(unescape(encodeURIComponent(s)))
        .replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
    };
    const header  = {alg:"RS256",typ:"JWT"};
    const payload = {
      iss:FCM_CLIENT_EMAIL, sub:FCM_CLIENT_EMAIL,
      aud:"https://oauth2.googleapis.com/token",
      iat:now, exp:now+3600,
      scope:"https://www.googleapis.com/auth/firebase.messaging",
    };
    const sigInput = b64url(header) + "." + b64url(payload);
    // PEM → DER
    const pem = FCM_PRIVATE_KEY
      .replace(/-----BEGIN PRIVATE KEY-----/g,"")
      .replace(/-----END PRIVATE KEY-----/g,"")
      .replace(/[\r\n\s]/g,"");
    if (!pem) { _LC.error("fcmGetToken","Empty PEM after parse"); return null; }
    const der = Uint8Array.from(atob(pem), c => c.charCodeAt(0));
    const key = await crypto.subtle.importKey(
      "pkcs8", der.buffer,
      {name:"RSASSA-PKCS1-v1_5", hash:"SHA-256"},
      false, ["sign"]
    );
    const sig = await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5", key,
      new TextEncoder().encode(sigInput)
    );
    const sigB64 = btoa(String.fromCharCode(...new Uint8Array(sig)))
      .replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
    const jwt = sigInput + "." + sigB64;
    const res = await fetch("https://oauth2.googleapis.com/token", {
      method:"POST",
      headers:{"Content-Type":"application/x-www-form-urlencoded"},
      body:"grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="+jwt,
    });
    const d = await res.json();
    if (!d.access_token) {
      _LC.error("fcmGetToken","Token fail: "+JSON.stringify(d).slice(0,200));
      return null;
    }
    _LC.info("fcmGetToken","FCM token ok");
    return d.access_token;
  } catch(e) {
    _LC.error("fcmGetToken","Error: "+e.message+" key_len:"+FCM_PRIVATE_KEY.length);
    return null;
  }
}

// Access token cache — ১ ঘণ্টা valid
let _fcmTokenCache = { token: null, exp: 0 };
async function _fcmToken() {
  if (_fcmTokenCache.token && Date.now() < _fcmTokenCache.exp) return _fcmTokenCache.token;
  const t = await _fcmGetAccessToken();
  if (t) _fcmTokenCache = { token: t, exp: Date.now() + 55 * 60 * 1000 };
  return t;
}

/* একজনকে FCM v1 notification পাঠাও */
async function fcmSendOne(fcmToken, title, body, data) {
  if (!FCM_CLIENT_EMAIL || !fcmToken) return false;
  data = data || {};
  try {
    const accessToken = await _fcmToken();
    if (!accessToken) { _LC.warn("fcmSendOne", "No access token"); return false; }

    const projectId = FB_PROJ || FCM_CLIENT_EMAIL.split("@")[1]?.split(".")[0];
    const r = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          "Authorization": "Bearer " + accessToken,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: fcmToken,
            notification: { title, body },
            android: {
              priority: "high",
              notification: { sound: "default", click_action: "FLUTTER_NOTIFICATION_CLICK" },
            },
            data: Object.fromEntries(
              Object.entries({ ...data, title, body }).map(([k,v]) => [k, String(v)])
            ),
          },
        }),
      }
    );
    const res = await r.json();
    const ok = !!res.name;
    _LC.api("fcmSendOne", ok ? "✅ FCM v1 sent" : "⚠️ FCM v1 fail", { token: fcmToken.slice(-8), title, res });
    return ok;
  } catch(e) {
    _LC.error("fcmSendOne", "FCM v1 error: " + e.message);
    return false;
  }
}

/* Phone নম্বর থেকে FCM token পড়ে notification পাঠাও */
async function fcmNotifyPhone(phone, title, body, extraData) {
  if (!phone || !FCM_CLIENT_EMAIL) return false;
  try {
    const phK = phoneKey(phone);
    const t = await _tok();
    const r = await fetch(`${FB}/Users/${phK}/fcmToken.json${_authQ(t)}`);
    const token = await r.json();
    if (!token || typeof token !== "string") {
      _LC.warn("fcmNotifyPhone", "No FCM token for: " + phone);
      return false;
    }
    return fcmSendOne(token, title, body, extraData || {});
  } catch(e) {
    _LC.error("fcmNotifyPhone", e.message);
    return false;
  }
}

/* সব active user কে broadcast FCM — 20 concurrent */
async function fcmBroadcast(title, body, users) {
  if (!FCM_CLIENT_EMAIL) return 0;
  const t = await _tok();
  let sent = 0;
  const CONC = 20;
  for (let i = 0; i < users.length; i += CONC) {
    const chunk = users.slice(i, i + CONC);
    const results = await Promise.all(chunk.map(async u => {
      const phK = phoneKey(u.Phone || u.phone || "");
      if (!phK) return false;
      try {
        const r = await fetch(`${FB}/Users/${phK}/fcmToken.json${_authQ(t)}`);
        const token = await r.json();
        if (!token || typeof token !== "string") return false;
        return fcmSendOne(token, title, body, {});
      } catch(_) { return false; }
    }));
    sent += results.filter(Boolean).length;
  }
  _LC.api("fcmBroadcast", `Broadcast done: ${sent}/${users.length}`, { title });
  return sent;
}

/* ══════════ SIMPLE FETCH CACHE — no subscriptions, no loops ══════════ */
const _store = {}; // path -> {data, ts, promise}
const STALE  = 90_000; // 90s

async function loadPath(path, force=false){
  const now = Date.now();
  const cached = _store[path];
  if(!force && cached && !cached.promise && now - cached.ts < STALE) return cached.data;
  if(cached?.promise) return cached.promise;
  const p = fbGet(path).then(data=>{
    _store[path] = {data, ts:Date.now(), promise:null};
    return data;
  }).catch(e=>{
    if(_store[path]) _store[path].promise = null;
    throw e;
  });
  if(!_store[path]) _store[path]={data:null,ts:0,promise:null};
  _store[path].promise = p;
  return p;
}

function invalidate(...paths){
  paths.forEach(p=>{if(_store[p]){_store[p].ts=0;_store[p].promise=null;}});
  // Notify all useFB hooks to re-fetch
  window.dispatchEvent(new CustomEvent("fb-invalidate",{detail:{paths}}));
}
function invalidateAll(){ Object.keys(_store).forEach(p=>{if(_store[p]){_store[p].ts=0;_store[p].promise=null;}}); }

/* Simple hook — fetches once, re-fetches on invalidate */
function useFB(path, tick=0){
  const [state, setState] = useState(()=>{
    const cached = _store[path];
    return {data: cached?.data??null, loading: !cached?.data};
  });
  const lastTick = useRef(-1);
  const lastPath = useRef(null);
  const localTick = useRef(0);
  const [_lt, setLt] = useState(0);

  // Listen for invalidate events for this path
  useEffect(()=>{
    if(!path) return;
    const handler=(e)=>{
      const paths=e.detail?.paths;
      if(!paths || paths.includes(path)){
        localTick.current++;
        setLt(t=>t+1);
      }
    };
    window.addEventListener("fb-invalidate", handler);
    return()=>window.removeEventListener("fb-invalidate", handler);
  },[path]);

  useEffect(()=>{
    if(!path) return;
    const force = tick !== lastTick.current || path !== lastPath.current || _lt !== undefined;
    lastTick.current = tick;
    lastPath.current = path;

    const cached = _store[path];
    if(!force && cached?.data && Date.now()-cached.ts < STALE){
      setState({data:cached.data, loading:false});
      return;
    }

    let cancelled = false;
    setState(s=>({...s, loading:!s.data}));
    loadPath(path, true).then(data=>{
      if(!cancelled) setState({data, loading:false});
    }).catch(()=>{
      if(!cancelled) setState(s=>({...s, loading:false}));
    });
    return ()=>{ cancelled=true; };
  }, [path, tick, _lt]);

  return state;
}

/* ══════════ HELPERS ══════════ */
const fmt=n=>(n||0).toLocaleString();
const pct=(a,b)=>b?Math.round(a/b*100):0;
const initials=n=>(n||"?").split(" ").map(w=>w[0]).join("").toUpperCase().slice(0,2);
const nowTs=()=>new Date().toLocaleString("bn-BD",{timeZone:"Asia/Dhaka"});
const timeAgo=ts=>{
  if(!ts)return"—";
  try{
    const d=new Date(ts.replace?ts.replace(/(\d{2})-(\d{2})-(\d{4})/,"$3-$2-$1"):ts);
    const s=Date.now()-d.getTime();
    if(s<60000)return"এখনই";
    if(s<3600000)return~~(s/60000)+"মি আগে";
    if(s<86400000)return~~(s/3600000)+"ঘণ্টা আগে";
    return~~(s/86400000)+"দিন আগে";
  }catch{return ts;}
};
const toArr=raw=>{
  if(!raw)return[];
  // IMPORTANT: never treat as plain array — Firebase numeric keys lose _fbKey
  // Convert array to indexed object so _fbKey is always set
  if(Array.isArray(raw)){
    return raw.map((v,i)=>v&&typeof v==="object"?{...v,_fbKey:String(i)}:null).filter(Boolean);
  }
  return Object.entries(raw).map(([k,v])=>v&&typeof v==="object"?{...v,_fbKey:k}:null).filter(Boolean);
};
const phoneKey=ph=>(ph||"").replace(/^'+/,"").trim().replace(/[.#$\[\]\s]/g,"_");
const matchPhone=(key,phone)=>{
  const k=key.replace(/_/g,"");
  const p=(phone||"").replace(/[.#$\[\]\s]/g,"");
  return k===p||k===p.replace(/^0+/,"")||k.replace(/^0+/,"")===p.replace(/^0+/,"");
};
const uploadImg=async file=>{
  const fd=new FormData();fd.append("image",file);
  const r=await fetch(`https://api.imgbb.com/1/upload?key=${IMGBB}`,{method:"POST",body:fd});
  return(await r.json())?.data?.url||"";
};

/* ══════════ IMAGE CROP PICKER ══════════ */
function ImageCropPicker({onCropToQuestion,onCropToSolution,onClose,push}){
  const canvasRef=React.useRef(null);
  const imgRef=React.useRef(null);
  const[srcImg,setSrcImg]=React.useState(null); // dataURL of loaded image
  const[natural,setNatural]=React.useState({w:1,h:1});
  const[sel,setSel]=React.useState(null);       // {x,y,w,h} in canvas coords
  const[dragging,setDragging]=React.useState(false);
  const[startPt,setStartPt]=React.useState(null);
  const[crops,setCrops]=React.useState([]);     // list of {dataUrl, target:'q'|'s'}
  const[uploading,setUploading]=React.useState(false);

  /* load image from file input */
  const handleFile=e=>{
    const f=e.target.files[0];if(!f)return;
    const reader=new FileReader();
    reader.onload=ev=>setSrcImg(ev.target.result);
    reader.readAsDataURL(f);
  };

  /* once srcImg set, draw on canvas */
  React.useEffect(()=>{
    if(!srcImg||!canvasRef.current)return;
    const canvas=canvasRef.current;
    const img=new Image();
    img.onload=()=>{
      const maxW=canvas.parentElement.offsetWidth||340;
      const scale=maxW/img.naturalWidth;
      canvas.width=maxW;
      canvas.height=img.naturalHeight*scale;
      setNatural({w:img.naturalWidth,h:img.naturalHeight,scale});
      imgRef.current=img;
      redraw(canvas,img,null);
    };
    img.src=srcImg;
  },[srcImg]);

  const redraw=(canvas,img,s)=>{
    const ctx=canvas.getContext("2d");
    ctx.clearRect(0,0,canvas.width,canvas.height);
    ctx.drawImage(img,0,0,canvas.width,canvas.height);
    if(s&&s.w&&s.h){
      ctx.fillStyle="rgba(59,130,246,0.18)";
      ctx.fillRect(s.x,s.y,s.w,s.h);
      ctx.strokeStyle="#3b82f6";ctx.lineWidth=2;ctx.setLineDash([5,3]);
      ctx.strokeRect(s.x,s.y,s.w,s.h);
    }
  };

  const getPos=(e,canvas)=>{
    const rect=canvas.getBoundingClientRect();
    const clientX=e.touches?e.touches[0].clientX:e.clientX;
    const clientY=e.touches?e.touches[0].clientY:e.clientY;
    return{x:clientX-rect.left,y:clientY-rect.top};
  };

  const onDown=e=>{
    e.preventDefault();
    if(!imgRef.current)return;
    const p=getPos(e,canvasRef.current);
    setStartPt(p);setSel(null);setDragging(true);
  };
  const onMove=e=>{
    e.preventDefault();
    if(!dragging||!startPt)return;
    const p=getPos(e,canvasRef.current);
    const s={x:Math.min(startPt.x,p.x),y:Math.min(startPt.y,p.y),w:Math.abs(p.x-startPt.x),h:Math.abs(p.y-startPt.y)};
    setSel(s);
    redraw(canvasRef.current,imgRef.current,s);
  };
  const onUp=e=>{e.preventDefault();setDragging(false);};

  /* crop selected region to dataURL */
  const doCrop=()=>{
    if(!sel||sel.w<5||sel.h<5){push("warn","একটু বড় করে select করুন","");return null;}
    const sc=natural.scale;
    const offscreen=document.createElement("canvas");
    offscreen.width=sel.w/sc; offscreen.height=sel.h/sc;
    const ctx=offscreen.getContext("2d");
    ctx.drawImage(imgRef.current,sel.x/sc,sel.y/sc,sel.w/sc,sel.h/sc,0,0,sel.w/sc,sel.h/sc);
    return offscreen.toDataURL("image/jpeg",0.92);
  };

  /* dataURL → File */
  const dataUrlToFile=dataUrl=>{
    const arr=dataUrl.split(","),mime=arr[0].match(/:(.*?);/)[1];
    const bstr=atob(arr[1]);let n=bstr.length;const u=new Uint8Array(n);
    while(n--)u[n]=bstr.charCodeAt(n);
    return new File([u],`crop_${Date.now()}.jpg`,{type:mime});
  };

  const addCrop=async target=>{
    const dataUrl=doCrop();if(!dataUrl)return;
    setUploading(true);
    try{
      const file=dataUrlToFile(dataUrl);
      const url=await uploadImg(file);
      if(target==="q")onCropToQuestion(url);
      else onCropToSolution(url);
      setCrops(prev=>[...prev,{dataUrl,target}]);
      push("success",target==="q"?"প্রশ্নে যোগ হয়েছে":"সমাধানে যোগ হয়েছে","");
    }catch{push("error","আপলোড ব্যর্থ","");}
    setUploading(false);
  };

  return(
    <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.92)",zIndex:9000,display:"flex",flexDirection:"column",maxWidth:480,margin:"0 auto"}}>
      {/* topbar */}
      <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",padding:"12px 14px",background:C.card,borderBottom:`1px solid ${C.border}`}}>
        <span style={{fontSize:14,fontWeight:700,color:C.text}}>✂️ ছবি Crop করুন</span>
        <button onClick={onClose} style={{background:"transparent",border:"none",color:C.muted,fontSize:20,cursor:"pointer",padding:"0 4px"}}>✕</button>
      </div>

      {/* if no image yet — show file picker */}
      {!srcImg&&(
        <div style={{flex:1,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",gap:16,padding:24}}>
          <div style={{fontSize:48}}>🖼️</div>
          <div style={{color:C.muted,fontSize:13,textAlign:"center"}}>বইয়ের পাতার ছবি তুলুন বা গ্যালারি থেকে বেছে নিন</div>
          <label style={{background:C.accent,color:"#fff",borderRadius:10,padding:"11px 28px",fontSize:14,fontWeight:700,cursor:"pointer"}}>
            📷 ছবি বেছে নিন
            <input type="file" accept="image/*" style={{display:"none"}} onChange={handleFile} capture="environment"/>
          </label>
          <label style={{background:C.panel,border:`1px solid ${C.border}`,color:C.text,borderRadius:10,padding:"9px 24px",fontSize:13,cursor:"pointer"}}>
            🖼 গ্যালারি
            <input type="file" accept="image/*" style={{display:"none"}} onChange={handleFile}/>
          </label>
        </div>
      )}

      {/* canvas area */}
      {srcImg&&(
        <div style={{flex:1,overflow:"auto",padding:"8px 0"}}>
          <div style={{padding:"6px 14px 4px",color:C.muted,fontSize:11}}>আঙুল দিয়ে drag করে অংক select করুন</div>
          <canvas
            ref={canvasRef}
            style={{display:"block",width:"100%",touchAction:"none",cursor:"crosshair"}}
            onMouseDown={onDown} onMouseMove={onMove} onMouseUp={onUp}
            onTouchStart={onDown} onTouchMove={onMove} onTouchEnd={onUp}
          />
        </div>
      )}

      {/* crops preview */}
      {crops.length>0&&(
        <div style={{padding:"6px 14px",display:"flex",gap:6,overflowX:"auto"}}>
          {crops.map((c,i)=>(
            <div key={i} style={{position:"relative",flexShrink:0}}>
              <img src={c.dataUrl} style={{height:48,borderRadius:6,border:`1.5px solid ${c.target==="q"?C.accent:C.green}`}} alt=""/>
              <div style={{position:"absolute",bottom:1,left:2,fontSize:8,fontWeight:700,color:"#fff",textShadow:"0 0 3px #000"}}>{c.target==="q"?"Q":"S"}</div>
            </div>
          ))}
        </div>
      )}

      {/* action buttons */}
      {srcImg&&(
        <div style={{padding:"10px 14px 20px",display:"flex",gap:8,borderTop:`1px solid ${C.border}`,background:C.card}}>
          <button onClick={()=>addCrop("q")} disabled={uploading||!sel} style={{flex:1,background:C.accent,color:"#fff",border:"none",borderRadius:10,padding:"12px 6px",fontSize:13,fontWeight:700,cursor:"pointer",opacity:(!sel||uploading)?0.5:1}}>
            {uploading?"⏳...":"➕ প্রশ্নে"}
          </button>
          <button onClick={()=>addCrop("s")} disabled={uploading||!sel} style={{flex:1,background:C.green,color:"#fff",border:"none",borderRadius:10,padding:"12px 6px",fontSize:13,fontWeight:700,cursor:"pointer",opacity:(!sel||uploading)?0.5:1}}>
            {uploading?"⏳...":"✅ সমাধানে"}
          </button>
          <label style={{flexShrink:0,background:C.panel,border:`1px solid ${C.border}`,color:C.text,borderRadius:10,padding:"12px 10px",fontSize:13,cursor:"pointer",display:"flex",alignItems:"center"}}>
            🔄
            <input type="file" accept="image/*" style={{display:"none"}} onChange={handleFile}/>
          </label>
          <button onClick={onClose} style={{flexShrink:0,background:"transparent",border:`1px solid ${C.border}`,color:C.muted,borderRadius:10,padding:"12px 10px",fontSize:13,cursor:"pointer"}}>✓ শেষ</button>
        </div>
      )}
    </div>
  );
}

/* ══════════ MODAL BACK HANDLER ══════════ */
// যেকোনো modal এ এটা call করলে Android back button এ modal বন্ধ হবে
function useModalBack(onClose){
  useEffect(()=>{
    window.dispatchEvent(new Event("modal-open"));
    const handler=()=>onClose();
    window.addEventListener("back-press",handler);
    return()=>{
      window.dispatchEvent(new Event("modal-close"));
      window.removeEventListener("back-press",handler);
    };
  },[]);// eslint-disable-line
}

/* ══════════ TOAST ══════════ */
let _tid=0;
function useToasts(){
  const[t,set]=useState([]);
  const push=useCallback((type,title,msg="")=>{
    const id=++_tid;
    set(p=>[...p.slice(-4),{id,type,title,msg}]);
    setTimeout(()=>set(p=>p.filter(x=>x.id!==id)),4000);
  },[]);
  return[t,push];
}
function Toasts({t}){return(
  <div className="toasts">
    {t.map(x=>(
      <div key={x.id} className={`toast ${x.type}`}>
        <div className="t-icon">{x.type==="success"?"✅":x.type==="error"?"❌":x.type==="warn"?"⚠️":"ℹ️"}</div>
        <div className="t-body"><div className="t-title">{x.title}</div>{x.msg&&<div className="t-msg">{x.msg}</div>}</div>
      </div>
    ))}
  </div>
);}

/* ══════════ DELETE WARNING MODAL ══════════ */
function DeleteWarningModal({title,description,onConfirm,onCancel,loading,progress}){
  useModalBack(onCancel);
  const pct = progress&&progress.total>0 ? Math.round(progress.done/progress.total*100) : 0;
  const showProgress = loading && progress && progress.total > 0;
  return(
    <div className="ovl" style={{zIndex:300}}>
      <div className="modal" style={{borderTop:`3px solid ${C.red}`}}>
        <div className="mh"/>
        <div style={{textAlign:"center",marginBottom:16}}>
          <div style={{fontSize:40,marginBottom:8}}>🗑️</div>
          <div style={{fontSize:16,fontWeight:700,color:C.red,marginBottom:6}}>{title}</div>
          <div style={{fontSize:12,color:C.muted,lineHeight:1.6}}>{description}</div>
        </div>
        {showProgress?(
          <div style={{marginBottom:14}}>
            <div style={{display:"flex",justifyContent:"space-between",fontSize:11,marginBottom:5,color:C.text}}>
              <span>⚡ ডিলিট হচ্ছে…</span>
              <span style={{fontWeight:700,color:C.red}}>{progress.done}/{progress.total} ({pct}%)</span>
            </div>
            <div style={{height:6,background:C.border,borderRadius:6,overflow:"hidden"}}>
              <div style={{height:"100%",width:pct+"%",background:C.red,borderRadius:6,transition:"width .3s ease"}}/>
            </div>
          </div>
        ):(
          <div style={{background:"#ef444412",border:"1px solid #ef444430",borderRadius:10,padding:"10px 12px",marginBottom:14,fontSize:11,color:C.red,textAlign:"center",fontWeight:600}}>
            ⚠️ এই কাজ পূর্বাবস্থায় ফেরানো যাবে না!
          </div>
        )}
        <div style={{display:"flex",gap:8}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onCancel} disabled={loading}>বাতিল</button>
          <button className="btn" style={{flex:2,justifyContent:"center",background:C.red,color:"#fff"}} onClick={onConfirm} disabled={loading}>
            {loading?`⏳ ${showProgress?pct+"%":"ডিলিট হচ্ছে..."}` :"🗑️ হ্যাঁ, ডিলিট করুন"}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ══════════ CSS ══════════ */
const css=`
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+Bengali:wght@400;500;600;700&family=Space+Grotesk:wght@400;500;600;700&display=swap');
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
html,body,#root{background:${C.bg};color:${C.text};font-family:'Noto Sans Bengali','Space Grotesk',sans-serif;min-height:100dvh;max-width:480px;margin:0 auto;overflow-x:hidden}
::-webkit-scrollbar{width:3px}::-webkit-scrollbar-thumb{background:${C.border};border-radius:10px}
.bottom-nav{position:fixed;bottom:0;left:50%;transform:translateX(-50%);width:100%;max-width:480px;background:${C.navBg};border-top:1px solid ${C.border};display:flex;z-index:100;padding-bottom:env(safe-area-inset-bottom,8px)}
.nav-btn{flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;padding:8px 2px 6px;cursor:pointer;border:none;background:transparent;color:${C.muted};font-family:inherit;font-size:9px;font-weight:500;transition:color .15s;position:relative}
.nav-btn.active{color:${C.accent}}.nav-icon{font-size:18px;line-height:1}
.nav-badge{position:absolute;top:5px;right:calc(50% - 16px);background:${C.red};color:#fff;font-size:8px;font-weight:700;width:14px;height:14px;border-radius:50%;display:flex;align-items:center;justify-content:center}
.topbar{background:${C.card};border-bottom:1px solid ${C.border};padding:12px 16px 10px;display:flex;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:50}
.topbar-title{font-size:15px;font-weight:700}.topbar-sub{font-size:10px;color:${C.muted};margin-top:1px}
.icon-btn{width:34px;height:34px;border-radius:9px;background:${C.panel};border:1px solid ${C.border};color:${C.text};font-size:16px;display:flex;align-items:center;justify-content:center;cursor:pointer}
.icon-btn.spin{animation:spin 1s linear infinite}
.page{padding:13px;padding-bottom:82px;min-height:100dvh}
.sg{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:10px}
.sc{background:${C.card};border:1px solid ${C.border};border-radius:13px;padding:12px;position:relative;overflow:hidden}
.sc::after{content:attr(data-icon);position:absolute;right:8px;bottom:6px;font-size:24px;opacity:.12}
.sl{font-size:10px;color:${C.muted};font-weight:600;margin-bottom:4px;text-transform:uppercase;letter-spacing:.5px}
.sv{font-size:24px;font-weight:700;line-height:1}
.sv-b{color:${C.accent}}.sv-g{color:${C.green}}.sv-r{color:${C.red}}.sv-y{color:${C.yellow}}.sv-p{color:${C.purple}}
.tb{border-top:2px solid ${C.accent}}.tg{border-top:2px solid ${C.green}}.tr{border-top:2px solid ${C.red}}.ty{border-top:2px solid ${C.yellow}}.tp{border-top:2px solid ${C.purple}}
.card{background:${C.card};border:1px solid ${C.border};border-radius:13px;padding:13px;margin-bottom:10px}
.ct{font-size:11px;font-weight:700;color:${C.muted};text-transform:uppercase;letter-spacing:.8px;margin-bottom:11px}
.av{width:38px;height:38px;border-radius:50%;background:linear-gradient(135deg,${C.accent},${C.purple});display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;color:#fff;flex-shrink:0}
.av.sm{width:30px;height:30px;font-size:11px}
.pill{display:inline-flex;align-items:center;padding:2px 8px;border-radius:20px;font-size:10px;font-weight:700;white-space:nowrap;flex-shrink:0}
.pa{background:#22c55e18;color:${C.green};border:1px solid #22c55e33}
.pi{background:#ef444418;color:${C.red};border:1px solid #ef444433}
.pp{background:#f59e0b18;color:${C.yellow};border:1px solid #f59e0b33}
.btn{display:inline-flex;align-items:center;gap:4px;padding:7px 12px;border-radius:9px;font-size:12px;font-weight:600;font-family:inherit;cursor:pointer;border:none;transition:all .15s;white-space:nowrap}
.btn:active{transform:scale(.96)}.btn:disabled{opacity:.45;pointer-events:none}
.bp{background:${C.accent};color:#fff}.bs{background:#22c55e20;color:${C.green};border:1px solid #22c55e40}
.bg{background:transparent;color:${C.muted};border:1px solid ${C.border}}.bg:hover{background:${C.border};color:${C.text}}
.bb{width:100%;justify-content:center;padding:10px}
.inp,.ta{background:${C.panel};border:1px solid ${C.border};border-radius:9px;padding:9px 12px;color:${C.text};font-family:inherit;font-size:13px;width:100%;outline:none;transition:border-color .2s;-webkit-appearance:none}
.inp:focus,.ta:focus{border-color:${C.accent}}.inp::placeholder,.ta::placeholder{color:${C.muted}}
.ta{resize:vertical;min-height:75px}.fld{margin-bottom:10px}
.fld label{display:block;font-size:10px;font-weight:700;color:${C.muted};letter-spacing:.8px;margin-bottom:4px;text-transform:uppercase}
.sw{position:relative;margin-bottom:10px}.sw .si{position:absolute;left:10px;top:50%;transform:translateY(-50%);font-size:14px;pointer-events:none}.sw .inp{padding-left:32px}
.ftabs{display:flex;gap:5px;margin-bottom:11px;overflow-x:auto;padding-bottom:2px;scrollbar-width:none}.ftabs::-webkit-scrollbar{display:none}
.ftab{flex-shrink:0;padding:6px 12px;border-radius:20px;font-size:11px;font-weight:600;cursor:pointer;border:1px solid ${C.border};background:transparent;color:${C.muted};font-family:inherit;transition:all .15s}
.ftab.on{background:${C.accent};color:#fff;border-color:${C.accent}}
.rc{background:${C.panel};border:1px solid ${C.border};border-radius:11px;padding:11px;margin-bottom:8px}
.ri{font-size:12px;color:${C.text};line-height:1.5;background:${C.card};border-radius:7px;padding:7px 9px;margin-top:7px;border-left:2px solid ${C.red}}
.rm{font-size:10px;color:${C.muted};margin-top:4px;display:flex;gap:6px;flex-wrap:wrap}
.ovl{position:fixed;inset:0;background:#00000094;z-index:200;display:flex;align-items:flex-end}
.modal{background:${C.card};border:1px solid ${C.border};border-radius:20px 20px 0 0;padding:16px 16px 36px;width:100%;max-height:88dvh;overflow-y:auto;animation:su .22s ease}
.mh{width:32px;height:4px;background:${C.border};border-radius:4px;margin:0 auto 13px}
.mt{font-size:15px;font-weight:700;margin-bottom:13px}
.fs{position:fixed;inset:0;background:${C.bg};z-index:150;overflow-y:auto}
.fsh{background:${C.card};border-bottom:1px solid ${C.border};padding:12px 14px;display:flex;align-items:center;gap:11px;position:sticky;top:0;z-index:10}
.bk{width:32px;height:32px;border-radius:8px;background:${C.panel};border:1px solid ${C.border};color:${C.text};font-size:15px;display:flex;align-items:center;justify-content:center;cursor:pointer;flex-shrink:0}
.sk{background:linear-gradient(90deg,${C.border},#1a2840,${C.border});background-size:200% 100%;animation:shim 1.4s infinite;border-radius:9px;height:64px;margin-bottom:8px}
.empty{text-align:center;padding:40px 20px;color:${C.muted}}.ei{font-size:36px;margin-bottom:8px;opacity:.25}
.toasts{position:fixed;top:13px;left:50%;transform:translateX(-50%);width:calc(100% - 26px);max-width:440px;z-index:999;display:flex;flex-direction:column;gap:6px;pointer-events:none}
.toast{background:${C.card};border:1px solid ${C.border};border-radius:11px;padding:10px 12px;display:flex;gap:8px;align-items:flex-start;animation:ti .25s ease;box-shadow:0 8px 28px #00000080;pointer-events:all}
.toast.success{border-left:3px solid ${C.green}}.toast.error{border-left:3px solid ${C.red}}.toast.warn{border-left:3px solid ${C.yellow}}.toast.info{border-left:3px solid ${C.accent}}
.t-icon{font-size:16px}.t-body{flex:1}.t-title{font-size:12px;font-weight:700}.t-msg{font-size:11px;color:${C.muted};margin-top:1px}
.atabs{display:flex;background:${C.panel};border-radius:10px;padding:3px;margin-bottom:11px;gap:3px}
.atab{flex:1;text-align:center;padding:7px 3px;border-radius:7px;font-size:11px;font-weight:600;cursor:pointer;border:none;background:transparent;color:${C.muted};font-family:inherit;transition:all .2s}
.atab.on{background:${C.card};color:${C.text};box-shadow:0 2px 6px #00000040}
.srow{display:flex;align-items:center;justify-content:space-between;padding:8px 0;border-bottom:1px solid ${C.border}40;font-size:12px}.srow:last-child{border-bottom:none}
.sbar{height:3px;border-radius:3px;background:${C.border};flex:1;margin:3px 6px 0 0;overflow:hidden}.sbar-f{height:100%;border-radius:3px;transition:width .6s ease}
.slb{font-size:10px;font-weight:700;color:${C.muted};letter-spacing:1.2px;text-transform:uppercase;margin:14px 0 8px}
.nr{display:flex;gap:8px;align-items:flex-start;padding:9px 0;border-bottom:1px solid ${C.border}40}.nr:last-child{border-bottom:none}
.nd{width:7px;height:7px;border-radius:50%;margin-top:4px;flex-shrink:0}.nd.n{background:${C.accent}}.nd.o{background:${C.muted}}
.nc{flex:1}.nt{font-size:12px;font-weight:600}.ns{font-size:11px;color:${C.muted};margin-top:1px}.ntm{font-size:10px;color:${C.muted};white-space:nowrap}
.steps{display:flex;margin-bottom:16px}.step{flex:1;text-align:center;font-size:10px;font-weight:700;padding:5px 2px;border-bottom:2px solid ${C.border};color:${C.muted};transition:all .2s}
.step.done{border-color:${C.green};color:${C.green}}.step.act{border-color:${C.accent};color:${C.accent}}
.bc{display:flex;align-items:flex-end;gap:2px;height:64px;margin-top:5px}
.bcol{display:flex;flex-direction:column;align-items:center;flex:1;gap:2px}
.brect{width:100%;border-radius:3px 3px 0 0;min-height:2px}.blbl{font-size:7px;color:${C.muted};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:26px;text-align:center}
.sri{display:flex;align-items:center;gap:9px;padding:9px;background:${C.panel};border:1px solid ${C.border};border-radius:10px;margin-bottom:6px;cursor:pointer;transition:border-color .15s}.sri:hover{border-color:${C.accent}}
.stag{font-size:9px;font-weight:700;padding:2px 6px;border-radius:7px;background:${C.accent}20;color:${C.accent};flex-shrink:0}
.rw{position:relative;width:68px;height:68px;flex-shrink:0}.rw svg{transform:rotate(-90deg)}
.rpct{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700}
.tp2{padding:6px 14px;border-radius:20px;font-size:11px;font-weight:700;cursor:pointer;border:1px solid ${C.border};background:transparent;color:${C.muted};font-family:inherit;transition:all .15s;flex-shrink:0}
.tp2.on{background:${C.accent};color:#fff;border-color:${C.accent}}
.cc{padding:4px 10px;border-radius:8px;font-size:11px;font-weight:700;cursor:pointer;border:1px solid ${C.border};background:transparent;color:${C.muted};font-family:inherit;transition:all .15s;white-space:nowrap}
.cc.on{background:${C.green}20;color:${C.green};border-color:${C.green}40}
.qcard{background:${C.panel};border:1px solid ${C.border};border-radius:11px;padding:11px;margin-bottom:8px}
.qcard-q{font-size:12px;font-weight:600;line-height:1.5;margin-bottom:7px;color:${C.text}}
.qcard-meta{display:flex;gap:5px;flex-wrap:wrap;align-items:center}
.qtag{font-size:9px;font-weight:700;padding:2px 7px;border-radius:6px;white-space:nowrap}
.qtag-mcq{background:${C.accent}20;color:${C.accent};border:1px solid ${C.accent}30}
.qtag-wr{background:${C.purple}20;color:${C.purple};border:1px solid ${C.purple}30}
.qtag-sub{background:${C.green}15;color:${C.green};border:1px solid ${C.green}25}
.qtag-tp{background:${C.yellow}15;color:${C.yellow};border:1px solid ${C.yellow}25}
.rename-row{display:flex;align-items:center;gap:8px;padding:10px;background:${C.panel};border:1px solid ${C.border};border-radius:10px;margin-bottom:7px}
.rename-name{flex:1;font-size:12px;font-weight:600}
.rename-count{font-size:10px;color:${C.muted};white-space:nowrap}
@keyframes spin{to{transform:rotate(360deg)}}
@keyframes su{from{transform:translateY(36px);opacity:0}to{transform:translateY(0);opacity:1}}
@keyframes shim{0%{background-position:-200% 0}100%{background-position:200% 0}}
@keyframes ti{from{transform:translateY(-16px);opacity:0}to{transform:translateY(0);opacity:1}}
@keyframes bgm-pulse{0%,100%{box-shadow:0 0 0 0 #3b82f644}50%{box-shadow:0 0 0 5px #3b82f611}}
@keyframes bgm-dot{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.4;transform:scale(.7)}}
`;

/* ══════════ MINI COMPONENTS ══════════ */
function Ring({val,max,color}){
  const r=26,c2=2*Math.PI*r,p=max?Math.min(100,Math.round(val/max*100)):0;
  return(
    <div className="rw">
      <svg width="68" height="68" viewBox="0 0 68 68">
        <circle cx="34" cy="34" r={r} fill="none" stroke={C.border} strokeWidth="6"/>
        <circle cx="34" cy="34" r={r} fill="none" stroke={color} strokeWidth="6"
          strokeDasharray={`${c2*p/100} ${c2}`} strokeLinecap="round"/>
      </svg>
      <div className="rpct" style={{color}}>{p}%</div>
    </div>
  );
}

function Bar({data,color}){
  if(!data?.length)return null;
  const mx=Math.max(...data.map(d=>d.v),1);
  return(
    <div className="bc">
      {data.map((d,i)=>(
        <div key={i} className="bcol">
          <div className="brect" style={{height:(d.v/mx*58)+"px",background:color,opacity:.85}}/>
          <div className="blbl">{d.l}</div>
        </div>
      ))}
    </div>
  );
}

/* ── Tree: memoised, renders only when data changes ── */
const Tree = React.memo(function Tree({entries,total,color}){
  const[open,setO]=useState({});
  const tog=useCallback(k=>setO(p=>({...p,[k]:!p[k]})),[]);
  return(
    <>
      {entries.map(([sub,v])=>{
        const tops=Object.entries(v.topics||{});
        return(
          <div key={sub} style={{marginBottom:7}}>
            <div style={{display:"flex",alignItems:"center",padding:"8px 0",borderBottom:`1px solid ${C.border}40`,cursor:tops.length?"pointer":"default"}} onClick={()=>tops.length&&tog(sub)}>
              <div style={{flex:1}}>
                <div style={{fontWeight:700,fontSize:12,display:"flex",alignItems:"center",gap:4}}>
                  {tops.length>0&&<span style={{fontSize:9,color:C.muted,display:"inline-block",transform:open[sub]?"rotate(90deg)":"none",transition:"transform .2s"}}>▶</span>}
                  {sub}
                </div>
                <div style={{display:"flex",alignItems:"center",marginTop:3}}><div className="sbar"><div className="sbar-f" style={{width:pct(v.total,total)+"%",background:color}}/></div></div>
                <div style={{fontSize:9,color:C.muted,marginTop:1}}>MCQ:{v.mcq||0} · Written:{v.written||0}{tops.length?` · ${tops.length}টি Topic`:""}</div>
              </div>
              <div style={{fontWeight:700,color,fontSize:16,minWidth:32,textAlign:"right"}}>{v.total}</div>
            </div>
            {open[sub]&&tops.map(([tp,tv])=>{
              const sts=Object.entries(tv.subtopics||{});
              const tk=sub+"_"+tp;
              return(
                <div key={tp} style={{marginLeft:12,borderLeft:`2px solid ${color}30`}}>
                  <div style={{display:"flex",alignItems:"center",padding:"6px 0 6px 9px",cursor:sts.length?"pointer":"default"}} onClick={()=>sts.length&&tog(tk)}>
                    <div style={{flex:1,fontSize:11,fontWeight:600,display:"flex",alignItems:"center",gap:3}}>
                      {sts.length>0&&<span style={{fontSize:8,color:C.muted,display:"inline-block",transform:open[tk]?"rotate(90deg)":"none",transition:"transform .2s"}}>▶</span>}
                      📂 {tp}
                    </div>
                    <div style={{fontWeight:700,color,fontSize:13,minWidth:28,textAlign:"right"}}>{tv.total}</div>
                  </div>
                  {open[tk]&&sts.map(([st,sv])=>(
                    <div key={st} style={{display:"flex",alignItems:"center",padding:"5px 0 5px 18px",borderBottom:`1px solid ${C.border}20`}}>
                      <div style={{flex:1}}><div style={{fontSize:10}}>📄 {st}</div><div style={{fontSize:9,color:C.muted}}>MCQ:{sv.mcq||0} · Written:{sv.written||0}</div></div>
                      <div style={{fontWeight:600,color:C.muted,fontSize:12,minWidth:26,textAlign:"right"}}>{sv.total}</div>
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
        );
      })}
    </>
  );
});

/* ── buildSubjectMap: heavy — called only when raw data changes ── */
function buildSubjectMap(arr){
  const map={};
  for(let i=0;i<arr.length;i++){
    const q=arr[i];
    const sub=(q.Subject||q.subject||"Unknown").trim();
    const typ=(q.QType||q.qtype||"MCQ").toLowerCase();
    const st=(q.Sub_topic||q.sub_topic||"General").trim();
    const parts=st.includes(" > ")?st.split(" > "):[st,st];
    const top=parts[0].trim()||"General";
    const stF=parts.length>1?parts[1].trim():st;
    const isWr=typ==="written";
    if(!map[sub])map[sub]={total:0,mcq:0,written:0,topics:{}};
    map[sub].total++;if(isWr)map[sub].written++;else map[sub].mcq++;
    if(!map[sub].topics[top])map[sub].topics[top]={total:0,subtopics:{}};
    map[sub].topics[top].total++;
    if(!map[sub].topics[top].subtopics[stF])map[sub].topics[top].subtopics[stF]={total:0,mcq:0,written:0};
    map[sub].topics[top].subtopics[stF].total++;
    if(isWr)map[sub].topics[top].subtopics[stF].written++;else map[sub].topics[top].subtopics[stF].mcq++;
  }
  return map;
}

/* ══════════ DASHBOARD ══════════ */
function DashboardPage({push,tick}){
  const{data:users,loading:uL}  = useFB("Users",tick);
  const{data:qbank}             = useFB("QBank",tick);
  const{data:study}             = useFB("Study",tick);
  const{data:reports}           = useFB("Reports",tick);
  // Quiz loaded lazily — not blocking
  const[quizData,setQuizData]   = useState(null);
  const[atab,setAtab]           = useState("qbank");

  // load quiz only when analytics tab selected
  useEffect(()=>{
    if(atab==="quiz"&&!quizData){
      loadPath("Quiz").then(d=>setQuizData(d)).catch(()=>{});
    }
  },[atab,quizData]);

  const userArr = useMemo(()=>toArr(users),[users]);
  const total   = userArr.length;
  const active  = useMemo(()=>userArr.filter(u=>(u.Status||u.status||"").toLowerCase()==="active").length,[userArr]);

  const qbankArr   = useMemo(()=>toArr(qbank),[qbank]);
  const qbT        = qbankArr.length;
  const stT        = useMemo(()=>toArr(study).length,[study]);
  const rptT       = useMemo(()=>toArr(reports).length,[reports]);
  const quizT      = useMemo(()=>toArr(quizData).length,[quizData]);

  const qbankMap   = useMemo(()=>buildSubjectMap(qbankArr),[qbankArr]);
  const quizMap    = useMemo(()=>buildSubjectMap(toArr(quizData)),[quizData]);
  const qbankEntries = useMemo(()=>Object.entries(qbankMap),[qbankMap]);
  const quizEntries  = useMemo(()=>Object.entries(quizMap),[quizMap]);

  const days = useMemo(()=>[...Array(7)].map((_,i)=>{
    const d=new Date();d.setDate(d.getDate()-(6-i));
    return{l:`${d.getDate()}/${d.getMonth()+1}`,v:0};
  }),[]);

  return(
    <div className="page">
      <div className="sg">
        <div className="sc tb" data-icon="👥"><div className="sl">স্টুডেন্ট</div><div className="sv sv-b">{fmt(total)}</div></div>
        <div className="sc tg" data-icon="✅"><div className="sl">অ্যাক্টিভ</div><div className="sv sv-g">{fmt(active)}</div></div>
        <div className="sc ty" data-icon="⏳"><div className="sl">পেন্ডিং</div><div className="sv sv-y">{fmt(total-active)}</div></div>
        <div className="sc tr" data-icon="🚨"><div className="sl">রিপোর্ট</div><div className="sv sv-r">{fmt(rptT)}</div></div>
      </div>
      <div className="sg">
        <div className="sc tb" data-icon="❓"><div className="sl">Quiz</div><div className="sv sv-b">{uL?"…":fmt(quizT)}</div></div>
        <div className="sc tg" data-icon="📚"><div className="sl">QBank</div><div className="sv sv-g">{fmt(qbT)}</div></div>
        <div className="sc ty" data-icon="📖"><div className="sl">Study</div><div className="sv sv-y">{fmt(stT)}</div></div>
        <div className="sc tp" data-icon="📊"><div className="sl">মোট</div><div className="sv sv-p">{fmt(quizT+qbT+stT)}</div></div>
      </div>
      <div className="card">
        <div className="ct">📈 Daily Active (৭ দিন)</div>
        <Bar data={days} color={C.accent}/>
      </div>
      <div className="card">
        <div className="ct">📊 Analytics</div>
        <div className="atabs">
          {[["qbank","📚 QBank"],["quiz","❓ Quiz"],["study","📖 Study"]].map(([v,l])=>(
            <button key={v} className={`atab${atab===v?" on":""}`} onClick={()=>setAtab(v)}>{l}</button>
          ))}
        </div>
        {atab==="qbank"&&(qbankEntries.length===0
          ?<div style={{textAlign:"center",color:C.muted,padding:"12px 0",fontSize:12}}>{!qbank?"⏳ লোড হচ্ছে...":"ডেটা নেই"}</div>
          :<Tree entries={qbankEntries} total={qbT} color={C.green}/>
        )}
        {atab==="quiz"&&(quizEntries.length===0
          ?<div style={{textAlign:"center",color:C.muted,padding:"12px 0",fontSize:12}}>{!quizData?"⏳ লোড হচ্ছে...":"ডেটা নেই"}</div>
          :<Tree entries={quizEntries} total={quizT} color={C.accent}/>
        )}
        {atab==="study"&&<div style={{textAlign:"center",color:C.muted,padding:"12px 0",fontSize:12}}>{fmt(stT)}টি নোট</div>}
      </div>
    </div>
  );
}

/* ══════════ SIGNUPS ══════════ */
function SignupsPage({push,tick}){
  const{data:usersRaw,loading}=useFB("Users",tick);
  const[activating,setActivating]=useState(null);
  const[done,setDone]=useState(new Set());
  const[rejectTarget,setRejectTarget]=useState(null); // user object pending reject confirm
  const[rejecting,setRejecting]=useState(false);

  const rows=useMemo(()=>toArr(usersRaw).filter(u=>{
    const st=(u.Status||u.status||"").toLowerCase();
    const id=u._fbKey||(u.Phone||u.phone||"");
    return(st==="inactive"||st===""||st==="pending")&&!done.has(id);
  }),[usersRaw,done]);

  const activate=async u=>{
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setActivating(fkey);
    try{
      await fbPatch(`Users/${fkey}`,{Status:"Active"});
      await fbSet(`Notifications/${fkey}/welcome_${Date.now()}`,{type:"welcome",title:"🎉 অ্যাকাউন্ট অ্যাক্টিভ!",body:"Smart Study-তে স্বাগতম!",time:nowTs(),read:false});
            push("success","✅ অ্যাক্টিভ!",u.Name||u.name||phone);
      setDone(p=>new Set([...p,fkey]));
      invalidate("Users");
    }catch(e){push("error","ব্যর্থ",e.message);}
    setActivating(null);
  };

  const confirmReject=async()=>{
    if(!rejectTarget)return;
    const u=rejectTarget;
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setRejecting(true);
    try{
      await fbDelete(`Users/${fkey}`); // permanently remove — শুধু inactive না, পুরোপুরি delete
      push("success","🗑️ রিজেক্ট হয়েছে",(u.Name||u.name||phone)+" সম্পূর্ণ ডিলিট হয়েছে");
      setDone(p=>new Set([...p,fkey]));
      invalidate("Users");
      setRejectTarget(null);
    }catch(e){push("error","ব্যর্থ",e.message);}
    setRejecting(false);
  };

  return(
    <div className="page">
      <div style={{background:"#ef444412",border:"1px solid #ef444430",borderRadius:10,padding:"8px 12px",fontSize:12,color:C.red,fontWeight:600,marginBottom:11,display:"flex",alignItems:"center",justifyContent:"space-between"}}>
        <span>🔔 {rows.length}টি পেন্ডিং</span>
        {loading&&<span style={{fontSize:10,color:C.muted}}>⏳</span>}
      </div>
      {loading&&!usersRaw?[...Array(3)].map((_,i)=><div key={i} className="sk"/>):
       rows.length===0?<div className="empty"><div className="ei">🎉</div><p>সব অ্যাক্টিভ!</p></div>:
       rows.map((u,i)=>{
        const nm=u.Name||u.name||"অজানা",ph=u.Phone||u.phone||"—";
        const fkey=u._fbKey||phoneKey(ph);
        return(
          <div key={i} className="card" style={{padding:12}}>
            <div style={{display:"flex",alignItems:"center",gap:10,marginBottom:10}}>
              <div className="av">{initials(nm)}</div>
              <div style={{flex:1,minWidth:0}}>
                <div style={{fontWeight:700,fontSize:13}}>{nm}</div>
                <div style={{fontSize:11,color:C.muted}}>📱 {ph}</div>
                {(u.Email||u.email)&&<div style={{fontSize:11,color:C.muted}}>✉️ {u.Email||u.email}</div>}
                <div style={{fontSize:10,color:C.muted}}>🕐 {timeAgo(u.Timestamp||u.createdAt)}</div>
              </div>
              <span className="pill pp">⏳ পেন্ডিং</span>
            </div>
            <div style={{display:"flex",gap:8}}>
              <button className="btn bs bb" style={{flex:2,justifyContent:"center"}} disabled={!!activating||rejecting} onClick={()=>activate(u)}>
                {activating===fkey?"⏳ হচ্ছে...":"✅ অ্যাক্টিভ করুন"}
              </button>
              <button className="btn bg" style={{flex:1,justifyContent:"center",color:C.red,borderColor:`${C.red}40`}} disabled={!!activating||rejecting} onClick={()=>setRejectTarget(u)}>
                ❌ রিজেক্ট
              </button>
            </div>
          </div>
        );
       })
      }
      {rejectTarget&&(
        <DeleteWarningModal
          title="সাইনআপ রিজেক্ট করবেন?"
          description={`"${rejectTarget.Name||rejectTarget.name||rejectTarget.Phone||rejectTarget.phone||"এই ইউজার"}" কে রিজেক্ট করলে ইউজারটি Firebase থেকে সম্পূর্ণভাবে ডিলিট হয়ে যাবে — শুধু ইনঅ্যাক্টিভ হবে না।`}
          onConfirm={confirmReject}
          onCancel={()=>!rejecting&&setRejectTarget(null)}
          loading={rejecting}
        />
      )}
    </div>
  );
}

/* ══════════ STUDENTS (signup tab সহ) ══════════ */
function StudentsPage({push,tick,pushLayer}){
  const{data:usersRaw,loading}=useFB("Users",tick);
  const[search,setSrc]=useState("");
  const[tab,setTab]=useState("active"); // default: running students
  const[detail,setDetail]=useState(null);
  const[notify,setNotify]=useState(null);
  const[editUser,setEditUser]=useState(null);
  const[busy,setBusy]=useState(null);
  const[activating,setActivating]=useState(null);
  const[deleteTarget,setDeleteTarget]=useState(null);
  const[deleting,setDeleting]=useState(false);
  const[signupDone,setSignupDone]=useState(new Set());
  const[rejectTarget,setRejectTarget]=useState(null); // pending signup user to reject/delete
  const[rejecting,setRejecting]=useState(false);

  const users=useMemo(()=>toArr(usersRaw),[usersRaw]);

  /* Signup pending rows */
  const signupRows=useMemo(()=>users.filter(u=>{
    const st=(u.Status||u.status||"").toLowerCase();
    const id=u._fbKey||(u.Phone||u.phone||"");
    return(st==="inactive"||st===""||st==="pending")&&!signupDone.has(id);
  }),[users,signupDone]);

  /* Students filtered rows */
  const filtered=useMemo(()=>{
    if(tab==="signups")return[];
    const q=search.toLowerCase();
    return users.filter(u=>{
      const nm=(u.Name||u.name||"").toLowerCase();
      const ph=(u.Phone||u.phone||"").toLowerCase();
      const st=(u.Status||"").toLowerCase();
      return(!q||nm.includes(q)||ph.includes(q))&&(tab==="all"||st===tab);
    });
  },[users,search,tab]);

  const activate=async u=>{
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setActivating(fkey);
    try{
      await fbPatch(`Users/${fkey}`,{Status:"Active"});
      await fbSet(`Notifications/${fkey}/welcome_${Date.now()}`,{type:"welcome",title:"🎉 অ্যাকাউন্ট অ্যাক্টিভ!",body:"Smart Study-তে স্বাগতম!",time:nowTs(),read:false});
            push("success","✅ অ্যাক্টিভ!",u.Name||u.name||phone);
      setSignupDone(p=>new Set([...p,fkey]));
      invalidate("Users");
    }catch(e){push("error","ব্যর্থ",e.message);}
    setActivating(null);
  };

  const confirmReject=async()=>{
    if(!rejectTarget)return;
    const u=rejectTarget;
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setRejecting(true);
    try{
      await fbDelete(`Users/${fkey}`); // পুরোপুরি ডিলিট — শুধু inactive মার্ক না
      push("success","🗑️ রিজেক্ট হয়েছে",(u.Name||u.name||phone)+" সম্পূর্ণ ডিলিট হয়েছে");
      setSignupDone(p=>new Set([...p,fkey]));
      invalidate("Users");
      setRejectTarget(null);
    }catch(e){push("error","ব্যর্থ",e.message);}
    setRejecting(false);
  };

  const activateStudent=async u=>{
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setBusy(fkey);
    try{
      await fbPatch(`Users/${fkey}`,{Status:"Active"});
      invalidate("Users");
            push("success","✅ অ্যাক্টিভ!",u.Name||u.name);
    }catch(e){push("error","ব্যর্থ",e.message);}
    setBusy(null);
  };

  const confirmDelete=async()=>{
    if(!deleteTarget)return;
    const u=deleteTarget;
    const phone=u.Phone||u.phone||"";
    const fkey=u._fbKey||phoneKey(phone);
    setDeleting(true);
    try{
      await fbDelete(`Users/${fkey}`);
      push("success","🗑️ ডিলিট হয়েছে",(u.Name||u.name||phone));
      invalidate("Users");
      setDeleteTarget(null);
    }catch(e){push("error","ব্যর্থ",e.message);}
    setDeleting(false);
  };

  // StudentDetail খুললে layer push
  const openDetail=useCallback((u)=>{
    setDetail(u);
    if(pushLayer){ pushLayer(()=>setDetail(null)); }
  },[pushLayer]);
  if(detail)return<StudentDetail user={detail} onBack={()=>setDetail(null)} push={push}/>;

  return(
    <div className="page">
      {/* Main Tabs */}
      <div className="ftabs" style={{marginBottom:10}}>
        <button className={`ftab${tab==="active"?" on":""}`} onClick={()=>setTab("active")}>🟢 Running</button>
        <button className={`ftab${tab==="all"?" on":""}`} onClick={()=>setTab("all")}>👥 সবাই</button>
        <button className={`ftab${tab==="inactive"?" on":""}`} onClick={()=>setTab("inactive")}>🔴 ইনঅ্যাক্টিভ</button>
        <button className={`ftab${tab==="signups"?" on":""}`} onClick={()=>setTab("signups")} style={{position:"relative"}}>
          🆕 সাইনআপ
          {signupRows.length>0&&<span style={{position:"absolute",top:-4,right:-4,background:"#ef4444",color:"#fff",fontSize:9,fontWeight:900,borderRadius:999,minWidth:16,height:16,display:"flex",alignItems:"center",justifyContent:"center",padding:"0 3px"}}>{signupRows.length}</span>}
        </button>
      </div>

      {/* ── Signups Tab ── */}
      {tab==="signups"&&(
        <>
          <div style={{background:"#ef444412",border:"1px solid #ef444430",borderRadius:10,padding:"8px 12px",fontSize:12,color:C.red,fontWeight:600,marginBottom:11,display:"flex",alignItems:"center",justifyContent:"space-between"}}>
            <span>🔔 {signupRows.length}টি পেন্ডিং</span>
            {loading&&<span style={{fontSize:10,color:C.muted}}>⏳</span>}
          </div>
          {loading&&!usersRaw?[...Array(3)].map((_,i)=><div key={i} className="sk"/>):
           signupRows.length===0?<div className="empty"><div className="ei">🎉</div><p>সব অ্যাক্টিভ!</p></div>:
           signupRows.map((u,i)=>{
            const nm=u.Name||u.name||"অজানা",ph=u.Phone||u.phone||"—";
            const fkey=u._fbKey||phoneKey(ph);
            return(
              <div key={i} className="card" style={{padding:12}}>
                <div style={{display:"flex",alignItems:"center",gap:10,marginBottom:10}}>
                  <div className="av">{initials(nm)}</div>
                  <div style={{flex:1,minWidth:0}}>
                    <div style={{fontWeight:700,fontSize:13}}>{nm}</div>
                    <div style={{fontSize:11,color:C.muted}}>📱 {ph}</div>
                    {(u.Email||u.email)&&<div style={{fontSize:11,color:C.muted}}>✉️ {u.Email||u.email}</div>}
                    <div style={{fontSize:10,color:C.muted}}>🕐 {timeAgo(u.Timestamp||u.createdAt)}</div>
                  </div>
                  <span className="pill pp">⏳ পেন্ডিং</span>
                </div>
                <div style={{display:"flex",gap:8}}>
                  <button className="btn bs bb" style={{flex:2,justifyContent:"center"}} disabled={!!activating||rejecting} onClick={()=>activate(u)}>
                    {activating===fkey?"⏳ হচ্ছে...":"✅ অ্যাক্টিভ করুন"}
                  </button>
                  <button className="btn bg" style={{flex:1,justifyContent:"center",color:C.red,borderColor:`${C.red}40`}} disabled={!!activating||rejecting} onClick={()=>setRejectTarget(u)}>
                    ❌ রিজেক্ট
                  </button>
                </div>
              </div>
            );
           })
          }
          {rejectTarget&&(
            <DeleteWarningModal
              title="সাইনআপ রিজেক্ট করবেন?"
              description={`"${rejectTarget.Name||rejectTarget.name||rejectTarget.Phone||rejectTarget.phone||"এই ইউজার"}" কে রিজেক্ট করলে ইউজারটি Firebase থেকে সম্পূর্ণভাবে ডিলিট হয়ে যাবে — শুধু ইনঅ্যাক্টিভ হবে না।`}
              onConfirm={confirmReject}
              onCancel={()=>!rejecting&&setRejectTarget(null)}
              loading={rejecting}
            />
          )}
        </>
      )}

      {/* ── Students Tabs ── */}
      {tab!=="signups"&&(
        <>
          <div className="sw"><span className="si">🔍</span><input className="inp" placeholder="নাম বা ফোন..." value={search} onChange={e=>setSrc(e.target.value)}/></div>
          <div style={{fontSize:11,color:C.muted,marginBottom:8}}>{filtered.length} জন</div>
          {loading&&!usersRaw?[...Array(4)].map((_,i)=><div key={i} className="sk"/>):
           filtered.length===0?<div className="empty"><div className="ei">👤</div><p>কেউ নেই</p></div>:
           filtered.map((u,i)=>{
            const nm=u.Name||u.name||"অজানা",ph=u.Phone||u.phone||"—";
            const st=(u.Status||"inactive").toLowerCase();
            const fkey=u._fbKey||phoneKey(ph);
            const c=parseInt(u.totalCorrect)||0,w=parseInt(u.totalWrong)||0,tot=c+w;
            const acc=tot?Math.round(c/tot*100):0;
            const mins=parseInt(u.totalMinutes||u.studyMinutes||u.totalTime||0);
            return(
              <div key={fkey||i} className="card" style={{padding:11}}>
                <div style={{cursor:"pointer",display:"flex",alignItems:"center",gap:9,marginBottom:8}} onClick={()=>openDetail(u)}>
                  <div className="av">{initials(nm)}</div>
                  <div style={{flex:1,minWidth:0}}>
                    <div style={{fontWeight:700,fontSize:13}}>{nm}</div>
                    <div style={{fontSize:10,color:C.muted}}>📱 {ph}</div>
                  </div>
                  <div style={{textAlign:"right"}}>
                    <span className={`pill ${st==="active"?"pa":"pi"}`}>{st==="active"?"✅":"🔴"} {st==="active"?"অ্যাক্টিভ":"ইনঅ্যাক্টিভ"}</span>
                    {tot>0&&<div style={{fontSize:9,color:acc>=70?C.green:acc>=40?C.yellow:C.red,marginTop:2,fontWeight:700}}>{acc}%</div>}
                  </div>
                </div>
            <div style={{display:"flex",gap:6,marginBottom:8}}>
              {[[C.green,c,"✅"],[C.red,w,"❌"],[C.accent,mins,"⏱"]].map(([cl,val,ic])=>(
                <div key={ic} style={{textAlign:"center",flex:1,background:C.panel,borderRadius:7,padding:"5px 2px"}}>
                  <div style={{color:cl,fontWeight:700,fontSize:13}}>{val}</div>
                  <div style={{color:C.muted,fontSize:9}}>{ic}</div>
                </div>
              ))}
            </div>
            <div style={{display:"flex",gap:6}}>
              {st!=="active"&&<button className="btn bs" style={{flex:1,justifyContent:"center",fontSize:11}} disabled={!!busy} onClick={()=>activateStudent(u)}>{busy===fkey?"⏳":"✅ অ্যাক্টিভ"}</button>}
              <button className="btn bg" style={{flex:1,justifyContent:"center",fontSize:11}} onClick={()=>setNotify(u)}>📣</button>
              <button className="btn" style={{flex:1,justifyContent:"center",fontSize:11,background:"#f59e0b22",color:C.yellow,border:"1px solid #f59e0b44"}} onClick={()=>setEditUser(u)}>✏️</button>
              <button className="btn bp" style={{flex:1,justifyContent:"center",fontSize:11}} onClick={()=>openDetail(u)}>👁</button>
              <button className="btn" style={{flex:1,justifyContent:"center",fontSize:11,background:"#ef444422",color:C.red,border:`1px solid ${C.red}44`}} onClick={()=>setDeleteTarget(u)}>🗑️</button>
            </div>
          </div>
        );
       })
      }
        </>
      )}
      {notify&&<NotifyModal user={notify} onClose={()=>setNotify(null)} push={push}/>}
      {deleteTarget&&(
        <DeleteWarningModal
          title="Student ডিলিট করবেন?"
          description={`"${deleteTarget.Name||deleteTarget.name||deleteTarget.Phone||deleteTarget.phone||"এই student"}" কে Firebase থেকে সম্পূর্ণভাবে ডিলিট করা হবে। এটি পূর্বাবস্থায় ফেরানো যাবে না।`}
          onConfirm={confirmDelete}
          onCancel={()=>!deleting&&setDeleteTarget(null)}
          loading={deleting}
        />
      )}
      {editUser&&<UserEditModal user={editUser} onClose={()=>setEditUser(null)} onSaved={updated=>{setEditUser(null);invalidate("Users");}} push={push}/>}
    </div>
  );
}

/* ══════════ CHANGE PASSWORD MODAL ══════════ */
function ChangePasswordModal({user,onClose,push}){
  useModalBack(onClose);
  const nm=user.Name||user.name||"ইউজার";
  const ph=(user.Phone||user.phone||"").replace(/^'+/,"");
  const phK=phoneKey(ph);

  const[newPass,setNewPass]=useState("");
  const[confirmPass,setConfirmPass]=useState("");
  const[showNew,setShowNew]=useState(false);
  const[showConfirm,setShowConfirm]=useState(false);
  const[saving,setSaving]=useState(false);

  const save=async()=>{
    if(!newPass.trim()){push("error","পাসওয়ার্ড দিন","");return;}
    if(newPass.length<6){push("error","পাসওয়ার্ড কমপক্ষে ৬ অক্ষর","");return;}
    if(newPass!==confirmPass){push("error","পাসওয়ার্ড মিলছে না","আবার চেষ্টা করুন");return;}
    setSaving(true);
    try{
      // 1. Firebase এ password update
      await fbPatch(`Users/${phK}`,{Password:newPass});

      // 2. Firebase notification + FCM direct
      const notifTitle="🔐 পাসওয়ার্ড পরিবর্তন";
      const notifBody=`আপনার অ্যাকাউন্টের পাসওয়ার্ড অ্যাডমিন কর্তৃক পরিবর্তন করা হয়েছে। নতুন পাসওয়ার্ড দিয়ে লগইন করুন।`;
      await fbSet(`Notifications/${phK}/notif_${Date.now()}`,{type:"security",title:notifTitle,body:notifBody,time:nowTs(),read:false});
      const fcmOkPw = await fcmNotifyPhone(ph, notifTitle, notifBody, {type:"security"});

      push("success","✅ পাসওয়ার্ড পরিবর্তন হয়েছে!",`${nm}-কে নোটিফিকেশন ${fcmOkPw?"📲 FCM ✓":"📲 FCM ✗"}`);
      onClose();
    }catch(e){push("error","ব্যর্থ হয়েছে",e.message||String(e));}
    setSaving(false);
  };

  const I={background:C.panel,border:`1px solid ${C.border}`,borderRadius:8,padding:"8px 10px",color:C.text,fontSize:13,flex:1,minWidth:0};
  const F={display:"flex",flexDirection:"column",gap:3,marginBottom:12};
  const L={fontSize:11,color:C.muted,fontWeight:600,marginBottom:3};

  return(
    <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.75)",zIndex:950,display:"flex",flexDirection:"column",justifyContent:"flex-end"}}>
      <div style={{background:C.card,borderRadius:"16px 16px 0 0",padding:"16px 14px 34px"}}>
        <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:6}}>
          <div style={{fontWeight:700,fontSize:15}}>🔐 পাসওয়ার্ড পরিবর্তন</div>
          <button onClick={onClose} style={{background:"none",border:"none",color:C.muted,fontSize:20,cursor:"pointer"}}>✕</button>
        </div>
        <div style={{fontSize:12,color:C.muted,marginBottom:14}}>👤 {nm} · 📱 {ph}</div>

        <div style={F}>
          <label style={L}>🔑 নতুন পাসওয়ার্ড</label>
          <div style={{display:"flex",gap:6,alignItems:"center"}}>
            <input style={I} type={showNew?"text":"password"} value={newPass} onChange={e=>setNewPass(e.target.value)} placeholder="কমপক্ষে ৬ অক্ষর"/>
            <button onClick={()=>setShowNew(v=>!v)} style={{background:"none",border:"none",color:C.muted,fontSize:18,cursor:"pointer",padding:"0 4px"}}>{showNew?"🙈":"👁"}</button>
          </div>
        </div>

        <div style={F}>
          <label style={L}>🔑 পাসওয়ার্ড নিশ্চিত করুন</label>
          <div style={{display:"flex",gap:6,alignItems:"center"}}>
            <input style={I} type={showConfirm?"text":"password"} value={confirmPass} onChange={e=>setConfirmPass(e.target.value)} placeholder="আবার লিখুন"/>
            <button onClick={()=>setShowConfirm(v=>!v)} style={{background:"none",border:"none",color:C.muted,fontSize:18,cursor:"pointer",padding:"0 4px"}}>{showConfirm?"🙈":"👁"}</button>
          </div>
          {confirmPass&&newPass!==confirmPass&&<div style={{fontSize:11,color:C.red,marginTop:3}}>⚠️ পাসওয়ার্ড মিলছে না</div>}
          {confirmPass&&newPass===confirmPass&&newPass.length>=6&&<div style={{fontSize:11,color:C.green,marginTop:3}}>✅ মিলেছে</div>}
        </div>

        <div style={{background:C.accent+"11",border:`1px solid ${C.accent}33`,borderRadius:8,padding:"8px 10px",marginBottom:14,fontSize:11,color:C.muted}}>
          📲 পাসওয়ার্ড পরিবর্তনের পর ইউজার <b style={{color:C.text}}>স্বয়ংক্রিয়ভাবে নোটিফিকেশন</b> পাবেন।
        </div>

        <div style={{display:"flex",gap:8}}>
          <button className="btn" style={{flex:1,justifyContent:"center",background:C.border,color:C.muted,padding:"10px 0",borderRadius:9,fontWeight:600}} onClick={onClose}>বাতিল</button>
          <button className="btn bg" style={{flex:2,justifyContent:"center",padding:"10px 0",borderRadius:9,fontWeight:700,fontSize:14,background:saving?"#444":undefined}} disabled={saving||newPass!==confirmPass||newPass.length<6} onClick={save}>
            {saving?"⏳ পরিবর্তন হচ্ছে...":"🔐 পাসওয়ার্ড সেট করুন"}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ══════════ USER EDIT MODAL ══════════ */
function UserEditModal({user,onClose,onSaved,push}){
  useModalBack(onClose);
  const ph=(user.Phone||user.phone||"").replace(/^'+/,"");
  const fkey=user._fbKey||phoneKey(ph);
  const[changePwOpen,setChangePwOpen]=useState(false);

  const[name,setName]=useState(user.Name||user.name||"");
  const[email,setEmail]=useState(user.Email||user.email||"");
  const[status,setStatus]=useState(user.Status||user.status||"Active");
  const[role,setRole]=useState(user.Role||user.role||"User");
  // মূল অ্যাপ (User.kt → fromFirebaseMap) ঠিক এই অর্ডারেই ফিল্ড পড়ে: UserType→userType→Type→type, ClassLevel→classLevel→Class→class
  const[classLevel,setClassLevel]=useState(user.ClassLevel||user.classLevel||user.Class||user.class||"");
  const[userType,setUserType]=useState(user.UserType||user.userType||user.Type||user.type||"Student");
  const[saving,setSaving]=useState(false);

  // মূল অ্যাপের AuthScreen.kt / ProfilePage.kt তে ব্যবহৃত আসল ভ্যালুগুলোর সাথে হুবহু মিল রাখা হয়েছে
  const CLASS_LEVELS=["Class 1","Class 2","Class 3","Class 4","Class 5","Class 6","Class 7","Class 8","Class 9","Class 10","Class 11","Class 12","Honours 1","Honours 2","Honours 3","Honours 4","Masters 1","Masters 2","Masters Final"];
  const TYPES=[{v:"Student",l:"Student (শিক্ষার্থী)"},{v:"Job",l:"Job (চাকরিজীবী)"}];
  const ROLES=["User","Admin"];
  const STATUSES=["Active","Inactive","Pending","Banned"];

  const save=async()=>{
    if(!name.trim()){push("error","নাম দিন","");return;}
    setSaving(true);
    try{
      // "UserType"/"ClassLevel" = ইউজার Student/Job কিনা ও কোন শ্রেণি (মূল অ্যাপ এই ফিল্ড থেকেই "ধরন"/"শ্রেণি" দেখায়)
      // "Role" = অ্যাডমিন পারমিশন (User/Admin) — সম্পূর্ণ আলাদা ফিল্ড, আগে "type:role" লিখে এটাকেই ওভাররাইট করা হতো
      const patch={
        Name:name.trim(),
        Email:email.trim(),
        Status:status,
        Role:role,
        UserType:userType,
        userType:userType,
        ClassLevel:userType==="Job"?"":classLevel,
        classLevel:userType==="Job"?"":classLevel,
      };
      await fbPatch(`Users/${fkey}`,patch);
      invalidate("Users");
      push("success","✅ সেভ হয়েছে!",name.trim());
      onSaved({...user,...patch,_fbKey:fkey});
    }catch(e){push("error","সেভ ব্যর্থ",e.message||String(e));}
    setSaving(false);
  };

  const F={display:"flex",flexDirection:"column",gap:3,marginBottom:12};
  const L={fontSize:11,color:C.muted,fontWeight:600,marginBottom:3};
  const I={background:C.panel,border:`1px solid ${C.border}`,borderRadius:8,padding:"8px 10px",color:C.text,fontSize:13,width:"100%",boxSizing:"border-box"};
  const S={...I,appearance:"none"};

  return(
    <>
    <div style={{position:"fixed",inset:0,background:"rgba(0,0,0,0.7)",zIndex:900,display:"flex",flexDirection:"column",justifyContent:"flex-end"}}>
      <div style={{background:C.card,borderRadius:"16px 16px 0 0",padding:"16px 14px 30px",maxHeight:"90vh",overflowY:"auto"}}>
        <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:14}}>
          <div style={{fontWeight:700,fontSize:15}}>✏️ ইউজার এডিট</div>
          <button onClick={onClose} style={{background:"none",border:"none",color:C.muted,fontSize:20,cursor:"pointer",lineHeight:1}}>✕</button>
        </div>

        <div style={F}>
          <label style={L}>👤 নাম</label>
          <input style={I} value={name} onChange={e=>setName(e.target.value)} placeholder="নাম লিখুন"/>
        </div>

        <div style={F}>
          <label style={L}>📱 ফোন (পরিবর্তন করা যাবে না)</label>
          <input style={{...I,opacity:.5,cursor:"not-allowed"}} value={ph} readOnly/>
        </div>

        <div style={F}>
          <label style={L}>✉️ ইমেইল</label>
          <input style={I} value={email} onChange={e=>setEmail(e.target.value)} placeholder="ইমেইল লিখুন"/>
        </div>

        <div style={F}>
          <label style={L}>📊 স্ট্যাটাস</label>
          <select style={S} value={status} onChange={e=>setStatus(e.target.value)}>
            {STATUSES.map(s=><option key={s} value={s}>{s}</option>)}
          </select>
        </div>

        <div style={F}>
          <label style={L}>🎭 রোল</label>
          <select style={S} value={role} onChange={e=>setRole(e.target.value)}>
            {ROLES.map(r=><option key={r} value={r}>{r}</option>)}
          </select>
        </div>

        <div style={F}>
          <label style={L}>🏷️ টাইপ</label>
          <select style={S} value={userType} onChange={e=>{setUserType(e.target.value);if(e.target.value==="Job")setClassLevel("");}}>
            {TYPES.map(t=><option key={t.v} value={t.v}>{t.l}</option>)}
          </select>
        </div>

        {userType!=="Job"&&(
        <div style={F}>
          <label style={L}>📚 ক্লাস লেভেল</label>
          <select style={S} value={classLevel} onChange={e=>setClassLevel(e.target.value)}>
            <option value="">— নির্বাচন করুন —</option>
            {CLASS_LEVELS.map(c=><option key={c} value={c}>{c}</option>)}
          </select>
        </div>
        )}

        <button className="btn" style={{width:"100%",justifyContent:"center",background:C.red+"18",color:C.red,border:`1px solid ${C.red}33`,padding:"9px 0",borderRadius:9,fontWeight:600,marginBottom:8,fontSize:13}} onClick={()=>setChangePwOpen(true)}>
          🔐 পাসওয়ার্ড পরিবর্তন করুন
        </button>

        <div style={{display:"flex",gap:8,marginTop:4}}>
          <button className="btn" style={{flex:1,justifyContent:"center",background:C.border,color:C.muted,padding:"10px 0",borderRadius:9,fontWeight:600}} onClick={onClose}>বাতিল</button>
          <button className="btn bg" style={{flex:2,justifyContent:"center",padding:"10px 0",borderRadius:9,fontWeight:700,fontSize:14}} disabled={saving} onClick={save}>
            {saving?"⏳ সেভ হচ্ছে...":"💾 সেভ করুন"}
          </button>
        </div>
      </div>
    </div>
    {changePwOpen&&<ChangePasswordModal user={user} onClose={()=>setChangePwOpen(false)} push={push}/>}
    </>
  );
}


function StudentDetail({user:userProp,onBack,push}){
  const[user,setUser]=useState(userProp);
  const[editOpen,setEditOpen]=useState(false);
  const[changePwOpen,setChangePwOpen]=useState(false);
  const nm=user.Name||user.name||"অজানা";
  const ph=(user.Phone||user.phone||"").replace(/^'+/,"");
  const st=(user.Status||user.status||"inactive").toLowerCase();
  const phK=phoneKey(ph);
  const{data:timeData}=useFB(`Analytics/Time/${phK}`);
  const{data:subjData}=useFB(`Analytics/Subject/${phK}`);

  const c=parseInt(user.totalCorrect)||0;
  const w=parseInt(user.totalWrong)||0;
  const tot=c+w,acc=tot?Math.round(c/tot*100):0;
  const mins=parseInt(user.totalMinutes||user.studyMinutes||user.totalTime||0);
  const dailyTime=useMemo(()=>timeData&&typeof timeData==="object"
    ?Object.entries(timeData).sort(([a],[b])=>a.localeCompare(b)).slice(-7).map(([d,v])=>({l:d.slice(5),v:parseInt(v)||0})):[]
  ,[timeData]);
  const subjEntries=useMemo(()=>subjData&&typeof subjData==="object"?Object.entries(subjData):[],[subjData]);

  return(
    <div className="fs">
      <div className="fsh">
        <button className="bk" onClick={onBack}>←</button>
        <div className="av">{initials(nm)}</div>
        <div style={{flex:1,minWidth:0}}>
          <div style={{fontWeight:700,fontSize:14,overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}}>{nm}</div>
          <div style={{fontSize:10,color:C.muted}}>📱 {ph}</div>
          {(user.classLevel||user.ClassLevel)&&<div style={{fontSize:10,color:C.accent}}>{user.classLevel||user.ClassLevel}{(user.userType||user.UserType)?` · ${user.userType||user.UserType}`:""}</div>}
          {(user.Role||user.role)&&<div style={{fontSize:10,color:C.yellow}}>🎭 {user.Role||user.role}</div>}
        </div>
        <div style={{display:"flex",flexDirection:"column",alignItems:"flex-end",gap:5}}>
          <span className={`pill ${st==="active"?"pa":"pi"}`}>{st==="active"?"✅":"🔴"}</span>
          <button className="btn bp" style={{fontSize:10,padding:"3px 8px",borderRadius:6,minWidth:0}} onClick={()=>setEditOpen(true)}>✏️ এডিট</button>
        </div>
      </div>
      {editOpen&&<UserEditModal user={user} onClose={()=>setEditOpen(false)} onSaved={updated=>{setUser(updated);setEditOpen(false);}} push={push}/>}
      <div style={{padding:"12px 12px 70px"}}>
        <div className="card">
          <div style={{display:"flex",alignItems:"center",gap:12}}>
            <Ring val={c} max={tot} color={acc>=70?C.green:acc>=40?C.yellow:C.red}/>
            <div style={{flex:1}}>
              <div style={{fontSize:18,fontWeight:700,color:acc>=70?C.green:acc>=40?C.yellow:C.red,marginBottom:3}}>{acc}% Accuracy</div>
              <div style={{fontSize:11,color:C.muted}}>✅ {c} &nbsp; ❌ {w} &nbsp; 🎯 {tot}</div>
            </div>
          </div>
        </div>
        <div className="sg">
          <div className="sc tp" data-icon="⏱"><div className="sl">মোট সময়</div><div className="sv sv-p" style={{fontSize:18}}>{mins<60?mins+"মি":~~(mins/60)+"ঘণ্টা"}</div></div>
          <div className="sc tb" data-icon="📅"><div className="sl">শেষ সক্রিয়</div><div style={{fontSize:12,fontWeight:700,marginTop:5,color:C.accent}}>{timeAgo(user.lastActive||user.Timestamp)}</div></div>
        </div>
        {dailyTime.length>0&&<div className="card"><div className="ct">⏱ দৈনিক সময়</div><Bar data={dailyTime} color={C.purple}/></div>}
        {subjEntries.length>0&&(
          <div className="card">
            <div className="ct">📚 বিষয়ভিত্তিক</div>
            {subjEntries.map(([sub,sv])=>{
              const sc=sv.correct||0,sw2=sv.wrong||0,st2=sc+sw2,sa=st2?Math.round(sc/st2*100):0;
              return(
                <div key={sub} className="srow">
                  <div style={{flex:1}}>
                    <div style={{fontWeight:600,fontSize:12}}>{sub}</div>
                    <div style={{display:"flex",alignItems:"center",marginTop:2}}><div className="sbar"><div className="sbar-f" style={{width:sa+"%",background:sa>=70?C.green:sa>=40?C.yellow:C.red}}/></div></div>
                    <div style={{fontSize:9,color:C.muted}}>✅{sc} ❌{sw2}</div>
                  </div>
                  <div style={{fontWeight:700,fontSize:14,color:sa>=70?C.green:sa>=40?C.yellow:C.red,minWidth:32,textAlign:"right"}}>{sa}%</div>
                </div>
              );
            })}
          </div>
        )}
        <div className="card" style={{marginBottom:8}}>
          <div className="ct">🔐 পাসওয়ার্ড পরিবর্তন</div>
          <button className="btn" style={{width:"100%",justifyContent:"center",background:C.red+"18",color:C.red,border:`1px solid ${C.red}33`,padding:"9px 0",borderRadius:9,fontWeight:600,fontSize:13}} onClick={()=>setChangePwOpen(true)}>
            🔐 নতুন পাসওয়ার্ড সেট করুন
          </button>
        </div>
        <NotifyModal user={user} onClose={onBack} push={push} inline/>
      </div>
      {changePwOpen&&<ChangePasswordModal user={user} onClose={()=>setChangePwOpen(false)} push={push}/>}
    </div>
  );
}

/* ══════════ REPORTS — hard delete ══════════ */
function ReportsPage({push,tick}){
  const{data:rRaw,loading}=useFB("Reports",tick);
  const[done,setDone]=useState(new Set());
  const[editing,setEditing]=useState(null);
  const reports=useMemo(()=>toArr(rRaw).filter(r=>!done.has(r._fbKey||r.row)).slice(-30).reverse(),[rRaw,done]);
  return(
    <div className="page">
      <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:11}}>
        <div style={{fontSize:11,color:C.muted}}>{reports.length}টি রিপোর্ট</div>
        {loading&&<span style={{fontSize:10,color:C.muted}}>⏳</span>}
      </div>
      {loading&&!rRaw?[...Array(3)].map((_,i)=><div key={i} className="sk"/>):
       reports.length===0?<div className="empty"><div className="ei">📋</div><p>রিপোর্ট নেই! 🎉</p></div>:
       reports.map((r,i)=>{
        const isMCQ=(r.QType||r.qtype||"MCQ").toLowerCase()!=="written";
        const qid2=r.QuestionID||r.questionId;
        return(
         <div key={r._fbKey||i} className="rc" style={{borderLeft:`3px solid ${isMCQ?C.accent:C.purple}`}}>
           <div style={{display:"flex",alignItems:"center",gap:7,marginBottom:6}}>
             <span style={{background:isMCQ?`${C.accent}22`:`${C.purple}22`,color:isMCQ?C.accent:C.purple,border:`1px solid ${isMCQ?C.accent:C.purple}44`,borderRadius:6,padding:"2px 8px",fontSize:10,fontWeight:700}}>{isMCQ?"❓ MCQ":"✍️ Written"}</span>
             <div style={{fontWeight:700,fontSize:13,flex:1}}>{r.Subject||r.subject||"অজানা"}</div>
           </div>
           <div className="rm">
             <span>📱 {(r.Phone||r.phone||"—").toString().replace(/^'+/,"")}</span>
             {(r.SubTopic||r.subtopic)&&<span>📌 {r.SubTopic||r.subtopic}</span>}
             {qid2&&<span style={{color:C.accent}}>#{qid2}</span>}
           </div>
           <div className="ri">{r.Issue||r.issue||"বিস্তারিত নেই"}</div>
           {(r.Question||r.question)&&<div style={{fontSize:11,color:C.muted,marginTop:4,fontStyle:"italic",borderLeft:`2px solid ${C.border}`,paddingLeft:6}}>প্রশ্ন: {(r.Question||r.question).toString().slice(0,80)}{(r.Question||r.question).length>80?"...":""}</div>}
           <button className="btn bp bb" style={{marginTop:8,background:isMCQ?C.accent:C.purple}} onClick={()=>setEditing(r)}>✏️ এডিট ও সমাধান</button>
         </div>
        );
       })
      }
      {editing&&<ReportEditModal report={editing} onClose={()=>setEditing(null)} onDone={key=>{setDone(p=>new Set([...p,key]));setEditing(null);invalidate("Reports");}} push={push}/>}
    </div>
  );
}

function ReportEditModal({report,onClose,onDone,push}){
  useModalBack(onClose);
  const[step,setStep]=useState(1);
  const[qdata,setQdata]=useState(null);
  const[loadQ,setLoadQ]=useState(true);
  const[saving,setSaving]=useState(false);
  const[notifying,setNotifying]=useState(false);
  const[question,setQuestion]=useState("");
  const[opt1,setOpt1]=useState(""); const[opt2,setOpt2]=useState("");
  const[opt3,setOpt3]=useState(""); const[opt4,setOpt4]=useState("");
  const[correct,setCorrect]=useState("");
  const[explanation,setExplanation]=useState("");
  const[technique,setTechnique]=useState("");
  const[qtype,setQtype]=useState("mcq");
  // Report এ কোন fields আছে সেটা normalize করো
  const qfbKey  = (report.QuestionFBKey||report.questionFBKey||report.fbKey||"").toString().trim();
  const qsheet  = (report.QSheet||report.qsheet||"").toString().trim();
  const qidRaw  = (report.QuestionID||report.questionId||"").toString().trim();
  // "0", "null", "" — এগুলো invalid ID
  const qid     = (qidRaw===""||qidRaw==="0"||qidRaw==="null"||qidRaw==="undefined") ? "" : qidRaw;
  // Report এর নিজস্ব question text — সবচেয়ে reliable fallback
  const reportQText = (report.Question||report.question||"").trim();

  useEffect(()=>{
    // কোনো identifier নেই — সরাসরি report এর text দিয়ে যাও
    if(!qid && !qfbKey && !reportQText){ setLoadQ(false); return; }

    let cancelled=false;
    (async()=>{
      setLoadQ(true);
      let found=false;

      // ── Firebase থেকে match করার চেষ্টা ──
      if(qid || qfbKey){
        const sheetsToTry=qsheet?[qsheet]:["QBank","Quiz","Study"];
        for(const t of sheetsToTry){
          if(found) break;
          try{
            const raw=await loadPath(t);
            const arr=toArr(raw);
            const qNorm=qid.replace(/^0+/,"");
            const q=arr.find(x=>{
              // Priority 1: Firebase key exact match
              if(qfbKey && x._fbKey && x._fbKey===qfbKey) return true;
              // Priority 2: Question text exact match — সবচেয়ে reliable
              if(reportQText){
                const xq=(x.Question||x.question||"").trim();
                if(xq && xq===reportQText) return true;
              }
              // Priority 3: ID field match
              if(qNorm){
                const xid=(x.ID||x.id||"").toString().replace(/^0+/,"");
                if(xid && xid===qNorm) return true;
              }
              return false;
            });
            if(q&&!cancelled){
              found=true;
              setQdata({...q,_tab:t});
              setQuestion(q.Question||q.question||"");
              setOpt1(q.Opt1||q.opt1||q.Option1||q.option1||"");
              setOpt2(q.Opt2||q.opt2||q.Option2||q.option2||"");
              setOpt3(q.Opt3||q.opt3||q.Option3||q.option3||"");
              setOpt4(q.Opt4||q.opt4||q.Option4||q.option4||"");
              setCorrect(q.Correct||q.correct||"");
              setExplanation(q.Explanation||q.explanation||"");
              setTechnique(q.Technique||q.technique||"");
              const qt=(q.QType||q.qtype||"MCQ").toLowerCase();
              setQtype(t==="Study"?"study":qt==="written"?"written":"mcq");
            }
          }catch(_){}
        }
      }

      // ── Firebase match না পেলে — Question text দিয়ে সব sheet search ──
      if(!found && reportQText && !cancelled){
        const sheetsAll=["QBank","Quiz","Study"];
        for(const t of sheetsAll){
          if(found) break;
          try{
            const raw=await loadPath(t);
            const arr=toArr(raw);
            const q=arr.find(x=>{
              const xq=(x.Question||x.question||"").trim();
              return xq && xq===reportQText;
            });
            if(q&&!cancelled){
              found=true;
              setQdata({...q,_tab:t});
              setQuestion(q.Question||q.question||"");
              setOpt1(q.Opt1||q.opt1||q.Option1||q.option1||"");
              setOpt2(q.Opt2||q.opt2||q.Option2||q.option2||"");
              setOpt3(q.Opt3||q.opt3||q.Option3||q.option3||"");
              setOpt4(q.Opt4||q.opt4||q.Option4||q.option4||"");
              setCorrect(q.Correct||q.correct||"");
              setExplanation(q.Explanation||q.explanation||"");
              setTechnique(q.Technique||q.technique||"");
              const qt=(q.QType||q.qtype||"MCQ").toLowerCase();
              setQtype(t==="Study"?"study":qt==="written"?"written":"mcq");
            }
          }catch(_){}
        }
      }

      // ── শেষ fallback — report এর নিজের data দিয়ে fill করো ──
      if(!found && reportQText && !cancelled){
        setQuestion(reportQText);
        setOpt1(report.Opt1||report.opt1||report.Option1||report.option1||"");
        setOpt2(report.Opt2||report.opt2||report.Option2||report.option2||"");
        setOpt3(report.Opt3||report.opt3||report.Option3||report.option3||"");
        setOpt4(report.Opt4||report.opt4||report.Option4||report.option4||"");
        setCorrect(report.Correct||report.correct||"");
        setExplanation(report.Explanation||report.explanation||"");
        const qt=(report.QType||report.qtype||"MCQ").toLowerCase();
        setQtype(qt==="written"?"written":"mcq");
      }

      if(!cancelled) setLoadQ(false);
    })();
    return()=>{cancelled=true;};
  },[qid,qfbKey,qsheet,reportQText]);

  const save=async()=>{
    setSaving(true);
    try{
      if(qdata&&qid){
        const t=qdata._tab||"QBank";
        const fkey=qdata._fbKey;
        let patch={};
        if(qtype==="mcq"){
          const o1k=qdata.Opt1!=null?"Opt1":qdata.opt1!=null?"opt1":"Option1";
          const o2k=o1k.replace(/1$/,"2");const o3k=o1k.replace(/1$/,"3");const o4k=o1k.replace(/1$/,"4");
          patch={Question:question,[o1k]:opt1,[o2k]:opt2,[o3k]:opt3,[o4k]:opt4,Correct:correct,Explanation:explanation,Technique:technique};
        } else {
          patch={Question:question,Explanation:explanation,Technique:technique};
          if(qtype==="study")patch.Correct=correct;
        }
        if(fkey)await fbPatch(`${t}/${fkey}`,patch);
        invalidate(t);
        // Sheet sync → GAS standalone handles this
      }
      push("success","✅ সেভ হয়েছে!","");
      setStep(2);
    }catch(e){push("error","Save ব্যর্থ",String(e?.message||e||""));}
    setSaving(false);
  };

  const doNotifyAndDelete=async()=>{
    setNotifying(true);
    try{
      const phone=(report.Phone||report.phone||"").toString().replace(/^'+/,"").trim();
      const subject=(report.Subject||report.subject||"প্রশ্নটি").toString();
      const phK=phoneKey(phone);
      const notifTitle="✅ রিপোর্ট সমাধান হয়েছে!";
      const notifBody=`"${subject}" সংশোধন হয়েছে।`;

      // reporter এর নাম খুঁজে নাও (Users থেকে), notification body তে দেখানোর জন্য
      let reporterName="";
      try{
        const usersRaw=await loadPath("Users");
        const u=toArr(usersRaw).find(x=>(x.Phone||x.phone||"").toString().replace(/^'+/,"").trim()===phone);
        reporterName=(u?.Name||u?.name||"").toString();
      }catch(_){}
      const finalBody=reporterName?`"${subject}" সংশোধন হয়েছে। (${reporterName}-এর রিপোর্ট)`:notifBody;

      await fbSet(`Notifications/${phK}/notif_${Date.now()}`,{type:"report_resolved",title:notifTitle,body:finalBody,questionId:qid,qsheet,time:nowTs(),read:false});
      // FCM direct — instant
      fcmNotifyPhone(phone, notifTitle, finalBody, {type:"report_resolved", questionId:qid}).catch(()=>{});

      // Hard delete: Firebase
      const reportKey=report._fbKey||report.row;
      if(reportKey){
        await fbDelete(`Reports/${reportKey}`);
        invalidate("Reports");
      }

      push("success","✅ নোটিফাই ও ডিলিট!","Report মুছে গেছে");
      onDone(reportKey);
    }catch(e){push("error","ব্যর্থ",String(e?.message||e||""));}
    setNotifying(false);
  };

  const ac=qtype==="mcq"?C.accent:qtype==="study"?C.green:C.purple;

  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div className="steps">
          <div className={`step${step===1?" act":step>1?" done":""}`}>① এডিট</div>
          <div className={`step${step===2?" act":""}`}>② নোটিফাই ও ডিলিট</div>
        </div>
        {step===1&&<>
          <div style={{background:"#ef444412",border:"1px solid #ef444430",borderRadius:9,padding:"7px 10px",marginBottom:10}}>
            <div style={{fontSize:10,color:C.red,fontWeight:700,marginBottom:2}}>🚨 {(report.Phone||report.phone||"").toString().replace(/^'+/,"")} · {report.Subject||report.subject}</div>
            <div style={{fontSize:11,color:C.text}}>{report.Issue||report.issue||"—"}</div>
          </div>
          {loadQ&&<><div className="sk" style={{height:52,marginBottom:8}}/><div className="sk" style={{height:36}}/></>}
          {!loadQ&&!qdata&&question&&<div style={{background:`${C.yellow}11`,border:`1px solid ${C.yellow}44`,borderRadius:8,padding:"10px 12px",marginBottom:8,fontSize:11,color:C.yellow}}>⚠️ Firebase এ fbKey match হয়নি — question text দিয়ে fill করা হয়েছে। Save করলে Firebase আপডেট হবে না।</div>}
          {!loadQ&&!qdata&&!question&&<div style={{textAlign:"center",color:C.muted,padding:"18px 0",fontSize:12}}>প্রশ্ন পাওয়া যায়নি।</div>}
          {!loadQ&&qdata&&qtype==="mcq"&&<>
            <div className="fld"><label>❓ প্রশ্ন</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:60}}/></div>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:7,marginBottom:10}}>
              {[[opt1,setOpt1,"A"],[opt2,setOpt2,"B"],[opt3,setOpt3,"C"],[opt4,setOpt4,"D"]].map(([v,sv,lbl])=>(
                <div key={lbl} className="fld" style={{margin:0}}><label>{lbl}</label><input className="inp" value={v} onChange={e=>sv(e.target.value)}/></div>
              ))}
            </div>
            <div className="fld">
              <label>✅ সঠিক উত্তর</label>
              <div style={{display:"flex",gap:4,flexWrap:"wrap",marginBottom:5}}>
                {[opt1,opt2,opt3,opt4].filter(Boolean).map((o,i)=>(
                  <button key={i} type="button" className={`cc${correct===o?" on":""}`} onClick={()=>setCorrect(o)}>{o.slice(0,16)}</button>
                ))}
              </div>
              <input className="inp" value={correct} onChange={e=>setCorrect(e.target.value)}/>
            </div>
            <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:60}}/></div>
            <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:45}}/></div>
          </>}
          {!loadQ&&qdata&&qtype!=="mcq"&&<>
            <div className="fld"><label>প্রশ্ন</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:75}}/></div>
            {qtype==="study"&&<div className="fld"><label>✅ উত্তর</label><textarea className="ta" value={correct} onChange={e=>setCorrect(e.target.value)} style={{minHeight:60}}/></div>}
            <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:60}}/></div>
            <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:45}}/></div>
          </>}
          <div style={{display:"flex",gap:6,marginTop:4}}>
            <button type="button" className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onClose}>বাতিল</button>
            <button type="button" className="btn bp" style={{flex:2,justifyContent:"center",background:ac}} disabled={saving||loadQ||!qdata} onClick={save}>{saving?"⏳ সেভ হচ্ছে...":"💾 সেভ করুন →"}</button>
          </div>
        </>}
        {step===2&&<>
          <div className="mt">📣 নোটিফাই ও রিপোর্ট মুছুন</div>
          <div style={{background:"#22c55e12",border:"1px solid #22c55e30",borderRadius:10,padding:"11px",marginBottom:12}}>
            <div style={{fontSize:13,fontWeight:700,color:C.green,marginBottom:4}}>✅ প্রশ্ন আপডেট সম্পন্ন!</div>
          </div>
          <div style={{background:"#ef444412",border:"1px solid #ef444330",borderRadius:9,padding:"8px 11px",marginBottom:14,fontSize:11,color:C.red}}>
            ⚠️ Firebase ও Google Sheet থেকে স্থায়ীভাবে মুছে যাবে।
          </div>
          <div style={{display:"flex",gap:6}}>
            <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={()=>onDone(report._fbKey||report.row)}>এড়িয়ে যান</button>
            <button className="btn" style={{flex:2,justifyContent:"center",background:C.green,color:"#fff"}} disabled={notifying} onClick={doNotifyAndDelete}>
              {notifying?"⏳...":"✅ নোটিফাই ও ডিলিট"}
            </button>
          </div>
        </>}
      </div>
    </div>
  );
}

/* ══════════ ENTRY ══════════ */
function EntryPage({push}){
  const[mode,setMode]=useState("QBank");
  const[qtype,setQtype]=useState("MCQ");
  const[saving,setSaving]=useState(false);
  const[uploading,setUp]=useState(false);
  const[question,setQuestion]=useState("");
  const[opt1,setOpt1]=useState("");const[opt2,setOpt2]=useState("");
  const[opt3,setOpt3]=useState("");const[opt4,setOpt4]=useState("");
  const[correct,setCorrect]=useState("");
  const[subject,setSubject]=useState("");
  const[topic,setTopic]=useState("");
  const[subtopic,setSubtopic]=useState("");
  const[explanation,setExplanation]=useState("");
  const[technique,setTechnique]=useState("");
  const[imgUrl,setImgUrl]=useState("");
  const[solImgUrls,setSolImgUrls]=useState([]); // solution/explanation image crops
  const[qImgUrls,setQImgUrls]=useState([]);     // question image crops
  const[showCropper,setShowCropper]=useState(false);
  const[subjects,setSubjects]=useState([]);

  useEffect(()=>{
    loadPath(mode).then(raw=>{
      const arr=toArr(raw);
      setSubjects([...new Set(arr.map(q=>q.Subject||q.subject||"").filter(Boolean))]);
    }).catch(()=>{});
  },[mode]);

  const reset=()=>{setQuestion("");setOpt1("");setOpt2("");setOpt3("");setOpt4("");setCorrect("");setExplanation("");setTechnique("");setImgUrl("");setQImgUrls([]);setSolImgUrls([]);};
  const handleImg=async e=>{
    const f=e.target.files[0];if(!f)return;
    setUp(true);
    try{const u=await uploadImg(f);setImgUrl(u);push("success","ছবি আপলোড হয়েছে","");}
    catch{push("error","আপলোড ব্যর্থ","");}
    setUp(false);
  };
  const submit=async()=>{
    if(!question.trim()&&qImgUrls.length===0){push("warn","প্রশ্ন লিখুন বা ছবি দিন","");return;}
    if(!subject.trim()){push("warn","বিষয় লিখুন","");return;}
    setSaving(true);
    try{
      const ts=nowTs(),id=Date.now();
      // combine: single upload imgUrl + crop question imgs
      const allQImgs=[...qImgUrls,...(imgUrl?[imgUrl]:[])];
      const finalImg=allQImgs.join(","); // multiple imgs comma separated
      const finalExpl=explanation+(solImgUrls.length?"\n"+solImgUrls.join("\n"):"");
      let rec={};
      if(mode==="Quiz")rec={ID:id,Question:question,Opt1:opt1,Opt2:opt2,Opt3:opt3,Opt4:opt4,Correct:correct,Subject:subject,Sub_topic:subtopic,Explanation:finalExpl,Technique:technique,QType:qtype,Timestamp:ts,Image:finalImg};
      else if(mode==="QBank")rec={ID:id,Question:question,Opt1:opt1,Opt2:opt2,Opt3:opt3,Opt4:opt4,Correct:correct,Subject:subject,Topic:topic,Sub_topic:subtopic,Explanation:finalExpl,Technique:technique,QType:qtype,Timestamp:ts,Image:finalImg};
      else rec={ID:id,Question:question,Correct:correct,Subject:subject,Sub_topic:subtopic,Explanation:finalExpl,Technique:technique,"Question Type":"Study",Timestamp:ts,Image:finalImg};
      await fbPush(mode,rec);
      invalidate(mode);
      push("success","✅ সেভ হয়েছে!",`${mode} #${id}`);
      reset();
    }catch(e){push("error","ব্যর্থ",e.message);}
    setSaving(false);
  };
  return(
    <div className="page">
      <div className="ftabs">{["QBank","Quiz","Study"].map(m=><button key={m} className={`ftab${mode===m?" on":""}`} onClick={()=>setMode(m)}>{m}</button>)}</div>
      {mode!=="Study"&&<div style={{display:"flex",gap:7,marginBottom:11}}>{["MCQ","Written"].map(t=><button key={t} className={`tp2${qtype===t?" on":""}`} onClick={()=>setQtype(t)}>{t}</button>)}</div>}
      <div className="fld"><label>{mode==="Study"?"📝 প্রশ্ন":"❓ প্রশ্ন"}</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:80}}/></div>
      {mode==="Study"&&<div className="fld"><label>✅ উত্তর</label><textarea className="ta" value={correct} onChange={e=>setCorrect(e.target.value)} style={{minHeight:80}}/></div>}
      {mode!=="Study"&&qtype==="MCQ"&&<>
        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:8}}>
          <div className="fld"><label>A</label><input className="inp" value={opt1} onChange={e=>setOpt1(e.target.value)}/></div>
          <div className="fld"><label>B</label><input className="inp" value={opt2} onChange={e=>setOpt2(e.target.value)}/></div>
          <div className="fld"><label>C</label><input className="inp" value={opt3} onChange={e=>setOpt3(e.target.value)}/></div>
          <div className="fld"><label>D</label><input className="inp" value={opt4} onChange={e=>setOpt4(e.target.value)}/></div>
        </div>
        <div className="fld">
          <label>✅ সঠিক উত্তর</label>
          <div style={{display:"flex",gap:5,flexWrap:"wrap",marginBottom:6}}>
            {[opt1,opt2,opt3,opt4].filter(Boolean).map((o,i)=><button key={i} type="button" className={`cc${correct===o?" on":""}`} onClick={()=>setCorrect(o)}>{o.slice(0,16)}</button>)}
          </div>
          <input className="inp" value={correct} onChange={e=>setCorrect(e.target.value)} placeholder="বা সরাসরি লিখুন..."/>
        </div>
      </>}
      <div className="fld">
        <label>📚 বিষয়</label>
        <input className="inp" list="sl" value={subject} onChange={e=>setSubject(e.target.value)} placeholder="Subject..."/>
        <datalist id="sl">{subjects.map((s,i)=><option key={i} value={s}/>)}</datalist>
      </div>
      {mode==="QBank"&&<div className="fld"><label>📂 Topic</label><input className="inp" value={topic} onChange={e=>setTopic(e.target.value)}/></div>}
      <div className="fld"><label>📌 Sub Topic</label><input className="inp" value={subtopic} onChange={e=>setSubtopic(e.target.value)}/></div>
      <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:80}}/></div>
      <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:60}}/></div>
      <div className="fld">
        <label>🖼 ছবি (প্রশ্ন ও সমাধান)</label>
        {/* crop button - main entry */}
        <button type="button" onClick={()=>setShowCropper(true)}
          style={{width:"100%",display:"flex",alignItems:"center",justifyContent:"center",gap:8,background:C.panel,border:`1.5px dashed ${C.accent}`,borderRadius:10,padding:"11px 12px",cursor:"pointer",fontSize:13,fontWeight:700,color:C.accent,marginBottom:8}}>
          ✂️ বইয়ের পাতা থেকে Crop করুন
        </button>
        {/* old-style single upload still available */}
        <label style={{display:"flex",alignItems:"center",gap:7,background:C.panel,border:`1px solid ${C.border}`,borderRadius:9,padding:"9px 12px",cursor:"pointer",fontSize:12,color:C.muted}}>
          {uploading?"⏳ আপলোড হচ্ছে...":"📷 সরাসরি ছবি আপলোড"}
          <input type="file" accept="image/*" style={{display:"none"}} onChange={handleImg}/>
          {imgUrl&&<a href={imgUrl} target="_blank" rel="noreferrer" style={{fontSize:11,color:C.accent,marginLeft:"auto"}} onClick={e=>e.stopPropagation()}>দেখুন ↗</a>}
        </label>
        {/* show question crops */}
        {qImgUrls.length>0&&(
          <div style={{marginTop:8}}>
            <div style={{fontSize:11,color:C.accent,marginBottom:4,fontWeight:700}}>📌 প্রশ্নের ছবি ({qImgUrls.length}টি)</div>
            <div style={{display:"flex",gap:6,flexWrap:"wrap"}}>
              {qImgUrls.map((u,i)=>(
                <div key={i} style={{position:"relative"}}>
                  <img src={u} style={{height:56,borderRadius:7,border:`1.5px solid ${C.accent}`}} alt=""/>
                  <button onClick={()=>setQImgUrls(p=>p.filter((_,j)=>j!==i))}
                    style={{position:"absolute",top:-5,right:-5,background:C.red,border:"none",borderRadius:"50%",width:16,height:16,fontSize:9,color:"#fff",cursor:"pointer",display:"flex",alignItems:"center",justifyContent:"center"}}>✕</button>
                </div>
              ))}
            </div>
          </div>
        )}
        {/* show solution crops */}
        {solImgUrls.length>0&&(
          <div style={{marginTop:8}}>
            <div style={{fontSize:11,color:C.green,marginBottom:4,fontWeight:700}}>✅ সমাধানের ছবি ({solImgUrls.length}টি)</div>
            <div style={{display:"flex",gap:6,flexWrap:"wrap"}}>
              {solImgUrls.map((u,i)=>(
                <div key={i} style={{position:"relative"}}>
                  <img src={u} style={{height:56,borderRadius:7,border:`1.5px solid ${C.green}`}} alt=""/>
                  <button onClick={()=>setSolImgUrls(p=>p.filter((_,j)=>j!==i))}
                    style={{position:"absolute",top:-5,right:-5,background:C.red,border:"none",borderRadius:"50%",width:16,height:16,fontSize:9,color:"#fff",cursor:"pointer",display:"flex",alignItems:"center",justifyContent:"center"}}>✕</button>
                </div>
              ))}
            </div>
          </div>
        )}
        {imgUrl&&<img src={imgUrl} style={{width:"100%",borderRadius:9,marginTop:6,border:`1px solid ${C.border}`}} alt="p"/>}
      </div>
      {/* Crop modal */}
      {showCropper&&(
        <ImageCropPicker
          push={push}
          onCropToQuestion={url=>setQImgUrls(p=>[...p,url])}
          onCropToSolution={url=>setSolImgUrls(p=>[...p,url])}
          onClose={()=>setShowCropper(false)}
        />
      )}
      <button className="btn bp bb" style={{marginTop:4}} disabled={saving} onClick={submit}>{saving?"⏳ সেভ হচ্ছে...":"💾 সেভ করুন"}</button>
    </div>
  );
}

/* ══════════ AI IMPORT PAGE (ML Kit OCR) ══════════ */
function AIImportPage({push,onSendToBulk}){
  const[images,setImages]=useState([]);   // [{uri,base64,status,ocrText}]
  const[ocrAll,setOcrAll]=useState("");
  const[running,setRunning]=useState(false);
  const[progress,setProgress]=useState({cur:0,total:0});
  const[copied,setCopied]=useState(false);
  const[showApiSettings,setShowApiSettings]=useState(false);
  const stopRef=useRef(false);

  /* ── Capacitor Camera plugin ── */

  // Capacitor 5 এ Camera plugin নানা নামে আসতে পারে।
  // সব possible name try করি, না পেলে available plugins log করি।
  const _getCamera=()=>{
    const P=window.Capacitor?.Plugins||{};
    const cam=P.Camera||P.CameraPlugin||P["@capacitor/camera"]||null;
    if(!cam){
      const available=Object.keys(P).join(", ")||"(none)";
      _LC.error("camera",`Camera plugin not found. Available plugins: ${available}`);
      // সরাসরি দেখানোর জন্য push notification
      push("error","Available Plugins:",available||"(none)");
    }
    return cam;
  };

  // Permission helper — Android 13+ needs READ_MEDIA_IMAGES
  const _ensureMediaPermission=async()=>{
    try{
      const Camera=_getCamera();
      if(!Camera) return true; // plugin নেই, proceed করি permission ছাড়া
      const perm=await Camera.checkPermissions();
      if(perm?.photos==="granted"||perm?.photos==="limited") return true;
      const req=await Camera.requestPermissions({permissions:["photos","camera"]});
      if(req?.photos==="denied"||req?.photos==="permanently_denied"){
        push("error","Permission denied","Settings থেকে Photos permission দিন");
        _LC.error("permission","READ_MEDIA_IMAGES denied by user");
        return false;
      }
      return true;
    }catch(e){
      _LC.warn("permission",`Permission check error: ${e.message}`);
      return true; // proceed anyway
    }
  };

  const pickGallery=async()=>{
    try{
      const Camera=_getCamera();
      if(!Camera){
        push("warn","Camera plugin নেই","Logcat দেখুন — available plugins log করা হয়েছে");
        return;
      }
      const allowed=await _ensureMediaPermission();
      if(!allowed) return;
      _LC.log("gallery","pickImages called");
      const res=await Camera.pickImages({quality:90,limit:0});
      const imgs=(res.photos||[]).map(p=>({
        webPath:p.webPath,base64:"",status:"pending",ocrText:"",id:Date.now()+Math.random()
      }));
      _LC.log("gallery",`${imgs.length} image(s) selected`);
      setImages(p=>[...p,...imgs]);
    }catch(e){
      _LC.error("gallery",`Gallery error: ${e.message}`);
      push("error","Gallery error",e.message);
    }
  };

  const openCamera=async()=>{
    try{
      const Camera=_getCamera();
      if(!Camera){push("warn","Camera plugin নেই","Logcat দেখুন");return;}
      const allowed=await _ensureMediaPermission();
      if(!allowed) return;
      const res=await Camera.getPhoto({quality:90,resultType:"base64",source:"CAMERA"});
      _LC.log("camera","Photo taken via camera");
      setImages(p=>[...p,{webPath:"",base64:res.base64String||"",status:"pending",ocrText:"",id:Date.now()}]);
    }catch(e){
      if(!e.message?.includes("cancelled")) push("error","Camera error",e.message);
      if(!e.message?.includes("cancelled")) _LC.error("camera",`Camera error: ${e.message}`);
    }
  };

  const removeImg=(id)=>setImages(p=>p.filter(x=>x.id!==id));
  const clearAll=()=>{setImages([]);setOcrAll("");setParsedAll("");setCopied(false);setShowParsed(true);};

  /* ── Convert webPath → base64 ── */
  const toBase64=async(img)=>{
    if(img.base64)return img.base64;
    return new Promise((res,rej)=>{
      const canvas=document.createElement("canvas");
      const image=new Image();
      image.onload=()=>{
        // 2-side detection: if width > height*1.4, split vertically
        const W=image.naturalWidth, H=image.naturalHeight;
        if(W>H*1.4){
          // দুই পাশের page — দুটো আলাদা base64 দেব
          const half=Math.floor(W/2);
          canvas.width=half; canvas.height=H;
          const ctx=canvas.getContext("2d");
          ctx.drawImage(image,0,0,half,H,0,0,half,H);
          const left=canvas.toDataURL("image/jpeg",0.9).split(",")[1];
          ctx.clearRect(0,0,half,H);
          ctx.drawImage(image,half,0,W-half,H,0,0,W-half,H);
          canvas.width=W-half;
          const right=canvas.toDataURL("image/jpeg",0.9).split(",")[1];
          res([left,right]); // array = 2 pages
        } else {
          canvas.width=W; canvas.height=H;
          canvas.getContext("2d").drawImage(image,0,0);
          res(canvas.toDataURL("image/jpeg",0.9).split(",")[1]);
        }
      };
      image.onerror=()=>rej(new Error("Image load failed"));
      image.src=img.webPath;
    });
  };

  /* ── ML Kit OCR via native plugin ── */
  // Returns {raw, parsed} — raw=full text, parsed=semicolon lines (bulk ready)
  const runOcrOnBase64=async(b64)=>{
    const {OcrPlugin}=window.Capacitor?.Plugins||{};
    if(!OcrPlugin){
      const available=Object.keys(window.Capacitor?.Plugins||{}).join(", ")||"(none)";
      _LC.crash("OcrPlugin",`OcrPlugin missing. Available: ${available}`,{available});
      throw new Error("OcrPlugin নেই — APK rebuild করুন");
    }
    try{
      _LC.api("OcrPlugin","recognizeText called");
      const res=await OcrPlugin.recognizeText({base64:b64});
      const raw=res.text||"";
      let parsed=res.parsed||"";  // semicolon format from Kotlin parser
      _LC.log("OcrPlugin",`OCR result: ${raw.length} chars, parsed: ${parsed.split("\n").filter(Boolean).length} questions`);
      // ── Auto AI parse if provider active ─────────────────────────────────
      try{
        const aiResult=await callAiProvider(raw);
        if(aiResult&&aiResult.includes(";")){
          parsed=aiResult;  // AI parse replaces local parser output
          _LC.log("OcrPlugin","AI parse success: "+aiResult.split("\n").filter(Boolean).length+" questions");
        }
      }catch(aiErr){
        _LC.warn("OcrPlugin","AI parse skipped ("+aiErr.message+") — using local parser");
        // fallback: keep local parsed result
      }
      return {raw, parsed};
    }catch(e){
      _LC.error("OcrPlugin",`recognizeText failed: ${e.message}`);
      throw e;
    }
  };

  /* ── Run OCR on all images serially ── */
  // ocrAll = raw text (দেখার জন্য), parsedAll = semicolon lines (bulk-ready)
  const[parsedAll,setParsedAll]=useState("");
  const[showParsed,setShowParsed]=useState(true); // toggle raw/parsed view

  const startOcr=async()=>{
    if(!images.length){push("warn","ছবি যোগ করুন","");return;}
    setRunning(true);stopRef.current=false;
    setOcrAll("");setParsedAll("");setCopied(false);setShowParsed(true);
    let combinedRaw="";
    let combinedParsed="";
    setProgress({cur:0,total:images.length});

    for(let i=0;i<images.length;i++){
      if(stopRef.current)break;
      setProgress({cur:i+1,total:images.length});
      setImages(p=>p.map((x,j)=>j===i?{...x,status:"running"}:x));
      try{
        // toBase64 returns string or [left,right] for wide pages
        // — Column split is now also done in Kotlin (OcrPlugin) for portrait
        // Here we handle the JS-side landscape split (original behavior kept)
        const b64raw=await toBase64(images[i]);
        const parts=Array.isArray(b64raw)?b64raw:[b64raw];
        let pageRaw="", pageParsed="";
        for(const b64 of parts){
          const {raw,parsed}=await runOcrOnBase64(b64);
          if(raw)    pageRaw    +=(pageRaw?"\n":"")+raw;
          if(parsed) pageParsed +=(pageParsed?"\n":"")+parsed;
        }
        setImages(p=>p.map((x,j)=>j===i?{...x,status:"done",ocrText:pageRaw}:x));
        combinedRaw    +=`--- ছবি ${i+1} ---\n${pageRaw}\n\n`;
        combinedParsed +=pageParsed?(pageParsed+"\n"):"";
        setOcrAll(combinedRaw);
        setParsedAll(combinedParsed);
      }catch(e){
        setImages(p=>p.map((x,j)=>j===i?{...x,status:"error",ocrText:e.message}:x));
        combinedRaw+=`--- ছবি ${i+1} ERROR: ${e.message} ---\n\n`;
        setOcrAll(combinedRaw);
      }
    }
    setRunning(false);
    const qCount=combinedParsed.split("\n").filter(l=>l.trim()&&l.includes(";")).length;
    push("success",`✅ OCR সম্পন্ন!`,`${images.length}টি ছবি — ${qCount}টি প্রশ্ন parse হয়েছে`);
  };

  /* ── Copy OCR + Prompt ── */
  const copyPrompt=(qtype)=>{
    if(!ocrAll.trim()){push("warn","আগে OCR চালান","");return;}
    const formats={
      MCQ:`MCQ format — প্রতি লাইন:\nপ্রশ্ন;অপ১;অপ২;অপ৩;অপ৪;সঠিকউত্তর;ব্যাখ্যা(optional)\nউদাহরণ: বাংলাদেশের রাজধানী?;ঢাকা;চট্টগ্রাম;খুলনা;রাজশাহী;ঢাকা`,
      Written:`Written format — প্রতি entry {} দিয়ে wrap করো:\n{প্রশ্ন;উত্তর}\nউদাহরণ: {সন্ধি বিচ্ছেদ: সঞ্চয়;সম+চয়}`,
      Study:`Study format — প্রতি entry {} দিয়ে wrap করো:\n{প্রশ্ন;উত্তর লাইন১\nউত্তর লাইন২}\nউদাহরণ: {রাষ্ট্রবিজ্ঞানের জনক কে?;এরিস্টটল}`,
    };
    const prompt=`তুমি একজন প্রশ্নপত্র formatter। নিচের OCR text থেকে সব প্রশ্ন বের করে নির্দিষ্ট format-এ দাও।\n\nOUTPUT FORMAT (${qtype}):\n${formats[qtype]}\n\nRULES:\n- শুধু formatted data দাও, কোনো label বা explanation নয়\n- Serial number বাদ দাও\n- field-এর ভেতরে ; থাকলে | দিয়ে replace করো\n- কোনো প্রশ্ন বাদ দিও না\n\n=== OCR TEXT ===\n${ocrAll}`;
    navigator.clipboard.writeText(prompt).then(()=>{
      setCopied(true);
      push("success","✅ Copied!","Gemini/ChatGPT-এ paste করুন → format করা text আবার Bulk-এ paste করুন");
      setTimeout(()=>setCopied(false),3000);
    }).catch(()=>{push("error","Copy ব্যর্থ","");});
  };

  /* ── Send to Bulk ── */
  // parsed থাকলে সেটাই পাঠাই (semicolon-ready), না থাকলে raw OCR text
  const sendToBulk=()=>{
    const toSend=(parsedAll&&parsedAll.trim())?parsedAll:ocrAll;
    if(!toSend.trim()){push("warn","আগে OCR চালান","");return;}
    const isParsed=!!(parsedAll&&parsedAll.trim());
    onSendToBulk(toSend);
    push("success",
      isParsed?"✅ Parsed প্রশ্ন Bulk-এ পাঠানো হয়েছে!":"📋 Raw OCR text Bulk-এ পাঠানো হয়েছে",
      isParsed?"Shuffle করুন → Upload করুন":"Gemini দিয়ে format করুন"
    );
  };

  const pct=progress.total?Math.round(progress.cur/progress.total*100):0;

  return(
    <div className="page">
      {/* Header */}
      <div style={{background:`linear-gradient(135deg,#7c3aed,#4f46e5)`,borderRadius:14,padding:"14px 16px",marginBottom:14,color:"#fff",position:"relative"}}>
        <div style={{display:"flex",alignItems:"center",justifyContent:"space-between"}}>
          <div>
            <div style={{fontWeight:900,fontSize:15,marginBottom:2}}>📸 AI Import — OCR</div>
            <div style={{fontSize:11,opacity:.8}}>
              {getActiveProvider()
                ? `✅ ${getActiveProvider().name} active`
                : "⚠️ API provider নেই — ⚙️ দিন"}
            </div>
          </div>
          <button onClick={()=>setShowApiSettings(v=>!v)}
            style={{background:"rgba(255,255,255,0.15)",border:"1px solid rgba(255,255,255,0.3)",
              borderRadius:10,color:"#fff",fontSize:20,width:40,height:40,cursor:"pointer",
              display:"flex",alignItems:"center",justifyContent:"center"}}>
            {showApiSettings?"✕":"⚙️"}
          </button>
        </div>
      </div>
      {/* Inline API Settings panel */}
      {showApiSettings&&<ApiSettingsPage push={push} inline={true}/>}

      {/* Image Picker Buttons */}
      <div style={{display:"flex",gap:8,marginBottom:12}}>
        <button className="btn bp bb" style={{flex:1}} onClick={pickGallery}>🖼 Gallery (একাধিক)</button>
        <button className="btn" style={{flex:1,background:"#1e293b",color:C.text,borderColor:C.border}} onClick={openCamera}>📷 Camera</button>
        {images.length>0&&<button className="btn" style={{background:"#7f1d1d",color:"#fca5a5",borderColor:"#991b1b",padding:"0 12px"}} onClick={clearAll}>🗑</button>}
      </div>

      {/* Image Grid */}
      {images.length>0&&(
        <div style={{display:"flex",flexWrap:"wrap",gap:8,marginBottom:12}}>
          {images.map((img,i)=>(
            <div key={img.id} style={{position:"relative",width:72,height:72}}>
              {img.webPath?(
                <img src={img.webPath} style={{width:72,height:72,borderRadius:10,objectFit:"cover",
                  border:`2px solid ${img.status==="done"?"#10b981":img.status==="error"?"#ef4444":img.status==="running"?"#6366f1":C.border}`}}/>
              ):(
                <div style={{width:72,height:72,borderRadius:10,background:C.panel,border:`2px solid ${C.border}`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:20}}>📷</div>
              )}
              {/* Status overlay */}
              <div style={{position:"absolute",bottom:2,left:2,right:2,textAlign:"center",fontSize:9,fontWeight:800,
                color:img.status==="done"?"#10b981":img.status==="error"?"#ef4444":img.status==="running"?"#818cf8":"#94a3b8"}}>
                {img.status==="done"?"✔":img.status==="error"?"✗":img.status==="running"?"⏳":`#${i+1}`}
              </div>
              {/* Remove */}
              {!running&&(
                <div onClick={()=>removeImg(img.id)} style={{position:"absolute",top:-6,right:-6,background:"#ef4444",color:"#fff",borderRadius:999,width:18,height:18,display:"flex",alignItems:"center",justifyContent:"center",fontSize:11,cursor:"pointer",fontWeight:900}}>×</div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Info box */}
      <div style={{background:"#0a1628",border:`1px solid ${C.border}`,borderRadius:10,padding:"8px 12px",fontSize:11,color:C.muted,marginBottom:12,lineHeight:1.7}}>
        <div style={{color:C.text,fontWeight:700,marginBottom:3}}>📋 ব্যবহার পদ্ধতি:</div>
        <div>① Gallery থেকে ছবি নিন (একসাথে অনেক)</div>
        <div>② <b style={{color:"#6366f1"}}>OCR চালান</b> → text বের হবে</div>
        <div>③ <b style={{color:"#f59e0b"}}>Prompt Copy</b> করুন → Gemini-তে paste করুন</div>
        <div>④ Gemini-র formatted text → <b style={{color:"#10b981"}}>Bulk-এ পাঠান</b></div>
        <div style={{color:"#d97706",marginTop:3}}>💡 2-side page (landscape) হলে automatically দুটো আলাদা করে OCR হবে</div>
      </div>

      {/* Progress */}
      {running&&(
        <div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:12,padding:"10px 14px",marginBottom:12}}>
          <div style={{display:"flex",justifyContent:"space-between",fontSize:11,marginBottom:6}}>
            <span style={{color:C.text,fontWeight:700}}>⏳ OCR চলছে...</span>
            <span style={{color:"#6366f1",fontWeight:900}}>{pct}% ({progress.cur}/{progress.total})</span>
          </div>
          <div style={{background:C.border,borderRadius:999,height:8,overflow:"hidden"}}>
            <div style={{height:"100%",width:`${pct}%`,background:"linear-gradient(90deg,#6366f1,#10b981)",borderRadius:999,transition:"width .3s"}}/>
          </div>
        </div>
      )}

      {/* OCR Result — Parsed / Raw toggle */}
      {ocrAll&&(
        <div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:12,padding:"10px 14px",marginBottom:12}}>
          {/* Header row */}
          <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:8}}>
            <span style={{fontSize:12,fontWeight:800,color:C.text}}>📄 OCR Result</span>
            <div style={{display:"flex",gap:5,alignItems:"center"}}>
              {parsedAll&&(
                <div style={{display:"flex",borderRadius:20,overflow:"hidden",border:`1px solid ${C.border}`}}>
                  <button onClick={()=>setShowParsed(true)} style={{
                    fontSize:10,padding:"3px 10px",border:"none",cursor:"pointer",fontWeight:700,
                    background:showParsed?"#10b981":"transparent",
                    color:showParsed?"#fff":C.muted
                  }}>✅ Parsed</button>
                  <button onClick={()=>setShowParsed(false)} style={{
                    fontSize:10,padding:"3px 10px",border:"none",cursor:"pointer",fontWeight:700,
                    background:!showParsed?"#6366f1":"transparent",
                    color:!showParsed?"#fff":C.muted
                  }}>📝 Raw</button>
                </div>
              )}
              <span style={{fontSize:10,color:C.muted}}>
                {showParsed&&parsedAll
                  ? `${parsedAll.split("\n").filter(l=>l.trim()&&l.includes(";")).length} প্রশ্ন`
                  : `${ocrAll.length} chars`}
              </span>
            </div>
          </div>
          {/* Parsed result info bar */}
          {showParsed&&parsedAll&&(
            <div style={{fontSize:11,color:"#10b981",fontWeight:700,marginBottom:6,padding:"4px 10px",
              background:"#052e16",borderRadius:8,border:"1px solid #10b98133"}}>
              🎯 Auto-parsed! প্রশ্ন + অপশন আলাদা হয়েছে। নিচে দেখুন ও দরকারে edit করুন।
            </div>
          )}
          {showParsed&&parsedAll?(
            <textarea className="ta" style={{minHeight:120,fontSize:11,fontFamily:"monospace",marginBottom:0,
              borderColor:"#10b98144"}}
              value={parsedAll} onChange={e=>setParsedAll(e.target.value)}/>
          ):(
            <textarea className="ta" style={{minHeight:120,fontSize:11,fontFamily:"monospace",marginBottom:0}}
              value={ocrAll} onChange={e=>setOcrAll(e.target.value)}/>
          )}
        </div>
      )}

      {/* Action Buttons */}
      <div style={{display:"flex",flexDirection:"column",gap:8}}>
        {/* OCR Button */}
        <button className="btn bp bb" disabled={running||!images.length} onClick={startOcr} style={{justifyContent:"center"}}>
          {running?(
            <span>⏳ OCR চলছে... {progress.cur}/{progress.total}</span>
          ):(
            <span>🔍 STEP 1: OCR চালান (ছবি → TEXT)</span>
          )}
        </button>

        {/* Stop */}
        {running&&(
          <button className="btn" style={{background:"#7f1d1d",color:"#fca5a5",borderColor:"#991b1b",justifyContent:"center"}}
            onClick={()=>stopRef.current=true}>⛔ বন্ধ করুন</button>
        )}

        {/* Prompt Copy buttons */}
        {ocrAll&&!running&&(
          <>
            <div style={{fontSize:11,color:C.muted,textAlign:"center",marginTop:4}}>STEP 2: Prompt copy করুন → Gemini-তে paste করুন → format করা text ফিরিয়ে আনুন</div>
            <div style={{display:"flex",gap:6}}>
              {["MCQ","Written","Study"].map(t=>(
                <button key={t} className="btn" onClick={()=>copyPrompt(t)}
                  style={{flex:1,justifyContent:"center",fontSize:11,
                    background:t==="MCQ"?"#1e3a5f":t==="Written"?"#1c2a1c":"#1a1a2e",
                    color:t==="MCQ"?"#60a5fa":t==="Written"?"#4ade80":"#818cf8",
                    borderColor:t==="MCQ"?"#3b82f6":t==="Written"?"#22c55e":"#6366f1"}}>
                  {copied?"✅ Copied!`":`📋 ${t} Prompt`}
                </button>
              ))}
            </div>
            <button className="btn" onClick={sendToBulk}
              style={{background:"#052e16",color:"#10b981",borderColor:"#10b981",justifyContent:"center"}}>
              {parsedAll?"📤 STEP 3: Parsed প্রশ্ন → Bulk-এ পাঠান":"📤 STEP 3: Raw OCR → Bulk-এ পাঠান"}
            </button>
          </>
        )}
      </div>
    </div>
  );
}

/* ══════════ BULK UPLOADER PAGE ══════════ */
function BulkUploaderPage({push,prefillText,onClearPrefill}){

  const[mode,setMode]=useState("Quiz");
  const[qtype,setQtype]=useState("MCQ");
  const[subject,setSubject]=useState("");
  const[subtopic,setSubtopic]=useState("");
  const[bulkText,setBulkText]=useState("");
  const[audienceTags,setAudienceTags]=useState([]);
  const[tagInput,setTagInput]=useState("");
  const[subjects,setSubjects]=useState([]);
  const[validStats,setValidStats]=useState(null);
  const[validDetail,setValidDetail]=useState(null); // detail modal data
  const[showDetail,setShowDetail]=useState(false);
  const[running,setRunning]=useState(false);
  const[stopped,setStopped]=useState(false);
  const[progress,setProgress]=useState({done:0,total:0,sent:0,failed:0});
  const[log,setLog]=useState([]);
  const[done,setDone]=useState(false);
  const stopRef=useRef(false);

  /* Load subjects for autocomplete */
  useEffect(()=>{
    loadPath(mode).then(raw=>{
      const arr=toArr(raw);
      const subs=[...new Set(arr.map(q=>q.subject||q.Subject||"").filter(Boolean))];
      setSubjects(subs);
    }).catch(()=>{});
  },[mode]);

  /* AI Import থেকে prefill */
  useEffect(()=>{
    if(prefillText){
      handleText(prefillText);
      if(onClearPrefill)onClearPrefill();
    }
  },[prefillText]);

  /* ── Parse helpers — explicit qtype/mode params এড়াতে pure functions ── */
  const getEntries=(raw)=>{
    const entries=[];
    const re=/\{([\s\S]+?)\}/g;
    let m;
    while((m=re.exec(raw))!==null){const e=m[1].trim();if(e)entries.push(e);}
    if(entries.length>0)return entries;
    return raw.split("\n").map(s=>s.trim()).filter(Boolean);
  };

  // effectiveType: "Study" | "Written" | "MCQ"  — caller বলে দেয়
  const parseEntry=(entry, effectiveType)=>{
    const tr=entry.trim();
    if(!tr||tr.startsWith("#"))return{skip:true};

    if(effectiveType==="Study"){
      const si=tr.indexOf(";");
      if(si===-1)return{err:true,reason:"Study: প্রথম ';' দিয়ে প্রশ্ন ও উত্তর আলাদা করুন"};
      const q=tr.substring(0,si).trim();
      const ans=tr.substring(si+1).trim();
      if(!q)return{err:true,reason:"Study: প্রশ্ন খালি"};
      if(!ans)return{err:true,reason:"Study: উত্তর খালি"};
      return{ok:true,q,correct:ans,explanation:""};

    } else if(effectiveType==="Written"){
      // প্রশ্ন ; উত্তর(multiline ok) ; ব্যাখ্যা(optional)
      const si=tr.indexOf(";");
      if(si===-1)return{err:true,reason:"Written: ';' দিয়ে প্রশ্ন ও উত্তর আলাদা করুন"};
      const q=tr.substring(0,si).trim();
      const rest=tr.substring(si+1);
      // ব্যাখ্যা optional — শেষ ';' এর পরে থাকলে নেব
      const lastSemi=rest.lastIndexOf(";");
      let ans,exp;
      // যদি rest-এ আরো ';' থাকে সেটাকে explanation ধরি
      if(lastSemi>0){
        ans=rest.substring(0,lastSemi).trim();
        exp=rest.substring(lastSemi+1).trim();
      } else {
        ans=rest.trim();exp="";
      }
      if(!q)return{err:true,reason:"Written: প্রশ্ন খালি"};
      if(!ans)return{err:true,reason:"Written: উত্তর খালি"};
      return{ok:true,q,correct:ans,explanation:exp};

    } else {
      // MCQ — {} ভেতরে newline থাকলেও flatten করে parse
      const flat=tr.replace(/\r?\n/g," ").replace(/\s+/g," ");
      const parts=flat.split(";").map(p=>p.trim());
      if(parts.length<6)return{err:true,reason:`MCQ: ${parts.length}টি কলাম পেয়েছি, দরকার কমপক্ষে ৬টি (প্রশ্ন;অপ১;অপ২;অপ৩;অপ৪;উত্তর)`};
      if(!parts[0])return{err:true,reason:"MCQ: প্রশ্ন খালি"};
      if(!parts[5])return{err:true,reason:"MCQ: সঠিক উত্তর খালি"};
      return{ok:true,q:parts[0],opt1:parts[1],opt2:parts[2],opt3:parts[3],opt4:parts[4],correct:parts[5],explanation:parts[6]||""};
    }
  };

  // current state থেকে effectiveType বের করে
  const getEffectiveType=(m,qt)=> m==="Study"?"Study":qt;

  const parseLine=(entry)=>parseEntry(entry, getEffectiveType(mode,qtype));

  /* Validate — detail list সহ */
  const runValidate=(text,m,qt)=>{
    if(!text.trim()){setValidStats(null);setValidDetail(null);return;}
    const eff=getEffectiveType(m,qt);
    const entries=getEntries(text);
    const rows=entries.map((e,i)=>{
      const r=parseEntry(e,eff);
      return{idx:i+1, entry:e, ...r};
    });
    const ok=rows.filter(r=>r.ok).length;
    const skip=rows.filter(r=>r.skip).length;
    const err=rows.filter(r=>r.err).length;
    setValidStats({total:rows.length,ok,skip,err});
    setValidDetail(rows);
  };

  const handleText=(v)=>{setBulkText(v);runValidate(v,mode,qtype);};
  const handleQtype=(v)=>{setQtype(v);runValidate(bulkText,mode,v);};
  const handleMode=(v)=>{setMode(v);runValidate(bulkText,v,qtype);};

  /* ── Shuffle MCQ Options ──
     প্রতিটি MCQ লাইনে অপশনগুলো (col 1-4) random করে সাজায়,
     correct field (col 5) সেই অনুযায়ী আপডেট করে।
     { } block এবং plain line দুটো format-ই handle করে।
  */
  const[shuffleInfo,setShuffleInfo]=useState(null); // {count} — কতটা shuffle হলো
  const handleShuffle=()=>{
    if(!bulkText.trim()||getEffectiveType(mode,qtype)!=="MCQ"){return;}
    const entries=getEntries(bulkText);
    let shuffled=0;
    const newLines=entries.map(entry=>{
      const tr=entry.trim();
      if(!tr||tr.startsWith("#"))return entry;
      const flat=tr.replace(/\r?\n/g," ").replace(/\s+/g," ");
      const parts=flat.split(";").map(p=>p.trim());
      // MCQ: index 0=প্রশ্ন, 1-4=অপশন, 5=correct, 6=ব্যাখ্যা(optional)
      if(parts.length<6)return entry;
      const q=parts[0];
      const opts=[parts[1],parts[2],parts[3],parts[4]];
      const correct=parts[5];
      const expl=parts[6]||"";
      // Fisher-Yates shuffle
      for(let i=opts.length-1;i>0;i--){
        const j=Math.floor(Math.random()*(i+1));
        [opts[i],opts[j]]=[opts[j],opts[i]];
      }
      // correct field = shuffled text-এ যেটা সঠিক (value same থাকে)
      const newLine=expl
        ?`${q} ; ${opts[0]} ; ${opts[1]} ; ${opts[2]} ; ${opts[3]} ; ${correct} ; ${expl}`
        :`${q} ; ${opts[0]} ; ${opts[1]} ; ${opts[2]} ; ${opts[3]} ; ${correct}`;
      shuffled++;
      return newLine;
    });
    // { } block ছিলে কিনা detect করি
    const wasBlock=/\{[\s\S]+?\}/.test(bulkText);
    const result=wasBlock
      ? newLines.map(l=>`{ ${l} }`).join("\n")
      : newLines.join("\n");
    setShuffleInfo({count:shuffled});
    handleText(result);
    setTimeout(()=>setShuffleInfo(null),3000);
  };

  /* Audience tag helpers */
  const addTag=()=>{
    const t=tagInput.trim();
    if(t&&!audienceTags.includes(t)){setAudienceTags(p=>[...p,t]);}
    setTagInput("");
  };
  const removeTag=(t)=>setAudienceTags(p=>p.filter(x=>x!==t));
  const QUICK_TAGS=["Job","Class 7","Computer Operator","Masters 1"];

  /* Build Firebase record — same as admin EntryPage pattern */
  const buildRec=(item,ts,id)=>{
    const tagStr=audienceTags.join(",");
    const isStudy=mode==="Study";
    const isWritten=qtype==="Written";
    if(mode==="Quiz"){
      return{
        id,question:item.q,
        option1:isStudy||isWritten?"":item.opt1||"",
        option2:isStudy||isWritten?"":item.opt2||"",
        option3:isStudy||isWritten?"":item.opt3||"",
        option4:isStudy||isWritten?"":item.opt4||"",
        correct:item.correct||"",
        subject,sub_topic:subtopic||subject,
        explanation:item.explanation||"",
        "Question Type":isWritten?"Written":"MCQ",
        AudienceTags:tagStr,
        Timestamp:ts,
        technique:"",Previous_Exam:"",
      };
    }
    if(mode==="QBank"){
      return{
        id,question:item.q,
        option1:isWritten?"":item.opt1||"",
        option2:isWritten?"":item.opt2||"",
        option3:isWritten?"":item.opt3||"",
        option4:isWritten?"":item.opt4||"",
        correct:item.correct||"",
        subject,sub_topic:subtopic||subject,topic:"",
        explanation:item.explanation||"",
        "Question Type":isWritten?"Written":"MCQ",
        AudienceTags:tagStr,
        Timestamp:ts,technique:"",
      };
    }
    /* Study */
    return{
      id,question:item.q,correct:item.correct||"",
      subject,sub_topic:subtopic||subject,
      explanation:item.explanation||"",
      "Question Type":"Study",
      AudienceTags:tagStr,
      Timestamp:ts,technique:"",
    };
  };

  /* Main upload */
  const startUpload=async()=>{
    if(!subject.trim()){push("warn","⚠️ Subject লিখুন","");return;}
    if(!bulkText.trim()){push("warn","⚠️ প্রশ্ন লিখুন","");return;}
    const eff=getEffectiveType(mode,qtype);
    const entries=getEntries(bulkText).map(l=>parseEntry(l,eff)).filter(r=>r.ok);
    if(!entries.length){push("warn","⚠️ কোনো valid প্রশ্ন নেই — Validation chips-এ ক্লিক করে দেখুন","");return;}

    setRunning(true);setDone(false);setStopped(false);
    stopRef.current=false;
    setLog([]);
    setProgress({done:0,total:entries.length,sent:0,failed:0});
    const addLog=(msg,type)=>setLog(p=>[...p.slice(-99),{msg,type,id:Date.now()+Math.random()}]);

    let sent=0,failed=0;
    const BATCH=8;
    for(let i=0;i<entries.length;i+=BATCH){
      if(stopRef.current){addLog("⛔ বন্ধ করা হয়েছে","err");break;}
      const batch=entries.slice(i,i+BATCH);
      await Promise.all(batch.map(async(item)=>{
        const ts=nowTs();
        const id=Date.now()+Math.floor(Math.random()*9999);
        const rec=buildRec(item,ts,id);
        try{
          const res=await fbPush(mode,rec);
          /* Set id field to the firebase push key — same as entry app */
          if(res?.name){
            await fbSet(`${mode}/${res.name}/id`,res.name);
          }
          // Sheet sync → GAS standalone handles this
          invalidate(mode);
          sent++;
          addLog(`✔ ${(item.q||"").substring(0,55)}...`,"ok");
        }catch(e){
          failed++;
          addLog(`✗ ব্যর্থ: ${(item.q||"").substring(0,45)}... [${e.message}]`,"err");
        }
        setProgress(p=>({...p,done:p.done+1,sent,failed}));
      }));
    }
    setRunning(false);setDone(true);
    if(sent>0)push("success",`✅ ${sent}টি সফলভাবে যোগ হয়েছে!`,`${mode} — ${subject}`);
    if(failed>0)push("error",`${failed}টি ব্যর্থ হয়েছে`,"আবার চেষ্টা করুন");
  };

  const reset=()=>{setBulkText("");setValidStats(null);setLog([]);setProgress({done:0,total:0,sent:0,failed:0});setDone(false);setSubtopic("");};

  const pct=progress.total?Math.round(progress.done/progress.total*100):0;

  return(
    <div className="page">
      {/* Header */}
      <div style={{background:`linear-gradient(135deg,${C.accent},#7c3aed)`,borderRadius:14,padding:"14px 16px",marginBottom:16,color:"#fff"}}>
        <div style={{fontWeight:900,fontSize:15,marginBottom:2}}>⚡ বাল্ক প্রশ্ন আপলোড</div>
        <div style={{fontSize:11,opacity:.8}}>একসাথে একাধিক প্রশ্ন Firebase-এ যোগ করুন</div>
      </div>

      {/* Target Sheet */}
      <div style={{display:"flex",gap:6,marginBottom:12}}>
        {["Quiz","QBank","Study"].map(m=>(
          <button key={m} className={`ftab${mode===m?" on":""}`} onClick={()=>handleMode(m)} style={{flex:1}}>{m}</button>
        ))}
      </div>

      {/* Question Type */}
      {mode!=="Study"&&(
        <div style={{display:"flex",gap:6,marginBottom:12}}>
          {["MCQ","Written"].map(t=>(
            <button key={t} className={`tp2${qtype===t?" on":""}`} onClick={()=>handleQtype(t)}>{t}</button>
          ))}
        </div>
      )}

      {/* Audience Tags */}
      <div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:12,padding:"10px 12px",marginBottom:12}}>
        <div style={{fontSize:10,fontWeight:800,color:C.muted,letterSpacing:".7px",marginBottom:7,textTransform:"uppercase"}}>🏷 Audience Tags</div>
        <div style={{display:"flex",gap:5,flexWrap:"wrap",marginBottom:7}}>
          {QUICK_TAGS.map(t=>(
            <button key={t} onClick={()=>{if(!audienceTags.includes(t))setAudienceTags(p=>[...p,t]);}}
              style={{fontSize:10,padding:"3px 9px",borderRadius:20,border:`1px solid ${audienceTags.includes(t)?C.accent:C.border}`,background:audienceTags.includes(t)?C.accent+"22":"transparent",color:audienceTags.includes(t)?C.accent:C.muted,cursor:"pointer",fontWeight:700}}>{t}</button>
          ))}
        </div>
        {audienceTags.length>0&&(
          <div style={{display:"flex",gap:5,flexWrap:"wrap",marginBottom:7}}>
            {audienceTags.map(t=>(
              <span key={t} style={{fontSize:11,padding:"2px 9px",borderRadius:20,background:C.accent,color:"#fff",fontWeight:700,display:"flex",alignItems:"center",gap:4}}>
                {t}<span onClick={()=>removeTag(t)} style={{cursor:"pointer",opacity:.8,marginLeft:2}}>×</span>
              </span>
            ))}
          </div>
        )}
        <div style={{display:"flex",gap:6}}>
          <input className="inp" style={{flex:1,marginBottom:0}} value={tagInput} onChange={e=>setTagInput(e.target.value)} onKeyDown={e=>{if(e.key==="Enter"){e.preventDefault();addTag();}}} placeholder="Tag লিখুন..."/>
          <button className="btn bp" style={{padding:"0 14px",fontSize:13}} onClick={addTag}>+</button>
        </div>
      </div>

      {/* Subject & Subtopic */}
      <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:8,marginBottom:12}}>
        <div className="fld" style={{marginBottom:0}}>
          <label>📚 Subject</label>
          <input className="inp" list="bulk-sl" value={subject} onChange={e=>setSubject(e.target.value)} placeholder="Subject..."/>
          <datalist id="bulk-sl">{subjects.map((s,i)=><option key={i} value={s}/>)}</datalist>
        </div>
        <div className="fld" style={{marginBottom:0}}>
          <label>📌 Sub-Topic</label>
          <input className="inp" value={subtopic} onChange={e=>setSubtopic(e.target.value)} placeholder="Sub topic..."/>
        </div>
      </div>

      {/* Format Guide */}
      <div style={{background:"#0a1628",border:`1px solid ${C.border}`,borderRadius:12,padding:"10px 12px",marginBottom:10,fontSize:11,color:C.muted,lineHeight:1.7}}>
        <div style={{fontWeight:800,color:C.text,marginBottom:4}}>📋 ফরম্যাট (প্রতি লাইন = একটি প্রশ্ন):</div>
        <div><span style={{color:"#10b981",fontWeight:700}}>MCQ →</span> প্রশ্ন ; অপ১ ; অপ২ ; অপ৩ ; অপ৪ ; সঠিকউত্তর ; ব্যাখ্যা(optional)</div>
        <div><span style={{color:"#f59e0b",fontWeight:700}}>Written →</span> প্রশ্ন ; উত্তর ; ব্যাখ্যা(optional)</div>
        <div><span style={{color:"#818cf8",fontWeight:700}}>Study →</span> {"{"} প্রশ্ন ; উত্তর লাইন১\nউত্তর লাইন২... {"}"}</div>
      </div>

      {/* Validation Stats — clickable */}
      {validStats&&(
        <div style={{display:"flex",gap:6,flexWrap:"wrap",marginBottom:8}}>
          {[
            {label:`Total: ${validStats.total}`,color:"#94a3b8",bg:"#1e293b",filter:"all"},
            {label:`✔ Valid: ${validStats.ok}`,color:"#10b981",bg:"#052e16",filter:"ok"},
            {label:`Skip: ${validStats.skip}`,color:"#d97706",bg:"#1c1004",filter:"skip"},
            {label:`✗ Wrong: ${validStats.err}`,color:"#ef4444",bg:"#1f0a0a",filter:"err"},
          ].map(x=>(
            <span key={x.label} onClick={()=>{setShowDetail(x.filter);}} style={{fontSize:11,fontWeight:800,padding:"4px 12px",borderRadius:20,color:x.color,background:x.bg,cursor:"pointer",border:`1px solid ${x.color}44`}}>{x.label} 👁</span>
          ))}
        </div>
      )}

      {/* Validation Detail Modal */}
      {showDetail&&validDetail&&(
        <div style={{position:"fixed",inset:0,background:"#000000cc",zIndex:300,display:"flex",flexDirection:"column"}} onClick={()=>setShowDetail(false)}>
          <div style={{background:C.bg,marginTop:"auto",borderRadius:"18px 18px 0 0",maxHeight:"80vh",display:"flex",flexDirection:"column"}} onClick={e=>e.stopPropagation()}>
            {/* Modal Header */}
            <div style={{padding:"14px 16px 10px",borderBottom:`1px solid ${C.border}`,display:"flex",justifyContent:"space-between",alignItems:"center",flexShrink:0}}>
              <div style={{fontWeight:900,fontSize:14,color:C.text}}>
                {showDetail==="all"?"📋 সব এন্ট্রি":showDetail==="ok"?"✅ Valid এন্ট্রি":showDetail==="err"?"❌ Error এন্ট্রি":"⏭ Skip এন্ট্রি"}
              </div>
              <button onClick={()=>setShowDetail(false)} style={{background:"transparent",border:"none",color:C.muted,fontSize:18,cursor:"pointer"}}>✕</button>
            </div>
            {/* Modal Body */}
            <div style={{overflowY:"auto",padding:"10px 14px",flex:1}}>
              {validDetail
                .filter(r=>showDetail==="all"||r[showDetail])
                .map((r,i)=>(
                  <div key={i} style={{
                    background:r.ok?"#052e16":r.err?"#1f0a0a":r.skip?"#1c1004":C.panel,
                    border:`1px solid ${r.ok?"#10b98133":r.err?"#ef444433":"#d9770633"}`,
                    borderRadius:10,padding:"8px 12px",marginBottom:8
                  }}>
                    <div style={{display:"flex",justifyContent:"space-between",marginBottom:4}}>
                      <span style={{fontSize:10,fontWeight:800,color:C.muted}}>#{r.idx}</span>
                      <span style={{fontSize:10,fontWeight:800,
                        color:r.ok?"#10b981":r.err?"#ef4444":"#d97706",
                        background:r.ok?"#10b98122":r.err?"#ef444422":"#d9770622",
                        padding:"1px 8px",borderRadius:10
                      }}>
                        {r.ok?"✔ VALID":r.err?"✗ ERROR":"⏭ SKIP"}
                      </span>
                    </div>
                    {r.err&&<div style={{fontSize:11,color:"#ef4444",fontWeight:700,marginBottom:4}}>⚠ {r.reason}</div>}
                    <div style={{fontSize:11,color:C.muted,lineHeight:1.5,
                      maxHeight:80,overflowY:"auto",
                      whiteSpace:"pre-wrap",wordBreak:"break-word"
                    }}>
                      {r.entry?r.entry.substring(0,200)+(r.entry.length>200?"...":""):"(খালি)"}
                    </div>
                    {r.ok&&<div style={{fontSize:10,color:"#10b981",marginTop:4}}>
                      ❓ {(r.q||"").substring(0,60)}{r.q?.length>60?"...":""}
                    </div>}
                  </div>
                ))
              }
              {validDetail.filter(r=>showDetail==="all"||r[showDetail]).length===0&&(
                <div style={{textAlign:"center",color:C.muted,padding:24,fontSize:13}}>কোনো এন্ট্রি নেই</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Bulk Textarea */}
      <div className="fld">
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:5}}>
          <label style={{marginBottom:0}}>প্রশ্নগুলো লিখুন / পেস্ট করুন</label>
          {getEffectiveType(mode,qtype)==="MCQ"&&bulkText.trim()&&(
            <button
              type="button"
              onClick={handleShuffle}
              style={{
                fontSize:11,fontWeight:800,padding:"4px 12px",borderRadius:20,
                border:`1px solid #f59e0b`,background:"#1c1004",color:"#f59e0b",
                cursor:"pointer",display:"flex",alignItems:"center",gap:5,whiteSpace:"nowrap",flexShrink:0
              }}
            >
              🔀 Options Shuffle
            </button>
          )}
        </div>
        {shuffleInfo&&(
          <div style={{fontSize:11,color:"#10b981",fontWeight:700,marginBottom:6,padding:"4px 10px",background:"#052e16",borderRadius:8,border:"1px solid #10b98133"}}>
            ✅ {shuffleInfo.count}টি প্রশ্নের অপশন shuffle হয়েছে!
          </div>
        )}
        <textarea className="ta" style={{minHeight:160,fontFamily:"monospace",fontSize:12}} value={bulkText}
          onChange={e=>handleText(e.target.value)}
          placeholder={mode==="Study"
            ?"{ প্রশ্ন ; উত্তর লাইন১\nউত্তর লাইন২ }\n{ পরের প্রশ্ন ; উত্তর }"
            :qtype==="Written"
            ?"{ প্রশ্ন ; উত্তর ; ব্যাখ্যা }\n{ পরের প্রশ্ন ; উত্তর }"
            :"{ প্রশ্ন ; অপ১ ; অপ২ ; অপ৩ ; অপ৪ ; সঠিকউত্তর ; ব্যাখ্যা }\n{ প্রশ্ন ; অপ১ ; অপ২ ; অপ৩ ; অপ৪ ; সঠিকউত্তর }"}
        />
      </div>

      {/* Progress Bar */}
      {(running||done)&&(
        <div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:12,padding:"12px 14px",marginBottom:12}}>
          <div style={{display:"flex",justifyContent:"space-between",fontSize:11,marginBottom:6}}>
            <span style={{color:C.text,fontWeight:700}}>{done?"✅ সম্পন্ন!":"⏳ আপলোড হচ্ছে..."}</span>
            <span style={{color:C.accent,fontWeight:900}}>{pct}% ({progress.done}/{progress.total})</span>
          </div>
          <div style={{background:C.border,borderRadius:999,height:8,overflow:"hidden",marginBottom:8}}>
            <div style={{height:"100%",width:`${pct}%`,background:"linear-gradient(90deg,#6366f1,#3b82f6,#10b981)",borderRadius:999,transition:"width .25s ease"}}/>
          </div>
          <div style={{display:"flex",gap:12,fontSize:11}}>
            <span style={{color:"#10b981",fontWeight:700}}>✔ {progress.sent} সফল</span>
            {progress.failed>0&&<span style={{color:"#ef4444",fontWeight:700}}>✗ {progress.failed} ব্যর্থ</span>}
          </div>
          {/* Log */}
          {log.length>0&&(
            <div style={{maxHeight:110,overflowY:"auto",marginTop:8,fontSize:10,lineHeight:1.7,background:"#060c18",borderRadius:8,padding:"6px 10px"}}>
              {log.map(l=>(
                <div key={l.id} style={{color:l.type==="ok"?"#10b981":l.type==="err"?"#ef4444":"#d97706"}}>{l.msg}</div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Action Buttons */}
      <div style={{display:"flex",gap:8,marginTop:4}}>
        <button className="btn bp bb" style={{flex:2}} disabled={running} onClick={startUpload}>
          {running?"⏳ আপলোড হচ্ছে...":"📤 Submit Bulk Question"}
        </button>
        {running&&(
          <button className="btn" style={{flex:1,background:"#7f1d1d",color:"#fca5a5",borderColor:"#991b1b"}} onClick={()=>{stopRef.current=true;setStopped(true);}}>⛔ স্টপ</button>
        )}
        {(done||stopped)&&(
          <button className="btn" style={{flex:1,background:C.panel,color:C.muted,borderColor:C.border}} onClick={reset}>🗑 Clear</button>
        )}
      </div>
    </div>
  );
}

/* ══════════ CONTENT MANAGER ══════════ */

/* ══════════ BULK QUESTION TYPE UPDATE TAB ══════════ */
function BulkQTypeTab({push,tick}){
  const[sheet,setSheet]=useState("Study");
  const{data:raw,loading}=useFB(sheet,tick);
  const[filterSub,setFilterSub]=useState("all");
  const[filterAudience,setFilterAudience]=useState("all");
  const[filterExisting,setFilterExisting]=useState("all"); // all|missing|study|mcq|written
  const[targetType,setTargetType]=useState("Study");
  const[selected,setSelected]=useState(new Set());
  const[running,setRunning]=useState(false);
  const[progress,setProgress]=useState({done:0,total:0});

  const allQ=useMemo(()=>toArr(raw),[raw]);

  const subjects=useMemo(()=>[...new Set(allQ.map(q=>q.subject||q.Subject||"").filter(Boolean))],[allQ]);
  const audiences=useMemo(()=>[...new Set(allQ.map(q=>q.AudienceTags||q.audienceTags||"").filter(Boolean))],[allQ]);

  const filtered=useMemo(()=>{
    return allQ.filter(q=>{
      const sub=q.subject||q.Subject||"";
      const aud=q.AudienceTags||q.audienceTags||"";
      const qt=q["Question Type"]||q.QType||q.qtype||"";
      if(filterSub!=="all"&&sub!==filterSub)return false;
      if(filterAudience!=="all"&&!aud.includes(filterAudience))return false;
      if(filterExisting==="missing"&&qt)return false;
      if(filterExisting==="study"&&qt!=="Study")return false;
      if(filterExisting==="mcq"&&qt.toLowerCase()!=="mcq")return false;
      if(filterExisting==="written"&&qt.toLowerCase()!=="written")return false;
      return true;
    });
  },[allQ,filterSub,filterAudience,filterExisting]);

  // reset selection when filter changes
  useEffect(()=>setSelected(new Set()),[filtered]);

  const toggleOne=(q)=>{
    const k=q._fbKey||q.id||q.ID;
    setSelected(prev=>{const s=new Set(prev);s.has(k)?s.delete(k):s.add(k);return s;});
  };
  const toggleAll=()=>{
    if(selected.size===filtered.length){setSelected(new Set());return;}
    setSelected(new Set(filtered.map(q=>q._fbKey||q.id||q.ID)));
  };

  const runUpdate=async()=>{
    const targets=filtered.filter(q=>selected.has(q._fbKey||q.id||q.ID));
    if(!targets.length){push("warn","⚠️ কোনো প্রশ্ন সিলেক্ট করা হয়নি","");return;}
    setRunning(true);
    setProgress({done:0,total:targets.length});
    let done=0;
    const BATCH=10;
    for(let i=0;i<targets.length;i+=BATCH){
      const batch=targets.slice(i,i+BATCH);
      await Promise.all(batch.map(async q=>{
        const fkey=q._fbKey;
        if(!fkey)return;
        try{
          await fbPatch(`${sheet}/${fkey}`,{"Question Type":targetType});
          done++;
          setProgress({done,total:targets.length});
        }catch(e){push("error","ব্যর্থ",String(e?.message||e));}
      }));
    }
    push("success",`✅ ${done}টি প্রশ্ন আপডেট হয়েছে!`,`→ Question Type: ${targetType}`);
    setSelected(new Set());
    invalidate(sheet);
    setRunning(false);
  };

  const getQtColor=(qt)=>{
    if(qt==="Study")return C.green;
    if(qt&&qt.toLowerCase()==="written")return C.purple;
    if(qt&&qt.toLowerCase()==="mcq")return C.accent;
    return C.red; // missing
  };

  return(
    <div style={{padding:"0 4px"}}>
      {/* Sheet selector */}
      <div style={{display:"flex",gap:6,marginBottom:12}}>
        {["QBank","Quiz","Study"].map(s=>(
          <button key={s} className={`ftab${sheet===s?" on":""}`} onClick={()=>setSheet(s)}>{s}</button>
        ))}
      </div>

      {/* Filters */}
      <div style={{background:C.panel,borderRadius:10,padding:10,marginBottom:10,border:`1px solid ${C.border}`}}>
        <div style={{fontSize:11,color:C.muted,marginBottom:6,fontWeight:600}}>🔍 ফিল্টার</div>
        <div style={{marginBottom:7}}>
          <label style={{fontSize:11,color:C.muted,display:"block",marginBottom:3}}>Subject</label>
          <select className="inp" style={{width:"100%",fontSize:12}} value={filterSub} onChange={e=>setFilterSub(e.target.value)}>
            <option value="all">— সব Subject —</option>
            {subjects.map(s=><option key={s} value={s}>{s}</option>)}
          </select>
        </div>
        <div style={{marginBottom:7}}>
          <label style={{fontSize:11,color:C.muted,display:"block",marginBottom:3}}>Audience Tag</label>
          <select className="inp" style={{width:"100%",fontSize:12}} value={filterAudience} onChange={e=>setFilterAudience(e.target.value)}>
            <option value="all">— সব Audience —</option>
            {audiences.map(a=><option key={a} value={a}>{a}</option>)}
          </select>
        </div>
        <div>
          <label style={{fontSize:11,color:C.muted,display:"block",marginBottom:3}}>বর্তমান Question Type</label>
          <select className="inp" style={{width:"100%",fontSize:12}} value={filterExisting} onChange={e=>setFilterExisting(e.target.value)}>
            <option value="all">— সব —</option>
            <option value="missing">❌ Missing (নেই)</option>
            <option value="study">📖 Study</option>
            <option value="mcq">❓ MCQ</option>
            <option value="written">✍️ Written</option>
          </select>
        </div>
      </div>

      {/* Target type + Update button */}
      <div style={{background:C.panel,borderRadius:10,padding:10,marginBottom:10,border:`1px solid ${C.border}`}}>
        <div style={{fontSize:11,color:C.muted,marginBottom:6,fontWeight:600}}>🎯 পরিবর্তন করব</div>
        <div style={{display:"flex",gap:6,marginBottom:10}}>
          {["MCQ","Written","Study"].map(t=>(
            <button key={t} type="button"
              style={{flex:1,padding:"8px 4px",borderRadius:8,border:`1.5px solid ${targetType===t?C.accent:C.border}`,background:targetType===t?`${C.accent}22`:C.bg,color:targetType===t?C.accent:C.muted,fontWeight:700,fontSize:12,cursor:"pointer"}}
              onClick={()=>setTargetType(t)}>{t==="MCQ"?"❓ MCQ":t==="Written"?"✍️ Written":"📖 Study"}</button>
          ))}
        </div>
        <button className="btn bp" style={{width:"100%",justifyContent:"center",opacity:running?0.6:1}}
          disabled={running||selected.size===0} onClick={runUpdate}>
          {running?`⏳ ${progress.done}/${progress.total} আপডেট হচ্ছে...`:`💾 ${selected.size}টি প্রশ্ন → ${targetType} করুন`}
        </button>
      </div>

      {/* Select all + count */}
      {!loading&&(
        <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:8}}>
          <button className="btn bg" style={{fontSize:11,padding:"5px 10px"}} onClick={toggleAll}>
            {selected.size===filtered.length&&filtered.length>0?"☑️ সব বাতিল":"☐ সব সিলেক্ট"}
          </button>
          <span style={{fontSize:11,color:C.muted}}>
            {filtered.length}টি প্রশ্ন · {selected.size}টি সিলেক্ট
          </span>
        </div>
      )}

      {/* Question list */}
      {loading?<div style={{textAlign:"center",color:C.muted,padding:30}}>⏳ লোড হচ্ছে...</div>:
        filtered.map(q=>{
          const k=q._fbKey||q.id||q.ID;
          const isSel=selected.has(k);
          const qt=q["Question Type"]||q.QType||q.qtype||"";
          const qtColor=getQtColor(qt);
          return(
            <div key={k} onClick={()=>toggleOne(q)}
              style={{background:isSel?`${C.accent}18`:C.card,border:`1.5px solid ${isSel?C.accent:C.border}`,borderRadius:10,padding:"10px 12px",marginBottom:7,cursor:"pointer",transition:"all .15s"}}>
              <div style={{display:"flex",alignItems:"flex-start",gap:8}}>
                <div style={{width:18,height:18,borderRadius:4,border:`2px solid ${isSel?C.accent:C.muted}`,background:isSel?C.accent:"transparent",flexShrink:0,marginTop:2,display:"flex",alignItems:"center",justifyContent:"center"}}>
                  {isSel&&<span style={{color:"#fff",fontSize:11,fontWeight:700}}>✓</span>}
                </div>
                <div style={{flex:1,minWidth:0}}>
                  <div style={{display:"flex",alignItems:"center",gap:6,marginBottom:4}}>
                    <span style={{fontSize:10,fontWeight:700,padding:"2px 7px",borderRadius:5,background:`${qtColor}22`,color:qtColor,border:`1px solid ${qtColor}44`}}>
                      {qt||"❌ Missing"}
                    </span>
                    <span style={{fontSize:10,color:C.muted}}>{q.subject||q.Subject||""}</span>
                  </div>
                  <div style={{fontSize:12,color:C.text,lineHeight:1.4,wordBreak:"break-word"}}>
                    {(q.question||q.Question||"").slice(0,100)}{(q.question||q.Question||"").length>100?"...":""}
                  </div>
                </div>
              </div>
            </div>
          );
        })
      }
      {!loading&&filtered.length===0&&<div style={{textAlign:"center",color:C.muted,padding:30}}>কোনো প্রশ্ন পাওয়া যায়নি</div>}
    </div>
  );
}

function ContentManagerPage({push,tick,pushLayer}){
  const[tab,setTab]=useState("browse");

  const goTab=useCallback((t)=>{
    if(t==="browse"){ setTab(t); return; }
    setTab(t);
    // sub-tab খুললে layer push — back চাপলে browse এ ফিরবে
    if(pushLayer){
      const pop=pushLayer(()=>setTab("browse"));
      // tab change হলে layer remove
      return pop;
    }
  },[pushLayer]);

  return(
    <div className="page" style={{paddingTop:0}}>
      <div style={{position:"sticky",top:0,zIndex:40,background:C.bg,paddingTop:13,paddingBottom:8}}>
        <div className="atabs">
          <button className={`atab${tab==="browse"?" on":""}`} onClick={()=>setTab("browse")}>📋 Browse</button>
          <button className={`atab${tab==="rename"?" on":""}`} onClick={()=>goTab("rename")}>✏️ Rename</button>
          <button className={`atab${tab==="audience"?" on":""}`} onClick={()=>goTab("audience")}>🎯 Audience</button>
          <button className={`atab${tab==="qtype"?" on":""}`} onClick={()=>goTab("qtype")} style={{color:tab==="qtype"?C.green:undefined}}>🏷️ QType</button>
          <button className={`atab${tab==="delete"?" on":""}`} onClick={()=>goTab("delete")}>🗑️ Delete</button>
        </div>
      </div>
      {tab==="browse"&&<BrowseTab push={push} tick={tick}/>}
      {tab==="rename"&&<RenameTab push={push} tick={tick}/>}
      {tab==="audience"&&<AudienceTagRenameTab push={push} tick={tick}/>}
      {tab==="qtype"&&<BulkQTypeTab push={push} tick={tick}/>}
      {tab==="delete"&&<DeleteTab push={push} tick={tick}/>}
    </div>
  );
}

function BrowseTab({push,tick}){
  const[sheet,setSheet]=useState("QBank");
  const{data:raw,loading}=useFB(sheet,tick);
  const[search,setSearch]=useState("");
  const[filterSub,setFilterSub]=useState("all");
  const[filterAudience,setFilterAudience]=useState("all");
  const[viewMode,setViewMode]=useState("all"); // "all" | "duplicates"
  const[editing,setEditing]=useState(null);
  const[delTarget,setDelTarget]=useState(null);
  const[delLoading,setDelLoading]=useState(false);
  const[bulkDelTargets,setBulkDelTargets]=useState(null); // array of qs to bulk delete
  const[bulkDelLoading,setBulkDelLoading]=useState(false);
  const[page,setPage]=useState(0);
  const PAGE=20;

  const allQ=useMemo(()=>toArr(raw).reverse(),[raw]);
  const subjects=useMemo(()=>["all",...new Set(allQ.map(q=>(q.Subject||q.subject||"").trim()).filter(Boolean))]
  ,[allQ]);

  // Collect all unique AudienceTags from current sheet, sorted by count
  const audienceTags=useMemo(()=>{
    const map={};
    allQ.forEach(q=>{
      const tagRaw=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim();
      if(!tagRaw)return;
      tagRaw.split(",").map(t=>t.trim()).filter(Boolean).forEach(t=>{
        map[t]=(map[t]||0)+1;
      });
    });
    return Object.entries(map).sort((a,b)=>b[1]-a[1]).map(([t,c])=>({tag:t,count:c}));
  },[allQ]);

  // Duplicate detection: same Question + AudienceTags + Subject + Sub_topic
  const duplicateGroups=useMemo(()=>{
    const map={};
    allQ.forEach(q=>{
      const qtext=(q.Question||q.question||"").trim().toLowerCase();
      const atag=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim().toLowerCase();
      const subj=(q.Subject||q.subject||"").trim().toLowerCase();
      const subt=(q.Sub_topic||q.sub_topic||"").trim().toLowerCase();
      if(!qtext)return;
      const key=`${qtext}|||${atag}|||${subj}|||${subt}`;
      if(!map[key])map[key]=[];
      map[key].push(q);
    });
    // Only groups with 2+ items are duplicates
    return Object.values(map).filter(g=>g.length>1);
  },[allQ]);

  // Flat list of all duplicate questions (keep originals marked)
  const duplicateQs=useMemo(()=>{
    const seen=new Set();
    const result=[];
    duplicateGroups.forEach(group=>{
      // First item = original (newest since reversed), rest = duplicates to delete
      group.forEach((q,idx)=>{
        if(!seen.has(q._fbKey)){
          seen.add(q._fbKey);
          result.push({...q,_isDupOriginal:idx===0,_dupGroup:group.length});
        }
      });
    });
    return result;
  },[duplicateGroups]);

  const filtered=useMemo(()=>{
    let arr=viewMode==="duplicates"?duplicateQs:allQ;
    if(filterAudience!=="all"){
      arr=arr.filter(q=>{
        const tagRaw=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim();
        return tagRaw.split(",").map(t=>t.trim()).includes(filterAudience);
      });
    }
    if(filterSub!=="all")arr=arr.filter(q=>(q.Subject||q.subject||"").trim()===filterSub);
    if(search.trim()){
      const qlo=search.toLowerCase();
      arr=arr.filter(q=>[(q.Question||q.question||""),(q.Subject||q.subject||""),(q.Sub_topic||q.sub_topic||""),(q.Correct||q.correct||"")].join(" ").toLowerCase().includes(qlo));
    }
    return arr;
  },[allQ,filterSub,filterAudience,search]);

  useEffect(()=>setPage(0),[sheet,filterSub,filterAudience,search]);

  const pageSlice=useMemo(()=>filtered.slice(page*PAGE,(page+1)*PAGE),[filtered,page]);
  const totalPages=Math.ceil(filtered.length/PAGE);

  const hardDelete=async()=>{
    if(!delTarget)return;
    setDelLoading(true);
    try{
      const fkey=delTarget._fbKey;
      const qid=(delTarget.ID||delTarget.id||"").toString();
      if(fkey){await fbDelete(`${sheet}/${fkey}`);invalidate(sheet);}
      push("success","🗑️ ডিলিট!",`#${qid}`);
      setDelTarget(null);
    }catch(e){push("error","ডিলিট ব্যর্থ",String(e?.message||e||"unknown"));}
    setDelLoading(false);
  };

  const bulkDeleteDuplicates=async(qs)=>{
    if(!qs||qs.length===0)return;
    setBulkDelLoading(true);
    try{
      // ⚡ Single multi-path PATCH — deletes all duplicates in one Firebase call
      const keys=qs.map(q=>q._fbKey).filter(Boolean);
      const deleted=await fbDeleteBatch(sheet, keys);
      invalidate(sheet);
      push("success",`🗑️ ${deleted}টি duplicate ডিলিট!`,"");
      setBulkDelTargets(null);
    }catch(e){push("error","Bulk ডিলিট ব্যর্থ",String(e?.message||e||"unknown"));}
    setBulkDelLoading(false);
  };

  return(
    <>
      {/* Sheet tabs + Audience selector row */}
      <div style={{display:"flex",gap:6,marginBottom:8,alignItems:"center",flexWrap:"wrap"}}>
        {["QBank","Quiz","Study"].map(s=>(
          <button key={s} className={`ftab${sheet===s&&viewMode==="all"?" on":""}`} onClick={()=>{setSheet(s);setFilterSub("all");setFilterAudience("all");setSearch("");setViewMode("all");}}>{s}</button>
        ))}
        <button
          onClick={()=>setViewMode(v=>v==="duplicates"?"all":"duplicates")}
          style={{marginLeft:"auto",fontSize:11,padding:"4px 11px",borderRadius:20,border:`1px solid ${viewMode==="duplicates"?C.red:C.border}`,background:viewMode==="duplicates"?C.red+"22":"transparent",color:viewMode==="duplicates"?C.red:C.muted,cursor:"pointer",fontWeight:700,display:"flex",alignItems:"center",gap:4}}>
          🔁 Duplicate {duplicateQs.length>0&&<span style={{fontSize:9,background:C.red,color:"#fff",borderRadius:10,padding:"1px 5px"}}>{duplicateQs.length}</span>}
        </button>
      </div>
      {/* Duplicate mode header */}
      {viewMode==="duplicates"&&(
        <div style={{background:C.red+"15",border:`1px solid ${C.red}33`,borderRadius:10,padding:"8px 12px",marginBottom:8}}>
          <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",flexWrap:"wrap",gap:6}}>
            <div>
              <div style={{fontSize:12,fontWeight:700,color:C.red}}>🔁 Duplicate প্রশ্ন</div>
              <div style={{fontSize:10,color:C.muted,marginTop:2}}>{duplicateGroups.length}টি গ্রুপে {duplicateQs.length}টি duplicate পাওয়া গেছে</div>
              <div style={{fontSize:10,color:C.muted,marginTop:1}}>একই Question + Audience + Subject + Sub-topic হলে duplicate গণনা হয়</div>
            </div>
            {duplicateGroups.length>0&&(
              <button
                onClick={()=>{
                  // Select all non-original (keep first of each group, delete rest)
                  const toDelete=duplicateGroups.flatMap(g=>g.slice(1));
                  setBulkDelTargets(toDelete);
                }}
                style={{fontSize:11,padding:"5px 12px",borderRadius:8,background:C.red+"22",color:C.red,border:`1px solid ${C.red}44`,fontWeight:700,cursor:"pointer"}}>
                🗑️ সব duplicate ডিলিট ({duplicateGroups.reduce((a,g)=>a+g.length-1,0)}টি)
              </button>
            )}
          </div>
        </div>
      )}
      {/* Audience Tag filter */}
      <div style={{marginBottom:8}}>
        <div style={{fontSize:10,color:C.muted,fontWeight:700,marginBottom:5,letterSpacing:".5px"}}>🎯 AUDIENCE</div>
        <div style={{display:"flex",gap:6,flexWrap:"wrap"}}>
          <button
            onClick={()=>setFilterAudience("all")}
            style={{fontSize:11,padding:"4px 12px",borderRadius:20,border:`1px solid ${filterAudience==="all"?C.accent:C.border}`,background:filterAudience==="all"?C.accent+"22":"transparent",color:filterAudience==="all"?C.accent:C.muted,cursor:"pointer",fontWeight:700}}>
            🌐 All {filterAudience==="all"&&allQ.length>0&&<span style={{fontSize:9,opacity:.7}}>({allQ.length})</span>}
          </button>
          {audienceTags.map(({tag,count})=>(
            <button key={tag}
              onClick={()=>setFilterAudience(filterAudience===tag?"all":tag)}
              style={{fontSize:11,padding:"4px 12px",borderRadius:20,border:`1px solid ${filterAudience===tag?C.accent:C.border}`,background:filterAudience===tag?C.accent+"22":"transparent",color:filterAudience===tag?C.accent:C.muted,cursor:"pointer",fontWeight:filterAudience===tag?700:500}}>
              {tag} <span style={{fontSize:9,opacity:.6}}>({count})</span>
            </button>
          ))}
          {audienceTags.length===0&&!loading&&<span style={{fontSize:11,color:C.muted,fontStyle:"italic"}}>কোনো audience tag নেই</span>}
        </div>
      </div>
      <div className="sw" style={{marginBottom:8}}>
        <span className="si">🔍</span>
        <input className="inp" placeholder="প্রশ্ন, বিষয়..." value={search} onChange={e=>setSearch(e.target.value)}/>
      </div>
      {subjects.length>2&&(
        <div className="ftabs" style={{marginBottom:8}}>
          {subjects.slice(0,8).map(s=>(
            <button key={s} className={`ftab${filterSub===s?" on":""}`} onClick={()=>setFilterSub(s)}>{s==="all"?"সব":s}</button>
          ))}
        </div>
      )}
      <div style={{fontSize:11,color:C.muted,marginBottom:8,display:"flex",justifyContent:"space-between"}}>
        <span>{loading?"⏳":`${filtered.length} / ${allQ.length}টি`}</span>
        {totalPages>1&&<span style={{color:C.accent}}>{page+1}/{totalPages}</span>}
      </div>
      {loading&&!raw?[...Array(4)].map((_,i)=><div key={i} className="sk"/>):
       filtered.length===0?<div className="empty"><div className="ei">📋</div><p>কিছু নেই</p></div>:
       pageSlice.map((q,i)=>{
        const qid=(q.ID||q.id||"").toString();
        const qtext=(q.Question||q.question||"(নোট)").slice(0,80);
        const sub=(q.Subject||q.subject||"—");
        const tp=(q.Sub_topic||q.sub_topic||"");
        const qt=(q.QType||q.qtype||"MCQ").toLowerCase();
        const isDup=viewMode==="duplicates";
        const isOriginal=q._isDupOriginal;
        return(
          <div key={q._fbKey||i} className="qcard" style={isDup?{border:`1.5px solid ${isOriginal?C.green:C.red}44`,background:isOriginal?C.green+"08":C.red+"08"}:{}}>
            <div style={{display:"flex",gap:6,marginBottom:5,alignItems:"flex-start"}}>
              <span className={`qtag ${qt==="written"?"qtag-wr":"qtag-mcq"}`}>{qt==="written"?"✍️":"❓"}</span>
              <span style={{fontSize:9,color:C.muted,marginTop:1}}>#{qid}</span>
              {isDup&&(
                <span style={{fontSize:9,padding:"2px 7px",borderRadius:8,background:isOriginal?C.green+"22":C.red+"22",color:isOriginal?C.green:C.red,fontWeight:700,border:`1px solid ${isOriginal?C.green:C.red}44`}}>
                  {isOriginal?`✅ Original (${q._dupGroup}টি)`:"🔁 Duplicate"}
                </span>
              )}
              <div style={{flex:1}}/>
              <button className="btn" style={{padding:"3px 9px",fontSize:10,background:C.accent+"22",color:C.accent,border:`1px solid ${C.accent}33`}} onClick={()=>setEditing(q)}>✏️</button>
              <button className="btn" style={{padding:"3px 9px",fontSize:10,background:C.red+"22",color:C.red,border:`1px solid ${C.red}33`}} onClick={()=>setDelTarget(q)}>🗑️</button>
            </div>
            <div className="qcard-q">{qtext}{qtext.length>=80?"…":""}</div>
            <div className="qcard-meta">
              <span className="qtag qtag-sub">📚 {sub}</span>
              {tp&&<span className="qtag qtag-tp">📌 {tp.slice(0,25)}</span>}
              {(q.AudienceTags||q.audienceTags||q.audience_tags)&&(
                <span style={{fontSize:9,padding:"2px 7px",borderRadius:10,background:C.accent+"18",color:C.accent,border:`1px solid ${C.accent}33`,fontWeight:700}}>
                  🎯 {(q.AudienceTags||q.audienceTags||q.audience_tags).toString().slice(0,30)}
                </span>
              )}
            </div>
          </div>
        );
       })
      }
      {totalPages>1&&(
        <div style={{display:"flex",gap:6,justifyContent:"center",marginTop:8}}>
          <button className="btn bg" disabled={page===0} onClick={()=>setPage(p=>p-1)}>← আগে</button>
          <span style={{padding:"7px 12px",fontSize:11,color:C.muted}}>{page+1} / {totalPages}</span>
          <button className="btn bg" disabled={page>=totalPages-1} onClick={()=>setPage(p=>p+1)}>পরে →</button>
        </div>
      )}
      {editing&&<InlineEditModal q={editing} sheet={sheet} onClose={()=>setEditing(null)} onSaved={()=>{setEditing(null);invalidate(sheet);}} push={push}/>}
      {delTarget&&<DeleteWarningModal
        title="এই প্রশ্নটি ডিলিট করবেন?"
        description={`"${(delTarget.Question||delTarget.question||"নোট").slice(0,60)}…" Firebase ও Google Sheet থেকে মুছে যাবে।`}
        onConfirm={hardDelete} onCancel={()=>setDelTarget(null)} loading={delLoading}
      />}
      {bulkDelTargets&&<DeleteWarningModal
        title={`🗑️ ${bulkDelTargets.length}টি Duplicate ডিলিট করবেন?`}
        description={`এগুলো হলো duplicate কপি। Original গুলো রেখে বাকি ${bulkDelTargets.length}টি Firebase থেকে মুছে যাবে।`}
        onConfirm={()=>bulkDeleteDuplicates(bulkDelTargets)} onCancel={()=>setBulkDelTargets(null)} loading={bulkDelLoading}
      />}
    </>
  );
}

function InlineEditModal({q,sheet,onClose,onSaved,push}){
  useModalBack(onClose);
  const[saving,setSaving]=useState(false);
  const rawQt=(q["Question Type"]||q.QType||q.qtype||"MCQ");
  const initQt=rawQt==="Study"?"Study":rawQt.toLowerCase()==="written"?"Written":"MCQ";
  const[questionType,setQuestionType]=useState(initQt);
  const qt=questionType==="Study"?"study":questionType==="Written"?"written":"mcq";
  const[question,setQuestion]=useState(q.Question||q.question||"");
  // Firebase এ option field নানা নামে থাকতে পারে — সব check করো
  const _o=(k1,k2,k3,k4)=>q[k1]||q[k2]||q[k3]||q[k4]||"";
  const[opt1,setOpt1]=useState(_o("Opt1","opt1","Option1","option1"));
  const[opt2,setOpt2]=useState(_o("Opt2","opt2","Option2","option2"));
  const[opt3,setOpt3]=useState(_o("Opt3","opt3","Option3","option3"));
  const[opt4,setOpt4]=useState(_o("Opt4","opt4","Option4","option4"));
  const[correct,setCorrect]=useState(q.Correct||q.correct||"");
  const[explanation,setExplanation]=useState(q.Explanation||q.explanation||"");
  const[technique,setTechnique]=useState(q.Technique||q.technique||"");
  const qid=(q.ID||q.id||"").toString();
  const ac=qt==="written"?C.purple:sheet==="Study"?C.green:C.accent;

  const save=async()=>{
    setSaving(true);
    try{
      const fkey=q._fbKey;
      let patch={};
      if(questionType==="Study"){
        patch={Question:question,Correct:correct,Explanation:explanation,Technique:technique,"Question Type":"Study"};
      } else if(questionType==="Written"){
        patch={Question:question,Explanation:explanation,Technique:technique,"Question Type":"Written"};
      } else {
        // Firebase এ যে key name আছে সেটাই use করো
        const o1k=q.Opt1!=null?"Opt1":q.opt1!=null?"opt1":q.Option1!=null?"Option1":q.option1!=null?"option1":"Option1";
        const o2k=o1k.replace("1","2"),o3k=o1k.replace("1","3"),o4k=o1k.replace("1","4");
        patch={Question:question,[o1k]:opt1,[o2k]:opt2,[o3k]:opt3,[o4k]:opt4,Correct:correct,Explanation:explanation,Technique:technique,"Question Type":"MCQ"};
      }
      if(fkey)await fbPatch(`${sheet}/${fkey}`,patch);
      // Sync all changed fields to Google Sheet
      const syncFields=[
        ["question",question],
        ["explanation",explanation],
        ["technique",technique],
        ["correct",correct],
        ["opt1",opt1],["opt2",opt2],["opt3",opt3],["opt4",opt4],
      ];
      syncFields.forEach(([f,v])=>{
        // Sheet sync → GAS standalone handles this
      });
      push("success","✅ আপডেট!",`#${qid}`);
      onSaved();
    }catch(e){push("error","Edit ব্যর্থ",String(e?.message||e||"unknown"));}
    setSaving(false);
  };

  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div style={{display:"flex",alignItems:"center",gap:8,marginBottom:12}}>
          <span style={{background:`${ac}22`,color:ac,border:`1px solid ${ac}44`,borderRadius:6,padding:"3px 9px",fontSize:11,fontWeight:700}}>
            {sheet==="Study"?"📖 Study":qt==="written"?"✍️ Written":"❓ MCQ"}
          </span>
          <span style={{fontSize:10,color:C.muted}}>#{qid} · {sheet}</span>
          <div style={{marginLeft:"auto",display:"flex",gap:6}}>
            <button className="btn bg" style={{padding:"5px 12px",fontSize:11}} onClick={onClose} disabled={saving}>বাতিল</button>
            <button className="btn bp" style={{padding:"5px 14px",fontSize:11,fontWeight:700}} onClick={save} disabled={saving}>
              {saving?"⏳":"💾"} সেভ
            </button>
          </div>
        </div>
        <div className="fld" style={{marginBottom:10}}>
          <label style={{marginBottom:6,display:"block"}}>🏷️ Question Type</label>
          <div style={{display:"flex",gap:6}}>
            {["MCQ","Written","Study"].map(t=>(
              <button key={t} type="button"
                style={{flex:1,padding:"7px 4px",borderRadius:8,border:`1.5px solid ${questionType===t?C.accent:C.border}`,background:questionType===t?`${C.accent}22`:C.panel,color:questionType===t?C.accent:C.muted,fontWeight:700,fontSize:12,cursor:"pointer"}}
                onClick={()=>setQuestionType(t)}>{t==="MCQ"?"❓ MCQ":t==="Written"?"✍️ Written":"📖 Study"}</button>
            ))}
          </div>
        </div>
        <div className="fld"><label>প্রশ্ন</label><textarea className="ta" value={question} onChange={e=>setQuestion(e.target.value)} style={{minHeight:70}}/></div>
        {questionType!=="Study"&&qt!=="written"&&<>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:7,marginBottom:10}}>
            {[[opt1,setOpt1,"A"],[opt2,setOpt2,"B"],[opt3,setOpt3,"C"],[opt4,setOpt4,"D"]].map(([v,sv,lbl])=>(
              <div key={lbl} className="fld" style={{margin:0}}><label>{lbl}</label><input className="inp" value={v} onChange={e=>sv(e.target.value)}/></div>
            ))}
          </div>
          <div className="fld">
            <label>✅ সঠিক উত্তর</label>
            <div style={{display:"flex",gap:4,flexWrap:"wrap",marginBottom:5}}>
              {[opt1,opt2,opt3,opt4].filter(Boolean).map((o,i)=>(
                <button key={i} type="button" className={`cc${correct===o?" on":""}`} onClick={()=>setCorrect(o)}>{o.slice(0,14)}</button>
              ))}
            </div>
            <input className="inp" value={correct} onChange={e=>setCorrect(e.target.value)}/>
          </div>
        </>}
        {questionType==="Study"&&<div className="fld"><label>✅ উত্তর</label><textarea className="ta" value={correct} onChange={e=>setCorrect(e.target.value)} style={{minHeight:60}}/></div>}
        <div className="fld"><label>📖 Explanation</label><textarea className="ta" value={explanation} onChange={e=>setExplanation(e.target.value)} style={{minHeight:60}}/></div>
        <div className="fld"><label>💡 Technique</label><textarea className="ta" value={technique} onChange={e=>setTechnique(e.target.value)} style={{minHeight:45}}/></div>
        <div style={{display:"flex",gap:7,marginTop:12}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onClose}>বাতিল</button>
          <button className="btn bp" style={{flex:2,justifyContent:"center"}} disabled={saving} onClick={save}>
            {saving?"⏳ সেভ হচ্ছে...":"💾 সেভ করুন"}
          </button>
        </div>
      </div>
    </div>
  );
}

function RenameTab({push,tick}){
  const[sheet,setSheet]=useState("QBank");
  const[type,setType]=useState("subject");
  const{data:raw,loading}=useFB(sheet,tick);
  const[renameTarget,setRenameTarget]=useState(null);
  const[newName,setNewName]=useState("");
  const[renaming,setRenaming]=useState(false);

  const allQ=useMemo(()=>toArr(raw),[raw]);

  const list=useMemo(()=>{
    const map={};
    allQ.forEach(q=>{
      let key="";
      if(type==="subject")key=(q.Subject||q.subject||"").trim();
      else if(type==="topic")key=(q.Topic||q.topic||"").trim()||(q.Sub_topic||q.sub_topic||"").split(" > ")[0].trim();
      else key=(q.Sub_topic||q.sub_topic||"").trim();
      if(key)map[key]=(map[key]||0)+1;
    });
    return Object.entries(map).sort((a,b)=>b[1]-a[1]);
  },[allQ,type]);

  const doRename=async()=>{
    if(!newName.trim()||!renameTarget){push("warn","নতুন নাম দিন","");return;}
    if(newName.trim()===renameTarget.name){push("info","একই নাম","");return;}
    setRenaming(true);
    try{
      const oldName=renameTarget.name;
      const nName=newName.trim();

      const affected=allQ.filter(q=>{
        if(type==="subject")return(q.Subject||q.subject||"").trim()===oldName;
        if(type==="topic")return(q.Topic||q.topic||"").trim()===oldName||(q.Sub_topic||q.sub_topic||"").split(" > ")[0].trim()===oldName;
        return(q.Sub_topic||q.sub_topic||"").trim()===oldName;
      });

      // ⚡ Parallel batch PATCH (20 concurrent) — much faster than serial
      const patchItems=affected.map(q=>{
        const fkey=q._fbKey;if(!fkey)return null;
        let data;
        if(type==="subject"){
          data={Subject:nName};
        } else if(type==="topic"){
          data={Topic:nName};
          const st=q.Sub_topic||q.sub_topic||"";
          if(st.includes(" > ")){const parts=st.split(" > ");if(parts[0].trim()===oldName)data.Sub_topic=`${nName} > ${parts.slice(1).join(" > ")}`;}
        } else {
          data={Sub_topic:nName};
        }
        return {path:`${sheet}/${fkey}`,data};
      }).filter(Boolean);
      const done=await fbPatchBatch(patchItems);
      invalidate(sheet);

      // GAS renameField skipped — Firebase already updated above
      push("success","✅ Rename সম্পন্ন!",`"${oldName}" → "${nName}" · ${done}টি`);
      setRenameTarget(null);setNewName("");
    }catch(e){push("error","Rename ব্যর্থ",String(e?.message||e||"unknown error"));}
    setRenaming(false);
  };

  return(
    <>
      <div style={{display:"flex",gap:6,marginBottom:8}}>
        {["QBank","Quiz","Study"].map(s=>(
          <button key={s} className={`ftab${sheet===s?" on":""}`} onClick={()=>setSheet(s)}>{s}</button>
        ))}
      </div>
      <div className="atabs" style={{marginBottom:10}}>
        <button className={`atab${type==="subject"?" on":""}`} onClick={()=>setType("subject")}>📚 Subject</button>
        <button className={`atab${type==="topic"?" on":""}`} onClick={()=>setType("topic")}>📂 Topic</button>
        <button className={`atab${type==="subtopic"?" on":""}`} onClick={()=>setType("subtopic")}>📌 Subtopic</button>
      </div>
      <div style={{fontSize:11,color:C.muted,marginBottom:10}}>{loading?"⏳":`${list.length}টি · ক্লিক করে রিনেম করুন`}</div>
      {loading&&!raw?[...Array(4)].map((_,i)=><div key={i} className="sk" style={{height:46}}/>):
       list.length===0?<div className="empty"><div className="ei">📂</div><p>কিছু নেই</p></div>:
       list.map(([name,count])=>(
        <div key={name} className="rename-row" onClick={()=>{setRenameTarget({name,count});setNewName(name);}}>
          <div className="rename-name">{name}</div>
          <div className="rename-count">{count}টি</div>
          <button className="btn" style={{padding:"4px 10px",fontSize:10,background:C.accent+"20",color:C.accent,border:`1px solid ${C.accent}30`}}>✏️</button>
        </div>
       ))
      }
      {renameTarget&&(
        <RenameModal
          type={type}
          target={renameTarget}
          newName={newName}
          setNewName={setNewName}
          onCancel={()=>{setRenameTarget(null);setNewName("");}}
          onRename={doRename}
          renaming={renaming}
        />
      )}
    </>
  );
}

function RenameModal({type,target,newName,setNewName,onCancel,onRename,renaming}){
  useModalBack(onCancel);
  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div style={{fontSize:13,fontWeight:700,marginBottom:4}}>✏️ Rename {type}</div>
        <div style={{background:`${C.yellow}12`,border:`1px solid ${C.yellow}30`,borderRadius:9,padding:"8px 11px",marginBottom:12,fontSize:11}}>
          <span style={{color:C.yellow,fontWeight:700}}>⚠️ </span>
          <span style={{color:C.muted}}><b style={{color:C.text}}>{target.count}টি</b> প্রশ্নে Firebase ও Sheet-এ আপডেট হবে।</span>
        </div>
        <div className="fld"><label>পুরোনো নাম</label><div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:9,padding:"9px 12px",fontSize:13,color:C.muted}}>{target.name}</div></div>
        <div className="fld"><label>নতুন নাম</label><input className="inp" value={newName} onChange={e=>setNewName(e.target.value)}/></div>
        <div style={{display:"flex",gap:7}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onCancel} disabled={renaming}>বাতিল</button>
          <button className="btn bp" style={{flex:2,justifyContent:"center"}} onClick={onRename} disabled={renaming}>{renaming?"⏳ আপডেট হচ্ছে...":"✅ Rename"}</button>
        </div>
      </div>
    </div>
  );
}

/* ══════════ AUDIENCE TAG RENAME TAB ══════════ */
function AudienceTagRenameTab({push,tick}){
  const SHEETS=["QBank","Quiz","Study"];

  // Load all 3 sheets
  const{data:qbRaw,loading:qbL}=useFB("QBank",tick);
  const{data:qzRaw,loading:qzL}=useFB("Quiz",tick);
  const{data:stRaw,loading:stL}=useFB("Study",tick);

  const loading=qbL||qzL||stL;

  const[renameTarget,setRenameTarget]=useState(null);
  const[newName,setNewName]=useState("");
  const[renaming,setRenaming]=useState(false);

  // ── Bulk Add Audience Tag state ──
  const[bulkMode,setBulkMode]=useState("subject"); // "subject" | "topic"
  const[bulkSheet,setBulkSheet]=useState("QBank");
  const[bulkTag,setBulkTag]=useState("");
  const[bulkSelected,setBulkSelected]=useState(new Set());
  const[bulkAdding,setBulkAdding]=useState(false);
  const[bulkProgress,setBulkProgress]=useState({done:0,total:0});

  // Subject/Topic list from selected sheet
  const bulkSheetData=useMemo(()=>{
    const raw=bulkSheet==="QBank"?qbRaw:bulkSheet==="Quiz"?qzRaw:stRaw;
    return toArr(raw);
  },[bulkSheet,qbRaw,qzRaw,stRaw]);

  const bulkGroupList=useMemo(()=>{
    const map={};
    bulkSheetData.forEach(q=>{
      const key=bulkMode==="subject"
        ?(q.Subject||q.subject||"").trim()
        :(q.Sub_topic||q.sub_topic||q.SubTopic||q.subTopic||"").trim(); // subtopic, not topic
      if(!key)return;
      if(!map[key])map[key]={count:0,hasMissing:false};
      map[key].count++;
      const tag=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim();
      if(!tag)map[key].hasMissing=true;
    });
    return Object.entries(map).sort((a,b)=>a[0].localeCompare(b[0],"bn"));
  },[bulkSheetData,bulkMode]);

  const toggleBulkSelect=useCallback((key)=>{
    setBulkSelected(prev=>{
      const next=new Set(prev);
      next.has(key)?next.delete(key):next.add(key);
      return next;
    });
  },[]);

  const selectAll=useCallback(()=>{
    setBulkSelected(new Set(bulkGroupList.map(([k])=>k)));
  },[bulkGroupList]);

  const clearSel=useCallback(()=>setBulkSelected(new Set()),[]);

  const doBulkAddTag=async()=>{
    if(!bulkTag.trim()){push("warn","Audience Tag লিখুন","");return;}
    if(bulkSelected.size===0){push("warn","Subject/Topic সিলেক্ট করুন","");return;}
    setBulkAdding(true);
    try{
      const tag=bulkTag.trim();
      const affected=bulkSheetData.filter(q=>{
        const key=bulkMode==="subject"
          ?(q.Subject||q.subject||"").trim()
          :(q.Topic||q.topic||q.Sub_topic||q.sub_topic||"").trim();
        return bulkSelected.has(key);
      });
      setBulkProgress({done:0,total:affected.length});
      const fieldKey=affected.find(q=>q.AudienceTags!=null)?"AudienceTags":
                    affected.find(q=>q.audienceTags!=null)?"audienceTags":"AudienceTags";
      const patchItems=affected.map(q=>{
        if(!q._fbKey)return null;
        return{path:`${bulkSheet}/${q._fbKey}`,data:{[fieldKey]:tag}};
      }).filter(Boolean);

      // Batch patch with progress
      const CONC=20;
      let done=0;
      for(let i=0;i<patchItems.length;i+=CONC){
        const chunk=patchItems.slice(i,i+CONC);
        await Promise.all(chunk.map(({path,data})=>fbPatch(path,data)));
        done+=chunk.length;
        setBulkProgress({done,total:affected.length});
      }
      invalidate(bulkSheet);
      push("success","✅ Audience Tag সেট!",`"${tag}" → ${done}টি question এ বসানো হয়েছে`);
      setBulkSelected(new Set());
      setBulkTag("");
    }catch(e){push("error","ব্যর্থ",e.message);}
    setBulkProgress({done:0,total:0});
    setBulkAdding(false);
  };

  // Collect all unique AudienceTags across all sheets with count & which sheets they appear in
  const tagList=useMemo(()=>{
    const map={}; // tag -> {count, sheets: {QBank:n, Quiz:n, Study:n}}
    [[qbRaw,"QBank"],[qzRaw,"Quiz"],[stRaw,"Study"]].forEach(([raw,sheet])=>{
      toArr(raw).forEach(q=>{
        const tag=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim();
        if(!tag)return;
        if(!map[tag])map[tag]={count:0,sheets:{}};
        map[tag].count++;
        map[tag].sheets[sheet]=(map[tag].sheets[sheet]||0)+1;
      });
    });
    return Object.entries(map).sort((a,b)=>b[1].count-a[1].count);
  },[qbRaw,qzRaw,stRaw]);

  const doRename=async()=>{
    if(!newName.trim()||!renameTarget){push("warn","নতুন নাম দিন","");return;}
    if(newName.trim()===renameTarget.tag){push("info","একই নাম","");return;}
    setRenaming(true);
    try{
      const oldTag=renameTarget.tag;
      const nTag=newName.trim();
      let totalUpdated=0;

      for(const sheet of SHEETS){
        const raw=sheet==="QBank"?qbRaw:sheet==="Quiz"?qzRaw:stRaw;
        const allQ=toArr(raw);
        const affected=allQ.filter(q=>{
          const t=(q.AudienceTags||q.audienceTags||q.audience_tags||"").trim();
          return t===oldTag;
        });
        if(affected.length===0)continue;

        // ⚡ Parallel batch PATCH (20 concurrent)
        const patchItems=affected.map(q=>{
          const fkey=q._fbKey;if(!fkey)return null;
          const fieldKey=q.AudienceTags!=null?"AudienceTags":q.audienceTags!=null?"audienceTags":"audience_tags";
          return {path:`${sheet}/${fkey}`,data:{[fieldKey]:nTag}};
        }).filter(Boolean);
        const sheetDone=await fbPatchBatch(patchItems);
        totalUpdated+=sheetDone;
        invalidate(sheet);
      }

      push("success","✅ Audience Tag Rename সম্পন্ন!",`"${oldTag}" → "${nTag}" · ${totalUpdated}টি কন্টেন্ট`);
      setRenameTarget(null);
      setNewName("");
    }catch(e){push("error","Rename ব্যর্থ",e.message);}
    setRenaming(false);
  };

  const bulkPct=bulkProgress.total>0?Math.round(bulkProgress.done/bulkProgress.total*100):0;

  return(
    <>
      {/* ══ BULK ADD AUDIENCE TAG ══ */}
      <div style={{background:`${C.green}10`,border:`1px solid ${C.green}30`,borderRadius:12,padding:"12px 14px",marginBottom:14}}>
        <div style={{fontWeight:700,color:C.green,marginBottom:10,fontSize:13}}>➕ Bulk Audience Tag সেট করুন</div>

        {/* Sheet selector */}
        <div style={{display:"flex",gap:6,marginBottom:10}}>
          {["QBank","Quiz","Study"].map(s=>(
            <button key={s} onClick={()=>{setBulkSheet(s);setBulkSelected(new Set());}}
              style={{flex:1,padding:"5px 0",borderRadius:8,fontSize:11,fontWeight:700,border:"none",cursor:"pointer",
                background:bulkSheet===s?C.green:"transparent",
                color:bulkSheet===s?"#fff":C.muted,
                outline:bulkSheet===s?"none":`1px solid ${C.border}`}}>
              {s}
            </button>
          ))}
        </div>

        {/* Mode: Subject or Topic */}
        <div style={{display:"flex",gap:6,marginBottom:10}}>
          <button onClick={()=>{setBulkMode("subject");setBulkSelected(new Set());}}
            style={{flex:1,padding:"5px 0",borderRadius:8,fontSize:11,fontWeight:700,border:"none",cursor:"pointer",
              background:bulkMode==="subject"?C.accent:"transparent",
              color:bulkMode==="subject"?"#fff":C.muted,
              outline:bulkMode==="subject"?"none":`1px solid ${C.border}`}}>
            📚 Subject অনুযায়ী
          </button>
          <button onClick={()=>{setBulkMode("topic");setBulkSelected(new Set());}}
            style={{flex:1,padding:"5px 0",borderRadius:8,fontSize:11,fontWeight:700,border:"none",cursor:"pointer",
              background:bulkMode==="topic"?C.accent:"transparent",
              color:bulkMode==="topic"?"#fff":C.muted,
              outline:bulkMode==="topic"?"none":`1px solid ${C.border}`}}>
            🏷️ Subtopic অনুযায়ী
          </button>
        </div>

        {/* Tag input */}
        <div style={{marginBottom:10}}>
          <input className="inp" placeholder="Audience Tag লিখুন (যেমন: Job, SSC, HSC)"
            value={bulkTag} onChange={e=>setBulkTag(e.target.value)}
            style={{width:"100%",boxSizing:"border-box"}}/>
        </div>

        {/* Select all / clear */}
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:6}}>
          <span style={{fontSize:11,color:C.muted}}>
            {bulkSelected.size>0?`${bulkSelected.size}টি সিলেক্ট`:`${bulkGroupList.length}টি ${bulkMode==="subject"?"Subject":"Subtopic"}`}
          </span>
          <div style={{display:"flex",gap:6}}>
            <button onClick={selectAll} style={{fontSize:10,padding:"3px 8px",borderRadius:6,border:`1px solid ${C.border}`,background:"transparent",color:C.muted,cursor:"pointer"}}>সব</button>
            <button onClick={clearSel} style={{fontSize:10,padding:"3px 8px",borderRadius:6,border:`1px solid ${C.border}`,background:"transparent",color:C.muted,cursor:"pointer"}}>বাদ</button>
          </div>
        </div>

        {/* Subject/Topic list */}
        <div style={{maxHeight:220,overflowY:"auto",marginBottom:10,display:"flex",flexDirection:"column",gap:4}}>
          {loading?<div style={{color:C.muted,fontSize:11,textAlign:"center",padding:8}}>⏳ লোড হচ্ছে...</div>:
           bulkGroupList.length===0?<div style={{color:C.muted,fontSize:11,textAlign:"center",padding:8}}>কিছু নেই</div>:
           bulkGroupList.map(([key,info])=>{
            const sel=bulkSelected.has(key);
            return(
              <div key={key} onClick={()=>toggleBulkSelect(key)}
                style={{display:"flex",alignItems:"center",gap:8,padding:"7px 10px",borderRadius:8,cursor:"pointer",
                  background:sel?`${C.accent}22`:C.panel,
                  border:`1px solid ${sel?C.accent:C.border}`,
                  transition:"all .15s"}}>
                <div style={{width:16,height:16,borderRadius:4,flexShrink:0,
                  background:sel?C.accent:"transparent",
                  border:`2px solid ${sel?C.accent:C.muted}`,
                  display:"flex",alignItems:"center",justifyContent:"center"}}>
                  {sel&&<span style={{color:"#fff",fontSize:10,lineHeight:1}}>✓</span>}
                </div>
                <div style={{flex:1,minWidth:0}}>
                  <div style={{fontSize:12,fontWeight:sel?700:400,color:sel?C.text:C.muted,
                    whiteSpace:"nowrap",overflow:"hidden",textOverflow:"ellipsis"}}>{key}</div>
                </div>
                <div style={{fontSize:10,color:C.muted,flexShrink:0}}>{info.count}টি</div>
                {info.hasMissing&&<span style={{fontSize:9,color:C.yellow,flexShrink:0}}>⚠️ফাঁকা</span>}
              </div>
            );
          })}
        </div>

        {/* Progress */}
        {bulkAdding&&bulkProgress.total>0&&(
          <div style={{marginBottom:8}}>
            <div style={{display:"flex",justifyContent:"space-between",fontSize:10,color:C.muted,marginBottom:3}}>
              <span>⚡ সেট হচ্ছে…</span>
              <span style={{fontWeight:700,color:C.green}}>{bulkProgress.done}/{bulkProgress.total} ({bulkPct}%)</span>
            </div>
            <div style={{height:5,background:C.border,borderRadius:5}}>
              <div style={{height:"100%",width:bulkPct+"%",background:C.green,borderRadius:5,transition:"width .3s"}}/>
            </div>
          </div>
        )}

        {/* Apply button */}
        <button onClick={doBulkAddTag} disabled={bulkAdding||bulkSelected.size===0||!bulkTag.trim()}
          style={{width:"100%",padding:"9px 0",borderRadius:10,border:"none",cursor:"pointer",fontWeight:700,fontSize:13,
            background:bulkSelected.size>0&&bulkTag.trim()?C.green:"#1a2a1a",
            color:bulkSelected.size>0&&bulkTag.trim()?"#fff":C.muted,
            transition:"all .2s"}}>
          {bulkAdding?`⏳ ${bulkPct}% হচ্ছে…`:`✅ ${bulkSelected.size>0?bulkSelected.size+"টিতে":"সিলেক্ট করুন"} Tag সেট করুন`}
        </button>
      </div>

      {/* ══ RENAME SECTION ══ */}
      <div style={{background:`${C.accent}12`,border:`1px solid ${C.accent}30`,borderRadius:10,padding:"9px 12px",marginBottom:12,fontSize:11}}>
        <div style={{fontWeight:700,color:C.accent,marginBottom:3}}>✏️ Audience Tag Rename</div>
        <div style={{color:C.muted,lineHeight:1.6}}>
          QBank, Quiz ও Study — তিনটি শিটে একসাথে AudienceTags আপডেট হবে।
        </div>
      </div>

      <div style={{fontSize:11,color:C.muted,marginBottom:10}}>
        {loading?"⏳ লোড হচ্ছে...":`${tagList.length}টি Audience Tag পাওয়া গেছে`}
      </div>

      {/* Tag list */}
      {loading?[...Array(4)].map((_,i)=><div key={i} className="sk" style={{height:56,marginBottom:7}}/>):
       tagList.length===0?
        <div className="empty"><div className="ei">🎯</div><p>কোনো AudienceTags নেই</p></div>:
        tagList.map(([tag,info])=>(
          <div key={tag} style={{
            background:C.panel,border:`1px solid ${C.border}`,borderRadius:11,
            padding:"11px 12px",marginBottom:7,
            display:"flex",alignItems:"center",gap:10
          }}>
            <div style={{flex:1,minWidth:0}}>
              <div style={{fontWeight:700,fontSize:13,marginBottom:3}}>{tag}</div>
              <div style={{display:"flex",gap:5,flexWrap:"wrap"}}>
                {Object.entries(info.sheets).map(([sh,n])=>(
                  <span key={sh} style={{
                    fontSize:9,fontWeight:700,padding:"1px 6px",borderRadius:6,
                    background:sh==="QBank"?`${C.green}20`:sh==="Quiz"?`${C.accent}20`:`${C.yellow}20`,
                    color:sh==="QBank"?C.green:sh==="Quiz"?C.accent:C.yellow,
                    border:`1px solid ${sh==="QBank"?C.green:sh==="Quiz"?C.accent:C.yellow}40`
                  }}>{sh}: {n}টি</span>
                ))}
              </div>
            </div>
            <div style={{fontWeight:700,fontSize:15,color:C.muted,minWidth:28,textAlign:"right"}}>{info.count}</div>
            <button
              className="btn"
              style={{padding:"5px 11px",fontSize:11,background:C.accent+"22",color:C.accent,border:`1px solid ${C.accent}33`,flexShrink:0}}
              onClick={()=>{setRenameTarget({tag,count:info.count});setNewName(tag);}}
            >✏️ Rename</button>
          </div>
        ))
      }

      {/* Rename Modal */}
      {renameTarget&&(
        <AudienceRenameModal
          target={renameTarget}
          newName={newName}
          setNewName={setNewName}
          onCancel={()=>{setRenameTarget(null);setNewName("");}}
          onRename={doRename}
          renaming={renaming}
        />
      )}
    </>
  );
}

function AudienceRenameModal({target,newName,setNewName,onCancel,onRename,renaming}){
  useModalBack(onCancel);
  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div style={{fontSize:14,fontWeight:700,marginBottom:4}}>🎯 Audience Tag Rename</div>

        {/* Warning */}
        <div style={{background:`${C.yellow}12`,border:`1px solid ${C.yellow}30`,borderRadius:9,padding:"8px 11px",marginBottom:12,fontSize:11}}>
          <span style={{color:C.yellow,fontWeight:700}}>⚠️ </span>
          <span style={{color:C.muted}}>
            <b style={{color:C.text}}>{target.count}টি</b> কন্টেন্টে Firebase-এ আপডেট হবে।
            <br/>ব্যবহারকারীর <b style={{color:C.text}}>classLevel</b>-এর সাথে মিল রাখুন।
          </span>
        </div>

        {/* Old name (readonly) */}
        <div className="fld">
          <label>পুরোনো Tag</label>
          <div style={{background:C.panel,border:`1px solid ${C.border}`,borderRadius:9,padding:"9px 12px",fontSize:13,color:C.muted,fontFamily:"monospace"}}>
            {target.tag}
          </div>
        </div>

        {/* New name input */}
        <div className="fld">
          <label>নতুন Tag</label>
          <input
            className="inp"
            value={newName}
            onChange={e=>setNewName(e.target.value)}
            placeholder="যেমন: Masters 1"
            style={{fontFamily:"monospace"}}
            autoFocus
          />
          {newName&&newName!==target.tag&&(
            <div style={{fontSize:10,color:C.green,marginTop:4,fontWeight:600}}>
              ✅ "{target.tag}" → "{newName}"
            </div>
          )}
        </div>

        {/* Hint */}
        <div style={{background:`${C.accent}10`,border:`1px solid ${C.accent}25`,borderRadius:8,padding:"7px 10px",marginBottom:12,fontSize:10,color:C.muted}}>
          💡 <b style={{color:C.text}}>classLevel মানগুলো:</b> Masters 1, Masters 2, Honours 1, Honours 2, Honours 3, Honours 4, Class 12, Job
        </div>

        <div style={{display:"flex",gap:7}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onCancel} disabled={renaming}>বাতিল</button>
          <button className="btn bp" style={{flex:2,justifyContent:"center"}} onClick={onRename} disabled={renaming||!newName.trim()||newName.trim()===target.tag}>
            {renaming?"⏳ আপডেট হচ্ছে...":"✅ Rename করুন"}
          </button>
        </div>
      </div>
    </div>
  );
}

function DeleteTab({push,tick}){
  const[sheet,setSheet]=useState("QBank");
  const[type,setType]=useState("subject");
  const{data:raw,loading}=useFB(sheet,tick);
  const[delTarget,setDelTarget]=useState(null);
  const[delLoading,setDelLoading]=useState(false);

  const allQ=useMemo(()=>toArr(raw),[raw]);

  const groups=useMemo(()=>{
    const map={};
    allQ.forEach(q=>{
      let key="";
      if(type==="subject")key=(q.Subject||q.subject||"").trim();
      else if(type==="topic")key=(q.Topic||q.topic||"").trim()||(q.Sub_topic||q.sub_topic||"").split(" > ")[0].trim();
      else key=(q.Sub_topic||q.sub_topic||"").trim();
      if(key)map[key]=(map[key]||[]).concat(q);
    });
    return Object.entries(map).sort((a,b)=>b[1].length-a[1].length);
  },[allQ,type]);

  const[delProgress,setDelProgress]=useState({done:0,total:0});

  const doBulkDelete=async()=>{
    if(!delTarget)return;
    setDelLoading(true);
    const[groupName,qs]=delTarget;
    setDelProgress({done:0,total:qs.length});
    try{
      // ⚡ Single multi-path PATCH call — O(1) instead of O(N) serial deletes
      const keys=qs.map(q=>q._fbKey).filter(Boolean);
      const deleted=await fbDeleteBatch(sheet, keys, (done,total)=>setDelProgress({done,total}));
      invalidate(sheet);
      push("success","🗑️ Bulk Delete!",`"${groupName}" · ${deleted}টি মুছে গেছে`);
      setDelTarget(null);
    }catch(e){push("error","Delete ব্যর্থ",e.message);}
    setDelProgress({done:0,total:0});
    setDelLoading(false);
  };

  return(
    <>
      <div style={{display:"flex",gap:6,marginBottom:8}}>
        {["QBank","Quiz","Study"].map(s=>(
          <button key={s} className={`ftab${sheet===s?" on":""}`} onClick={()=>setSheet(s)}>{s}</button>
        ))}
      </div>
      <div className="atabs" style={{marginBottom:8}}>
        <button className={`atab${type==="subject"?" on":""}`} onClick={()=>setType("subject")}>📚 Subject</button>
        <button className={`atab${type==="topic"?" on":""}`} onClick={()=>setType("topic")}>📂 Topic</button>
        <button className={`atab${type==="subtopic"?" on":""}`} onClick={()=>setType("subtopic")}>📌 Subtopic</button>
      </div>
      <div style={{background:"#ef444412",border:"1px solid #ef444330",borderRadius:9,padding:"8px 11px",marginBottom:10,fontSize:11,color:C.red}}>⚠️ পুরো Subject/Topic ডিলিট — ভেতরের সব প্রশ্ন মুছে যাবে।</div>
      {loading&&!raw?[...Array(4)].map((_,i)=><div key={i} className="sk" style={{height:46}}/>):
       groups.length===0?<div className="empty"><div className="ei">📂</div><p>কিছু নেই</p></div>:
       groups.map(([name,qs])=>(
        <div key={name} className="rename-row">
          <div className="rename-name">{name}</div>
          <div className="rename-count">{qs.length}টি</div>
          <button className="btn" style={{padding:"4px 10px",fontSize:10,background:C.red+"22",color:C.red,border:`1px solid ${C.red}33`}} onClick={()=>setDelTarget([name,qs])}>🗑️</button>
        </div>
       ))
      }
      {delTarget&&<DeleteWarningModal
        title={`"${delTarget[0]}" ডিলিট?`}
        description={`${delTarget[1].length}টি প্রশ্ন Firebase থেকে মুছে যাবে।`}
        onConfirm={doBulkDelete} onCancel={()=>setDelTarget(null)} loading={delLoading}
        progress={delProgress}
      />}
    </>
  );
}

/* ══════════ SEARCH ══════════ */
function SearchPage({push,onDetail}){
  const[q,setQ]=useState("");
  const[results,setResults]=useState(null);
  const[searching,setSearching]=useState(false);
  const deb=useRef(null);

  const doSearch=useCallback(async query=>{
    if(!query||query.length<2){setResults(null);return;}
    setSearching(true);
    const qlo=query.toLowerCase();
    try{
      const[qbankRaw,usersRaw]=await Promise.all([loadPath("QBank"),loadPath("Users")]);
      const srch=(raw,tab)=>toArr(raw).filter(q2=>{
        return[(q2.Question||q2.question||""),(q2.Subject||q2.subject||""),(q2.Correct||q2.correct||"")].join(" ").toLowerCase().includes(qlo);
      }).slice(0,8).map(q2=>({...q2,_tab:tab}));
      const uRes=toArr(usersRaw).filter(u=>(u.Name||u.name||"").toLowerCase().includes(qlo)||(u.Phone||u.phone||"").toLowerCase().includes(qlo)).slice(0,5);
      setResults({questions:srch(qbankRaw,"QBank"),users:uRes});
    }catch(e){}
    setSearching(false);
  },[]);

  const onIn=v=>{setQ(v);clearTimeout(deb.current);deb.current=setTimeout(()=>doSearch(v),400);};
  const tot=(results?.questions?.length||0)+(results?.users?.length||0);

  return(
    <div className="page">
      <div className="sw" style={{marginBottom:12}}>
        <span className="si">🔍</span>
        <input className="inp" placeholder="নাম, ফোন, প্রশ্ন..." value={q} onChange={e=>onIn(e.target.value)}/>
      </div>
      {searching&&<div style={{textAlign:"center",padding:"20px 0",color:C.muted,fontSize:12}}>⏳</div>}
      {!searching&&results&&<div style={{fontSize:11,color:C.muted,marginBottom:8}}>{tot}টি ফলাফল</div>}
      {results?.users?.length>0&&<>
        <div className="slb">👥 স্টুডেন্ট</div>
        {results.users.map((u,i)=>{
          const nm=u.Name||u.name||"অজানা",ph=u.Phone||u.phone||"—";
          const st=(u.Status||u.status||"inactive").toLowerCase();
          return(
            <div key={i} className="sri" onClick={()=>onDetail(u)}>
              <div className="av sm">{initials(nm)}</div>
              <div style={{flex:1,minWidth:0}}><div style={{fontWeight:600,fontSize:12}}>{nm}</div><div style={{fontSize:10,color:C.muted}}>📱 {ph}</div></div>
              <span className={`pill ${st==="active"?"pa":"pi"}`}>{st==="active"?"✅":"🔴"}</span>
            </div>
          );
        })}
      </>}
      {results?.questions?.length>0&&<>
        <div className="slb">❓ প্রশ্ন</div>
        {results.questions.map((q2,i)=>(
          <div key={i} className="sri" style={{cursor:"default"}}>
            <span className="stag">{q2._tab}</span>
            <div style={{flex:1,minWidth:0}}>
              <div style={{fontSize:12,fontWeight:600,lineHeight:1.4}}>{(q2.Question||q2.question||"").slice(0,80)}</div>
              {(q2.Correct||q2.correct)&&<div style={{fontSize:10,color:C.green,marginTop:1}}>✅ {q2.Correct||q2.correct}</div>}
            </div>
          </div>
        ))}
      </>}
      {!searching&&results&&tot===0&&<div className="empty"><div className="ei">🔍</div><p>পাওয়া যায়নি</p></div>}
      {!results&&<div className="empty" style={{paddingTop:28}}><div className="ei">🔍</div><p style={{fontSize:12}}>সব কিছু খুঁজুন</p></div>}
    </div>
  );
}

/* ══════════ NOTIFY PAGE ══════════ */
function NotifyPage({push,tick}){
  const[title,setTitle]=useState("");
  const[body,setBody]=useState("");
  const[sending,setSending]=useState(false);
  const[hist,setHist]=useState([]);
  const[q,setQ]=useState("");
  const[selUser,setSelUser]=useState(null);
  const{data:usersRaw}=useFB("Users",tick);
  const userList=useMemo(()=>toArr(usersRaw),[usersRaw]);
  const results=useMemo(()=>{
    const s=q.trim().toLowerCase();
    if(!s)return[];
    return userList.filter(u=>{
      const nm=(u.Name||u.name||"").toLowerCase();
      const ph=(u.Phone||u.phone||"").toString().replace(/^'+/,"");
      return nm.includes(s)||ph.includes(s);
    }).slice(0,8);
  },[q,userList]);

  // Firebase থেকে notification history load করো
  useEffect(()=>{
    loadPath("AdminNotifHistory").then(raw=>{
      if(!raw)return;
      const arr=toArr(raw).sort((a,b)=>(b.sentAt||0)-(a.sentAt||0));
      setHist(arr);
    }).catch(()=>{});
  },[tick]);

  const send=async()=>{
    if(!title||!body){push("warn","তথ্য দিন","");return;}
    setSending(true);
    try{
      const raw=await loadPath("Users");
      const users=toArr(raw);
      const active=users.filter(u=>(u.Status||u.status||"").toLowerCase()==="active");
      const ts=nowTs();
      const notifKey=`broadcast_${Date.now()}`;
      await Promise.all(active.map(u=>{
        const phK=phoneKey(u.Phone||u.phone||"");
        if(!phK)return Promise.resolve();
        return fbSet(`Notifications/${phK}/${notifKey}`,{type:"broadcast",title,body,time:ts,read:false});
      }));
      // FCM direct — সব active user কে একসাথে (20 concurrent)
      const fcmSent = await fcmBroadcast(title, body, active);
      // ✅ Firebase এ history save করো — restart করলেও থাকবে
      const histKey=`notif_${Date.now()}`;
      await fbSet(`AdminNotifHistory/${histKey}`,{
        type:"broadcast", title, body, time:ts, sentAt:Date.now(),
        totalUsers:active.length, fcmSent, sentBy:"admin"
      });
      push("success","📣 পাঠানো হয়েছে!",`Notification: ${active.length}জন · FCM: ${fcmSent}জন`);
      setHist(p=>[{title,body,time:ts,count:active.length,fcmSent,totalUsers:active.length},...p.slice(0,49)]);
      setTitle("");setBody("");
    }catch(e){push("error","ব্যর্থ",String(e?.message||e||""));}
    setSending(false);
  };

  return(
    <div className="page">
      <div className="card">
        <div className="ct">👤 একজনকে নোটিফাই করুন</div>
        <div className="fld">
          <label>স্টুডেন্ট খুঁজুন (নাম/ফোন)</label>
          <input className="inp" placeholder="নাম বা ফোন নাম্বার লিখুন..." value={q} onChange={e=>{setQ(e.target.value);setSelUser(null);}}/>
        </div>
        {q&&!selUser&&(
          results.length===0?<div style={{fontSize:11,color:C.muted,padding:"4px 2px"}}>কেউ পাওয়া যায়নি</div>:
          results.map((u,i)=>(
            <div key={i} className="nr" style={{cursor:"pointer"}} onClick={()=>{setSelUser(u);setQ(u.Name||u.name||u.Phone||u.phone||"");}}>
              <div className="nd o"/>
              <div className="nc"><div className="nt">{u.Name||u.name||"—"}</div><div className="ns">📱 {(u.Phone||u.phone||"").toString().replace(/^'+/,"")}</div></div>
            </div>
          ))
        )}
        {selUser&&(
          <div style={{marginTop:8}}>
            <NotifyModal user={selUser} push={push} inline onClose={()=>{}}/>
            <button className="btn bg" style={{marginTop:6,justifyContent:"center",width:"100%"}} onClick={()=>{setSelUser(null);setQ("");}}>✖️ বাতিল</button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="ct">📣 সবাইকে Broadcast</div>
        <div className="fld"><label>শিরোনাম</label><input className="inp" placeholder="Title..." value={title} onChange={e=>setTitle(e.target.value)}/></div>
        <div className="fld"><label>বার্তা</label><textarea className="ta" placeholder="Message..." value={body} onChange={e=>setBody(e.target.value)}/></div>
        <button className="btn bp bb" onClick={send} disabled={sending}>{sending?"⏳ পাঠানো হচ্ছে...":"📣 সবাইকে পাঠান"}</button>
      </div>
      {hist.length>0&&<div className="card"><div className="ct">ইতিহাস</div>{hist.map((h,i)=>(
        <div key={i} className="nr">
          <div className={`nd ${i===0?"n":"o"}`}/>
          <div className="nc"><div className="nt">{h.title}</div><div className="ns">{h.body?.slice(0,55)}<span style={{color:C.accent}}> · {h.count}জন</span></div></div>
          <div className="ntm">{h.time}</div>
        </div>
      ))}</div>}
    </div>
  );
}

function NotifyModal({user,onClose,push,inline}){
  useModalBack(inline?()=>{}:onClose);
  const[title,setTitle]=useState("");
  const[body,setBody]=useState("");
  const[sending,setSending]=useState(false);
  const nm=user.Name||user.name||"স্টুডেন্ট";

  const send=async()=>{
    if(!title||!body)return;
    setSending(true);
    try{
      const phone=(user.Phone||user.phone||"").toString();
      const phK=phoneKey(phone);
      await fbSet(`Notifications/${phK}/notif_${Date.now()}`,{type:"personal",title,body,time:nowTs(),read:false});
      // FCM direct — instant
      const fcmOk = await fcmNotifyPhone(phone, title, body, {type:"personal"});
      push("success","✅ পাঠানো হয়েছে",(fcmOk?"📲 FCM ✓ ":"📲 FCM ✗ ")+nm);
      if(!inline)onClose();
    }catch(e){push("error","ব্যর্থ",String(e?.message||e||""));}
    setSending(false);
  };

  if(inline)return(
    <div className="card">
      <div className="ct">📣 ব্যক্তিগত নোটিফিকেশন</div>
      <div className="fld"><label>শিরোনাম</label><input className="inp" placeholder="Title..." value={title} onChange={e=>setTitle(e.target.value)}/></div>
      <div className="fld"><label>বার্তা</label><textarea className="ta" placeholder="Message..." value={body} onChange={e=>setBody(e.target.value)}/></div>
      <button className="btn bp bb" onClick={send} disabled={sending}>{sending?"⏳":"📨 পাঠান"}</button>
    </div>
  );
  return(
    <div className="ovl">
      <div className="modal">
        <div className="mh"/>
        <div className="mt">📣 {nm}</div>
        <div style={{fontSize:11,color:C.muted,marginBottom:11}}>📱 {user.Phone||user.phone}</div>
        <div className="fld"><label>শিরোনাম</label><input className="inp" placeholder="Title..." value={title} onChange={e=>setTitle(e.target.value)}/></div>
        <div className="fld"><label>বার্তা</label><textarea className="ta" placeholder="Message..." value={body} onChange={e=>setBody(e.target.value)}/></div>
        <div style={{display:"flex",gap:7}}>
          <button className="btn bg" style={{flex:1,justifyContent:"center"}} onClick={onClose}>বাতিল</button>
          <button className="btn bp" style={{flex:1,justifyContent:"center"}} onClick={send} disabled={sending}>{sending?"⏳":"📨 পাঠান"}</button>
        </div>
      </div>
    </div>
  );
}

/* ══════════ ROOT APP ══════════ */

/* ══════════ TECHNIQUES PAGE ══════════ */
function TechniquesPage({push,tick}){
  const{data:raw,loading}=useFB("UserTechniques",tick);
  const[tab,setTab]=useState("pending");
  const[busy,setBusy]=useState(null);
  const[detail,setDetail]=useState(null);
  const[done,setDone]=useState(new Set());

  // Flatten nested structure: { questionId: { pushKey: {...} } }
  const allTechniques=useMemo(()=>{
    if(!raw||typeof raw!=="object")return[];
    const list=[];
    Object.entries(raw).forEach(([qId,entries])=>{
      if(!entries||typeof entries!=="object")return;
      Object.entries(entries).forEach(([pushKey,data])=>{
        if(!data||typeof data!=="object")return;
        list.push({...data,_qId:qId,_pushKey:pushKey,_path:`UserTechniques/${qId}/${pushKey}`});
      });
    });
    return list.sort((a,b)=>(b.timestamp||0)-(a.timestamp||0));
  },[raw]);

  const filtered=useMemo(()=>
    allTechniques.filter(t=>{
      if(done.has(t._pushKey))return false;
      if(tab==="pending")return t.isPublic&&(t.status==="pending"||!t.status);
      if(tab==="approved")return t.isPublic&&t.status==="approved";
      if(tab==="rejected")return t.isPublic&&t.status==="rejected";
      return true; // "all"
    })
  ,[allTechniques,tab,done]);

  const pendingCount=useMemo(()=>
    allTechniques.filter(t=>!done.has(t._pushKey)&&t.isPublic&&(t.status==="pending"||!t.status)).length
  ,[allTechniques,done]);

  const updateStatus=async(t,status)=>{
    const key=t._pushKey;
    setBusy(key);
    try{
      await fbPatch(`UserTechniques/${t._qId}/${key}`,{status});
      invalidate("UserTechniques");
      setDone(p=>new Set([...p,key]));
      push("success",status==="approved"?"✅ Approved!":"❌ Rejected!",t.userName||"ব্যবহারকারী");

      // ── ব্যবহারকারীকে instant notification পাঠাও ──
      const phone=(t.userId||t.phone||t.Phone||"").toString().replace(/^'+/,"").trim();
      if(phone){
        const notifTitle=status==="approved"?"✅ টেকনিক Approved!":"❌ টেকনিক Rejected";
        const notifBody=status==="approved"
          ? "আপনার শেয়ার করা টেকনিকটি অনুমোদিত হয়েছে এবং সবাই দেখতে পারবে। ধন্যবাদ! 🎉"
          : "আপনার শেয়ার করা টেকনিকটি এই মুহূর্তে গ্রহণ করা হয়নি।";
        const phK=phoneKey(phone);
        fbSet(`Notifications/${phK}/notif_${Date.now()}`,{type:"technique_"+status,title:notifTitle,body:notifBody,questionId:t._qId||"",time:nowTs(),read:false}).catch(()=>{});
        // FCM direct — instant
        fcmNotifyPhone(phone, notifTitle, notifBody, {type:"technique_"+status, questionId:t._qId||""}).catch(()=>{});
      }
    }catch(e){push("error","ব্যর্থ",e.message);}
    setBusy(null);
  };

  const deleteT=async(t)=>{
    setBusy(t._pushKey);
    try{
      await fbDelete(`UserTechniques/${t._qId}/${t._pushKey}`);
      invalidate("UserTechniques");
      setDone(p=>new Set([...p,t._pushKey]));
      push("success","🗑️ ডিলিট!","");
    }catch(e){push("error","ব্যর্থ",e.message);}
    setBusy(null);
  };

  const tsFormat=ts=>{
    if(!ts)return"—";
    const d=new Date(parseInt(ts));
    return d.toLocaleString("bn-BD",{timeZone:"Asia/Dhaka",dateStyle:"short",timeStyle:"short"});
  };

  return(
    <div className="page">
      {/* Summary row */}
      <div className="sg" style={{marginBottom:10}}>
        <div className="sc tb" data-icon="⏳">
          <div className="sl">পেন্ডিং</div>
          <div className="sv sv-b">{pendingCount}</div>
        </div>
        <div className="sc tg" data-icon="✅">
          <div className="sl">মোট</div>
          <div className="sv sv-g">{allTechniques.filter(t=>t.isPublic).length}</div>
        </div>
      </div>

      {/* Tabs */}
      <div className="atabs" style={{marginBottom:10}}>
        {[["pending","⏳ পেন্ডিং"],["approved","✅ Approved"],["rejected","❌ Rejected"],["all","📋 সব"]].map(([v,l])=>(
          <button key={v} className={`atab${tab===v?" on":""}`} onClick={()=>setTab(v)}>{l}
            {v==="pending"&&pendingCount>0&&<span style={{background:C.red,color:"#fff",borderRadius:"50%",fontSize:8,padding:"1px 4px",marginLeft:3}}>{pendingCount}</span>}
          </button>
        ))}
      </div>

      <div style={{fontSize:11,color:C.muted,marginBottom:8,display:"flex",justifyContent:"space-between"}}>
        <span>{loading?"⏳":`${filtered.length}টি`}</span>
        {loading&&<span>⏳</span>}
      </div>

      {/* List */}
      {loading&&!raw?[...Array(3)].map((_,i)=><div key={i} className="sk"/>):
       filtered.length===0?<div className="empty"><div className="ei">🧠</div><p>{tab==="pending"?"কোনো পেন্ডিং নেই 🎉":"কিছু নেই"}</p></div>:
       filtered.map((t,i)=>(
        <div key={t._pushKey||i} style={{
          background:C.card,border:`1px solid ${C.border}`,borderRadius:12,
          padding:12,marginBottom:8,
          borderLeft:`3px solid ${t.status==="approved"?C.green:t.status==="rejected"?C.red:C.yellow}`
        }}>
          {/* Header */}
          <div style={{display:"flex",alignItems:"center",gap:6,marginBottom:7,flexWrap:"wrap"}}>
            <div style={{width:28,height:28,borderRadius:"50%",background:`linear-gradient(135deg,${C.accent},${C.purple})`,display:"flex",alignItems:"center",justifyContent:"center",fontSize:11,fontWeight:700,color:"#fff",flexShrink:0}}>
              {(t.userName||"?").slice(0,1).toUpperCase()}
            </div>
            <div style={{flex:1,minWidth:0}}>
              <div style={{fontWeight:700,fontSize:12}}>{t.userName||"ব্যবহারকারী"}</div>
              <div style={{fontSize:10,color:C.muted}}>📱 {t.userId||"—"}</div>
            </div>
            <div style={{textAlign:"right"}}>
              <span style={{
                fontSize:9,fontWeight:700,padding:"2px 7px",borderRadius:6,
                background:t.status==="approved"?`${C.green}20`:t.status==="rejected"?`${C.red}20`:`${C.yellow}20`,
                color:t.status==="approved"?C.green:t.status==="rejected"?C.red:C.yellow,
                border:`1px solid ${t.status==="approved"?C.green:t.status==="rejected"?C.red:C.yellow}40`
              }}>
                {t.status==="approved"?"✅ Approved":t.status==="rejected"?"❌ Rejected":"⏳ Pending"}
              </span>
            </div>
          </div>

          {/* Question ID */}
          <div style={{fontSize:10,color:C.accent,marginBottom:5,fontWeight:600}}>
            ❓ প্রশ্ন ID: {t._qId||"—"}
          </div>

          {/* Technique text */}
          <div style={{
            background:C.panel,border:`1px solid ${C.border}`,borderRadius:8,
            padding:"8px 10px",fontSize:12,lineHeight:1.6,color:C.text,marginBottom:7
          }}>
            💡 {t.text||"(খালি)"}
          </div>

          <div style={{fontSize:10,color:C.muted,marginBottom:8}}>
            🕐 {tsFormat(t.timestamp)}
          </div>

          {/* Action buttons */}
          <div style={{display:"flex",gap:6}}>
            {(t.status==="pending"||!t.status)&&<>
              <button
                className="btn"
                style={{flex:2,justifyContent:"center",background:C.green,color:"#fff",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"approved")}
              >
                {busy===t._pushKey?"⏳...":"✅ Approve"}
              </button>
              <button
                className="btn"
                style={{flex:2,justifyContent:"center",background:C.red,color:"#fff",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"rejected")}
              >
                {busy===t._pushKey?"⏳...":"❌ Reject"}
              </button>
            </>}
            {t.status==="approved"&&(
              <button
                className="btn bg"
                style={{flex:2,justifyContent:"center",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"rejected")}
              >❌ Reject করুন</button>
            )}
            {t.status==="rejected"&&(
              <button
                className="btn"
                style={{flex:2,justifyContent:"center",background:C.green,color:"#fff",fontSize:11}}
                disabled={!!busy}
                onClick={()=>updateStatus(t,"approved")}
              >✅ Re-Approve</button>
            )}
            <button
              className="btn"
              style={{flex:1,justifyContent:"center",background:C.red+"22",color:C.red,border:`1px solid ${C.red}33`,fontSize:11}}
              disabled={!!busy}
              onClick={()=>deleteT(t)}
            >🗑️</button>
          </div>
        </div>
       ))
      }
    </div>
  );
}

const NAV=[
  {id:"dashboard",  icon:"📊", label:"Dashboard"},
  {id:"students",   icon:"👥", label:"Users",    badge:true},
  {id:"reports",    icon:"🚨", label:"Reports",  badge:true},
  {id:"content",    icon:"📋", label:"Content"},
  {id:"techniques", icon:"🧠", label:"Techniques",badge:true},
  {id:"notify",     icon:"📣", label:"Notify"},
  {id:"uploader",   icon:"📤", label:"Upload",
    children:[
      {id:"uploader", icon:"📝", label:"Bulk Upload"},
      {id:"aiimport", icon:"📸", label:"AI Import"},
    ]
  },
];


/* ══════════════════════════════════════════════════════════════════
   API SETTINGS  —  OCR auto-parse provider manager
   localStorage-এ সেভ → rebuild লাগে না, key চেঞ্জ করা যায়
   ══════════════════════════════════════════════════════════════════ */
const DEFAULT_PROVIDERS=[
  {id:"gemini",name:"Google Gemini",icon:"🟢",free:true,
   model:"gemini-2.0-flash",
   url:"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=",
   keyHint:"aistudio.google.com → Get API Key (Gmail, free, no card)",
   limit:"1500 req/day free"},
  {id:"mistral",name:"Mistral AI",icon:"🔵",free:true,
   model:"mistral-small-latest",
   url:"https://api.mistral.ai/v1/chat/completions",
   keyHint:"console.mistral.ai → API Keys",
   limit:"Free tier available"},
  {id:"openrouter",name:"OpenRouter",icon:"🟣",free:true,
   model:"mistralai/mistral-7b-instruct:free",
   url:"https://openrouter.ai/api/v1/chat/completions",
   keyHint:"openrouter.ai → Keys (free models, no card needed)",
   limit:"Free models available"},
];
const LS_PROV="ocr_api_providers";
function loadProviders(){
  try{
    const s=JSON.parse(localStorage.getItem(LS_PROV)||"{}");
    return DEFAULT_PROVIDERS.map(p=>({...p,key:s[p.id]?.key||"",active:s[p.id]?.active||false}));
  }catch{return DEFAULT_PROVIDERS.map(p=>({...p,key:"",active:false}));}
}
function saveProviders(providers){
  const o={};
  providers.forEach(p=>{o[p.id]={key:p.key,active:p.active};});
  localStorage.setItem(LS_PROV,JSON.stringify(o));
}
function getActiveProvider(){
  return loadProviders().find(p=>p.active&&p.key)||null;
}
const OCR_PROMPT=`তুমি একজন বাংলা MCQ প্রশ্নপত্র formatter।
নিচের OCR text থেকে সব MCQ প্রশ্ন বের করে নিচের format-এ দাও।
প্রশ্ন;অপশন১;অপশন২;অপশন৩;অপশন৪;সঠিকউত্তর
RULES:
- শুধু formatted data দাও, কোনো label বা explanation নয়
- Serial number বাদ দাও
- 2-column হলে প্রশ্ন নম্বর অনুযায়ী sort করো
- পৃষ্ঠা নম্বর, বিজ্ঞাপন, Facebook, প্রমোশনাল text বাদ দাও
- উ. ক/খ/গ/ঘ দেখে সঠিক option text দাও (letter নয়)
- field-এ ; থাকলে | দিয়ে replace করো
- কোনো প্রশ্ন বাদ দিও না
=== OCR TEXT ===
`;
async function callAiProvider(ocrText){
  const p=getActiveProvider();
  if(!p) return null; // no active provider = skip silently
  const prompt=OCR_PROMPT+ocrText;
  if(p.id==="gemini"){
    // Try multiple endpoints — 403 হলে পরেরটা try করে
    const models=["gemini-2.0-flash","gemini-1.5-flash","gemini-1.5-flash-latest"];
    const versions=["v1beta","v1"];
    let lastErr=null;
    for(const ver of versions){
      for(const model of models){
        try{
          const url=`https://generativelanguage.googleapis.com/${ver}/models/${model}:generateContent`;
          const res=await fetch(url,{
            method:"POST",
            headers:{
              "Content-Type":"application/json",
              "x-goog-api-key":p.key,   // header দিয়েও try
            },
            body:JSON.stringify({contents:[{parts:[{text:prompt}]}]}),
          });
          if(res.status===403){
            // API key restriction — user কে specific বলি
            const errBody=await res.json().catch(()=>({}));
            const reason=errBody?.error?.message||"";
            if(reason.includes("API_KEY_HTTP_REFERRER_BLOCKED")||reason.includes("referer")){
              throw new Error("API Key-এ HTTP Referrer restriction আছে → Google Cloud Console → Credentials → Edit Key → Restrictions → None করুন");
            }
            if(reason.includes("API not enabled")||reason.includes("has not been used")){
              throw new Error("Generative Language API enable নেই → console.cloud.google.com → APIs → Generative Language API → Enable");
            }
            lastErr=new Error(`403: ${reason||"Permission denied"}`);
            continue; // try next model/version
          }
          if(!res.ok){lastErr=new Error(`HTTP ${res.status}`);continue;}
          const d=await res.json();
          const text=d?.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
          if(text) return text;
          lastErr=new Error("Empty response");
        }catch(e){
          if(e.message.includes("Console")||e.message.includes("enable")) throw e; // user action needed
          lastErr=e;
        }
      }
    }
    throw lastErr||new Error("Gemini সব endpoint ব্যর্থ");
  }
  const headers={"Content-Type":"application/json","Authorization":"Bearer "+p.key};
  if(p.id==="openrouter") headers["HTTP-Referer"]="https://smartstudy.admin";
  const res=await fetch(p.url,{method:"POST",headers,
    body:JSON.stringify({model:p.model,messages:[{role:"user",content:prompt}],max_tokens:4096})});
  if(!res.ok) throw new Error(p.name+" HTTP "+res.status);
  const d=await res.json();
  return d?.choices?.[0]?.message?.content?.trim()||null;
}

function ApiSettingsPage({push,inline=false}){
  const[providers,setProviders]=React.useState(loadProviders);
  const[editing,setEditing]=React.useState(null);
  const[keyInput,setKeyInput]=React.useState("");
  const[testing,setTesting]=React.useState(null);
  const[showKey,setShowKey]=React.useState({});
  const active=providers.find(p=>p.active&&p.key);

  const doSetActive=(id)=>{
    const upd=providers.map(p=>({...p,active:p.id===id&&!!p.key}));
    setProviders(upd);saveProviders(upd);
    push("success","✅ Active করা হয়েছে!","OCR-এর পর auto parse হবে");
  };
  const doSaveKey=(id)=>{
    const upd=providers.map(p=>p.id===id?{...p,key:keyInput.trim()}:p);
    setProviders(upd);saveProviders(upd);setEditing(null);setKeyInput("");
    push("success","✅ Key সেভ হয়েছে!","এখন Active করুন");
  };
  const doDelete=(id)=>{
    const upd=providers.map(p=>p.id===id?{...p,key:"",active:false}:p);
    setProviders(upd);saveProviders(upd);
    push("warn","Key মুছে দেওয়া হয়েছে","");
  };
  const doTest=async(p)=>{
    if(!p.key){push("warn","আগে Key দিন","");return;}
    setTesting(p.id);
    try{
      const tmp=[...providers].map(x=>({...x,active:x.id===p.id}));
      saveProviders(tmp);
      const r=await callAiProvider("১. বাংলাদেশের রাজধানী কোনটি?\nক. ঢাকা খ. চট্টগ্রাম গ. খুলনা ঘ. রাজশাহী উ. ক");
      saveProviders(providers);
      if(r&&r.includes(";")) push("success","✅ "+p.name+" কাজ করছে!",r.substring(0,80));
      else push("warn","⚠️ Response অদ্ভুত",(r||"empty").substring(0,60));
    }catch(e){push("error","❌ "+p.name+" ব্যর্থ",e.message);saveProviders(providers);}
    setTesting(null);
  };

  return(
    <div className={inline?"":"page"} style={inline?{marginBottom:14}:{}}>
      {!inline&&<div style={{background:"linear-gradient(135deg,#0f172a,#1e3a5f)",borderRadius:14,
        padding:"14px 16px",marginBottom:14,color:"#fff"}}>
        <div style={{fontWeight:900,fontSize:16}}>🔑 API Key Settings</div>
        <div style={{fontSize:11,opacity:.8,marginTop:2}}>OCR-এর পর auto parse — একটাই active থাকবে</div>
      </div>}
      <div style={{background:active?"#052e16":"#450a0a",borderRadius:10,
        padding:"10px 14px",marginBottom:12,
        border:"1px solid "+(active?"#16a34a":"#991b1b")}}>
        {active
          ?<span style={{color:"#4ade80",fontWeight:700}}>✅ Active: {active.icon} {active.name} — {active.limit}</span>
          :<span style={{color:"#f87171",fontWeight:700}}>⚠️ কোনো provider active নেই — নিচে key দিয়ে Active করুন</span>}
      </div>
      {providers.map(p=>(
        <div key={p.id} style={{background:C.card,borderRadius:12,marginBottom:10,
          border:"2px solid "+(p.active?"#6366f1":C.border),overflow:"hidden"}}>
          <div style={{padding:"12px 14px",display:"flex",alignItems:"center",gap:10}}>
            <span style={{fontSize:22}}>{p.icon}</span>
            <div style={{flex:1}}>
              <div style={{fontWeight:800,color:C.text,fontSize:14}}>{p.name}</div>
              <div style={{fontSize:10,color:C.sub}}>{p.limit}</div>
            </div>
            {p.active&&p.key&&<span style={{background:"#4f46e5",color:"#fff",fontSize:10,
              fontWeight:700,borderRadius:999,padding:"2px 8px"}}>ACTIVE</span>}
          </div>
          <div style={{padding:"0 14px 12px"}}>
            {editing===p.id?(
              <div>
                <div style={{fontSize:11,color:C.sub,marginBottom:4}}>{p.keyHint}</div>
                <input style={{width:"100%",background:C.input,border:"1px solid "+C.border,
                  borderRadius:8,padding:"8px 10px",color:C.text,fontSize:12,
                  fontFamily:"monospace",boxSizing:"border-box",marginBottom:6}}
                  placeholder={p.name+" API Key..."}
                  value={keyInput} onChange={e=>setKeyInput(e.target.value)} autoFocus/>
                <div style={{display:"flex",gap:6}}>
                  <button onClick={()=>doSaveKey(p.id)} style={{flex:1,background:"#4f46e5",
                    color:"#fff",border:"none",borderRadius:8,padding:8,fontWeight:700,fontSize:12}}>
                    💾 Save</button>
                  <button onClick={()=>{setEditing(null);setKeyInput("");}}
                    style={{background:C.border,color:C.text,border:"none",borderRadius:8,padding:"8px 12px",fontSize:12}}>
                    বাতিল</button>
                </div>
              </div>
            ):p.key?(
              <div>
                <div style={{background:C.input,borderRadius:8,padding:"7px 10px",
                  marginBottom:8,display:"flex",alignItems:"center",gap:6}}>
                  <span style={{flex:1,fontFamily:"monospace",fontSize:11,color:C.sub,wordBreak:"break-all"}}>
                    {showKey[p.id]?p.key:p.key.substring(0,8)+"••••••••••••"+p.key.slice(-4)}</span>
                  <button onClick={()=>setShowKey(v=>({...v,[p.id]:!v[p.id]}))}
                    style={{background:"none",border:"none",color:C.sub,fontSize:14,cursor:"pointer"}}>
                    {showKey[p.id]?"🙈":"👁️"}</button>
                </div>
                <div style={{display:"flex",gap:6,flexWrap:"wrap"}}>
                  {!p.active&&<button onClick={()=>doSetActive(p.id)}
                    style={{flex:1,background:"#4f46e5",color:"#fff",border:"none",
                      borderRadius:8,padding:7,fontWeight:700,fontSize:12}}>⚡ Active</button>}
                  <button onClick={()=>doTest(p)} disabled={!!testing}
                    style={{flex:1,background:"#065f46",color:"#fff",border:"none",
                      borderRadius:8,padding:7,fontWeight:700,fontSize:12}}>
                    {testing===p.id?"⏳...":"🧪 Test"}</button>
                  <button onClick={()=>{setEditing(p.id);setKeyInput(p.key);}}
                    style={{background:C.border,color:C.text,border:"none",borderRadius:8,padding:"7px 10px",fontSize:12}}>✏️</button>
                  <button onClick={()=>doDelete(p.id)}
                    style={{background:"#450a0a",color:"#f87171",border:"none",borderRadius:8,padding:"7px 10px",fontSize:12}}>🗑️</button>
                </div>
              </div>
            ):(
              <div>
                <div style={{fontSize:11,color:C.sub,marginBottom:6}}>{p.keyHint}</div>
                <button onClick={()=>{setEditing(p.id);setKeyInput("");}}
                  style={{width:"100%",background:"#1e3a5f",color:"#93c5fd",border:"none",
                    borderRadius:8,padding:8,fontWeight:700,fontSize:12}}>🔑 Key যোগ করুন</button>
              </div>
            )}
          </div>
        </div>
      ))}
      <div style={{background:C.card,borderRadius:10,padding:"12px 14px",
        border:"1px solid "+C.border,fontSize:11,color:C.sub,lineHeight:1.8}}>
        <div style={{fontWeight:700,color:C.text,marginBottom:4}}>💡 কীভাবে কাজ করে</div>
        <div>📸 ছবি → MLKit text বের করে → Active provider parse করে</div>
        <div>✅ শুধু text যায়, image নয় → load নেই, fast</div>
        <div>🔄 Key চেঞ্জ করলে rebuild লাগে না</div>
        <div>🔒 Key device-এ localStorage-এ সংরক্ষিত</div>
        <div>⚡ Provider active না থাকলে local Java parser ব্যবহার হয়</div>
      </div>
    </div>
  );
}

/* ══════════ LOGIN SCREEN ══════════ */
function LoginScreen({onLogin}){
  const[email,setEmail]=useState(localStorage.getItem("fb_email")||"");
  const[pass,setPass]=useState("");
  const[loading,setLoading]=useState(false);
  const[err,setErr]=useState("");

  // Auto-login if saved credentials exist
  useEffect(()=>{
    const savedEmail=localStorage.getItem("fb_email");
    const savedPass=localStorage.getItem("fb_pass_enc");
    if(savedEmail&&savedPass){
      setLoading(true);
      _LC.auth("autoLogin", `Auto-login attempt: ${savedEmail}`);
      signInWithEmail(savedEmail,atob(savedPass))
        .then(()=>{ _LC.auth("autoLogin", `Auto-login SUCCESS: ${savedEmail}`); onLogin(); })
        .catch((e)=>{ _LC.error("autoLogin", `Auto-login FAILED: ${e?.message}`, { email: savedEmail }); localStorage.removeItem("fb_email");localStorage.removeItem("fb_pass_enc");setLoading(false); });
    } else {
      _LC.lifecycle("LoginScreen", "Login screen shown — no saved credentials");
    }
  },[]);

  const doLogin=async()=>{
    if(!email||!pass){setErr("Email ও Password দিন");_LC.warn("doLogin","Login attempted with empty fields");return;}
    setLoading(true);setErr("");
    try{
      await signInWithEmail(email,pass);
      _LC.auth("doLogin", `Manual login SUCCESS: ${email}`);
      onLogin();
    }catch(e){
      _LC.error("doLogin", `Manual login FAILED: ${e?.message}`, { email });
      setErr(e.message||"Login ব্যর্থ");setLoading(false);
    }
  };

  return(
    <div style={{minHeight:"100dvh",display:"flex",alignItems:"center",justifyContent:"center",background:"#06080f",padding:24}}>
      <div style={{width:"100%",maxWidth:360}}>
        <div style={{textAlign:"center",marginBottom:32}}>
          <div style={{fontSize:42,marginBottom:8}}>📊</div>
          <div style={{fontSize:20,fontWeight:700,color:"#e2e8f0"}}>Smart Study Admin</div>
          <div style={{fontSize:12,color:"#4b5e7a",marginTop:4}}>Firebase অ্যাডমিন প্যানেল</div>
        </div>
        <div style={{background:"#0c1220",border:"1px solid #16253d",borderRadius:16,padding:20}}>
          <div style={{marginBottom:12}}>
            <label style={{display:"block",fontSize:10,fontWeight:700,color:"#4b5e7a",letterSpacing:".8px",marginBottom:4,textTransform:"uppercase"}}>Email</label>
            <input
              style={{background:"#0e1a2e",border:"1px solid #16253d",borderRadius:9,padding:"10px 12px",color:"#e2e8f0",fontFamily:"inherit",fontSize:13,width:"100%",outline:"none",boxSizing:"border-box"}}
              type="email" placeholder="admin@example.com"
              value={email} onChange={e=>setEmail(e.target.value)}
              onKeyDown={e=>e.key==="Enter"&&doLogin()}
            />
          </div>
          <div style={{marginBottom:16}}>
            <label style={{display:"block",fontSize:10,fontWeight:700,color:"#4b5e7a",letterSpacing:".8px",marginBottom:4,textTransform:"uppercase"}}>Password</label>
            <input
              style={{background:"#0e1a2e",border:"1px solid #16253d",borderRadius:9,padding:"10px 12px",color:"#e2e8f0",fontFamily:"inherit",fontSize:13,width:"100%",outline:"none",boxSizing:"border-box"}}
              type="password" placeholder="••••••••"
              value={pass} onChange={e=>setPass(e.target.value)}
              onKeyDown={e=>e.key==="Enter"&&doLogin()}
            />
          </div>
          {err&&<div style={{background:"#ef444418",border:"1px solid #ef444430",borderRadius:8,padding:"8px 12px",fontSize:11,color:"#ef4444",marginBottom:12,fontWeight:600}}>{err}</div>}
          <button
            onClick={doLogin} disabled={loading}
            style={{width:"100%",padding:"11px",background:"#3b82f6",color:"#fff",border:"none",borderRadius:9,fontSize:13,fontWeight:700,cursor:"pointer",opacity:loading?0.6:1}}
          >{loading?"⏳ লগইন হচ্ছে...":"🔐 লগইন করুন"}</button>
        </div>
      </div>
    </div>
  );
}

export default function App(){
  // ── Android system back button — modal থাকলে close, নইলে double-back-to-exit ──
  useEffect(()=>{
    let _depth=0;
    const inc=()=>_depth++;
    const dec=()=>{_depth=Math.max(0,_depth-1);};
    window.addEventListener("modal-open",inc);
    window.addEventListener("modal-close",dec);
    const onBack=()=>{
      if(_depth>0) window.dispatchEvent(new Event("back-press"));
      // depth===0 হলে MainActivity এর double-back-to-exit কাজ করবে
    };
    window.addEventListener("androidBackButton",onBack);
    return()=>{
      window.removeEventListener("modal-open",inc);
      window.removeEventListener("modal-close",dec);
      window.removeEventListener("androidBackButton",onBack);
    };
  },[]);

  const[loggedIn,setLoggedIn]=useState(()=>{
    // Check if we have saved credentials — will auto-login in LoginScreen
    return false;
  });
  const[page,setPage]=useState("dashboard");
  const[toasts,push]=useToasts();
  const[tick,setTick]=useState(0);
  const[spin,setSpin]=useState(false);
  const[bulkPrefill,setBulkPrefill]=useState("");
  const[searchDetail,setSearchDetail]=useState(null);
  const backStack=useRef(["dashboard"]);
  const modalOpen=useRef(false);

  // Badge counts — must be here (Rules of Hooks: no hooks after early return)
  const{data:usersRawBadge}=useFB("Users",tick);
  const{data:reportsRawBadge}=useFB("Reports",tick);
  const{data:techRawBadge}=useFB("Techniques",tick);

  // ALL hooks must be called unconditionally (Rules of Hooks)
  const goPage=useCallback((p)=>{
    _LC.lifecycle("navigate", `Page → ${p}`);
    setPage(prev=>{
      if(prev!==p){ backStack.current=[...backStack.current.filter(x=>x!==p),p]; }
      return p;
    });
  },[]);

  // ── FCM Notification click → page navigate (deeplink) ──
  useEffect(()=>{
    const onNavTo = (e) => {
      try {
        const data = typeof e.detail === "string" ? JSON.parse(e.detail) : e.detail;
        const pg = data?.page || "";
        if(!pg || !loggedIn) return;
        if(pg === "reports")    { goPage("reports");    }
        if(pg === "techniques") { goPage("techniques"); }
        _LC.info("FCM","📲 Deeplink nav to: " + pg);
      } catch(_) {}
    };
    window.addEventListener("adminNavTo", onNavTo);
    return () => window.removeEventListener("adminNavTo", onNavTo);
  }, [loggedIn, goPage]);

  // ══════════════════════════════════════════════════════════
  //  LAYERED BACK STACK
  //  প্রতিটা layer push/pop হয়। back চাপলে top layer pop হয়।
  //  Layer types: modal | sublayer | page | exit-confirm
  // ══════════════════════════════════════════════════════════
  const layerStack = useRef([]); // [{type, pop}]
  const[exitConfirm,setExitConfirm]=useState(false);
  const exitTimer=useRef(null);
  const[uploadMenuOpen,setUploadMenuOpen]=useState(false);

  // Layer push — যেকোনো component call করবে
  const pushLayer = useCallback((popFn)=>{
    const id = Date.now() + Math.random();
    layerStack.current = [...layerStack.current, {id, pop: popFn}];
    return ()=>{
      layerStack.current = layerStack.current.filter(l=>l.id!==id);
    };
  },[]);

  // Global modal-open/close events → layer stack এ যাবে
  useEffect(()=>{
    if(!loggedIn) return;
    // modal-open event এ layer push (useModalBack থেকে আসে)
    const onModalOpen = (e)=>{
      // back-press event dispatch হলে top modal close হবে
      modalOpen.current = true;
    };
    const onModalClose=()=>{ modalOpen.current=false; };
    window.addEventListener("modal-open",  onModalOpen);
    window.addEventListener("modal-close", onModalClose);
    return()=>{
      window.removeEventListener("modal-open",  onModalOpen);
      window.removeEventListener("modal-close", onModalClose);
    };
  },[loggedIn]);

  useEffect(()=>{
    if(!loggedIn) return;
    const handleBack=(e)=>{
      if(e&&e.preventDefault) e.preventDefault();

      // 1. SearchDetail (Student profile from search)
      if(searchDetail){ setSearchDetail(null); return; }

      // 2. Modal খোলা → modal close
      if(modalOpen.current){
        window.dispatchEvent(new Event("back-press"));
        return;
      }

      // 3. Sub-layer stack এ কিছু আছে → pop
      if(layerStack.current.length>0){
        const top=layerStack.current[layerStack.current.length-1];
        layerStack.current=layerStack.current.slice(0,-1);
        try{ top.pop(); } catch(_){}
        return;
      }

      // 4. Page back
      if(page!=="dashboard"){
        const stack=backStack.current;
        if(stack.length>1){
          const ns=stack.slice(0,-1);
          backStack.current=ns;
          setPage(ns[ns.length-1]);
        } else {
          setPage("dashboard");
          backStack.current=["dashboard"];
        }
        return;
      }

      // 5. Dashboard এ → exit confirm (2 সেকেন্ড)
      if(exitConfirm){
        clearTimeout(exitTimer.current);
        setExitConfirm(false);
        if(window.Capacitor?.Plugins?.App) window.Capacitor.Plugins.App.exitApp();
        else window.close();
        return;
      }
      setExitConfirm(true);
      exitTimer.current=setTimeout(()=>setExitConfirm(false),2000);
    };

    // Capacitor back button + browser popstate
    document.addEventListener("backbutton",handleBack,false);
    window.addEventListener("androidBackButton",handleBack);
    return()=>{
      document.removeEventListener("backbutton",handleBack,false);
      window.removeEventListener("androidBackButton",handleBack);
      clearTimeout(exitTimer.current);
    };
  },[loggedIn,page,searchDetail,exitConfirm]);

  const refresh=useCallback(()=>{
    setSpin(true);invalidateAll();setTick(t=>t+1);
    setTimeout(()=>setSpin(false),1400);
  },[]);

  useEffect(()=>{
    if(!loggedIn) return;
    const id=setInterval(()=>setTick(t=>t+1),120_000);
    return()=>clearInterval(id);
  },[loggedIn]);

  /* ── নতুন Report detect করে clickable notification দেখাও ── */
  const seenReportKeys=useRef(new Set());
  const[reportAlert,setReportAlert]=useState(null); // {count, keys[]}
  useEffect(()=>{
    if(!loggedIn)return;
    // প্রতি ৩০ সেকেন্ডে Reports চেক করো
    const checkReports=async()=>{
      try{
        const raw=await fbGet("Reports");
        if(!raw||typeof raw!=="object")return;
        const entries=Object.entries(raw);
        const newKeys=entries
          .map(([k])=>k)
          .filter(k=>!seenReportKeys.current.has(k));
        if(newKeys.length>0&&seenReportKeys.current.size>0){
          // প্রথমবার load হলে শুধু mark করো, notification দেখাবো না
          setReportAlert({count:newKeys.length,keys:newKeys});
        }
        entries.forEach(([k])=>seenReportKeys.current.add(k));
      }catch(_){}
    };
    checkReports(); // initial load
    const id=setInterval(checkReports,30_000);
    return()=>clearInterval(id);
  },[loggedIn]);

  useEffect(()=>{
    if(!loggedIn) return;
    const cap=window.Capacitor;
    if(!cap?.Plugins?.PushNotifications) return;

    const PN=cap.Plugins.PushNotifications;

    // ── Permission চাও ──
    PN.requestPermissions().then(result=>{
      if(result.receive==="granted"){
        PN.register();
      }
    }).catch(()=>{});

    // ── Token পেলে Firebase AdminAppFCM-এ save করো ──
    PN.addListener("registration", async(tokenData)=>{
      try{
        const token=tokenData?.value||tokenData?.token||"";
        if(!token||token.length<10) return;
        // token-এর hash key হিসেবে শেষ ১৬ char ব্যবহার করো
        const key="admin_"+token.slice(-16).replace(/[^a-zA-Z0-9]/g,"_");
        await fbSet(`AdminAppFCM/${key}`,{token,savedAt:nowTs(),app:"admin"});
        console.log("✅ Admin FCM token saved:",key);
        _LC.info("FCM", `FCM token saved: ${key}`);
      }catch(e){ console.warn("FCM token save error",e); _LC.error("FCM",`FCM token save error: ${e?.message}`,{key}); }
    });

    // ── Notification tap হলে সঠিক page-এ যাও ──
    const handler=(event)=>{
      try{
        const data=event?.notification?.data||event?.data||{};
        const url=data.url||data.url_key||"";
        const pageMap={
          reports:"reports",techniques:"techniques",
          students:"students",dashboard:"dashboard",
          notify:"notify",content:"content",uploader:"uploader",
          new_report:"reports", // type দিয়েও navigate
        };
        const target=pageMap[url]||pageMap[data.type]||null;
        if(target) goPage(target);
      } catch(e){ console.warn("Push nav error",e); _LC.error("pushNav",`Push notification nav error: ${e?.message}`); }
    };
    PN.addListener("pushNotificationActionPerformed", handler);

    return()=>{ try{ PN.removeAllListeners(); }catch(e){} };
  },[loggedIn,goPage]);

  // ── Badge counts & derived values (must be before early returns — Rules of Hooks) ──
  const signupBadge=useMemo(()=>{
    const arr=toArr(usersRawBadge);
    return arr.filter(u=>{
      const st=(u.Status||u.status||"").toLowerCase();
      return st==="inactive"||st===""||st==="pending";
    }).length;
  },[usersRawBadge]);

  const reportBadge=useMemo(()=>{
    const arr=toArr(reportsRawBadge);
    return arr.filter(r=>!r.resolved&&!r.Resolved).length;
  },[reportsRawBadge]);

  const techBadge=useMemo(()=>{
    const arr=toArr(techRawBadge);
    return arr.filter(t=>!t.approved&&!t.Approved).length;
  },[techRawBadge]);

  const badgeMap={students:signupBadge,reports:reportBadge,techniques:techBadge};
  const pageLabel=NAV.find(n=>n.id===page) ||
    NAV.flatMap(n=>n.children||[]).find(c=>c.id===page);

  // ── Render ──
  if(!loggedIn) return(
    <ErrorBoundary>
      <style>{css}</style>
      <LoginScreen onLogin={()=>{ _LC.lifecycle("App","User logged in — entering admin panel"); setLoggedIn(true); _saveAdminFcmToken(); }}/>
    </ErrorBoundary>
  );

  if(searchDetail)return(
    <ErrorBoundary>
      <>
      <style>{css}</style>
      <StudentDetail user={searchDetail} onBack={()=>setSearchDetail(null)} push={push}/>
      <Toasts t={toasts}/>
      </>
    </ErrorBoundary>
  );

  return(
    <ErrorBoundary>
      <>
      <style>{css}</style>
      <div className="topbar">
        <div>
          <div className="topbar-title">{pageLabel?.icon} {pageLabel?.label}</div>
          <div className="topbar-sub">Smart Study Admin</div>
        </div>
        <div style={{display:"flex",gap:6}}>
          <button className={`icon-btn${spin?" spin":""}`} onClick={refresh}>🔄</button>
          <button className="icon-btn" title="Logout" onClick={()=>{ _LC.auth("logout","Admin logged out manually"); localStorage.removeItem("fb_email");localStorage.removeItem("fb_pass_enc");localStorage.removeItem("fb_refresh_token");window.__adminIdToken=null;_idToken=null;setLoggedIn(false); }}><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg></button>
        </div>
      </div>

      <div style={{display:page==="dashboard"?"block":"none"}}><DashboardPage push={push} tick={tick}/></div>
      <div style={{display:page==="students" ?"block":"none"}}><StudentsPage  push={push} tick={tick} pushLayer={pushLayer}/></div>
      <div style={{display:page==="reports"  ?"block":"none"}}><ReportsPage   push={push} tick={tick}/></div>
      <div style={{display:page==="content"  ?"block":"none"}}><ContentManagerPage push={push} tick={tick} pushLayer={pushLayer}/></div>
      <div style={{display:page==="techniques"?"block":"none"}}><TechniquesPage push={push} tick={tick}/></div>
      <div style={{display:page==="notify"   ?"block":"none"}}><NotifyPage    push={push} tick={tick}/></div>
      <div style={{display:page==="uploader" ?"block":"none"}}><BulkUploaderPage push={push} prefillText={bulkPrefill} onClearPrefill={()=>setBulkPrefill("")}/></div>
      <div style={{display:page==="aiimport"?"block":"none"}}><AIImportPage push={push} onSendToBulk={txt=>{setBulkPrefill(txt);goPage("uploader");}}/></div>

      <nav className="bottom-nav">
        {NAV.map(n=>{
          const cnt=badgeMap[n.id]||0;
          const isActive=n.children?n.children.some(c=>c.id===page):page===n.id;
          return(
            <button key={n.id} className={`nav-btn${isActive?" active":""}`}
              onClick={()=>{
                if(n.children){
                  // Upload — sub-menu popup দেখাবে
                  setUploadMenuOpen(v=>!v);
                } else {
                  goPage(n.id);
                  setUploadMenuOpen(false);
                }
              }}>
              <span className="nav-icon" style={{position:"relative",display:"inline-block"}}>
                {n.icon}
                {cnt>0&&(
                  <span style={{position:"absolute",top:-5,right:-7,background:"#ef4444",color:"#fff",
                    fontSize:8,fontWeight:900,borderRadius:999,minWidth:14,height:14,
                    display:"flex",alignItems:"center",justifyContent:"center",padding:"0 3px",
                    lineHeight:1}}>
                    {cnt>99?"99+":cnt}
                  </span>
                )}
              </span>
              <span>{n.label}{n.children?"▾":""}</span>
            </button>
          );
        })}
        {/* Upload sub-menu popup */}
        {uploadMenuOpen&&(()=>{
          const uploadNav=NAV.find(n=>n.children);
          if(!uploadNav)return null;
          return(
            <div style={{
              position:"fixed",bottom:64,left:"50%",transform:"translateX(-50%)",
              background:C.card,border:`1px solid ${C.border}`,
              borderRadius:14,padding:"8px",zIndex:9998,
              display:"flex",gap:6,boxShadow:"0 -4px 20px #0009",
              minWidth:200,
            }}>
              {uploadNav.children.map(c=>(
                <button key={c.id}
                  onClick={()=>{goPage(c.id);setUploadMenuOpen(false);}}
                  style={{
                    flex:1,padding:"10px 8px",borderRadius:10,border:"none",cursor:"pointer",
                    background:page===c.id?`${C.accent}22`:"transparent",
                    color:page===c.id?C.accent:C.text,
                    fontSize:11,fontWeight:700,
                    display:"flex",flexDirection:"column",alignItems:"center",gap:4,
                  }}>
                  <span style={{fontSize:20}}>{c.icon}</span>
                  <span>{c.label}</span>
                </button>
              ))}
            </div>
          );
        })()}
      </nav>
      <Toasts t={toasts}/>
      <BgTaskIndicator/>
      {exitConfirm&&(
        <div style={{
          position:"fixed",bottom:80,left:"50%",transform:"translateX(-50%)",
          background:"#1e293b",border:"1px solid #334155",
          borderRadius:12,padding:"10px 20px",
          fontSize:13,color:"#e2e8f0",fontWeight:600,
          zIndex:9999,whiteSpace:"nowrap",
          boxShadow:"0 4px 20px #0008",
          animation:"ti .2s ease",
        }}>
          আবার Back চাপুন বন্ধ করতে
        </div>
      )}
    </>
    </ErrorBoundary>
  );
}
